import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../api/api_client.dart';
import '../../home/state/home_providers.dart';
import '../data/challenge.dart';
import '../data/goals_repository.dart';
import '../data/rewards.dart';

// ── Repository + current client ─────────────────────────────────────────────

/// Rebuilds with the api client so `Accept-Language` tracks the active locale
/// (the server localises `progressText`'s SAR label off that header).
final goalsRepositoryProvider = Provider<GoalsRepository>(
  (ref) => GoalsRepository(ref.watch(apiClientProvider)),
);

/// The current persona's id, reusing Home's resolver (Step 2.3). Null while the
/// `/clients` call is in flight — the controller stays in [GoalsPhase.loading]
/// until it lands. Public so widget tests can override it with a fixed id.
final goalsClientIdProvider = Provider<String?>(
  (ref) => ref.watch(currentClientProvider).valueOrNull?.id,
);

// ── Screen state ────────────────────────────────────────────────────────────

/// The discrete phases the Goals screen moves through: initial fetch → *ready*
/// (points card + challenge list) or *error* (load failed + retry).
enum GoalsPhase { loading, error, ready }

/// Immutable snapshot of the Goals screen. In [GoalsPhase.ready] both [rewards]
/// and [challenges] are non-null; [claimingIds] holds the challenges whose claim
/// is in flight (so their card shows a spinner and locks) — a Set, not a single
/// id, so independent claims never clobber each other's pending state.
class GoalsState {
  const GoalsState._({
    required this.phase,
    this.rewards,
    this.challenges = const [],
    this.claimingIds = const {},
    this.failure,
  });

  const GoalsState.loading() : this._(phase: GoalsPhase.loading);

  const GoalsState.error(Object failure)
    : this._(phase: GoalsPhase.error, failure: failure);

  const GoalsState.ready(
    Rewards rewards,
    List<Challenge> challenges, {
    Set<String> claimingIds = const {},
  }) : this._(
         phase: GoalsPhase.ready,
         rewards: rewards,
         challenges: challenges,
         claimingIds: claimingIds,
       );

  final GoalsPhase phase;
  final Rewards? rewards;
  final List<Challenge> challenges;
  final Set<String> claimingIds;

  /// The exception from a failed initial fetch (error phase); retained for
  /// diagnostics — the view renders a generic message, not this object.
  final Object? failure;

  bool isClaiming(String challengeId) => claimingIds.contains(challengeId);
}

/// Owns the Goals screen's state machine (Step 5.3). Fetches the rewards summary
/// and challenge list together on creation, and drives the live claim flow:
/// claim → server confirms → the points card + affected challenge flip in place.
/// The repository is read fresh per call so requests always carry the current
/// locale's `Accept-Language`.
class GoalsController extends StateNotifier<GoalsState> {
  GoalsController(this._ref, this._clientId)
    : super(const GoalsState.loading()) {
    if (_clientId != null) _load();
  }

  final Ref _ref;
  final String? _clientId;

  Future<void> _load() async {
    final id = _clientId;
    if (id == null) return;
    state = const GoalsState.loading();
    try {
      final repo = _ref.read(goalsRepositoryProvider);
      // Fetch both in parallel — neither depends on the other, so this halves
      // the time-to-first-paint versus awaiting them in sequence.
      final results = await Future.wait([
        repo.fetchRewards(id),
        repo.fetchChallenges(id),
      ]);
      if (!mounted) return;
      state = GoalsState.ready(
        results[0] as Rewards,
        results[1] as List<Challenge>,
      );
    } catch (error) {
      if (mounted) state = GoalsState.error(error);
    }
  }

  /// Claim a completed challenge. On success the points card updates from the
  /// server's new balance/tier and the challenge flips to `claimed` in place;
  /// returns true. On a rejected/failed claim it returns false, leaving the
  /// points and challenges unchanged so the screen can surface a non-blocking
  /// error (DESIGN §7.4) — the on-response merge means there's nothing to roll
  /// back. Guards against claiming a non-claimable or already-in-flight card.
  Future<bool> claim(String challengeId) async {
    final current = state;
    final id = _clientId;
    if (id == null || current.phase != GoalsPhase.ready) return false;

    Challenge? target;
    for (final c in current.challenges) {
      if (c.id == challengeId) {
        target = c;
        break;
      }
    }
    if (target == null ||
        !target.claimable ||
        current.claimingIds.contains(challengeId)) {
      return false;
    }

    // Mark this card as claiming (spinner + locked) without touching the rest.
    state = GoalsState.ready(
      current.rewards!,
      current.challenges,
      claimingIds: {...current.claimingIds, challengeId},
    );

    try {
      final result =
          await _ref.read(goalsRepositoryProvider).claimChallenge(id, challengeId);
      if (!mounted) return false;
      final latest = state;
      // Merge on-response: swap the claimed challenge in and adopt the new
      // rewards summary; clear this card's pending flag. Rebuild off `latest`
      // (not the captured `current`) so a concurrent claim isn't lost.
      state = GoalsState.ready(
        result.rewards,
        [
          for (final c in latest.challenges)
            if (c.id == challengeId) result.challenge else c,
        ],
        claimingIds: latest.claimingIds.difference({challengeId}),
      );
      return true;
    } catch (_) {
      if (mounted) {
        final latest = state;
        // Leave points + challenges untouched; just release the pending flag.
        state = GoalsState.ready(
          latest.rewards!,
          latest.challenges,
          claimingIds: latest.claimingIds.difference({challengeId}),
        );
      }
      return false;
    }
  }

  /// Retry after a failed initial fetch.
  Future<void> retry() => _load();
}

/// Recreated per screen visit (autoDispose) so Goals always reflects current
/// data, and rebuilt when the async client id resolves (null → id).
final goalsControllerProvider =
    StateNotifierProvider.autoDispose<GoalsController, GoalsState>((ref) {
      final id = ref.watch(goalsClientIdProvider);
      return GoalsController(ref, id);
    });
