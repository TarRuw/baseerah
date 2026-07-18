// Client-side views of the Bank Portal financing-review API (Smart Rescue RFP),
// mirroring the backend wire DTOs served by:
//   • `GET  /api/v1/bank/financing-requests`                  → List<FinancingRequestRow>
//   • `POST /api/v1/bank/financing-requests/{id}/reply`       → FinancingRequestRow
//   • `POST /api/v1/bank/financing-requests/{id}/decline`     → FinancingRequestRow

import 'applicant_models.dart' show Verdict;

/// Lifecycle of a consumer proposal as the bank operator sees it (mirrors the
/// backend `ProposalStatus`). The inbox lists `PENDING`; a reply/decline returns
/// the updated row.
enum ProposalReviewStatus {
  pending,
  replied,
  declined;

  static ProposalReviewStatus fromWire(String value) => switch (value) {
    'PENDING' => ProposalReviewStatus.pending,
    'REPLIED' => ProposalReviewStatus.replied,
    'DECLINED' => ProposalReviewStatus.declined,
    _ => throw ArgumentError('Unknown proposal status: $value'),
  };
}

/// One row of the Bank Portal financing (Price stage) inbox: a consumer proposal
/// the operator can price, with the applicant context and two risk hints —
/// [clientScore] (the consumer's current stress score) and, once the request has
/// been underwritten, the parent request's [verdict] + [staminaScore] carried
/// down as pricing decision support (Step 12.6). [verdict]/[staminaScore] are
/// null while the request is un-underwritten; [rate]/[termMonths] are null until
/// replied.
class FinancingRequestRow {
  const FinancingRequestRow({
    required this.proposalId,
    required this.requestId,
    required this.bankName,
    required this.applicantLabel,
    required this.amount,
    required this.clientScore,
    required this.verdict,
    required this.staminaScore,
    required this.status,
    required this.rate,
    required this.termMonths,
  });

  final String proposalId;
  final String requestId;
  final String bankName;
  final String applicantLabel;
  final double amount;
  final int clientScore;

  /// The parent request's underwriting verdict, or null if not yet underwritten.
  final Verdict? verdict;

  /// The parent request's underwriting stamina (0–100), or null if not underwritten.
  final int? staminaScore;

  final ProposalReviewStatus status;
  final double? rate;
  final int? termMonths;

  factory FinancingRequestRow.fromJson(Map<String, dynamic> json) {
    final verdict = json['verdict'] as String?;
    return FinancingRequestRow(
      proposalId: json['proposalId'] as String,
      requestId: json['requestId'] as String,
      bankName: json['bankName'] as String,
      applicantLabel: json['applicantLabel'] as String,
      amount: (json['amount'] as num).toDouble(),
      clientScore: (json['clientScore'] as num).toInt(),
      verdict: verdict == null ? null : Verdict.fromWire(verdict),
      staminaScore: (json['staminaScore'] as num?)?.toInt(),
      status: ProposalReviewStatus.fromWire(json['status'] as String),
      rate: (json['rate'] as num?)?.toDouble(),
      termMonths: (json['termMonths'] as num?)?.toInt(),
    );
  }
}
