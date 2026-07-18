// Client-side views of the consumer financing RFP API (Smart Rescue), mirroring
// the backend wire DTOs served by:
//   • `POST /api/v1/clients/{id}/financing/requests`               → FinancingRequest
//   • `GET  /api/v1/clients/{id}/financing/requests`               → FinancingRequest
//   • `POST /api/v1/clients/{id}/financing/requests/{rid}/choose`  → FinancingOutcome

/// Lifecycle of one bank's proposal (mirrors the backend `ProposalStatus`).
enum FinancingProposalStatus {
  pending,
  replied,
  accepted,
  disbursed,
  declined;

  static FinancingProposalStatus fromWire(String value) => switch (value) {
    'PENDING' => FinancingProposalStatus.pending,
    'REPLIED' => FinancingProposalStatus.replied,
    'ACCEPTED' => FinancingProposalStatus.accepted,
    'DISBURSED' => FinancingProposalStatus.disbursed,
    'DECLINED' => FinancingProposalStatus.declined,
    _ => throw ArgumentError('Unknown proposal status: $value'),
  };
}

/// How a replied proposal would affect the client's situation — the reused loan
/// affordability result (instalment, total, DTI + band colours, verdict, and the
/// genuine projected stress score). Mirrors the backend `LoanSimulateResponse`.
class FinancingImpact {
  const FinancingImpact({
    required this.installment,
    required this.total,
    required this.dti,
    required this.dtiColor,
    required this.verdict,
    required this.verdictColor,
    required this.projectedScore,
  });

  final double installment;
  final double total;
  final double dti;
  final String dtiColor;
  final String verdict;
  final String verdictColor;
  final int projectedScore;

  factory FinancingImpact.fromJson(Map<String, dynamic> json) {
    return FinancingImpact(
      installment: (json['installment'] as num).toDouble(),
      total: (json['total'] as num).toDouble(),
      dti: (json['dti'] as num).toDouble(),
      dtiColor: json['dtiColor'] as String,
      verdict: json['verdict'] as String,
      verdictColor: json['verdictColor'] as String,
      projectedScore: (json['projectedScore'] as num).toInt(),
    );
  }
}

/// One bank's proposal within a financing request. [rate]/[termMonths]/[impact]
/// are null until the bank replies.
class FinancingProposal {
  const FinancingProposal({
    required this.id,
    required this.bankName,
    required this.status,
    required this.rate,
    required this.termMonths,
    required this.amount,
    required this.firstPaymentDate,
    required this.impact,
  });

  final String id;
  final String bankName;
  final FinancingProposalStatus status;
  final double? rate;
  final int? termMonths;
  final double amount;

  /// The first repayment due date, set once the facility is disbursed; null before.
  final DateTime? firstPaymentDate;
  final FinancingImpact? impact;

  factory FinancingProposal.fromJson(Map<String, dynamic> json) {
    final impactJson = json['impact'] as Map<String, dynamic>?;
    final firstPayment = json['firstPaymentDate'] as String?;
    return FinancingProposal(
      id: json['id'] as String,
      bankName: json['bankName'] as String,
      status: FinancingProposalStatus.fromWire(json['status'] as String),
      rate: (json['rate'] as num?)?.toDouble(),
      termMonths: (json['termMonths'] as num?)?.toInt(),
      amount: (json['amount'] as num).toDouble(),
      firstPaymentDate: firstPayment == null ? null : DateTime.tryParse(firstPayment),
      impact: impactJson == null ? null : FinancingImpact.fromJson(impactJson),
    );
  }
}

/// The single at-a-glance status shown as a pill on a request in the history
/// list, derived from the request status and its proposals' states.
enum FinancingRequestUiStatus {
  /// Still awaiting at least one bank's reply.
  pending,

  /// At least one bank has made an offer — ready for the client to choose.
  offersReady,

  /// The client accepted an offer; it now awaits the bank's disbursement.
  accepted,

  /// The accepted offer was disbursed — the facility is active.
  active,

  /// Every bank declined — nothing to choose.
  declined,
}

/// A consumer financing request and its per-bank proposals.
class FinancingRequest {
  const FinancingRequest({
    required this.id,
    required this.amount,
    required this.status,
    required this.proposals,
  });

  final String id;
  final double amount;
  final String status;
  final List<FinancingProposal> proposals;

  /// True while any bank has yet to answer — the signal to keep polling.
  bool get anyPending =>
      proposals.any((p) => p.status == FinancingProposalStatus.pending);

  /// Replied proposals (the ones the client can actually choose), price-carrying.
  List<FinancingProposal> get replied => proposals
      .where((p) => p.status == FinancingProposalStatus.replied)
      .toList();

  /// The single proposal that carries the facility once accepted/disbursed, else null.
  FinancingProposal? get facility {
    for (final p in proposals) {
      if (p.status == FinancingProposalStatus.accepted ||
          p.status == FinancingProposalStatus.disbursed) {
        return p;
      }
    }
    return null;
  }

  /// True while the request is still moving (awaiting a bank reply, or awaiting
  /// disbursement after acceptance) — the signal to keep polling.
  bool get awaitingBank => anyPending || status == 'ACCEPTED';

  /// The at-a-glance status for the history pill.
  FinancingRequestUiStatus get uiStatus {
    if (status == 'ACTIVE') return FinancingRequestUiStatus.active;
    if (status == 'ACCEPTED') return FinancingRequestUiStatus.accepted;
    if (replied.isNotEmpty) return FinancingRequestUiStatus.offersReady;
    if (proposals.isNotEmpty &&
        proposals.every((p) => p.status == FinancingProposalStatus.declined)) {
      return FinancingRequestUiStatus.declined;
    }
    return FinancingRequestUiStatus.pending;
  }

  factory FinancingRequest.fromJson(Map<String, dynamic> json) {
    return FinancingRequest(
      id: json['id'] as String,
      amount: (json['amount'] as num).toDouble(),
      status: json['status'] as String,
      proposals: (json['proposals'] as List<dynamic>)
          .map((e) => FinancingProposal.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

/// The before/after stress score of choosing a proposal (`…/choose`).
class FinancingOutcome {
  const FinancingOutcome({required this.scoreBefore, required this.scoreAfter});

  final int scoreBefore;
  final int scoreAfter;

  factory FinancingOutcome.fromJson(Map<String, dynamic> json) {
    return FinancingOutcome(
      scoreBefore: (json['scoreBefore'] as num).toInt(),
      scoreAfter: (json['scoreAfter'] as num).toInt(),
    );
  }
}
