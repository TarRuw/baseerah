// Client-side view of the Bank Portal portfolio (FR-08, DESIGN §7.6), mirroring
// the backend `PortfolioDto` served by `GET /api/v1/bank/portfolio`:
//   • four KPI figures (active facilities, avg stamina, NPL rate + baseline
//     delta, at-risk accounts), and
//   • one `MonitoringRow` per active-book facility for the monitoring table.
//
// Every figure is server-computed from the seeded underwriting data (Global
// Rule: nothing recomputed client-side) — this layer is pure presentation.

/// Health trend of a monitoring row relative to the portfolio mean — drives the
/// table's ↑ / → / ↓ arrow (DESIGN §7.6). Mirrors the backend `Trend` enum.
enum Trend {
  up,
  flat,
  down;

  /// Parse the upper-case wire value (`UP` | `FLAT` | `DOWN`); throws on
  /// anything else so a malformed payload surfaces as an error, not a wrong arrow.
  static Trend fromWire(String value) => switch (value) {
    'UP' => Trend.up,
    'FLAT' => Trend.flat,
    'DOWN' => Trend.down,
    _ => throw ArgumentError('Unknown trend: $value'),
  };
}

/// Monitoring status badge (DESIGN §7.6): Healthy · Watch · At-risk, aligned to
/// the §5.5 verdict. Mirrors the backend `Status` enum and reuses the shared
/// zone palette when rendered (green / orange / red).
enum Status {
  healthy,
  watch,
  atRisk;

  /// Parse the upper-case wire value (`HEALTHY` | `WATCH` | `AT_RISK`).
  static Status fromWire(String value) => switch (value) {
    'HEALTHY' => Status.healthy,
    'WATCH' => Status.watch,
    'AT_RISK' => Status.atRisk,
    _ => throw ArgumentError('Unknown status: $value'),
  };
}

/// One row of the portfolio monitoring table (DESIGN §7.6): a borrower, their
/// facility, a health score, a trend arrow, and a status badge.
class MonitoringRow {
  const MonitoringRow({
    required this.borrower,
    required this.facility,
    required this.health,
    required this.trend,
    required this.status,
  });

  final String borrower;
  final String facility;

  /// The facility's health score (its stamina, 0–100).
  final int health;

  final Trend trend;
  final Status status;

  factory MonitoringRow.fromJson(Map<String, dynamic> json) {
    return MonitoringRow(
      borrower: json['borrower'] as String,
      facility: json['facility'] as String,
      health: (json['health'] as num).toInt(),
      trend: Trend.fromWire(json['trend'] as String),
      status: Status.fromWire(json['status'] as String),
    );
  }
}

/// The bank's monitored portfolio (`GET …/portfolio`): four KPI figures plus the
/// monitoring rows. [nplRate] is the screened active book's modelled NPL rate as
/// a percentage; [nplBaselineDelta] is that rate minus the un-screened baseline
/// (≤ 0 = a reduction) in percentage points — both server-computed.
class Portfolio {
  const Portfolio({
    required this.activeFacilities,
    required this.avgStamina,
    required this.nplRate,
    required this.nplBaselineDelta,
    required this.atRiskAccounts,
    required this.monitoring,
  });

  final int activeFacilities;
  final int avgStamina;
  final double nplRate;
  final double nplBaselineDelta;
  final int atRiskAccounts;
  final List<MonitoringRow> monitoring;

  factory Portfolio.fromJson(Map<String, dynamic> json) {
    return Portfolio(
      activeFacilities: (json['activeFacilities'] as num).toInt(),
      avgStamina: (json['avgStamina'] as num).toInt(),
      nplRate: (json['nplRate'] as num).toDouble(),
      nplBaselineDelta: (json['nplBaselineDelta'] as num).toDouble(),
      atRiskAccounts: (json['atRiskAccounts'] as num).toInt(),
      monitoring: (json['monitoring'] as List<dynamic>)
          .map((e) => MonitoringRow.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}
