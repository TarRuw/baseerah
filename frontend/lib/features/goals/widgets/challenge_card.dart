import 'package:flutter/material.dart';

import '../../../core/format.dart';
import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../data/challenge.dart';

/// A single challenge card for the Goals list (DESIGN §7.4): a leading semantic
/// icon, the title + subtitle, the reward points, a progress bar that fills to
/// `pct` (gold, transitioning to success-green once complete), the server
/// `progressText`, and a tri-state action button (In-progress / Claim /
/// Claimed ✓). Presentation-only: [claiming] shows a per-card spinner while its
/// claim is in flight, and [onClaim] is invoked when an enabled Claim is tapped.
class ChallengeCard extends StatelessWidget {
  const ChallengeCard({
    super.key,
    required this.challenge,
    required this.claiming,
    required this.onClaim,
    required this.fmt,
  });

  final Challenge challenge;

  /// This card's claim request is in flight — lock the button and show progress.
  final bool claiming;
  final VoidCallback onClaim;
  final Fmt fmt;

  /// Maps the backend's semantic icon token to a Material glyph. Unknown tokens
  /// fall back to the savings glyph so a new server token never renders blank.
  IconData get _glyph => switch (challenge.icon) {
    'star' => Icons.star_rounded,
    'restaurant' => Icons.restaurant_rounded,
    'shopping' => Icons.shopping_bag_rounded,
    'flight' => Icons.flight_rounded,
    'hotel' => Icons.hotel_rounded,
    'subscription' => Icons.subscriptions_rounded,
    'fitness' => Icons.fitness_center_rounded,
    'savings' => Icons.savings_rounded,
    _ => Icons.savings_rounded,
  };

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    // Completed goals colour their bar (and icon tint) success-green; in-progress
    // goals stay gold — the single §7.4 gold→green rule, keyed off the model.
    final barColor =
        challenge.isComplete ? BaseerahTokens.successGreen : BaseerahTokens.gold;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
        border: Border.all(color: const Color(0xFFE4E0D6)),
        boxShadow: BaseerahTokens.shadowSoft,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: barColor.withValues(alpha: 0.12),
                  shape: BoxShape.circle,
                ),
                child: Icon(_glyph, color: barColor, size: 24),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      challenge.title,
                      style: textTheme.titleMedium?.copyWith(
                        color: BaseerahTokens.darkText,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      challenge.subtitle,
                      style: textTheme.bodySmall?.copyWith(
                        color: BaseerahTokens.muted,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 10),
              _RewardChip(points: fmt.fmt(challenge.reward), label: l.goalsPointsShort),
            ],
          ),
          const SizedBox(height: 14),
          _ProgressBar(pct: challenge.pct, color: barColor),
          const SizedBox(height: 8),
          Row(
            children: [
              Text(
                challenge.progressText,
                style: textTheme.bodySmall?.copyWith(
                  color: BaseerahTokens.muted,
                ),
              ),
              const Spacer(),
              Text(
                '${challenge.pct}%',
                style: textTheme.labelLarge?.copyWith(
                  color: barColor,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          _ClaimButton(
            state: challenge.buttonState,
            claiming: claiming,
            onClaim: onClaim,
          ),
        ],
      ),
    );
  }
}

/// The reward-points pill on a challenge card, e.g. `120 pts`.
class _RewardChip extends StatelessWidget {
  const _RewardChip({required this.points, required this.label});

  final String points;
  final String label;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
      decoration: BoxDecoration(
        color: BaseerahTokens.gold.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.add_rounded, size: 13, color: BaseerahTokens.goldDark),
          Text(
            '$points $label',
            style: textTheme.labelMedium?.copyWith(
              color: BaseerahTokens.goldDark,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}

/// A rounded progress track that animates its fill from 0 → `pct` on build,
/// reusing the theme's score-animation curve so it feels of a piece with the
/// Home gauge. The fill [color] carries the gold→green completion signal.
class _ProgressBar extends StatelessWidget {
  const _ProgressBar({required this.pct, required this.color});

  final int pct;
  final Color color;

  @override
  Widget build(BuildContext context) {
    final fraction = (pct.clamp(0, 100)) / 100.0;
    // LinearProgressIndicator fills its width and honours Directionality (fills
    // from the right under RTL), so the bar mirrors correctly for Arabic.
    return ClipRRect(
      borderRadius: BorderRadius.circular(999),
      child: TweenAnimationBuilder<double>(
        tween: Tween(begin: 0, end: fraction),
        duration: BaseerahTokens.scoreAnimation,
        curve: BaseerahTokens.scoreAnimationCurve,
        builder: (context, value, _) => LinearProgressIndicator(
          value: value,
          minHeight: 10,
          backgroundColor: const Color(0xFFEDEAE1),
          valueColor: AlwaysStoppedAnimation<Color>(color),
        ),
      ),
    );
  }
}

/// The tri-state action button (DESIGN §7.4). *Claim* is the only enabled state;
/// *In-progress* and *Claimed ✓* are disabled. While [claiming] it shows a
/// spinner so a slow backend never looks unresponsive.
class _ClaimButton extends StatelessWidget {
  const _ClaimButton({
    required this.state,
    required this.claiming,
    required this.onClaim,
  });

  final ClaimButtonState state;
  final bool claiming;
  final VoidCallback onClaim;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);

    if (claiming) {
      return const SizedBox(
        width: double.infinity,
        child: ElevatedButton(
          onPressed: null,
          child: SizedBox(
            width: 18,
            height: 18,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
        ),
      );
    }

    return switch (state) {
      ClaimButtonState.claim => SizedBox(
        width: double.infinity,
        child: ElevatedButton.icon(
          onPressed: onClaim,
          icon: const Icon(Icons.redeem_rounded, size: 18),
          label: Text(l.goalsClaim),
          style: ElevatedButton.styleFrom(
            backgroundColor: BaseerahTokens.successGreen,
            padding: const EdgeInsets.symmetric(vertical: 13),
          ),
        ),
      ),
      ClaimButtonState.claimed => SizedBox(
        width: double.infinity,
        child: OutlinedButton.icon(
          onPressed: null,
          icon: const Icon(Icons.check_circle_rounded, size: 18),
          label: Text(l.goalsClaimed),
          style: OutlinedButton.styleFrom(
            foregroundColor: BaseerahTokens.successGreen,
            disabledForegroundColor: BaseerahTokens.successGreen,
            side: const BorderSide(color: BaseerahTokens.successGreen),
            padding: const EdgeInsets.symmetric(vertical: 13),
            shape: RoundedRectangleBorder(
              borderRadius:
                  BorderRadius.circular(BaseerahTokens.radiusControl),
            ),
          ),
        ),
      ),
      ClaimButtonState.inProgress => SizedBox(
        width: double.infinity,
        child: ElevatedButton(
          onPressed: null,
          style: ElevatedButton.styleFrom(
            padding: const EdgeInsets.symmetric(vertical: 13),
          ),
          child: Text(l.goalsInProgress),
        ),
      ),
    };
  }
}
