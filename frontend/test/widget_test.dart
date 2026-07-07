// Smoke test: the app boots into the Arabic-first consumer shell and the
// language toggle flips locale + text direction.
import 'package:baseerah/l10n/app_localizations.dart';
import 'package:baseerah/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/test_prefs.dart';

void main() {
  testWidgets('boots in Arabic (RTL) and toggles to English (LTR)', (
    tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [await prefsOverride()],
        child: const BaseerahApp(),
      ),
    );
    await tester.pumpAndSettle();

    // Default locale is Arabic → the brand name renders in Arabic (it appears
    // both in the toolbar title and the Home header), direction RTL.
    expect(find.text('بصيرة'), findsWidgets);
    expect(Directionality.of(tester.element(find.text('بصيرة').first)),
        TextDirection.rtl);

    // Toggle language (button shows the target language: "EN" while in Arabic).
    await tester.tap(find.text('EN'));
    await tester.pumpAndSettle();

    // Now English → brand "Baseerah", direction LTR.
    final l = AppLocalizations.of(tester.element(find.byType(Scaffold).first));
    expect(l.appTitle, 'Baseerah');
    expect(find.text('Baseerah'), findsWidgets);
    expect(Directionality.of(tester.element(find.text('Baseerah').first)),
        TextDirection.ltr);
  });
}
