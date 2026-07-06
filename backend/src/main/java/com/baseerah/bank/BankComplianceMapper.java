package com.baseerah.bank;

import com.baseerah.account.Account;
import com.baseerah.account.AccountRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The single choke-point for turning a linked consumer's accounts into the compliance surface a bank-side DTO
 * is allowed to carry (SAMA tokenization + NDMO residency, DESIGN.md §9; Phase 7 / Step 7.1). Centralising it
 * here is the structural backstop for the Global Rule that entities never leave the service layer: no bank DTO
 * is ever handed a raw account id, because the only method that maps an {@link Account} to a bank field
 * ({@link #accountTokensFor}) reads <strong>exclusively</strong> {@link Account#getTokenizedAccountId()} and
 * has no code path that touches {@link Account#getExternalId()} or {@link Account#getId()}.
 *
 * <h2>Toggle semantics (driven by the singleton {@link RiskPolicy}, read in the service layer)</h2>
 * <ul>
 *   <li><b>{@code tokenization == true}</b> (default): bank report/portfolio responses expose the accounts'
 *       non-reversible {@code TKN-…} tokens.</li>
 *   <li><b>{@code tokenization == false}</b>: <em>no</em> account reference is surfaced to the bank at all —
 *       {@link #accountTokensFor} returns an empty list. Baseerah deliberately does <strong>not</strong> fall
 *       back to raw ids when the SAMA control is disabled; the toggle changes the token surface, never the
 *       leak guarantee. (The Settings screen copy, DESIGN §7.7, must reflect exactly this: "off" hides the
 *       token surface, it does not reveal raw account numbers.)</li>
 *   <li><b>{@code ndmoResidency == true}</b> (default): responses are stamped with the local-residency marker
 *       {@link #RESIDENCY_LOCAL} and {@code exportAllowed = false} — the data-export path is gated closed so
 *       report/portfolio payloads may not leave the residency boundary.</li>
 *   <li><b>{@code ndmoResidency == false}</b>: the marker becomes {@link #RESIDENCY_UNRESTRICTED} and
 *       {@code exportAllowed = true}. Flipping the flag via {@code PUT /api/v1/bank/risk-policy} changes the
 *       observable stamp on the very next request.</li>
 * </ul>
 *
 * <h2>Encryption-layer note (simulated control — DESIGN §9)</h2>
 * The bank predictive-report and portfolio payloads this mapper feeds are the sensitive B2B surface. In a
 * production deployment a real encryption boundary would wrap these DTOs — at rest (envelope-encrypted report
 * columns / a KMS-managed data key) and in transit (mTLS between the bank portal and this service, payload
 * signing so a tampered report is rejected). For the MVP this is <em>documented, not implemented</em> (no
 * production key management, per BRD out-of-scope): the tokenization + residency controls above are the
 * demonstrable, leak-provable stand-ins, and this class marks precisely where that crypto boundary would sit.
 */
@Component
public class BankComplianceMapper {

    /** NDMO residency marker stamped on bank payloads when local-data-residency is enforced. */
    public static final String RESIDENCY_LOCAL = "KSA";

    /** Residency marker when the NDMO local-residency simulation is disabled. */
    public static final String RESIDENCY_UNRESTRICTED = "UNRESTRICTED";

    private final AccountRepository accountRepository;

    public BankComplianceMapper(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * The tokenized account references a bank DTO may carry for a linked consumer, honouring the
     * {@code tokenization} toggle. Returns the accounts' {@code TKN-…} tokens when tokenization is on, and an
     * empty list when it is off or the applicant has no linked telemetry ({@code clientRef == null}). Never
     * returns a raw account id under any toggle — that identifier stays inside the entity/service layer.
     *
     * @param clientRef the linked consumer, or {@code null} for a synthetic applicant
     * @param policy    the live risk policy supplying the tokenization toggle
     * @return the tokenized references, or an empty list
     */
    public List<String> accountTokensFor(UUID clientRef, RiskPolicy policy) {
        if (clientRef == null || !policy.isTokenization()) {
            return List.of();
        }
        return accountRepository.findByClientId(clientRef).stream()
                .map(Account::getTokenizedAccountId)
                .toList();
    }

    /** The NDMO residency marker for the current policy (local vs unrestricted). */
    public String residencyMarker(RiskPolicy policy) {
        return policy.isNdmoResidency() ? RESIDENCY_LOCAL : RESIDENCY_UNRESTRICTED;
    }

    /** Whether a bank payload may be exported outside the residency boundary — gated closed when NDMO is on. */
    public boolean exportAllowed(RiskPolicy policy) {
        return !policy.isNdmoResidency();
    }
}
