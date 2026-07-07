import 'package:flutter/material.dart';

import '../../../theme/baseerah_theme.dart';

/// A single 0–100 sub-score card (income consistency / spending velocity),
/// styled per DESIGN §8 (card radius, soft shadow). Shows the label, the value,
/// and a proportional bar. Two of these sit side-by-side under the gauge.
class StatCard extends StatelessWidget {
  const StatCard({
    super.key,
    required this.label,
    required this.value,
    required this.icon,
    required this.accent,
  });

  final String label;

  /// Sub-score on the 0–100 healthiness scale.
  final double value;
  final IconData icon;
  final Color accent;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    final clamped = (value / 100).clamp(0.0, 1.0);
    // The bar encodes health (higher = healthier for both sub-scores), so its
    // colour follows the value's zone — a top value reads green, not caution
    // (QA UI-04). The icon keeps its per-metric brand [accent] for identity.
    final barColor = BaseerahTokens.subScoreColor(value);

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
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
            ],
          ),
          const SizedBox(height: 8),
          Text(
            value.round().toString(),
            style: textTheme.headlineSmall?.copyWith(
              color: BaseerahTokens.darkText,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 8),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: clamped,
              minHeight: 6,
              backgroundColor: barColor.withValues(alpha: 0.12),
              valueColor: AlwaysStoppedAnimation<Color>(barColor),
            ),
          ),
        ],
      ),
    );
  }
}
