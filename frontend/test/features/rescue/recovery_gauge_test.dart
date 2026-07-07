// QA UI-03: the rescue recovery gauge's headline number must be the *recovered*
// score (matches the "recovers X → Y" caption), never the pre-rescue score.
import 'package:baseerah/features/rescue/data/rescue_models.dart';
import 'package:baseerah/features/rescue/widgets/recovery_gauge.dart';
import 'package:baseerah/l10n/app_localizations.dart';
import 'package:baseerah/theme/baseerah_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';

Widget _host(RescueOutcome outcome) {
  const locale = Locale('en');
  return MaterialApp(
    locale: locale,
    theme: BaseerahTheme.light(locale),
    supportedLocales: AppLocalizations.supportedLocales,
    localizationsDelegates: const [
      AppLocalizations.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    home: Scaffold(body: RecoveryGauge(outcome: outcome)),
  );
}

void main() {
  testWidgets('rests on the recovered score, not the before score', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(const RescueOutcome(scoreBefore: 83, scoreAfter: 91, message: '')),
    );
    await tester.pumpAndSettle();

    expect(find.text('91'), findsOneWidget);
    expect(find.text('83'), findsNothing);
  });
}
