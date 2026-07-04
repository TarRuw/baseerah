// Client-side views of the Smart Rescue API (FR-06/07, DESIGN §5.4/§7.3),
// mirroring the backend wire DTOs served by
// `GET /api/v1/clients/{id}/rescue` and `POST …/rescue/confirm`.
//
// The no-deficit (healthy) case is an explicit *state*, not an error: the
// server returns `hasDeficit:false` with a 200 and null shortfall/empty
// options, so the screen branches on `RescueState.hasDeficit` rather than
// catching a 4xx. `deficitInDays`/`shortfall` are therefore nullable.

/// The two Sharia-aware bridge options (mirrors the backend `RescueOptionType`
/// enum). The wire form is upper-case (`MURABAHA` | `LIQUIDATE`); [fromWire]
/// parses inbound values and [wire] produces the value echoed back on confirm.
enum RescueOptionType {
  /// Pre-approved Sharia-compliant micro-finance repaid over a term.
  murabaha,

  /// Liquidate safe fund assets — no financing cost, no repayment term.
  liquidate;

  /// The upper-case name the API expects/serves (`MURABAHA` | `LIQUIDATE`).
  String get wire => name.toUpperCase();

  /// Parse an API wire value; throws on an unexpected type so a malformed
  /// payload surfaces as an error state rather than a silently-wrong card.
  static RescueOptionType fromWire(String value) => switch (value) {
    'MURABAHA' => RescueOptionType.murabaha,
    'LIQUIDATE' => RescueOptionType.liquidate,
    _ => throw ArgumentError('Unknown rescue option type: $value'),
  };
}

/// A single bridge option offered to cover the predicted shortfall. [term] is
/// the repayment term in months for [RescueOptionType.murabaha] and `null` for
/// [RescueOptionType.liquidate]. [label] and [detail] are server-provided.
class RescueOption {
  const RescueOption({
    required this.type,
    required this.label,
    required this.amount,
    required this.term,
    required this.detail,
  });

  final RescueOptionType type;
  final String label;

  /// SAR amount the bridge provides, sized to the shortfall (pre-rounded).
  final double amount;

  /// Repayment term in months (`murabaha` only); `null` for `liquidate`.
  final int? term;
  final String detail;

  factory RescueOption.fromJson(Map<String, dynamic> json) {
    return RescueOption(
      type: RescueOptionType.fromWire(json['type'] as String),
      label: json['label'] as String,
      amount: (json['amount'] as num).toDouble(),
      term: (json['term'] as num?)?.toInt(),
      detail: json['detail'] as String,
    );
  }
}

/// A Smart Rescue assessment (`GET …/rescue`). When [hasDeficit] is false the
/// remaining fields collapse — [deficitInDays]/[shortfall] are null and
/// [options] is empty. [alertRaised] is the FR-06 15-day-lead flag; it can be
/// false even when a deficit exists (a faithful engine may not meet the 15-day
/// window on the frozen mock — see the Step 4.1 handoff), so the banner renders
/// on [hasDeficit], and [alertRaised] only escalates the urgency styling.
class RescueState {
  const RescueState({
    required this.hasDeficit,
    required this.deficitInDays,
    required this.alertRaised,
    required this.shortfall,
    required this.options,
  });

  final bool hasDeficit;
  final int? deficitInDays;
  final bool alertRaised;
  final double? shortfall;
  final List<RescueOption> options;

  factory RescueState.fromJson(Map<String, dynamic> json) {
    return RescueState(
      hasDeficit: json['hasDeficit'] as bool,
      deficitInDays: (json['deficitInDays'] as num?)?.toInt(),
      alertRaised: json['alertRaised'] as bool? ?? false,
      shortfall: (json['shortfall'] as num?)?.toDouble(),
      options: (json['options'] as List<dynamic>)
          .map((e) => RescueOption.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

/// The result of confirming a bridge (`POST …/rescue/confirm`). The backend
/// guarantees [scoreAfter] > [scoreBefore] (recovery curve in `RescueService`);
/// the complete-state gauge sweeps between the two.
class RescueOutcome {
  const RescueOutcome({
    required this.scoreBefore,
    required this.scoreAfter,
    required this.message,
  });

  final int scoreBefore;
  final int scoreAfter;
  final String message;

  factory RescueOutcome.fromJson(Map<String, dynamic> json) {
    return RescueOutcome(
      scoreBefore: (json['scoreBefore'] as num).toInt(),
      scoreAfter: (json['scoreAfter'] as num).toInt(),
      message: json['message'] as String,
    );
  }
}
