import 'package:flutter/material.dart';

import '../../../core/format.dart';
import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';

/// Dark-red shortfall banner for the Rescue *open* state (DESIGN §7.3/§8): the
/// predicted **−SAR** magnitude and **"in N days"**, on the red-warning
/// gradient. It [pulse]s with `bsr-pulse` only when the deficit is inside the
/// FR-06 15-day alert window (`alertRaised`) — an honest urgency cue; a distant,
/// non-alerting deficit shows the same banner without the attention glow.
class ShortfallBanner extends StatefulWidget {
  const ShortfallBanner({
    super.key,
    required this.shortfall,
    required this.days,
    required this.pulse,
    required this.fmt,
  });

  final double shortfall;
  final int days;
  final bool pulse;
  final Fmt fmt;

  @override
  State<ShortfallBanner> createState() => _ShortfallBannerState();
}

class _ShortfallBannerState extends State<ShortfallBanner>
    with SingleTickerProviderStateMixin {
  AnimationController? _pulse;

  @override
  void initState() {
    super.initState();
    if (widget.pulse) {
      // bsr-pulse: a continuous 2.4 s ease-in-out glow (repeat, reversing).
      _pulse = AnimationController(vsync: this, duration: BaseerahTokens.pulse)
        ..repeat(reverse: true);
    }
  }

  @override
  void dispose() {
    _pulse?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;

    final content = Padding(
      padding: const EdgeInsets.all(18),
      child: Row(
        children: [
          const Icon(Icons.trending_down_rounded, color: Colors.white, size: 34),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  l.rescueShortfallLabel,
                  style: textTheme.bodySmall?.copyWith(
                    color: Colors.white.withValues(alpha: 0.9),
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  '−${widget.fmt.money(widget.shortfall)}',
                  style: textTheme.headlineSmall?.copyWith(
                    color: Colors.white,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  l.rescueInDays(widget.fmt.fmt(widget.days)),
                  style: textTheme.bodyMedium?.copyWith(
                    color: Colors.white.withValues(alpha: 0.9),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );

    final pulse = _pulse;
    if (pulse == null) {
      return Container(
        decoration: BoxDecoration(
          gradient: BaseerahTokens.redWarningGradient,
          borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
          boxShadow: BaseerahTokens.shadowSoft,
        ),
        child: content,
      );
    }

    return AnimatedBuilder(
      animation: pulse,
      builder: (context, child) {
        final t = Curves.easeInOut.transform(pulse.value);
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
      child: content,
    );
  }
}
