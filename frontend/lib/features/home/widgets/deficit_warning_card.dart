import 'package:flutter/material.dart';

import '../../../core/format.dart';
import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../state/home_providers.dart';

/// Pulsing liquidity-deficit warning (DESIGN §7.1/§8).
///
/// Rendered only when a [DeficitSignal] is present (its real source is the
/// Phase 3 forecast; hidden until then — see `deficitSignalProvider`). Uses the
/// red-warning gradient and the `bsr-pulse` motion (2.4 s glow) to draw the eye.
class DeficitWarningCard extends StatefulWidget {
  const DeficitWarningCard({super.key, required this.signal, required this.fmt});

  final DeficitSignal signal;
  final Fmt fmt;

  @override
  State<DeficitWarningCard> createState() => _DeficitWarningCardState();
}

class _DeficitWarningCardState extends State<DeficitWarningCard>
    with SingleTickerProviderStateMixin {
  late final AnimationController _pulse;

  @override
  void initState() {
    super.initState();
    // bsr-pulse: a continuous 2.4 s ease-in-out glow (repeat, reversing).
    _pulse = AnimationController(
      vsync: this,
      duration: BaseerahTokens.pulse,
    )..repeat(reverse: true);
  }

  @override
  void dispose() {
    _pulse.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;

    return AnimatedBuilder(
      animation: _pulse,
      builder: (context, child) {
        final t = Curves.easeInOut.transform(_pulse.value);
        return Container(
          decoration: BoxDecoration(
            gradient: BaseerahTokens.redWarningGradient,
            borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
            boxShadow: [
              BoxShadow(
                color: BaseerahTokens.redWarningStart.withValues(
                  alpha: 0.25 + 0.35 * t,
                ),
                blurRadius: 12 + 18 * t,
                spreadRadius: 1 + 2 * t,
              ),
            ],
          ),
          child: child,
        );
      },
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            const Icon(Icons.warning_amber_rounded, color: Colors.white, size: 32),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    l.deficitTitle,
                    style: textTheme.titleMedium?.copyWith(
                      color: Colors.white,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    l.deficitSub,
                    style: textTheme.bodySmall?.copyWith(
                      color: Colors.white.withValues(alpha: 0.9),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Text(
              '−${widget.fmt.money(widget.signal.amountShort)}',
              style: textTheme.titleMedium?.copyWith(
                color: Colors.white,
                fontWeight: FontWeight.w700,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
