// QA UI-01: on a wide (desktop web) surface the consumer shell must stay
// phone-shaped instead of stretching edge-to-edge, and be a no-op at phone width.
import 'package:baseerah/main.dart';
import 'package:baseerah/shell/bottom_nav.dart';
import 'package:baseerah/theme/baseerah_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  // The consumer bottom nav spans the full width of the shell frame, so its
  // rendered width is a clean proxy for the frame's width.
  Future<double> pumpConsumerNavWidth(WidgetTester tester, Size surface) async {
    await tester.binding.setSurfaceSize(surface);
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(const ProviderScope(child: BaseerahApp()));
    await tester.pumpAndSettle();

    return tester.getSize(find.byType(ConsumerBottomNav)).width;
  }

  testWidgets('caps the consumer shell to a phone width on a wide surface', (
    tester,
  ) async {
    final navWidth = await pumpConsumerNavWidth(tester, const Size(1400, 1000));

    // The frame kicks in: the shell is the phone cap, not the 1400 surface.
    expect(
      navWidth,
      moreOrLessEquals(BaseerahTokens.phoneFrameMaxWidth, epsilon: 1),
    );
    expect(
      tester.getSize(find.byType(MaterialApp)).width,
      greaterThan(BaseerahTokens.phoneFrameMaxWidth),
    );
  });

  testWidgets('is a no-op at phone width (mobile layout is full-bleed)', (
    tester,
  ) async {
    final navWidth = await pumpConsumerNavWidth(tester, const Size(390, 844));

    // 390 <= the breakpoint, so the shell fills the surface — no regression.
    expect(navWidth, moreOrLessEquals(390, epsilon: 1));
  });
}
