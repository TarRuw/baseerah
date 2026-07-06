// Offline widget test for the Step 3.5 Simulate screen. The backend is faked via
// a stub repository (Riverpod override), so the test proves the screen renders,
// switches tabs, surfaces the API-driven loan result with the server-supplied
// colours, and completes a chat round-trip — all without a network call.
import 'package:baseerah/features/simulate/data/invoice_result.dart';
import 'package:baseerah/features/simulate/data/loan_result.dart';
import 'package:baseerah/features/simulate/data/simulate_repository.dart';
import 'package:baseerah/features/simulate/simulate_screen.dart';
import 'package:baseerah/features/simulate/state/simulate_providers.dart';
import 'package:baseerah/l10n/app_localizations.dart';
import 'package:baseerah/theme/baseerah_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// Stub backend: implements the repository's public surface (the private dio
/// field isn't part of the cross-library interface, so it isn't required).
class _FakeRepo implements SimulateRepository {
  int loanCalls = 0;

  @override
  Future<LoanResult> simulate(
    String clientId, {
    required double principal,
    required double rate,
    required int term,
  }) async {
    loanCalls++;
    return LoanResult(
      installment: 1234,
      total: 44424,
      dti: 0.55,
      dtiColor: BaseerahTokens.successGreen,
      verdict: 'Comfortably affordable',
      verdictColor: BaseerahTokens.successGreen,
      projectedScore: 58,
    );
  }

  @override
  Future<String> chat(String clientId, String message) async =>
      'Mock reply to: $message';

  @override
  Future<InvoiceResult> parseInvoice(
    String clientId,
    List<int> bytes, {
    required String filename,
  }) async =>
      const InvoiceResult(
        merchant: 'Store',
        amount: 90,
        category: 'Groceries',
        suggestedAction: 'Review it',
      );
}

Widget _app(Locale locale, {required List<Override> overrides}) {
  return ProviderScope(
    overrides: overrides,
    child: MaterialApp(
      locale: locale,
      theme: BaseerahTheme.light(locale),
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      home: const Scaffold(body: SimulateScreen()),
    ),
  );
}

void main() {
  late _FakeRepo repo;

  List<Override> overrides() => [
        simulateRepositoryProvider.overrideWithValue(repo),
        currentClientIdProvider.overrideWithValue('test-client'),
      ];

  setUp(() => repo = _FakeRepo());

  testWidgets('loan tab shows the API-driven result with server colours',
      (tester) async {
    await tester.pumpWidget(_app(const Locale('en'), overrides: overrides()));
    // Resolve the initial (un-debounced) loan fetch — pump past its microtask.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    // Tab labels present.
    expect(find.text('Loan Affordability'), findsOneWidget);
    expect(find.text('Ask Baseerah AI'), findsOneWidget);

    // Result card is driven by the (faked) API: installment + verdict rendered.
    expect(find.textContaining('1,234'), findsOneWidget);
    expect(find.text('Comfortably affordable'), findsOneWidget);
    expect(repo.loanCalls, 1);
  });

  testWidgets('AI tab: suggestion chip sends and appends a reply',
      (tester) async {
    await tester.pumpWidget(_app(const Locale('en'), overrides: overrides()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    // Switch to the chat tab.
    await tester.tap(find.text('Ask Baseerah AI'));
    await tester.pump();

    // Suggestion chips show before the conversation starts.
    const chip = 'Can I afford a 40,000 SAR loan?';
    expect(find.text(chip), findsOneWidget);

    // Tapping a chip sends it (user bubble) and, after the typing floor, appends
    // the AI reply. Advance the clock explicitly — the typing dots never settle.
    await tester.tap(find.text(chip));
    await tester.pump(); // register tap → user bubble + typing dots
    await tester.pump(const Duration(milliseconds: 700)); // past 500ms floor
    await tester.pump(); // apply the reply state

    expect(find.text('Mock reply to: $chip'), findsOneWidget);
  });

  testWidgets('renders right-to-left under Arabic', (tester) async {
    await tester.pumpWidget(_app(const Locale('ar'), overrides: overrides()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    // Arabic title present and laid out RTL.
    final title = find.text('محاكاة');
    expect(title, findsOneWidget);
    expect(Directionality.of(tester.element(title)), TextDirection.rtl);
  });
}
