// Offline widget tests for the Step 6.4 Bank Portal Portfolio + Settings
// screens. The backend is faked via a stub repository (Riverpod override), so
// the tests prove: the Portfolio screen renders the 4 live KPI cards + the
// monitoring table (health/trend/status), the Settings screen loads the risk
// policy into toggles/sliders, a toggle persists via updateRiskPolicy and
// round-trips, and both lay out RTL under Arabic — all without a network call.
import 'package:baseerah/features/bank/data/applicant_models.dart';
import 'package:baseerah/features/bank/data/bank_repository.dart';
import 'package:baseerah/features/bank/data/portfolio_models.dart';
import 'package:baseerah/features/bank/data/risk_policy_model.dart';
import 'package:baseerah/features/bank/portfolio/portfolio_screen.dart';
import 'package:baseerah/features/bank/settings/settings_screen.dart';
import 'package:baseerah/features/bank/state/bank_providers.dart';
import 'package:baseerah/l10n/app_localizations.dart';
import 'package:baseerah/theme/baseerah_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

final _portfolio = const Portfolio(
  activeFacilities: 12,
  avgStamina: 71,
  nplRate: 1.8,
  nplBaselineDelta: -23,
  atRiskAccounts: 7,
  monitoring: [
    MonitoringRow(
      borrower: 'Noura Al-Qahtani',
      facility: 'Auto finance',
      health: 82,
      trend: Trend.up,
      status: Status.healthy,
    ),
    MonitoringRow(
      borrower: 'Hind Al-Zahrani',
      facility: 'Home renov.',
      health: 38,
      trend: Trend.down,
      status: Status.atRisk,
    ),
  ],
);

final _policy = const RiskPolicy(
  staminaFloor: 60,
  autoDeclineThreshold: 15,
  ndmoResidency: true,
  tokenization: true,
  samaLastSync: null,
);

/// Stub backend: Portfolio + risk-policy are real; the Applications surface
/// (applicants/report/decide) is unused here so it throws if touched.
class _FakeRepo implements BankRepository {
  int updateCalls = 0;
  RiskPolicy? lastSaved;

  @override
  Future<Portfolio> portfolio() async => _portfolio;

  @override
  Future<RiskPolicy> riskPolicy() async => _policy;

  @override
  Future<RiskPolicy> updateRiskPolicy(RiskPolicy policy) async {
    updateCalls++;
    lastSaved = policy;
    return policy; // round-trip: server echoes the saved policy
  }

  @override
  Future<List<Applicant>> applicants() => throw UnimplementedError();
  @override
  Future<UnderwritingReport> report(String applicationId) =>
      throw UnimplementedError();
  @override
  Future<Applicant> decide(String applicationId, Decision decision) =>
      throw UnimplementedError();
}

Widget _app(Locale locale, Widget screen, List<Override> overrides) {
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
      home: Scaffold(body: screen),
    ),
  );
}

void main() {
  late _FakeRepo repo;
  List<Override> overrides() => [bankRepositoryProvider.overrideWithValue(repo)];
  setUp(() => repo = _FakeRepo());

  // The bank portal is a desktop shell — drive tests at a desktop viewport.
  void useDesktopSurface(WidgetTester tester) {
    tester.view.physicalSize = const Size(1440, 1024);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
  }

  /// Pump past the initial FutureProvider / StateNotifier load.
  Future<void> settle(WidgetTester tester) async {
    await tester.pump(); // build → load fires
    await tester.pump(); // resolve → data/ready state
  }

  group('Portfolio', () {
    testWidgets('renders the 4 KPI cards from the live portfolio',
        (tester) async {
      useDesktopSurface(tester);
      await tester.pumpWidget(
        _app(const Locale('en'), const PortfolioScreen(), overrides()),
      );
      await settle(tester);

      expect(find.text('Active facilities'), findsOneWidget);
      expect(find.text('12'), findsOneWidget);
      expect(find.text('Avg. stamina'), findsOneWidget);
      expect(find.text('71'), findsOneWidget);
      expect(find.text('NPL rate'), findsOneWidget);
      expect(find.text('1.8%'), findsOneWidget);
      // The −% vs-baseline delta on the NPL card.
      expect(find.textContaining('vs baseline'), findsOneWidget);
      expect(find.text('At-risk accounts'), findsOneWidget);
      expect(find.text('7'), findsOneWidget);
    });

    testWidgets('renders monitoring rows with health + status badges',
        (tester) async {
      useDesktopSurface(tester);
      await tester.pumpWidget(
        _app(const Locale('en'), const PortfolioScreen(), overrides()),
      );
      await settle(tester);

      expect(find.text('Noura Al-Qahtani'), findsOneWidget);
      expect(find.text('Hind Al-Zahrani'), findsOneWidget);
      expect(find.text('82'), findsOneWidget); // health score
      expect(find.text('38'), findsOneWidget);
      expect(find.text('Healthy'), findsOneWidget); // status badge
      expect(find.text('At risk'), findsOneWidget);
      expect(find.byIcon(Icons.trending_up), findsOneWidget); // up trend
      expect(find.byIcon(Icons.trending_down), findsOneWidget); // down trend
    });
  });

  group('Settings', () {
    testWidgets('loads the risk policy into status, toggles and sliders',
        (tester) async {
      useDesktopSurface(tester);
      await tester.pumpWidget(
        _app(const Locale('en'), const SettingsScreen(), overrides()),
      );
      await settle(tester);

      expect(find.text('SAMA Open Banking Framework'), findsOneWidget);
      expect(find.text('Never synced'), findsOneWidget); // null last-sync
      expect(find.text('NDMO data residency'), findsOneWidget);
      expect(find.text('Payload tokenization'), findsOneWidget);
      expect(find.text('Minimum stamina to approve'), findsOneWidget);
      expect(find.text('60 / 100'), findsOneWidget); // stamina value chip
      expect(find.text('15%'), findsOneWidget); // auto-decline value chip
      expect(find.byType(Switch), findsNWidgets(2));
      expect(find.byType(Slider), findsNWidgets(2));
    });

    testWidgets('toggling NDMO persists via updateRiskPolicy and round-trips',
        (tester) async {
      useDesktopSurface(tester);
      await tester.pumpWidget(
        _app(const Locale('en'), const SettingsScreen(), overrides()),
      );
      await settle(tester);

      // NDMO is the first switch; it starts on (true).
      final ndmo = find.byType(Switch).first;
      expect(tester.widget<Switch>(ndmo).value, isTrue);

      await tester.tap(ndmo);
      await tester.pump(); // save → saving
      await tester.pump(); // resolve updateRiskPolicy → ready with saved policy

      expect(repo.updateCalls, 1);
      expect(repo.lastSaved?.ndmoResidency, isFalse);
      // Round-tripped result reflected in the UI.
      expect(tester.widget<Switch>(find.byType(Switch).first).value, isFalse);
    });
  });

  testWidgets('Portfolio lays out RTL under Arabic', (tester) async {
    useDesktopSurface(tester);
    await tester.pumpWidget(
      _app(const Locale('ar'), const PortfolioScreen(), overrides()),
    );
    await settle(tester);

    expect(find.text('محفظة القروض'), findsOneWidget); // localized title
    expect(Directionality.of(tester.element(find.text('محفظة القروض'))),
        TextDirection.rtl);
  });

  testWidgets('Settings lays out RTL under Arabic', (tester) async {
    useDesktopSurface(tester);
    await tester.pumpWidget(
      _app(const Locale('ar'), const SettingsScreen(), overrides()),
    );
    await settle(tester);

    expect(find.text('إقامة البيانات (NDMO)'), findsOneWidget);
    expect(Directionality.of(tester.element(find.byType(Switch).first)),
        TextDirection.rtl);
  });
}
