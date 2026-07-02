package com.baseerah.seed;

import com.baseerah.account.Account;
import com.baseerah.client.Client;
import com.baseerah.client.ClientRepository;
import com.baseerah.seed.dto.SeedEnvelope;
import com.baseerah.seed.dto.SeedTransaction;
import com.baseerah.transaction.Direction;
import com.baseerah.transaction.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Ingests the five {@code data-mocks/*.json} SAMA Open-Banking payloads into Postgres on startup
 * (DESIGN.md §3, §4), so every downstream feature has realistic seeded telemetry.
 *
 * <p><strong>Idempotent at client granularity:</strong> a file is skipped in full when its client's
 * {@code external_id} already exists, making re-seeding a cheap no-op. (A file partially loaded after a
 * crash is therefore also skipped — recovery is a manual truncate/re-seed, out of scope here.)
 *
 * <p>Runs as an {@link ApplicationRunner} after the context is ready and Liquibase has migrated the
 * schema. Each client's object graph is persisted through a single cascading
 * {@link ClientRepository#save} call, which is itself transactional.
 */
@Component
@EnableConfigurationProperties(SeedProperties.class)
public class MockDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MockDataSeeder.class);

    /** Bank a filename-derived {@code external_id} maps to when it has no {@code acc-} prefix. */
    private static final String ACCOUNT_PREFIX = "acc-";

    private final ClientRepository clientRepository;
    private final SeedProperties properties;
    private final ObjectMapper objectMapper;

    public MockDataSeeder(ClientRepository clientRepository, SeedProperties properties,
            ObjectMapper objectMapper) {
        this.clientRepository = clientRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            log.info("Seeding disabled (baseerah.seed.enabled=false) — skipping mock data load.");
            return;
        }

        Path dir = resolveDataMocksDir(properties.dataMocksPath());
        if (dir == null) {
            log.warn("Seed directory '{}' not found (searched working dir and parents) — nothing seeded.",
                    properties.dataMocksPath());
            return;
        }

        List<Path> files = listJsonFiles(dir);
        if (files.isEmpty()) {
            log.warn("No *.json files under {} — nothing seeded.", dir.toAbsolutePath());
            return;
        }

        log.info("Seeding from {} ({} file(s)).", dir.toAbsolutePath(), files.size());
        int clients = 0;
        int accounts = 0;
        int transactions = 0;
        for (Path file : files) {
            FileSummary summary = seedFile(file);
            if (summary != null) {
                clients++;
                accounts += summary.accounts();
                transactions += summary.transactions();
            }
        }
        log.info("Seeding complete: {} new client(s), {} account(s), {} transaction(s).",
                clients, accounts, transactions);
    }

    /**
     * Loads one persona file, or returns {@code null} if it was skipped (client already present).
     * The whole graph — client, its accounts, and their transactions — is saved atomically via cascade.
     */
    private FileSummary seedFile(Path file) {
        SeedEnvelope envelope = read(file);
        List<SeedTransaction> txns = envelope.data() == null ? List.of() : envelope.data().transactions();
        if (txns == null || txns.isEmpty()) {
            log.warn("Skipping {} — no transactions.", file.getFileName());
            return null;
        }

        String clientExternalId = deriveClientExternalId(file, txns);
        if (clientRepository.existsByExternalId(clientExternalId)) {
            log.info("Skipping {} — client '{}' already seeded.", file.getFileName(), clientExternalId);
            return null;
        }

        String profile = envelope.data().clientProfile();
        Client client = new Client(clientExternalId, profile, profile);

        // Group transactions by their account_id, preserving first-seen order for stable logging.
        Map<String, List<SeedTransaction>> byAccount = new LinkedHashMap<>();
        for (SeedTransaction tx : txns) {
            byAccount.computeIfAbsent(tx.accountId(), k -> new ArrayList<>()).add(tx);
        }

        Instant envelopeTimestamp = parse(envelope.timestamp());
        List<String> imputedIds = new ArrayList<>();

        int accountCount = 0;
        int txnCount = 0;
        for (Map.Entry<String, List<SeedTransaction>> entry : byAccount.entrySet()) {
            String accountExternalId = entry.getKey();
            List<SeedTransaction> accountTxns = entry.getValue();

            // Resolve a booking date per transaction, interpolating any that are missing (below).
            List<Instant> bookingDates = resolveBookingDates(accountTxns, envelopeTimestamp, imputedIds);

            int mostRecent = 0;
            for (int i = 1; i < bookingDates.size(); i++) {
                if (bookingDates.get(i).isAfter(bookingDates.get(mostRecent))) {
                    mostRecent = i;
                }
            }
            BigDecimal latestBalance = accountTxns.get(mostRecent).balance().amount().amount();
            String currency = accountTxns.get(0).amount().currency();

            Account account = new Account(client, accountExternalId, bankFor(accountExternalId),
                    colorFor(accountExternalId), currency, latestBalance,
                    tokenize(accountExternalId));
            client.getAccounts().add(account);

            for (int i = 0; i < accountTxns.size(); i++) {
                account.getTransactions().add(toTransaction(account, accountTxns.get(i), bookingDates.get(i)));
                txnCount++;
            }
            accountCount++;
        }

        clientRepository.save(client); // cascades accounts + transactions
        log.info("Seeded client '{}' ({}): {} account(s), {} transaction(s).",
                clientExternalId, profile, accountCount, txnCount);
        if (!imputedIds.isEmpty()) {
            log.warn("Client '{}': imputed booking_date for {} transaction(s) missing booking_date_time in "
                    + "the source (interpolated PLACEHOLDER between dated neighbours — review/adjust if the "
                    + "true dates matter): {}", clientExternalId, imputedIds.size(), imputedIds);
        }
        return new FileSummary(accountCount, txnCount);
    }

    private Transaction toTransaction(Account account, SeedTransaction tx, Instant bookingDate) {
        return new Transaction(
                account,
                tx.transactionId(),
                Direction.valueOf(tx.creditDebitIndicator().trim().toUpperCase()),
                tx.amount().amount(),
                tx.amount().currency(),
                tx.transactionInformation(),
                tx.insights() == null ? null : tx.insights().descriptionCleansed(),
                tx.insights() == null ? null : tx.insights().category(),
                tx.insights() == null ? null : tx.insights().categoryConfidence(),
                bookingDate,
                tx.balance().amount().amount());
    }

    /**
     * Resolves a non-null {@code booking_date} for every transaction in an account. Real dates are parsed
     * as-is; a small number of source rows (notably the freelancer persona) omit {@code booking_date_time}
     * entirely, and the column is {@code NOT NULL} while counts must match the source — so those are
     * <strong>imputed</strong>. The mock array is confirmed newest-first, so a missing date is placed at
     * the midpoint of its nearest real-dated neighbours (or that single neighbour at an edge, or the file
     * timestamp if an account has no dates at all). Imputed transaction ids are collected into
     * {@code imputedIds} for logging so an operator can spot and adjust the placeholders.
     */
    private List<Instant> resolveBookingDates(List<SeedTransaction> txns, Instant fallback,
            List<String> imputedIds) {
        int n = txns.size();
        Instant[] parsed = new Instant[n];
        for (int i = 0; i < n; i++) {
            parsed[i] = parse(txns.get(i).bookingDateTime());
        }

        List<Instant> resolved = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (parsed[i] != null) {
                resolved.add(parsed[i]);
                continue;
            }
            Instant newer = null;
            for (int j = i - 1; j >= 0; j--) {
                if (parsed[j] != null) {
                    newer = parsed[j];
                    break;
                }
            }
            Instant older = null;
            for (int j = i + 1; j < n; j++) {
                if (parsed[j] != null) {
                    older = parsed[j];
                    break;
                }
            }
            Instant imputed;
            if (newer != null && older != null) {
                imputed = Instant.ofEpochMilli((newer.toEpochMilli() + older.toEpochMilli()) / 2);
            } else if (newer != null) {
                imputed = newer;
            } else if (older != null) {
                imputed = older;
            } else {
                imputed = fallback;
            }
            resolved.add(imputed);
            imputedIds.add(txns.get(i).transactionId());
        }
        return resolved;
    }

    /** Parses an ISO-8601 instant, tolerating {@code null}/blank (returns {@code null}). */
    private static Instant parse(String isoInstant) {
        return (isoInstant == null || isoInstant.isBlank()) ? null : Instant.parse(isoInstant);
    }

    // ── Derivation helpers ──────────────────────────────────────────────────────────────────────

    /**
     * The client's stable {@code external_id}. The file <em>is</em> the client, so we derive it from the
     * filename (e.g. {@code client_001_family_6_months_data.json → client_001_family}); if the pattern
     * does not match we fall back to the account_id convention ({@code acc-<id> → <id>}).
     */
    private String deriveClientExternalId(Path file, List<SeedTransaction> txns) {
        String stem = file.getFileName().toString().replaceFirst("\\.json$", "");
        String fromName = stem.replaceFirst("_(\\d+_months_)?data$", "");
        if (!fromName.equals(stem)) {
            return fromName;
        }
        String accountId = txns.get(0).accountId();
        return accountId.startsWith(ACCOUNT_PREFIX) ? accountId.substring(ACCOUNT_PREFIX.length()) : accountId;
    }

    // TODO(learning): deterministic bank + colour assignment lives in bankFor()/colorFor() below.

    private static final List<String> BANKS = List.of(
            "Al Rajhi Bank", "Saudi National Bank", "Riyad Bank", "Banque Saudi Fransi",
            "Arab National Bank", "Alinma Bank", "Bank Albilad", "The Saudi Investment Bank");

    /** Account-badge colours from the DESIGN.md §8 palette (teal, gold, green, orange, red, deep teal). */
    private static final List<String> PALETTE = List.of(
            "#0e6b54", "#C4A24C", "#1D9E63", "#E5A63A", "#E0574F", "#0a3d33");

    /**
     * Deterministically assigns a Saudi bank to an account. Uses {@link String#hashCode()} — which the
     * language spec fixes for a given string across JVMs and reboots — so the same account id always
     * yields the same bank, keeping the multi-bank demo badge (DESIGN.md §7) stable across re-seeds.
     */
    private String bankFor(String accountExternalId) {
        return BANKS.get(stableIndex(accountExternalId, BANKS.size()));
    }

    /** Deterministically assigns a badge colour, on the same stable-hash basis as {@link #bankFor}. */
    private String colorFor(String accountExternalId) {
        return PALETTE.get(stableIndex(accountExternalId, PALETTE.size()));
    }

    private static int stableIndex(String key, int size) {
        return Math.floorMod(key.hashCode(), size);
    }

    /**
     * Produces a deterministic, non-reversible token standing in for the raw account id (SAMA
     * tokenization, DESIGN.md §9). SHA-256 of the id, hex-encoded and truncated — never the raw id, so
     * the bank side never sees real account numbers.
     */
    private String tokenize(String accountExternalId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(accountExternalId.getBytes(StandardCharsets.UTF_8));
            return "TKN-" + HexFormat.of().formatHex(hash).substring(0, 24).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e); // never happens on a standard JRE
        }
    }

    // ── I/O helpers ─────────────────────────────────────────────────────────────────────────────

    private SeedEnvelope read(Path file) {
        try {
            return objectMapper.readValue(file.toFile(), SeedEnvelope.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse seed file " + file, e);
        }
    }

    private List<Path> listJsonFiles(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list seed directory " + dir, e);
        }
    }

    /**
     * Resolves the configured path relative to the working directory, then — because the app is run from
     * {@code backend/} while {@code data-mocks/} lives at the repo root — searches parent directories for
     * a folder of that name. Returns {@code null} if none exists.
     */
    private Path resolveDataMocksDir(String configured) {
        Path direct = Path.of(configured);
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path name = direct.getFileName();
        for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
            Path candidate = base.resolve(name);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private record FileSummary(int accounts, int transactions) {
    }
}
