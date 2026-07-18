import 'package:flutter/material.dart';

import '../../../theme/baseerah_theme.dart';
import 'info_hint.dart';

/// A compact money figure card (average monthly income / spending), styled like
/// [StatCard] but for a currency amount rather than a 0–100 score: label with an
/// optional info tooltip, an icon, and the formatted amount. Its [accent] tints
/// the surface, border, icon and value so income and spending read as two
/// distinct, colour-coded figures. Two sit side-by-side above the sub-score
/// cards on Home.
class MoneyStatCard extends StatelessWidget {
  const MoneyStatCard({
    super.key,
    required this.label,
    required this.value,
    required this.icon,
    required this.accent,
    this.hint,
  });

  final String label;

  /// Pre-formatted money string (e.g. `18,500 SAR` / `١٨٬٥٠٠ ر.س`).
  final String value;
  final IconData icon;
  final Color accent;

  /// Optional plain-language explanation surfaced via an info tooltip.
  final String? hint;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Color.alphaBlend(accent.withValues(alpha: 0.08), Colors.white),
        borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
        border: Border.all(color: accent.withValues(alpha: 0.35)),
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
          FittedBox(
            fit: BoxFit.scaleDown,
            alignment: AlignmentDirectional.centerStart,
            child: Text(
              value,
              maxLines: 1,
              style: textTheme.titleLarge?.copyWith(
                color: accent,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
