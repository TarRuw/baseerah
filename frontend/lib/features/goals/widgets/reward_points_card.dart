import 'package:flutter/material.dart';

import '../../../core/format.dart';
import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../data/rewards.dart';

/// The gold reward-points card at the top of the Goals screen (DESIGN §7.4/§8):
/// the client's total Akhtar Points shown large on the 135° gold gradient, with
/// a risk-tier badge. Presentation-only — it takes a [Rewards] and the locale
/// [Fmt] helper, so the same widget renders live or under test.
class RewardPointsCard extends StatelessWidget {
  const RewardPointsCard({super.key, required this.rewards, required this.fmt});

  final Rewards rewards;
  final Fmt fmt;

  /// A distinguishing glyph per tier — a small visual reward for ranking up.
  IconData get _tierIcon => switch (rewards.tier) {
    RewardTier.bronze => Icons.workspace_premium_outlined,
    RewardTier.silver => Icons.workspace_premium,
    RewardTier.gold => Icons.military_tech_outlined,
    RewardTier.platinum => Icons.military_tech,
  };

  String _tierLabel(AppLocalizations l) => switch (rewards.tier) {
    RewardTier.bronze => l.tierBronze,
    RewardTier.silver => l.tierSilver,
    RewardTier.gold => l.tierGold,
    RewardTier.platinum => l.tierPlatinum,
  };

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: BaseerahTokens.goldGradient,
        borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
        boxShadow: BaseerahTokens.shadowMedium,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.stars_rounded, color: Colors.white, size: 22),
              const SizedBox(width: 8),
              Text(
                l.goalsPointsLabel,
                style: textTheme.titleMedium?.copyWith(
                  color: Colors.white,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const Spacer(),
              _TierBadge(icon: _tierIcon, label: _tierLabel(l)),
            ],
          ),
          const SizedBox(height: 14),
          // The big points number: uses the §8 fmt helper (locale digits/grouping).
          Text(
            fmt.fmt(rewards.points),
            style: textTheme.displaySmall?.copyWith(
              color: Colors.white,
              fontWeight: FontWeight.w800,
              height: 1.0,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            l.goalsPointsCaption,
            style: textTheme.bodySmall?.copyWith(
              color: Colors.white.withValues(alpha: 0.85),
            ),
          ),
        ],
      ),
    );
  }
}

/// The risk-tier pill on the points card — a translucent chip with the tier
/// glyph + localized name.
class _TierBadge extends StatelessWidget {
  const _TierBadge({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.18),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: Colors.white, size: 15),
          const SizedBox(width: 5),
          Text(
            label,
            style: textTheme.labelMedium?.copyWith(
              color: Colors.white,
              fontWeight: FontWeight.w700,
              letterSpacing: 0.3,
            ),
          ),
        ],
      ),
    );
  }
}
