import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../api/api_client.dart';
import '../data/account.dart';
import '../data/accounts_repository.dart';
import '../data/client.dart';
import '../data/client_repository.dart';
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

/// A predicted near-term liquidity shortfall used to drive the pulsing
/// deficit-warning card. Its real source is the Phase 3 Step 3.2 forecast
/// endpoint (`deficit_date`/`min_projected_balance`); until that lands this is
/// intentionally `null`, so the card stays hidden and Home degrades gracefully
/// (Task 4/7). Wiring it later is a drop-in: return a [DeficitSignal] here.
final deficitSignalProvider = Provider<DeficitSignal?>((ref) => null);

/// A near-term liquidity shortfall: the amount projected short and how many days
/// out. Populated from the forecast in Phase 3; consumed by `DeficitWarningCard`.
class DeficitSignal {
  const DeficitSignal({required this.amountShort, required this.daysUntil});

  final double amountShort;
  final int daysUntil;
}
