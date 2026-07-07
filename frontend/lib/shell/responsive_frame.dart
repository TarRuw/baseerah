import 'package:flutter/material.dart';

import '../theme/baseerah_theme.dart';

/// Keeps a mobile-first shell phone-shaped on wide (web/desktop) viewports.
///
/// Fixes QA finding **UI-01**: without a width constraint the consumer shell
/// stretched edge-to-edge on desktop web, blowing up the 1:1 stress gauge and
/// pushing the score below the fold. Here [child] is capped to [maxWidth] and
/// centered once the viewport is wider than [maxWidth]; at or below it (real
/// phones, and the narrow-web breakpoint) the child renders full-bleed — a true
/// no-op, so the mobile layout is unchanged.
///
/// With [framed] (the consumer shell) the capped column floats as a rounded
/// device frame over the [BaseerahTokens.desktopBackdrop]. Without it (the bank
/// portal, which is a wide desktop layout by design) the cap is applied plainly.
class ResponsiveFrame extends StatelessWidget {
  const ResponsiveFrame({
    super.key,
    required this.maxWidth,
    required this.child,
    this.framed = true,
  });

  /// Width the content is capped to on wide viewports, and the breakpoint below
  /// which this widget is inert.
  final double maxWidth;

  /// When true, wide viewports get a rounded phone frame over the desktop
  /// backdrop; when false, only the plain centered width cap is applied.
  final bool framed;

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        // Real phones (and narrow web) — render unchanged, frame is a no-op.
        if (constraints.maxWidth <= maxWidth) return child;

        final capped = ConstrainedBox(
          constraints: BoxConstraints(maxWidth: maxWidth),
          child: child,
        );

        if (!framed) return Center(child: capped);

        // Wide consumer web: a centered phone frame floating over the backdrop.
        return DecoratedBox(
          decoration: const BoxDecoration(
            gradient: BaseerahTokens.desktopBackdrop,
          ),
          child: Center(
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 16),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(
                  BaseerahTokens.radiusPhoneScreen,
                ),
                child: capped,
              ),
            ),
          ),
        );
      },
    );
  }
}
