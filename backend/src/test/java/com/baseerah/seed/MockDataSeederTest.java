package com.baseerah.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.baseerah.account.Account;
import com.baseerah.account.AccountRepository;
import com.baseerah.client.Client;
import com.baseerah.client.ClientRepository;
import com.baseerah.seed.dto.SeedEnvelope;
import com.baseerah.seed.dto.SeedTransaction;
import com.baseerah.transaction.TransactionRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies {@link MockDataSeeder} against the local Liquibase-managed PostgreSQL (started via
 * {@code docker-compose up}, per the project run model). Skipped — not failed — when Postgres is not
 * reachable on {@code localhost:5432}, so a fresh checkout without the DB running is not a spurious
 * failure (same convention as {@code PersistenceContextTest}).
 *
 * <p>The test clears the five seeded personas, then drives the seeder directly: once to assert a fresh
 * load (counts, persona, tokenization, deterministic bank/colour, latest balance) and a second time to
 * assert idempotency (no new rows).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIf("com.baseerah.seed.MockDataSeederTest#postgresReachable")
class MockDataSeederTest {

    // Mirror of the seeder's deterministic-assignment inputs, to re-derive expected bank/colour.
    private static final List<String> BANKS = List.of(
            "Al Rajhi Bank", "Saudi National Bank", "Riyad Bank", "Banque Saudi Fransi",
            "Arab National Bank", "Alinma Bank", "Bank Albilad", "The Saudi Investment Bank");
    private static final List<String> PALETTE = List.of(
            "#0e6b54", "#C4A24C", "#1D9E63", "#E5A63A", "#E0574F", "#0a3d33");

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Autowired
    private MockDataSeeder seeder;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void freshLoadThenIdempotentReseed() throws IOException {
        List<ExpectedClient> expected = loadExpected();
        assertThat(expected).hasSize(5);

        // Start from a clean slate for exactly the five personas (cascade removes their accounts + txns).
        expected.forEach(e -> clientRepository.findByExternalId(e.externalId).ifPresent(clientRepository::delete));

        // ── First load ──────────────────────────────────────────────────────────────────────────
        seeder.run(new DefaultApplicationArguments());

        for (ExpectedClient e : expected) {
            Client client = clientRepository.findByExternalId(e.externalId).orElseThrow(
                    () -> new AssertionError("client not seeded: " + e.externalId));
            assertThat(client.getPersona()).isEqualTo(e.profile);
            assertThat(client.getProfileLabel()).isEqualTo(e.profile);

            List<Account> accounts = accountRepository.findByClientId(client.getId());
            assertThat(accounts).hasSize(e.accountsByExternalId.size());

            int txForClient = 0;
            for (Account account : accounts) {
                ExpectedAccount ea = e.accountsByExternalId.get(account.getExternalId());
                assertThat(ea).as("unexpected account %s", account.getExternalId()).isNotNull();

                // Tokenization: set, and never the raw id (DESIGN.md §9).
                assertThat(account.getTokenizedAccountId()).isNotBlank();
                assertThat(account.getTokenizedAccountId()).isNotEqualTo(account.getExternalId());

                // Deterministic bank + colour on a stable hash of the account id.
                assertThat(account.getBankName())
                        .isEqualTo(BANKS.get(Math.floorMod(account.getExternalId().hashCode(), BANKS.size())));
                assertThat(account.getDisplayColor())
                        .isEqualTo(PALETTE.get(Math.floorMod(account.getExternalId().hashCode(), PALETTE.size())));

                // latest_balance = closing balance of the most recent transaction.
                assertThat(account.getLatestBalance()).isEqualByComparingTo(ea.latestBalance);
                txForClient += ea.transactionCount;
            }

            long persisted = transactionRepository.findByAccount_Client_Id(client.getId(),
                    org.springframework.data.domain.Pageable.unpaged()).getTotalElements();
            assertThat(persisted).as("tx count for %s", e.externalId).isEqualTo(txForClient);
        }

        long clientsAfterFirst = clientRepository.count();
        long accountsAfterFirst = accountRepository.count();
        long txAfterFirst = transactionRepository.count();

        // ── Second load: must be a no-op ──────────────────────────────────────────────────────────
        seeder.run(new DefaultApplicationArguments());

        assertThat(clientRepository.count()).isEqualTo(clientsAfterFirst);
        assertThat(accountRepository.count()).isEqualTo(accountsAfterFirst);
        assertThat(transactionRepository.count()).isEqualTo(txAfterFirst);
    }

    // ── Expected values parsed straight from the read-only source files ───────────────────────────

    private List<ExpectedClient> loadExpected() throws IOException {
        Path dir = resolveDataMocksDir();
        assertThat(dir).as("data-mocks directory").isNotNull();
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .map(this::toExpected)
                    .toList();
        }
    }

    private ExpectedClient toExpected(Path file) {
        SeedEnvelope env;
        try {
            env = mapper.readValue(file.toFile(), SeedEnvelope.class);
        } catch (IOException e) {
            throw new AssertionError("cannot read " + file, e);
        }
        List<SeedTransaction> txns = env.data().transactions();
        String stem = file.getFileName().toString().replaceFirst("\\.json$", "");
        String externalId = stem.replaceFirst("_(\\d+_months_)?data$", "");

        Map<String, List<SeedTransaction>> byAccount = txns.stream()
                .collect(Collectors.groupingBy(SeedTransaction::accountId));
        Map<String, ExpectedAccount> accounts = new java.util.HashMap<>();
        byAccount.forEach((accId, list) -> {
            // The seeder's "most recent" is always a real-dated row (imputed dates are bounded by the
            // real max), so compute the expected latest balance over dated rows only — and this also
            // avoids parsing the missing-date rows.
            BigDecimal latest = list.stream()
                    .filter(t -> t.bookingDateTime() != null && !t.bookingDateTime().isBlank())
                    .max(Comparator.comparing(t -> Instant.parse(t.bookingDateTime())))
                    .orElseThrow()
                    .balance().amount().amount();
            accounts.put(accId, new ExpectedAccount(list.size(), latest));
        });
        return new ExpectedClient(externalId, env.data().clientProfile(), accounts);
    }

    private Path resolveDataMocksDir() {
        for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
            Path candidate = base.resolve("data-mocks");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private record ExpectedClient(String externalId, String profile,
            Map<String, ExpectedAccount> accountsByExternalId) {
    }

    private record ExpectedAccount(int transactionCount, BigDecimal latestBalance) {
    }

    /** Fast TCP probe so the suite skips cleanly when the local Postgres is not up. */
    @SuppressWarnings("unused")
    static boolean postgresReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 5432), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
