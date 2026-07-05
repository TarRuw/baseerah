// Client-side views of the Bank Portal API (FR-08, DESIGN §5.5/§7.5), mirroring
// the backend wire DTOs served by the Step 6.2 endpoints:
//   • `GET  /api/v1/bank/applicants`            → List<Applicant>
//   • `POST /api/v1/bank/applicants/{id}/report`   → UnderwritingReport
//   • `POST /api/v1/bank/applicants/{id}/decision` → Applicant (updated echo)
//
// A freshly-seeded applicant sits in the queue un-underwritten, so `verdict`,
// `riskTier` and `decision` are all nullable until a report is generated / a
// banker acts — the pipeline renders an "unscored" badge rather than guessing.

/// The system's predictive recommendation for an applicant (mirrors the backend
/// `Verdict` enum). Fixed by the §5.5 thresholds over stamina + forecast DTI:
/// `OK` (stamina≥70 & DTI≤34%), `BAD` (stamina≤48 | DTI≥71%), `WARN` otherwise.
/// Doubles as the list-row risk badge and the report's headline verdict.
enum Verdict {
  ok,
  warn,
  bad;

  /// Parse the upper-case wire value (`OK` | `WARN` | `BAD`); throws on anything
  /// else so a malformed payload surfaces as an error state, not a wrong badge.
  static Verdict fromWire(String value) => switch (value) {
    'OK' => Verdict.ok,
    'WARN' => Verdict.warn,
    'BAD' => Verdict.bad,
    _ => throw ArgumentError('Unknown verdict: $value'),
  };
}

/// The human lending outcome a banker records (mirrors the backend `Decision`
/// enum). Distinct from [Verdict]: the verdict is the model's recommendation,
/// the decision is the acted-upon result the decision endpoint persists.
enum Decision {
  approve,
  decline;

  /// The upper-case value the decision endpoint expects (`APPROVE`|`DECLINE`).
  String get wire => name.toUpperCase();

  /// Parse an inbound wire value; throws on an unexpected type.
  static Decision fromWire(String value) => switch (value) {
    'APPROVE' => Decision.approve,
    'DECLINE' => Decision.decline,
    _ => throw ArgumentError('Unknown decision: $value'),
  };
}

/// One applicant in the underwriting queue (`GET …/applicants`) and the echo of
/// `POST …/decision`. [verdict]/[riskTier] are null until a report is generated;
/// [decision] is null until a banker acts.
class Applicant {
  const Applicant({
    required this.id,
    required this.applicantName,
    required this.initials,
    required this.purpose,
    required this.amount,
    required this.verdict,
    required this.riskTier,
    required this.decision,
  });

  final String id;
  final String applicantName;
  final String initials;
  final String purpose;

  /// Requested financing amount (SAR), raw `numeric(14,2)` from the API.
  final double amount;

  /// The §5.5 risk badge, or null if not yet underwritten.
  final Verdict? verdict;

  /// Tier label (A/B/C) consistent with the verdict, or null if not underwritten.
  final String? riskTier;

  /// The recorded human decision, or null if none yet.
  final Decision? decision;

  factory Applicant.fromJson(Map<String, dynamic> json) {
    final verdict = json['verdict'] as String?;
    final decision = json['decision'] as String?;
    return Applicant(
      id: json['id'] as String,
      applicantName: json['applicantName'] as String,
      initials: json['initials'] as String,
      purpose: json['purpose'] as String,
      amount: (json['amount'] as num).toDouble(),
      verdict: verdict == null ? null : Verdict.fromWire(verdict),
      riskTier: json['riskTier'] as String?,
      decision: decision == null ? null : Decision.fromWire(decision),
    );
  }
}

/// One month-end point on the report's 12-month cash-flow chart: the projected
/// month-end [balance] on [date] (reuses the consumer forecast chart's shape).
class CashFlowPoint {
  const CashFlowPoint({required this.date, required this.balance});

  final DateTime date;
  final double balance;

  factory CashFlowPoint.fromJson(Map<String, dynamic> json) {
    return CashFlowPoint(
      date: DateTime.parse(json['date'] as String),
      balance: (json['balance'] as num).toDouble(),
    );
  }
}

/// A full predictive underwriting report (`POST …/report`, DESIGN §5.5/§7.5):
/// the verdict panel, the stamina box, the three KPI boxes (forecast DTI, income
/// stability, 12-month default probability) and the 12-month cash-flow chart.
/// The three ratio metrics are already percentages (server-computed).
class UnderwritingReport {
  const UnderwritingReport({
    required this.applicationId,
    required this.applicantName,
    required this.initials,
    required this.purpose,
    required this.amount,
    required this.staminaScore,
    required this.forecastDti,
    required this.incomeStability,
    required this.defaultProb12mo,
    required this.verdict,
    required this.riskTier,
    required this.cashFlow,
  });

  final String applicationId;
  final String applicantName;
  final String initials;
  final String purpose;
  final double amount;

  /// Long-term cash-flow endurance, 0–100 (higher = stronger).
  final int staminaScore;

  /// Forecast debt-to-income after the requested loan, as a percentage.
  final double forecastDti;

  /// Income regularity, as a percentage (100 = perfectly steady).
  final double incomeStability;

  /// Modelled 12-month probability of default, as a percentage.
  final double defaultProb12mo;

  final Verdict verdict;
  final String riskTier;

  /// The 12-month projected month-end balance series driving the report chart.
  final List<CashFlowPoint> cashFlow;

  factory UnderwritingReport.fromJson(Map<String, dynamic> json) {
    return UnderwritingReport(
      applicationId: json['applicationId'] as String,
      applicantName: json['applicantName'] as String,
      initials: json['initials'] as String,
      purpose: json['purpose'] as String,
      amount: (json['amount'] as num).toDouble(),
      staminaScore: (json['staminaScore'] as num).toInt(),
      forecastDti: (json['forecastDti'] as num).toDouble(),
      incomeStability: (json['incomeStability'] as num).toDouble(),
      defaultProb12mo: (json['defaultProb12mo'] as num).toDouble(),
      verdict: Verdict.fromWire(json['verdict'] as String),
      riskTier: json['riskTier'] as String,
      cashFlow: (json['cashFlow'] as List<dynamic>)
          .map((e) => CashFlowPoint.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}
