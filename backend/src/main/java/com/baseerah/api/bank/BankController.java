package com.baseerah.api.bank;

import com.baseerah.api.bank.dto.PortfolioDto;
import com.baseerah.api.bank.dto.RiskPolicyDto;
import com.baseerah.application.bank.BankComplianceMapper;
import com.baseerah.application.bank.BankService;
import com.baseerah.application.bank.MonitoringRowData;
import com.baseerah.application.bank.PortfolioResult;
import com.baseerah.domain.bank.RiskPolicy;
import com.baseerah.shared.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bank Portal endpoints (DESIGN.md §6). Delegates the domain/orchestration work to {@link BankService},
 * projects the result to a wire DTO via the static {@link BankWebMapper}, and wraps it in the shared
 * {@link ApiResponse} envelope (Global Rules).
 *
 * <p><strong>Phase 12 (unified loan pipeline).</strong> The FR-08 approve/decline underwriting queue
 * ({@code GET /applicants}, {@code POST /applicants/{id}/report|decision}) and the portfolio-over-applicants
 * ({@code GET /portfolio}) were retired in Step 12.1 together with the {@code loan_applications} table — every
 * loan is now a consumer-originated request in the unified {@code financing_requests} model. Underwriting is
 * rebuilt on that model by the bank underwrite/price/disburse surface (Steps 12.2–12.3 /
 * {@link BankFinancingController}), and the <strong>Portfolio</strong> is rebuilt here (Step 12.4) over the
 * disbursed facilities. This controller now serves:
 *
 * <ul>
 *   <li>{@code GET} {@code /portfolio} — the monitored book (KPIs + rows) over disbursed facilities.</li>
 *   <li>{@code GET}/{@code PUT} {@code /risk-policy} — read/update the singleton risk policy.</li>
 * </ul>
 *
 * <p>An out-of-range risk-policy {@code PUT} yields the shared {@code 400 VALIDATION_ERROR} envelope.
 */
@RestController
@RequestMapping("/api/v1/bank")
public class BankController {

    private final BankService bankService;
    private final BankComplianceMapper complianceMapper;

    public BankController(BankService bankService, BankComplianceMapper complianceMapper) {
        this.bankService = bankService;
        this.complianceMapper = complianceMapper;
    }

    /**
     * {@code GET /api/v1/bank/portfolio} — the monitored loan portfolio (DESIGN §7.6) over disbursed
     * facilities. {@link BankService#portfolio()} computes the KPIs + monitoring rows and returns the live risk
     * policy; this controller stamps the compliance surface (each row's tokenized accounts, batch-resolved
     * through the {@link BankComplianceMapper}, plus the payload's residency marker and export gate) so no raw
     * account id crosses the wire.
     */
    @GetMapping("/portfolio")
    public ApiResponse<PortfolioDto> portfolio() {
        PortfolioResult result = bankService.portfolio();
        RiskPolicy policy = result.policy();
        List<UUID> clientRefs = result.monitoring().stream()
                .map(MonitoringRowData::clientRef)
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, List<String>> tokensByClient = complianceMapper.accountTokensFor(clientRefs, policy);
        return ApiResponse.ok(BankWebMapper.toPortfolioDto(result, tokensByClient,
                complianceMapper.residencyMarker(policy), complianceMapper.exportAllowed(policy)));
    }

    /** {@code GET /api/v1/bank/risk-policy} — read the singleton risk policy. */
    @GetMapping("/risk-policy")
    public ApiResponse<RiskPolicyDto> riskPolicy() {
        return ApiResponse.ok(BankWebMapper.toRiskPolicyDto(bankService.riskPolicy()));
    }

    /** {@code PUT /api/v1/bank/risk-policy} — update the singleton and return the persisted policy. */
    @PutMapping("/risk-policy")
    public ApiResponse<RiskPolicyDto> updateRiskPolicy(@Valid @RequestBody RiskPolicyDto request) {
        RiskPolicy updated = bankService.updateRiskPolicy(request.staminaFloor(),
                request.autoDeclineThreshold(), request.ndmoResidency(), request.tokenization(),
                request.samaLastSync());
        return ApiResponse.ok(BankWebMapper.toRiskPolicyDto(updated));
    }
}
