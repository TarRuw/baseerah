package com.baseerah.application.bank;

import com.baseerah.domain.bank.Status;
import com.baseerah.domain.bank.Trend;
import java.util.UUID;

/**
 * One computed portfolio-monitoring row (DESIGN §7.6) before the web layer stamps on its compliance surface:
 * a borrower, their facility, a health score, a trend arrow and a status badge, plus the linked
 * {@code clientRef} so the controller can resolve the borrower's tokenized accounts through the
 * {@code BankComplianceMapper} (the account→token choke-point stays in the application layer). Carries no raw account
 * reference.
 *
 * @param borrower  borrower display name
 * @param facility  the facility descriptor (loan purpose)
 * @param health    the facility's health score (its stamina, 0–100)
 * @param trend     health trend relative to the portfolio mean
 * @param status    monitoring status badge, banded off {@code health}
 * @param clientRef linked consumer whose tokenized accounts the web layer resolves, or {@code null}
 */
public record MonitoringRowData(
        String borrower,
        String facility,
        int health,
        Trend trend,
        Status status,
        UUID clientRef) {
}
