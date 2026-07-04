import 'package:flutter/material.dart';

import '../../../core/format.dart';
import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../data/rescue_models.dart';

/// A selectable bridge-option card for the Rescue *open* state (DESIGN §7.3):
/// **Murabaha** (green `#1D9E63`) or **Liquidate** (gold `#C4A24C`), each showing
/// the server-provided label/detail, the SAR amount, and — for Murabaha — the
/// repayment term. Selecting one highlights it (accent border + tint + check);
/// the parent gates the confirm button on a selection.
class BridgeCard extends StatelessWidget {
  const BridgeCard({
    super.key,
    required this.option,
    required this.selected,
    required this.onTap,
    required this.fmt,
  });

  final RescueOption option;
  final bool selected;
  final VoidCallback onTap;
  final Fmt fmt;

  /// Per-type accent (DESIGN §8): Murabaha success-green, Liquidate gold.
  Color get _accent => switch (option.type) {
    RescueOptionType.murabaha => BaseerahTokens.successGreen,
    RescueOptionType.liquidate => BaseerahTokens.gold,
  };

  IconData get _icon => switch (option.type) {
    RescueOptionType.murabaha => Icons.handshake_outlined,
    RescueOptionType.liquidate => Icons.savings_outlined,
  };

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final accent = _accent;

    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: selected ? accent.withValues(alpha: 0.06) : Colors.white,
          borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
          border: Border.all(
            color: selected ? accent : const Color(0xFFE4E0D6),
            width: selected ? 2 : 1,
          ),
          boxShadow: selected ? null : BaseerahTokens.shadowSoft,
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: accent.withValues(alpha: 0.12),
                shape: BoxShape.circle,
              ),
              child: Icon(_icon, color: accent, size: 24),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    option.label,
                    style: textTheme.titleMedium?.copyWith(
                      color: BaseerahTokens.darkText,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    option.detail,
                    style: textTheme.bodySmall?.copyWith(
                      color: BaseerahTokens.muted,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Text(
                        fmt.money(option.amount),
                        style: textTheme.titleSmall?.copyWith(
                          color: accent,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      if (option.term != null) ...[
                        Text(
                          '  ·  ',
                          style: textTheme.titleSmall?.copyWith(
                            color: BaseerahTokens.muted,
                          ),
                        ),
                        Text(
                          l.loanTermMonths(option.term!),
                          style: textTheme.bodyMedium?.copyWith(
                            color: BaseerahTokens.muted,
                          ),
                        ),
                      ],
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Icon(
              selected ? Icons.check_circle_rounded : Icons.circle_outlined,
              color: selected ? accent : const Color(0xFFCFC9BC),
              size: 24,
            ),
          ],
        ),
      ),
    );
  }
}
