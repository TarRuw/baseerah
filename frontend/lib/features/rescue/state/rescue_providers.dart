import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../api/api_client.dart';
import '../../home/state/home_providers.dart';
import '../data/rescue_models.dart';
import '../data/rescue_repository.dart';

// ── Repository + current client ─────────────────────────────────────────────

/// Rebuilds with the api client so `Accept-Language` tracks the active locale.
final rescueRepositoryProvider = Provider<RescueRepository>(
  (ref) => RescueRepository(ref.watch(apiClientProvider)),
);

/// The current persona's id, reusing Home's canonical resolver (Step 2.3). Null
/// while the `/clients` call is in flight — the controller stays in [loading]
/// until it lands, then rebuilds with the real id and fetches.
final _clientIdProvider = Provider<String?>(
  (ref) => ref.watch(currentClientProvider).valueOrNull?.id,
);

// ── Screen state machine ────────────────────────────────────────────────────

/// The discrete phases the Rescue screen moves through (DESIGN §7.3):
/// fetch → *open* (deficit + selectable bridges) or *no-deficit* → *confirming*
/// → *complete* (recovery). [error] is the fetch-failed path with a retry.
enum RescuePhase { loading, error, noDeficit, open, confirming, complete }

/// Immutable snapshot of the Rescue screen. The phase governs which fields are
/// meaningful, so illegal combinations (e.g. confirming without a selection)
/// can't be constructed — every transition goes through a named constructor.
class RescueScreenState {
  const RescueScreenState._({
    required this.phase,
    this.assessment,
    this.selected,
    this.outcome,
    this.failure,
  });

  /// Initial fetch in flight.
  const RescueScreenState.loading() : this._(phase: RescuePhase.loading);

  /// Fetch failed — the screen shows the shared load error + a retry.
  const RescueScreenState.error(Object failure)
    : this._(phase: RescuePhase.error, failure: failure);

  /// Healthy persona: an explicit no-deficit state, not an error.
  const RescueScreenState.noDeficit(RescueState assessment)
    : this._(phase: RescuePhase.noDeficit, assessment: assessment);

  /// Deficit predicted: banner + selectable bridge cards. [selected] is null
  /// until the user picks a card (the confirm button gates on it).
  const RescueScreenState.open(RescueState assessment, {RescueOptionType? selected})
    : this._(phase: RescuePhase.open, assessment: assessment, selected: selected);

  /// Confirm request in flight for [selected] (button shows progress).
  const RescueScreenState.confirming(
    RescueState assessment,
    RescueOptionType selected,
  ) : this._(
        phase: RescuePhase.confirming,
        assessment: assessment,
        selected: selected,
      );

  /// Bridge confirmed: before→after recovery + run-again.
  const RescueScreenState.complete(RescueState assessment, RescueOutcome outcome)
    : this._(
        phase: RescuePhase.complete,
        assessment: assessment,
        outcome: outcome,
      );

  final RescuePhase phase;
  final RescueState? assessment;
  final RescueOptionType? selected;
  final RescueOutcome? outcome;

  /// The exception from a failed initial fetch (error phase); retained for
  /// diagnostics — the view renders a generic message, not this object.
  final Object? failure;

  /// The confirm button is enabled only with a selection on the open state.
  bool get canConfirm => phase == RescuePhase.open && selected != null;
}

/// Owns the Rescue screen's state machine (Step 4.3). Fetches on creation,
/// tracks the selected bridge, drives confirm→recovery, and re-fetches on
/// "run again". The repository is read fresh per call so requests always carry
/// the current locale's `Accept-Language`.
class RescueController extends StateNotifier<RescueScreenState> {
  RescueController(this._ref, this._clientId)
    : super(const RescueScreenState.loading()) {
    if (_clientId != null) _load();
  }

  final Ref _ref;
  final String? _clientId;

  Future<void> _load() async {
    final id = _clientId;
    if (id == null) return;
    state = const RescueScreenState.loading();
    try {
      final assessment = await _ref.read(rescueRepositoryProvider).fetch(id);
      if (!mounted) return;
      state = assessment.hasDeficit
          ? RescueScreenState.open(assessment)
          : RescueScreenState.noDeficit(assessment);
    } catch (error) {
      if (mounted) state = RescueScreenState.error(error);
    }
  }

  /// Highlight a bridge card (only meaningful on the open state).
  void select(RescueOptionType type) {
    final current = state;
    final assessment = current.assessment;
    if (current.phase != RescuePhase.open || assessment == null) return;
    state = RescueScreenState.open(assessment, selected: type);
  }

  /// Confirm the selected bridge. On success transitions to the complete state
  /// and returns true; on failure reverts to the open state (keeping the
  /// selection) and returns false so the screen can surface a retry snackbar —
  /// a dropped backend never strands the user on a spinner.
  Future<bool> confirm() async {
    final current = state;
    final id = _clientId;
    final assessment = current.assessment;
    final type = current.selected;
    if (id == null ||
        assessment == null ||
        type == null ||
        current.phase != RescuePhase.open) {
      return false;
    }
    state = RescueScreenState.confirming(assessment, type);
    try {
      final outcome = await _ref.read(rescueRepositoryProvider).confirm(id, type);
      if (!mounted) return false;
      state = RescueScreenState.complete(assessment, outcome);
      return true;
    } catch (_) {
      if (mounted) state = RescueScreenState.open(assessment, selected: type);
      return false;
    }
  }

  /// Reset back to a freshly re-fetched open state (§7.3 "run again").
  Future<void> runAgain() => _load();

  /// Retry after a failed initial fetch.
  Future<void> retry() => _load();
}

/// Recreated per screen visit (autoDispose) so Rescue always reflects current
/// data, and rebuilt when the async client id resolves (null → id).
final rescueControllerProvider =
    StateNotifierProvider.autoDispose<RescueController, RescueScreenState>((ref) {
      final id = ref.watch(_clientIdProvider);
      return RescueController(ref, id);
    });
