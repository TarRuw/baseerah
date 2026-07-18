import 'package:flutter/material.dart';

import '../../../theme/baseerah_theme.dart';

/// A small circled-info affordance that reveals an explanatory [message] as a
/// tooltip. Tap-triggered (not just hover/long-press) so it works the same on a
/// phone as on the web frame, giving newcomers a plain-language definition of an
/// on-screen metric (e.g. "Income consistency", the health score) without
/// cluttering the card with body copy.
class InfoHint extends StatelessWidget {
  const InfoHint({super.key, required this.message, this.size = 15});

  /// The plain-language explanation shown when the icon is tapped.
  final String message;

  /// Diameter of the info glyph in logical pixels.
  final double size;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: message,
      triggerMode: TooltipTriggerMode.tap,
      showDuration: const Duration(seconds: 6),
      preferBelow: false,
      margin: const EdgeInsets.symmetric(horizontal: 24),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: BaseerahTokens.darkText,
        borderRadius: BorderRadius.circular(BaseerahTokens.radiusControl),
      ),
      textStyle: Theme.of(context).textTheme.bodySmall?.copyWith(
        color: Colors.white,
        height: 1.4,
      ),
      child: Icon(
        Icons.info_outline,
        size: size,
        color: BaseerahTokens.muted,
        semanticLabel: message,
      ),
    );
  }
}
