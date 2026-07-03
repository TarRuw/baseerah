import 'package:flutter/painting.dart';

import '../../../theme/baseerah_theme.dart';

/// Slope band of a projection (mirrors the backend `ForecastResponse.Trend`,
/// DESIGN §5.2/§7.1). Drives the chart's line colour — resolved here from the
/// single theme token file so the served trend and the drawn colour never
/// disagree (the same discipline as [StressScore.color]).
enum ForecastTrend {
  up,
  flat,
  down;

  static ForecastTrend fromApi(String raw) => switch (raw.toUpperCase()) {
    'UP' => ForecastTrend.up,
    'DOWN' => ForecastTrend.down,
    _ => ForecastTrend.flat,
  };

  /// Line colour for this trend (DESIGN §8): green = rising, orange = flat,
  /// red = falling / deficit present.
  Color get color => switch (this) {
    ForecastTrend.up => BaseerahTokens.successGreen,
    ForecastTrend.flat => BaseerahTokens.warningOrange,
    ForecastTrend.down => BaseerahTokens.alertRed,
  };
}

/// One projected day: the [date] and the [balance] reached that day.
class ForecastPoint {
  const ForecastPoint({required this.date, required this.balance});

  final DateTime date;
  final double balance;

  factory ForecastPoint.fromJson(Map<String, dynamic> json) {
    return ForecastPoint(
      date: DateTime.parse(json['date'] as String),
      balance: (json['balance'] as num).toDouble(),
    );
  }
}

/// Client-side view of `GET /api/v1/clients/{id}/forecast` (DESIGN §5.2/§6).
///
/// [points] is the ordered day-by-day projected series; [deficitDate] is the
/// first day the balance goes negative ([hasDeficit] is `false`/`null` when it
/// never does); [minProjectedBalance] is the lowest balance over the horizon;
/// [trend] drives the line colour. Amounts stay raw — formatting to `SAR`/`ر.س`
/// is the UI's job (`Fmt.money`).
class Forecast {
  const Forecast({
    required this.points,
    required this.deficitDate,
    required this.minProjectedBalance,
    required this.trend,
  });

  final List<ForecastPoint> points;
  final DateTime? deficitDate;
  final double minProjectedBalance;
  final ForecastTrend trend;

  bool get hasDeficit => deficitDate != null;

  factory Forecast.fromJson(Map<String, dynamic> json) {
    final rawPoints = (json['points'] as List<dynamic>? ?? const [])
        .map((e) => ForecastPoint.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
    final rawDeficit = json['deficitDate'] as String?;
    return Forecast(
      points: rawPoints,
      deficitDate: rawDeficit == null ? null : DateTime.parse(rawDeficit),
      minProjectedBalance: (json['minProjectedBalance'] as num?)?.toDouble() ?? 0,
      trend: ForecastTrend.fromApi(json['trend'] as String? ?? 'FLAT'),
    );
  }
}
