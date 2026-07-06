// Offline widget test for the Step 6.3 Bank Portal Applications screen. The
// backend is faked via a stub repository (Riverpod override), so the test proves
// the split-pane screen renders the applicant queue with risk badges, drives the
// empty → generating → report flow on "generate report", records an
// approve decision (showing the confirmation), disables Approve for a BAD
// verdict, and lays out RTL under Arabic — all without a network call.
import 'package:baseerah/features/bank/applications/applications_screen.dart';
import 'package:baseerah/features/bank/data/applicant_models.dart';
import 'package:baseerah/features/bank/data/bank_repository.dart';
import 'package:baseerah/features/bank/data/portfolio_models.dart';
import 'package:baseerah/features/bank/data/risk_policy_model.dart';
import 'package:baseerah/features/bank/state/bank_providers.dart';
import 'package:baseerah/l10n/app_localizations.dart';
import 'package:baseerah/theme/baseerah_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

const _okId = 'app-ok';
const _badId = 'app-bad';

final _okApplicant = const Applicant(
  id: _okId,
  applicantName: 'Noura Al-Qahtani',
  initials: 'NQ',
  purpose: 'Auto finance',
  amount: 85000,
  verdict: Verdict.ok,
  riskTier: 'A',
  decision: null,
);

final _badApplicant = const Applicant(
  id: _badId,
  applicantName: 'Khalid Al-Otaibi',
  initials: 'KO',
  purpose: 'Personal',
  amount: 60000,
  verdict: Verdict.bad,
  riskTier: 'C',
  decision: null,
);

UnderwritingReport _report(String id, Verdict verdict, int stamina) =>
    UnderwritingReport(
      applicationId: id,
      applicantName: verdict == Verdict.bad ? 'Khalid Al-Otaibi' : 'Noura Al-Qahtani',
      initials: verdict == Verdict.bad ? 'KO' : 'NQ',
      purpose: 'Auto finance',
      amount: 85000,
      staminaScore: stamina,
      forecastDti: 34,
      incomeStability: 94,
      defaultProb12mo: 2.1,
      verdict: verdict,
      riskTier: verdict == Verdict.bad ? 'C' : 'A',
      cashFlow: [
        CashFlowPoint(date: DateTime(2026, 1, 31), balance: 12000),
        CashFlowPoint(date: DateTime(2026, 6, 30), balance: 9000),
        CashFlowPoint(date: DateTime(2026, 12, 31), balance: 15000),
      ],
    );

/// Stub backend: only the public surface (the private dio field isn't part of
/// the cross-library interface, so it isn't required).
class _FakeRepo implements BankRepository {
  int reportCalls = 0;
  int decideCalls = 0;

  @override
  Future<List<Applicant>> applicants() async => [_okApplicant, _badApplicant];

  @override
  Future<UnderwritingReport> report(String applicationId) async {
    reportCalls++;
    // A small delay so the generating (bsr-spin) state renders a real frame
    // before the report resolves — tests advance time to move past it.
    await Future<void>.delayed(const Duration(milliseconds: 50));
    final bad = applicationId == _badId;
    return _report(applicationId, bad ? Verdict.bad : Verdict.ok, bad ? 30 : 82);
  }

  // Portfolio + risk-policy aren't exercised by this Applications-screen test,
  // but the fake must satisfy the full BankRepository surface (Step 6.4).
  @override
  Future<Portfolio> portfolio() async => const Portfolio(
    activeFacilities: 0,
    avgStamina: 0,
    nplRate: 0,
    nplBaselineDelta: 0,
    atRiskAccounts: 0,
    monitoring: [],
  );

  @override
  Future<RiskPolicy> riskPolicy() async => const RiskPolicy(
    staminaFloor: 60,
    autoDeclineThreshold: 15,
    ndmoResidency: true,
    tokenization: true,
    samaLastSync: null,
  );

  @override
  Future<RiskPolicy> updateRiskPolicy(RiskPolicy policy) async => policy;

  @override
  Future<Applicant> decide(String applicationId, Decision decision) async {
    decideCalls++;
    final base = applicationId == _badId ? _badApplicant : _okApplicant;
    return Applicant(
      id: base.id,
      applicantName: base.applicantName,
      initials: base.initials,
      purpose: base.purpose,
      amount: base.amount,
      verdict: base.verdict,
      riskTier: base.riskTier,
      decision: decision,
    );
  }
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
      home: const Scaffold(body: ApplicationsScreen()),
    ),
  );
}

void main() {
  late _FakeRepo repo;

  List<Override> overrides() => [
    bankRepositoryProvider.overrideWithValue(repo),
  ];

  setUp(() => repo = _FakeRepo());

  /// The bank portal is a desktop-only shell (sidebar + split pane), so drive the
  /// tests at a desktop viewport rather than the 800×600 default — otherwise the
  /// split pane is too narrow/short and the report legitimately overflows.
  void useDesktopSurface(WidgetTester tester) {
    tester.view.physicalSize = const Size(1440, 1024);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
  }

  /// Pump past the initial applicant-queue fetch.
  Future<void> settleLoad(WidgetTester tester) async {
    await tester.pump(); // build → FutureProvider fires
    await tester.pump(); // resolve applicants() → data state
  }

  testWidgets('renders the applicant queue with risk badges', (tester) async {
    useDesktopSurface(tester);
    await tester.pumpWidget(_app(const Locale('en'), overrides: overrides()));
    await settleLoad(tester);

    expect(find.text('Noura Al-Qahtani'), findsOneWidget);
    expect(find.text('Khalid Al-Otaibi'), findsOneWidget);
    expect(find.text('Low risk'), findsOneWidget); // OK verdict badge
    expect(find.text('High risk'), findsOneWidget); // BAD verdict badge
    // Empty detail placeholder until a row is selected.
    expect(
      find.text('Select an applicant to run predictive verification'),
      findsOneWidget,
    );
  });

  testWidgets('selecting an applicant runs the empty → generating → report flow',
      (tester) async {
    useDesktopSurface(tester);
    await tester.pumpWidget(_app(const Locale('en'), overrides: overrides()));
    await settleLoad(tester);

    await tester.tap(find.text('Noura Al-Qahtani'));
    await tester.pump(); // select → detail header with Generate button
    expect(find.text('Generate predictive report'), findsOneWidget);

    await tester.tap(find.text('Generate predictive report'));
    await tester.pump(); // → generating (spinner + analyzing copy)
    expect(find.text('Analyzing 24-month telemetry…'), findsOneWidget);
    await tester.pump(const Duration(milliseconds: 100)); // resolve report()

    expect(repo.reportCalls, 1);
    expect(find.text('Sustains the debt'), findsOneWidget); // OK verdict panel
    expect(find.text('82'), findsOneWidget); // stamina score
    expect(find.text('Forecast DTI'), findsOneWidget);
    expect(find.text('Approve loan'), findsOneWidget);
  });

  testWidgets('approving a report records the decision and shows confirmation',
      (tester) async {
    useDesktopSurface(tester);
    await tester.pumpWidget(_app(const Locale('en'), overrides: overrides()));
    await settleLoad(tester);

    await tester.tap(find.text('Noura Al-Qahtani'));
    await tester.pump();
    await tester.tap(find.text('Generate predictive report'));
    await tester.pump(); // → generating
    await tester.pump(const Duration(milliseconds: 100)); // resolve report()

    await tester.tap(find.text('Approve loan'));
    await tester.pump(); // register → deciding
    await tester.pump(); // resolve decide() → decided

    expect(repo.decideCalls, 1);
    expect(
      find.text('Loan approved and dispatched to applicant ✓'),
      findsOneWidget,
    );
  });

  testWidgets('Approve is disabled for a BAD verdict', (tester) async {
    useDesktopSurface(tester);
    await tester.pumpWidget(_app(const Locale('en'), overrides: overrides()));
    await settleLoad(tester);

    await tester.tap(find.text('Khalid Al-Otaibi'));
    await tester.pump();
    await tester.tap(find.text('Generate predictive report'));
    await tester.pump(); // → generating
    await tester.pump(const Duration(milliseconds: 100)); // resolve report()

    expect(find.text('Risk of non-performance'), findsOneWidget);
    final approve = tester.widget<ElevatedButton>(
      find.ancestor(
        of: find.text('Approve loan'),
        matching: find.byType(ElevatedButton),
      ),
    );
    expect(approve.onPressed, isNull); // disabled → can't approve a BAD verdict
  });

  testWidgets('renders right-to-left under Arabic', (tester) async {
    useDesktopSurface(tester);
    await tester.pumpWidget(_app(const Locale('ar'), overrides: overrides()));
    await settleLoad(tester);

    final title = find.text('الطلبات');
    expect(title, findsOneWidget);
    expect(Directionality.of(tester.element(title)), TextDirection.rtl);
  });
}
