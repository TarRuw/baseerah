import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../api/api_client.dart';
import '../data/account.dart';
import '../data/accounts_repository.dart';
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

// ── Screen state ──────────────────────────────────────────────────────────────

/// The persona the consumer app currently represents. Until a persona picker
/// exists, this resolves the first seeded client (`GET /api/v1/clients`). The
/// score/accounts providers depend on it, so everything Home shows is scoped to
/// one client fetched from the API — no hardcoded ids.
final currentClientProvider = FutureProvider<Client>((ref) async {
  final clients = await ref.watch(_clientRepositoryProvider).fetchAll();
  if (clients.isEmpty) {
    throw StateError('No seeded clients returned by /clients');
  }
  return clients.first;
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
