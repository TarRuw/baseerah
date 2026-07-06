package com.baseerah.genai;

import com.baseerah.client.Client;
import com.baseerah.client.ClientService;
import com.baseerah.genai.GenAiClient.ChatContext;
import com.baseerah.genai.GenAiClient.ChatReply;
import com.baseerah.genai.GenAiClient.InvoiceParseResult;
import com.baseerah.stress.StressScoreResponse;
import com.baseerah.stress.StressScoreService;
import com.baseerah.transaction.Direction;
import com.baseerah.transaction.Transaction;
import com.baseerah.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Orchestration for the chat endpoints (FR-03, DESIGN.md §6): resolves the client, assembles the
 * {@link ChatContext} from the client's telemetry, and delegates to the configured {@link GenAiClient}. This
 * keeps {@link ChatController} thin (project convention: controller → service → repository) and depends only
 * on the {@link GenAiClient} interface, so the mock/remote swap is invisible here.
 *
 * <p>The grounding telemetry is deliberately simple — the current stress score and zone (via
 * {@link StressScoreService}, reusing its single 404 contract) plus mean monthly inflow/outflow over a
 * trailing {@value #WINDOW_DAYS}-day window. It intentionally does <em>not</em> reuse the loan package's
 * cadence-based recurring detector (that logic is private to the completed Step 3.3); monthly means are
 * enough to ground a conversational reply and keep this step self-contained.
 */
@Service
public class ChatService {

    /** Trailing window (days) used to derive monthly cash flow — matches the §5.1/§5.2/§5.3 windows. */
    static final int WINDOW_DAYS = 90;

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    private final GenAiClient genAiClient;
    private final ClientService clientService;
    private final StressScoreService stressScoreService;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    @Autowired
    public ChatService(GenAiClient genAiClient, ClientService clientService,
            StressScoreService stressScoreService, TransactionRepository transactionRepository) {
        this(genAiClient, clientService, stressScoreService, transactionRepository, Clock.systemUTC());
    }

    ChatService(GenAiClient genAiClient, ClientService clientService,
            StressScoreService stressScoreService, TransactionRepository transactionRepository, Clock clock) {
        this.genAiClient = genAiClient;
        this.clientService = clientService;
        this.stressScoreService = stressScoreService;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    /**
     * Answer a client's chat message, grounded in their telemetry.
     *
     * @param clientId canonical client UUID (as a string; unknown/malformed → shared 404 envelope)
     * @param message  the user's message
     * @return the assistant's reply
     */
    public ChatReply chat(String clientId, String message) {
        return genAiClient.chat(buildContext(clientId), message);
    }

    /**
     * Stream a client's chat reply chunk-by-chunk (FR-03, DESIGN §9 time-to-first-token &lt; 1.0&nbsp;s).
     * When the configured provider can stream ({@link StreamingGenAiClient} — the remote adapter), deltas are
     * forwarded as they arrive; otherwise (the mock) the buffered {@link GenAiClient#chat} reply is emitted as
     * a single chunk, so the streaming endpoint works under every provider.
     *
     * @param clientId canonical client UUID (as a string; unknown/malformed → shared 404 envelope)
     * @param message  the user's message
     * @param onChunk  invoked once per delta, in order
     */
    public void streamChat(String clientId, String message, Consumer<String> onChunk) {
        ChatContext context = buildContext(clientId);
        if (genAiClient instanceof StreamingGenAiClient streaming) {
            streaming.streamChat(context, message, onChunk);
        } else {
            onChunk.accept(genAiClient.chat(context, message).reply());
        }
    }

    /**
     * Parse an uploaded invoice image into a suggested action.
     *
     * @param clientId canonical client UUID (as a string; unknown/malformed → shared 404 envelope)
     * @param image    the raw uploaded image bytes
     * @return the parsed-action result
     */
    public InvoiceParseResult parseInvoice(String clientId, byte[] image) {
        clientService.requireClient(clientId); // resolve for the 404 contract even though the mock ignores it
        return genAiClient.parseInvoice(image);
    }

    /** Assemble the grounding {@link ChatContext} from the client's latest score and trailing cash flow. */
    private ChatContext buildContext(String clientId) {
        Client client = clientService.requireClient(clientId);
        StressScoreResponse score = stressScoreService.latestFor(clientId);

        LocalDate today = LocalDate.now(clock);
        Instant from = today.minusDays(WINDOW_DAYS).atStartOfDay(UTC).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(UTC).toInstant();
        List<Transaction> window = transactionRepository
                .findByAccount_Client_IdAndBookingDateBetween(client.getId(), from, to);

        MonthlyCashFlow cashFlow = deriveMonthlyCashFlow(window);
        return new ChatContext(client.getProfileLabel(), score.score(), score.zone(),
                cashFlow.income(), cashFlow.outflow());
    }

    /**
     * Mean monthly inflow and outflow over the window: total credit / total debit divided by the number of
     * distinct months observed (at least one, so an empty window yields zeros rather than a divide-by-zero).
     */
    private static MonthlyCashFlow deriveMonthlyCashFlow(List<Transaction> window) {
        TreeSet<YearMonth> months = new TreeSet<>();
        BigDecimal totalCredit = BigDecimal.ZERO;
        BigDecimal totalDebit = BigDecimal.ZERO;
        for (Transaction tx : window) {
            if (tx.getBookingDate() == null) {
                continue;
            }
            months.add(YearMonth.from(tx.getBookingDate().atZone(UTC).toLocalDate()));
            BigDecimal amount = tx.getAmount() == null ? BigDecimal.ZERO : tx.getAmount().abs();
            if (tx.getDirection() == Direction.CREDIT) {
                totalCredit = totalCredit.add(amount);
            } else {
                totalDebit = totalDebit.add(amount);
            }
        }
        BigDecimal monthCount = BigDecimal.valueOf(Math.max(1, months.size()));
        BigDecimal income = totalCredit.divide(monthCount, 2, RoundingMode.HALF_UP);
        BigDecimal outflow = totalDebit.divide(monthCount, 2, RoundingMode.HALF_UP);
        return new MonthlyCashFlow(income, outflow);
    }

    /** Mean monthly income and outflow derived from a client's transaction window (both SAR). */
    private record MonthlyCashFlow(BigDecimal income, BigDecimal outflow) {
    }
}
