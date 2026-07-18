import 'package:flutter/painting.dart';

import '../../../theme/baseerah_theme.dart';

/// Health band for a stress score (mirrors the backend `Zone` enum, DESIGN §5.1).
enum StressZone {
  optimal,
  warning,
  critical;

  static StressZone fromApi(String raw) => switch (raw.toUpperCase()) {
    'OPTIMAL' => StressZone.optimal,
    'WARNING' => StressZone.warning,
    _ => StressZone.critical,
  };
}

/// Client-side view of `GET /api/v1/clients/{id}/stress-score` (DESIGN §5.1/§6).
///
/// [color] is parsed from the server-resolved `color` hex rather than re-derived
/// from [score], so the gauge readout always agrees with the served zone (step
/// notes). Sub-scores are on the same 0–100 healthiness scale.
class StressScore {
  const StressScore({
    required this.score,
    required this.zone,
    required this.color,
    required this.spendingHealth,
    required this.incomeConsistency,
    required this.obligationHealth,
    required this.asOfDate,
  });

  final int score;
  final StressZone zone;
  final Color color;
  final double spendingHealth;
  final double incomeConsistency;
  final double obligationHealth;
  final DateTime asOfDate;

  factory StressScore.fromJson(Map<String, dynamic> json) {
    return StressScore(
      score: (json['score'] as num).toInt(),
      zone: StressZone.fromApi(json['zone'] as String),
      color: BaseerahTokens.hex(json['color'] as String),
      spendingHealth: (json['spendingHealth'] as num).toDouble(),
      incomeConsistency: (json['incomeConsistency'] as num).toDouble(),
      obligationHealth: (json['obligationHealth'] as num).toDouble(),
      asOfDate: DateTime.parse(json['asOfDate'] as String),
    );
  }
}
