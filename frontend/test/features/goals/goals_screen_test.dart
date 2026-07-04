// Offline widget test for the Step 5.3 Goals screen. The backend is faked via a
// stub repository (Riverpod override), so the test proves the screen renders the
// gold points card + challenge list, shows an enabled Claim on a completed
// challenge, and — on a claim that returns updated points + `claimed` — updates
// the displayed points live and flips the button to "Claimed ✓", all without a
// network call. A final case checks the screen lays out RTL under Arabic.
import 'package:baseerah/features/goals/data/challenge.dart';
import 'package:baseerah/features/goals/data/claim_result.dart';
import 'package:baseerah/features/goals/data/goals_repository.dart';
import 'package:baseerah/features/goals/data/rewards.dart';
import 'package:baseerah/features/goals/goals_screen.dart';
import 'package:baseerah/features/goals/state/goals_providers.dart';
import 'package:baseerah/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

const _completedId = 'challenge-complete';
const _inProgressId = 'challenge-progress';

Challenge _completed({bool claimed = false}) => Challenge(
  id: _completedId,
  icon: 'savings',
  title: 'Micro-saving sprint',
  subtitle: 'Set aside SAR 400',
  reward: 120,
  pct: 100,
  progressText: '400 / 400 SAR',
  claimable: !claimed,
  claimed: claimed,
);

final _inProgress = const Challenge(
  id: _inProgressId,
  icon: 'restaurant',
  title: 'Cap your dining spend',
  subtitle: 'Keep dining under SAR 900',
  reward: 90,
  pct: 40,
  progressText: '360 / 900 SAR',
  claimable: false,
  claimed: false,
);

/// Stub backend: the repository's public surface (the private dio field isn't
/// part of the cross-library interface, so it isn't required).
class _FakeRepo implements GoalsRepository {
  int claimCalls = 0;

  /// When true, `claimChallenge` throws to simulate a 409-rejected claim.
  bool rejectClaim = false;

  @override
  Future<Rewards> fetchRewards(String clientId) async =>
      const Rewards(points: 500, tier: RewardTier.gold);

  @override
  Future<List<Challenge>> fetchChallenges(String clientId) async =>
      [_completed(), _inProgress];

  @override
  Future<ClaimResult> claimChallenge(String clientId, String challengeId) async {
    claimCalls++;
    if (rejectClaim) throw Exception('rejected (409)');
    return ClaimResult(
      rewards: const Rewards(points: 620, tier: RewardTier.gold),
      challenge: _completed(claimed: true),
    );
  }
}

Widget _app(Locale locale, {required List<Override> overrides}) {
  return ProviderScope(
    overrides: overrides,
    child: MaterialApp(
      locale: locale,
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      home: const Scaffold(body: GoalsScreen()),
    ),
  );
}

void main() {
  late _FakeRepo repo;

  List<Override> overrides() => [
    goalsRepositoryProvider.overrideWithValue(repo),
    goalsClientIdProvider.overrideWithValue('test-client'),
  ];

  setUp(() => repo = _FakeRepo());

  /// Pump past the initial parallel rewards+challenges fetch.
  Future<void> settleLoad(WidgetTester tester) async {
    await tester.pump(); // build → controller fires _load()
    await tester.pump(); // resolve Future.wait → ready state
  }

  testWidgets('renders the points card and challenge list', (tester) async {
    await tester.pumpWidget(_app(const Locale('en'), overrides: overrides()));
    await settleLoad(tester);

    // Gold points card: total + tier badge.
    expect(find.text('Reward points'), findsOneWidget);
    expect(find.text('500'), findsOneWidget);
    expect(find.text('Gold'), findsOneWidget);

    // Both challenges rendered, each with its button state.
    expect(find.text('Micro-saving sprint'), findsOneWidget);
    expect(find.text('Cap your dining spend'), findsOneWidget);
    expect(find.text('Claim'), findsOneWidget); // completed → enabled
    expect(find.text('In progress'), findsOneWidget); // incomplete → disabled
  });

  testWidgets('claiming a completed challenge updates points and flips the button',
      (tester) async {
    await tester.pumpWidget(_app(const Locale('en'), overrides: overrides()));
    await settleLoad(tester);

    // Tap the enabled Claim button on the completed challenge.
    await tester.tap(find.text('Claim'));
    await tester.pump(); // register tap → claiming (spinner)
    await tester.pump(); // resolve claim future → merged ready state

    expect(repo.claimCalls, 1);
    // Points card updated live from the claim response, button flipped.
    expect(find.text('620'), findsOneWidget);
    expect(find.text('500'), findsNothing);
    expect(find.text('Claimed ✓'), findsOneWidget);
    expect(find.text('Claim'), findsNothing);
  });

  testWidgets('a rejected claim leaves points and button unchanged',
      (tester) async {
    repo.rejectClaim = true;
    await tester.pumpWidget(_app(const Locale('en'), overrides: overrides()));
    await settleLoad(tester);

    await tester.tap(find.text('Claim'));
    await tester.pump();
    await tester.pump();

    expect(repo.claimCalls, 1);
    // State unchanged: still 500 points and still a claimable button.
    expect(find.text('500'), findsOneWidget);
    expect(find.text('620'), findsNothing);
    expect(find.text('Claim'), findsOneWidget);
    expect(find.text('Claimed ✓'), findsNothing);
  });

  testWidgets('renders right-to-left under Arabic', (tester) async {
    await tester.pumpWidget(_app(const Locale('ar'), overrides: overrides()));
    await settleLoad(tester);

    final title = find.text('تحديات الادخار');
    expect(title, findsOneWidget);
    expect(Directionality.of(tester.element(title)), TextDirection.rtl);
  });
}
