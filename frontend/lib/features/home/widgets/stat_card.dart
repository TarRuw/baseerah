import 'package:flutter/material.dart';

import '../../../theme/baseerah_theme.dart';
import 'info_hint.dart';

/// A single 0–100 sub-score card (income consistency / spending health),
/// styled per DESIGN §8 (card radius, soft shadow). Shows the label, an optional
/// tooltip explaining the metric, the value, and a proportional bar. Two of
/// these sit side-by-side under the gauge.
///
/// The whole card speaks the value's health band: a critical score tints the
/// card red, a warning score orange, and an optimal score green (same §5.1
/// cutoffs as the gauge), so the colour tells the story before the number is
/// read.
class StatCard extends StatelessWidget {
  const StatCard({
    super.key,
    required this.label,
    required this.value,
    required this.icon,
    required this.accent,
    this.hint,
  });

  final String label;

  /// Sub-score on the 0–100 healthiness scale.
  final double value;
  final IconData icon;
  final Color accent;

  /// Optional plain-language explanation surfaced via an info tooltip.
  final String? hint;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    final clamped = (value / 100).clamp(0.0, 1.0);
    // The card colour encodes health (higher = healthier for both sub-scores):
    // its zone colour tints the surface, border, value number and bar so a top
    // value reads green and a poor one red (QA UI-04). The icon keeps its
    // per-metric brand [accent] for identity.
    final scoreColor = BaseerahTokens.subScoreColor(value);

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Color.alphaBlend(scoreColor.withValues(alpha: 0.08), Colors.white),
        borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
        border: Border.all(color: scoreColor.withValues(alpha: 0.35)),
        boxShadow: BaseerahTokens.shadowSoft,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 18, color: accent),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  label,
                  style: textTheme.bodySmall?.copyWith(
                    color: BaseerahTokens.muted,
                  ),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              if (hint != null) ...[
                const SizedBox(width: 4),
                InfoHint(message: hint!),
              ],
            ],
          ),
          const SizedBox(height: 8),
          Text(
            value.round().toString(),
            style: textTheme.headlineSmall?.copyWith(
              color: scoreColor,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 8),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: clamped,
              minHeight: 6,
              backgroundColor: scoreColor.withValues(alpha: 0.12),
              valueColor: AlwaysStoppedAnimation<Color>(scoreColor),
            ),
          ),
        ],
      ),
    );
  }
}
