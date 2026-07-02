// Smoke test: the app boots into the Arabic-first consumer shell and the
// language toggle flips locale + text direction.
import 'package:baseerah/l10n/app_localizations.dart';
import 'package:baseerah/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('boots in Arabic (RTL) and toggles to English (LTR)', (
    tester,
  ) async {
    await tester.pumpWidget(const ProviderScope(child: BaseerahApp()));
    await tester.pumpAndSettle();

    // Default locale is Arabic → title in Arabic, direction RTL.
    expect(find.text('بصيرة'), findsOneWidget);
    expect(Directionality.of(tester.element(find.text('بصيرة'))),
        TextDirection.rtl);

    // Toggle language (button shows the target language: "EN" while in Arabic).
    await tester.tap(find.text('EN'));
    await tester.pumpAndSettle();

    // Now English → title "Baseerah", direction LTR.
    final l = AppLocalizations.of(tester.element(find.byType(Scaffold)));
    expect(l.appTitle, 'Baseerah');
    expect(find.text('Baseerah'), findsOneWidget);
    expect(Directionality.of(tester.element(find.text('Baseerah'))),
        TextDirection.ltr);
  });
}
