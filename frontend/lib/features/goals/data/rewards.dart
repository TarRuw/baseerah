// Client-side view of the Akhtar-Points balance (FR-10, DESIGN §5.6/§7.4),
// mirroring the backend `RewardsDto` from `GET /api/v1/clients/{id}/rewards`
// and the `points`/`riskTier` head of the claim response.

/// The consumer gamification tier a points balance falls into (mirrors the
/// backend `RewardTier`). The wire form is upper-case (`BRONZE`…`PLATINUM`);
/// [fromWire] parses it, tolerating an unknown value as [bronze] so a new tier
/// added server-side never crashes an older client — the badge just shows the
/// base tier until the app catches up.
///
/// Deliberately distinct from the bank-side underwriting `risk_tier` (Phase 6);
/// they share no mapping (Step 5.1 handoff). The field is named `riskTier` on
/// the wire only to match the DESIGN §5.6 Goals-screen label.
enum RewardTier {
  bronze,
  silver,
  gold,
  platinum;

  static RewardTier fromWire(String value) => switch (value.toUpperCase()) {
    'BRONZE' => RewardTier.bronze,
    'SILVER' => RewardTier.silver,
    'GOLD' => RewardTier.gold,
    'PLATINUM' => RewardTier.platinum,
    _ => RewardTier.bronze,
  };
}

/// The client's rewards summary shown on the gold points card: the current
/// [points] balance and the [tier] it maps to.
class Rewards {
  const Rewards({required this.points, required this.tier});

  final int points;
  final RewardTier tier;

  factory Rewards.fromJson(Map<String, dynamic> json) {
    return Rewards(
      points: (json['points'] as num).toInt(),
      tier: RewardTier.fromWire(json['riskTier'] as String),
    );
  }
}
