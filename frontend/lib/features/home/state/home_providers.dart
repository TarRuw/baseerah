import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../api/api_client.dart';
import '../../auth/state/auth_providers.dart';
import '../data/account.dart';
import '../data/accounts_repository.dart';
import '../data/cashflow_repository.dart';
import '../data/cashflow_summary.dart';
import '../data/client.dart';
import '../data/client_repository.dart';
import '../data/forecast.dart';
import '../data/forecast_repository.dart';
import '../data/stress_score.dart';
import '../data/stress_score_repository.dart';

// ── Repositories ──────────────────────────────────────────────────────────────
// Rebuild with the api client so `Accept-Language` tracks the active locale.

final _clientRepositoryProvider = Provider<ClientRepository>(
  (ref) => ClientRepository(ref.watch(apiClientProvider)),
);

final _stressScoreRepositoryProvider = Provider<StressScoreRepository>(
  (ref) => StressScoreRepository(ref.watch(apiClientProvider)),
);

final _accountsRepositoryProvider = Provider<AccountsRepository>(
  (ref) => AccountsRepository(ref.watch(apiClientProvider)),
);

final _forecastRepositoryProvider = Provider<ForecastRepository>(
  (ref) => ForecastRepository(ref.watch(apiClientProvider)),
);

final _cashflowRepositoryProvider = Provider<CashflowRepository>(
  (ref) => CashflowRepository(ref.watch(apiClientProvider)),
);

// ── Screen state ──────────────────────────────────────────────────────────────

/// The persona (client) the consumer app runs as — now resolved from the
/// **authenticated user** (Step 9.4), not a build-time selector. Login (Step 9.5)
/// replaces the retired `BASEERAH_CLIENT` dart-define: the signed-in
/// `AuthUser.clientId` / `clientExternalId` (from `/auth/me`) identifies the
/// persona, and the app never shows a client the user isn't logged in as.
///
/// The `/clients` row is still fetched to hydrate the human-readable
/// `profileLabel` / `persona` the Home UI shows; it is matched to the session by
/// id (no hardcoded UUIDs). Depends on [authControllerProvider], so it refetches
/// on login/logout. Throws when there is no authenticated consumer, so consumer
/// screens degrade to an error state rather than leaking another persona.
final currentClientProvider = FutureProvider<Client>((ref) async {
  final user = ref.watch(currentUserProvider);
  if (user == null || !user.isConsumer) {
    throw StateError('No authenticated consumer; cannot resolve current client.');
  }
  final clients = await ref.watch(_clientRepositoryProvider).fetchAll();
  return clients.firstWhere(
    (c) => c.id == user.clientId || c.externalId == user.clientExternalId,
    orElse: () => throw StateError(
      'Authenticated client "${user.clientExternalId ?? user.clientId}" '
      'is not among the seeded personas returned by /clients.',
    ),
  );
});

/// Live Financial Stress Score for the current client (loading/error surfaced by
/// the `AsyncValue`). Depends on [currentClientProvider], so it refetches if the
/// client changes.
final stressScoreProvider = FutureProvider<StressScore>((ref) async {
  final client = await ref.watch(currentClientProvider.future);
  return ref.watch(_stressScoreRepositoryProvider).fetch(client.id);
});

/// Live linked accounts for the current client.
final accountsProvider = FutureProvider<List<Account>>((ref) async {
  final client = await ref.watch(currentClientProvider.future);
  return ref.watch(_accountsRepositoryProvider).fetch(client.id);
});

/// Average monthly income and spending for the current client (loading/error
/// surfaced by the `AsyncValue`). Backs the two Home cash-flow cards; depends on
/// [currentClientProvider], so it refetches if the client changes.
final cashflowSummaryProvider = FutureProvider<CashflowSummary>((ref) async {
  final client = await ref.watch(currentClientProvider.future);
  return ref.watch(_cashflowRepositoryProvider).fetch(client.id);
});

/// Live 30-day cash-flow forecast for the current client (loading/error surfaced
/// by the `AsyncValue`). Backs the Home forecast chart; depends on
/// [currentClientProvider], so it refetches if the client changes.
final forecastProvider = FutureProvider<Forecast>((ref) async {
  final client = await ref.watch(currentClientProvider.future);
  return ref.watch(_forecastRepositoryProvider).fetch(client.id, horizonDays: 30);
});

/// A predicted near-term liquidity shortfall driving the pulsing deficit-warning
/// card. Derived from [forecastProvider]: when the projection crosses zero
/// (`deficitDate`), the card shows the deepest projected shortfall
/// (`minProjectedBalance`) and how many days out it is. Otherwise `null`, so the
/// card stays hidden and Home degrades gracefully. On the seeded personas the
/// base forecast never crosses zero (see Step 3.1 handoff), so this stays `null`
/// until a Phase-4 what-if scenario drives a deficit.
final deficitSignalProvider = Provider<DeficitSignal?>((ref) {
  return ref.watch(forecastProvider).maybeWhen(
    data: (forecast) {
      final deficitDate = forecast.deficitDate;
      if (deficitDate == null || forecast.points.isEmpty) return null;
      // Anchor "days until" to the series' first point (server "today"), not the
      // device clock, so it can't drift from the projection.
      final daysUntil = deficitDate.difference(forecast.points.first.date).inDays;
      return DeficitSignal(
        amountShort: forecast.minProjectedBalance.abs(),
        daysUntil: daysUntil < 0 ? 0 : daysUntil,
      );
    },
    orElse: () => null,
  );
});

/// A near-term liquidity shortfall: the amount projected short and how many days
/// out. Populated from the forecast in Phase 3; consumed by `DeficitWarningCard`.
class DeficitSignal {
  const DeficitSignal({required this.amountShort, required this.daysUntil});

  final double amountShort;
  final int daysUntil;
}
