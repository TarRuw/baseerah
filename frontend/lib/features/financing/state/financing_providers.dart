import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../api/api_client.dart';
import '../../home/state/home_providers.dart';
import '../data/financing_models.dart';
import '../data/financing_repository.dart';

// ── Repository + current client ─────────────────────────────────────────────

/// Rebuilds with the api client so `Accept-Language` tracks the active locale.
final financingRepositoryProvider = Provider<FinancingRepository>(
  (ref) => FinancingRepository(ref.watch(apiClientProvider)),
);

/// The current persona's id, reusing Home's canonical resolver. Null while the
/// `/clients` call is in flight.
final _clientIdProvider = Provider<String?>(
  (ref) => ref.watch(currentClientProvider).valueOrNull?.id,
);

// ── Screen state machine ────────────────────────────────────────────────────

/// The screens the financing flow moves between: *loading* (initial history
/// fetch) → *list* (the requests history, each a status pill) → *picker* (choose
/// banks for a new request) or *proposals* (a request's offers, choose) → *done*
/// (before→after). *error* is a failed load/submit with a retry.
enum FinancingView { loading, list, picker, proposals, done, error }

/// Immutable snapshot of the financing screen. [requests] is the client's
/// history (newest first); [activeId] is the request open in *proposals*/*done*;
/// [submitting] is a new-request POST in flight (an inline button spinner, never
/// a full-screen loader); [choosingId] marks the proposal whose choose is in
/// flight; [outcome] is set in *done*.
class FinancingScreenState {
  const FinancingScreenState({
    required this.view,
    this.requests = const [],
    this.activeId,
    this.submitting = false,
    this.choosingId,
    this.outcome,
    this.failure,
  });

  final FinancingView view;
  final List<FinancingRequest> requests;
  final String? activeId;
  final bool submitting;
  final String? choosingId;
  final FinancingOutcome? outcome;
  final Object? failure;

  /// The request currently open in the detail/done view, resolved from [requests].
  FinancingRequest? get active {
    for (final r in requests) {
      if (r.id == activeId) return r;
    }
    return null;
  }
}

/// Owns the financing screen: loads the client's request history, polls it every
/// few seconds while any bank has yet to reply, submits new requests, and drives
/// choose → outcome. Submitting shows an inline button spinner (never a
/// full-screen loader) and lands back on the list, where the new request appears
/// with a status pill. Stops polling on dispose.
class FinancingController extends StateNotifier<FinancingScreenState> {
  FinancingController(this._ref, this._clientId)
      : super(const FinancingScreenState(view: FinancingView.loading)) {
    if (_clientId != null) _load();
  }

  final Ref _ref;
  final String? _clientId;
  Timer? _poll;

  /// Poll cadence while any request is still awaiting a bank reply.
  static const _pollInterval = Duration(seconds: 4);

  @override
  void dispose() {
    _poll?.cancel();
    super.dispose();
  }

  FinancingRepository get _repo => _ref.read(financingRepositoryProvider);

  /// Initial history load → the list view.
  Future<void> _load() async {
    final id = _clientId;
    if (id == null) return;
    try {
      final requests = await _repo.list(id);
      if (!mounted) return;
      state = FinancingScreenState(view: FinancingView.list, requests: requests);
      _syncPolling(requests);
    } catch (error) {
      if (mounted) {
        state = FinancingScreenState(view: FinancingView.error, failure: error);
      }
    }
  }

  /// Retry the initial load after an error.
  Future<void> retry() => _load();

  /// Open the bank picker to raise a new request.
  void newRequest() =>
      state = FinancingScreenState(view: FinancingView.picker, requests: state.requests);

  /// Return to the history list (from the picker or a request detail).
  void backToList() =>
      state = FinancingScreenState(view: FinancingView.list, requests: state.requests);

  /// Open a request's detail (its per-bank offers).
  void open(String requestId) => state = FinancingScreenState(
        view: FinancingView.proposals,
        requests: state.requests,
        activeId: requestId,
      );

  /// Submit a new request to the chosen [banks]; lands back on the list with the
  /// new request shown as a status pill. [origin]/[purpose] tag how the request
  /// was raised (`RESCUE` from Smart Rescue, `DIRECT` from the Simulate CTA) — the
  /// backend defaults them when omitted. Returns false on failure (the picker
  /// stays put so the screen can surface a retry).
  Future<bool> submit({
    required double amount,
    required int deficitInDays,
    required List<String> banks,
    String origin = 'RESCUE',
    String? purpose,
  }) async {
    final id = _clientId;
    if (id == null || banks.isEmpty || state.submitting) return false;
    state = FinancingScreenState(
        view: FinancingView.picker, requests: state.requests, submitting: true);
    try {
      await _repo.create(id,
          amount: amount,
          deficitInDays: deficitInDays,
          banks: banks,
          origin: origin,
          purpose: purpose);
      final requests = await _repo.list(id);
      if (!mounted) return true;
      state = FinancingScreenState(view: FinancingView.list, requests: requests);
      _syncPolling(requests);
      return true;
    } catch (_) {
      if (mounted) {
        state = FinancingScreenState(view: FinancingView.picker, requests: state.requests);
      }
      return false;
    }
  }

  /// Accept a replied offer's terms in the active request → its before/after outcome.
  /// (Acceptance awaits the bank's disbursement; polling then tracks it to active.)
  Future<bool> accept(String proposalId) async {
    final id = _clientId;
    final active = state.active;
    if (id == null || active == null || state.choosingId != null) return false;
    state = FinancingScreenState(
      view: FinancingView.proposals,
      requests: state.requests,
      activeId: state.activeId,
      choosingId: proposalId,
    );
    try {
      final outcome = await _repo.accept(id, active.id, proposalId);
      final requests = await _repo.list(id);
      if (!mounted) return true;
      state = FinancingScreenState(
        view: FinancingView.done,
        requests: requests,
        activeId: state.activeId,
        outcome: outcome,
      );
      _syncPolling(requests);
      return true;
    } catch (_) {
      if (mounted) {
        state = FinancingScreenState(
          view: FinancingView.proposals,
          requests: state.requests,
          activeId: state.activeId,
        );
      }
      return false;
    }
  }

  void _syncPolling(List<FinancingRequest> requests) {
    if (requests.any((r) => r.awaitingBank)) {
      _poll ??= Timer.periodic(_pollInterval, (_) => _refresh());
    } else {
      _poll?.cancel();
      _poll = null;
    }
  }

  /// Background refresh of the history while offers are pending — updates the
  /// list (and the open detail, which reads from it) in place.
  Future<void> _refresh() async {
    final id = _clientId;
    if (id == null) return;
    try {
      final requests = await _repo.list(id);
      if (!mounted) return;
      state = FinancingScreenState(
        view: state.view,
        requests: requests,
        activeId: state.activeId,
        submitting: state.submitting,
        choosingId: state.choosingId,
        outcome: state.outcome,
      );
      _syncPolling(requests);
    } catch (_) {
      // Transient poll failure: keep the current view and try again next tick.
    }
  }
}

/// Recreated per flow entry (autoDispose) so a fresh visit reloads the history,
/// and rebuilt when the async client id resolves (null → id).
final financingControllerProvider =
    StateNotifierProvider.autoDispose<FinancingController, FinancingScreenState>(
  (ref) => FinancingController(ref, ref.watch(_clientIdProvider)),
);
