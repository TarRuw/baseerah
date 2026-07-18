// Client-side view of the Bank Portal disbursements API (Smart Rescue RFP, final
// stage), mirroring the backend wire DTOs served by:
//   • `GET  /api/v1/bank/financing-disbursements`                → List<DisbursementRow>
//   • `POST /api/v1/bank/financing-disbursements/{id}/disburse`  → DisbursementRow
//   • `POST /api/v1/bank/financing-disbursements/{id}/decline`   → DisbursementRow

/// One accepted offer awaiting a funding decision, with the applicant context,
/// accepted terms, and a final affordability signal ([installment] +
/// [affordabilityVerdict]) so the operator can disburse or decline.
class DisbursementRow {
  const DisbursementRow({
    required this.proposalId,
    required this.requestId,
    required this.bankName,
    required this.applicantLabel,
    required this.amount,
    required this.rate,
    required this.termMonths,
    required this.clientScore,
    required this.installment,
    required this.affordabilityVerdict,
    required this.status,
  });

  final String proposalId;
  final String requestId;
  final String bankName;
  final String applicantLabel;
  final double amount;
  final double rate;
  final int termMonths;
  final int clientScore;
  final double installment;

  /// COMFORTABLE / STRAINS / NOT_AFFORDABLE — the loan-engine final check.
  final String affordabilityVerdict;

  /// PENDING/REPLIED/ACCEPTED/DISBURSED/DECLINED (ACCEPTED in the queue; the
  /// post-action state after disburse/decline).
  final String status;

  factory DisbursementRow.fromJson(Map<String, dynamic> json) {
    return DisbursementRow(
      proposalId: json['proposalId'] as String,
      requestId: json['requestId'] as String,
      bankName: json['bankName'] as String,
      applicantLabel: json['applicantLabel'] as String,
      amount: (json['amount'] as num).toDouble(),
      rate: (json['rate'] as num).toDouble(),
      termMonths: (json['termMonths'] as num).toInt(),
      clientScore: (json['clientScore'] as num).toInt(),
      installment: (json['installment'] as num).toDouble(),
      affordabilityVerdict: json['affordabilityVerdict'] as String,
      status: json['status'] as String,
    );
  }
}
