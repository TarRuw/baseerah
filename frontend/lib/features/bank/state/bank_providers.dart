import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../api/api_client.dart';
import '../data/applicant_models.dart';
import '../data/bank_repository.dart';
import '../data/disbursement_models.dart';
import '../data/financing_request_models.dart';
import '../data/portfolio_models.dart';
import '../data/risk_policy_model.dart';

// ── Repository ───────────────────────────────────────────────────────────────

/// Rebuilds with the api client so `Accept-Language` tracks the active locale.
final bankRepositoryProvider = Provider<BankRepository>(
  (ref) => BankRepository(ref.watch(apiClientProvider)),
);

/// The underwrite-stage queue (`GET /bank/loan-requests?stage=underwrite`). A
/// [FutureProvider] like Home's per-section loads: the left list shows a spinner
/// / inline retry on its own, and [BankDetailController.declineRequest]
/// invalidates it so a declined request drops out of the queue without a manual
/// reload. autoDispose so a fresh visit re-fetches the live queue.
final bankLoanRequestsProvider =
    FutureProvider.autoDispose<List<LoanRequestRow>>(
  (ref) => ref.watch(bankRepositoryProvider).loanRequests(),
);

// ── Financing inbox (read + reply/decline) ───────────────────────────────────

/// The pending financing inbox (`GET /bank/financing-requests`). A [FutureProvider]
/// like the applicant queue: the left list shows a spinner / inline retry on its
/// own, and [BankFinancingController.reply]/[decline] invalidate it so an answered
/// proposal drops out of the pending list without a manual reload. autoDispose so
/// a fresh visit re-fetches the live inbox.
final bankFinancingRequestsProvider =
    FutureProvider.autoDispose<List<FinancingRequestRow>>(
  (ref) => ref.watch(bankRepositoryProvider).financingRequests(),
);

/// Immutable snapshot of the financing-review detail pane. [selected] is the row
/// the operator picked; [submitting] is true while a reply/decline POST is in
/// flight (the form disables + shows progress); [answered] is set once the reply
/// or decline lands so the pane can show a confirmation.
class BankFinancingState {
  const BankFinancingState({
    this.selected,
    this.submitting = false,
    this.answered = false,
  });

  final FinancingRequestRow? selected;
  final bool submitting;
  final bool answered;

  BankFinancingState copyWith({
    FinancingRequestRow? selected,
    bool? submitting,
    bool? answered,
  }) {
    return BankFinancingState(
      selected: selected ?? this.selected,
      submitting: submitting ?? this.submitting,
      answered: answered ?? this.answered,
    );
  }
}

/// Owns the financing-review detail state machine: select a pending proposal,
/// then reply with a rate/term or decline. A successful write invalidates the
/// inbox list (so the answered row drops out) and marks the pane [answered] with
/// a confirmation. On failure it clears [submitting] and returns false so the
/// screen can surface a retry snackbar (never a stuck spinner).
class BankFinancingController extends StateNotifier<BankFinancingState> {
  BankFinancingController(this._ref) : super(const BankFinancingState());

  final Ref _ref;

  /// Select a pending row: reset the pane to its editable, un-answered state.
  void select(FinancingRequestRow row) {
    if (state.selected?.proposalId == row.proposalId && !state.answered) return;
    state = BankFinancingState(selected: row);
  }

  /// Send the offer (rate %, term months) for the selected proposal.
  Future<bool> reply(double rate, int term) async {
    final row = state.selected;
    if (row == null || state.submitting || state.answered) return false;
    state = state.copyWith(submitting: true);
    try {
      final updated = await _ref
          .read(bankRepositoryProvider)
          .replyFinancing(row.proposalId, rate: rate, term: term);
      if (!mounted) return true;
      state = BankFinancingState(selected: updated, answered: true);
      _ref.invalidate(bankFinancingRequestsProvider);
      return true;
    } catch (_) {
      if (mounted) state = state.copyWith(submitting: false);
      return false;
    }
  }

  /// Decline the selected proposal.
  Future<bool> decline() async {
    final row = state.selected;
    if (row == null || state.submitting || state.answered) return false;
    state = state.copyWith(submitting: true);
    try {
      final updated =
          await _ref.read(bankRepositoryProvider).declineFinancing(row.proposalId);
      if (!mounted) return true;
      state = BankFinancingState(selected: updated, answered: true);
      _ref.invalidate(bankFinancingRequestsProvider);
      return true;
    } catch (_) {
      if (mounted) state = state.copyWith(submitting: false);
      return false;
    }
  }
}

/// Recreated per screen visit (autoDispose) so the detail always starts empty.
final bankFinancingProvider =
    StateNotifierProvider.autoDispose<BankFinancingController, BankFinancingState>(
      (ref) => BankFinancingController(ref),
    );

// ── Disbursements queue (read + disburse/decline) ─────────────────────────────

/// Accepted offers awaiting a funding decision (`GET /bank/financing-disbursements`).
/// A [FutureProvider] like the requests inbox; [BankDisbursementController] invalidates
/// it after a funding decision so the answered offer drops out. autoDispose.
final bankDisbursementsProvider =
    FutureProvider.autoDispose<List<DisbursementRow>>(
  (ref) => ref.watch(bankRepositoryProvider).disbursements(),
);

/// Immutable snapshot of the disbursement detail pane. [answered] is set once the
/// operator disburses or declines, so the pane can show a confirmation.
class BankDisbursementState {
  const BankDisbursementState({this.selected, this.submitting = false, this.answered = false});

  final DisbursementRow? selected;
  final bool submitting;
  final bool answered;

  BankDisbursementState copyWith({
    DisbursementRow? selected,
    bool? submitting,
    bool? answered,
  }) {
    return BankDisbursementState(
      selected: selected ?? this.selected,
      submitting: submitting ?? this.submitting,
      answered: answered ?? this.answered,
    );
  }
}

/// Owns the disbursement-detail state machine: select an accepted offer, then
/// disburse (fund it) or decline at the final stage. A successful write invalidates
/// the queue and marks the pane answered. Returns false on failure so the screen
/// can surface a retry.
class BankDisbursementController extends StateNotifier<BankDisbursementState> {
  BankDisbursementController(this._ref) : super(const BankDisbursementState());

  final Ref _ref;

  void select(DisbursementRow row) {
    if (state.selected?.proposalId == row.proposalId && !state.answered) return;
    state = BankDisbursementState(selected: row);
  }

  Future<bool> disburse() => _act((repo, id) => repo.disburse(id));

  Future<bool> decline() => _act((repo, id) => repo.declineDisbursement(id));

  Future<bool> _act(Future<DisbursementRow> Function(BankRepository, String) call) async {
    final row = state.selected;
    if (row == null || state.submitting || state.answered) return false;
    state = state.copyWith(submitting: true);
    try {
      final updated = await call(_ref.read(bankRepositoryProvider), row.proposalId);
      if (!mounted) return true;
      state = BankDisbursementState(selected: updated, answered: true);
      _ref.invalidate(bankDisbursementsProvider);
      return true;
    } catch (_) {
      if (mounted) state = state.copyWith(submitting: false);
      return false;
    }
  }
}

/// Recreated per screen visit (autoDispose) so the detail always starts empty.
final bankDisbursementProvider =
    StateNotifierProvider.autoDispose<BankDisbursementController, BankDisbursementState>(
      (ref) => BankDisbursementController(ref),
    );

// ── Portfolio (read-only) ────────────────────────────────────────────────────

/// The monitored portfolio (`GET /bank/portfolio`, Step 6.4). Read-only, so a
/// plain [FutureProvider] like the applicant queue: the Portfolio screen shows a
/// spinner while loading and an inline retry on failure via [ref.invalidate].
/// autoDispose so a fresh visit re-fetches the live figures.
final bankPortfolioProvider = FutureProvider.autoDispose<Portfolio>(
  (ref) => ref.watch(bankRepositoryProvider).portfolio(),
);

// ── Risk policy (read + write) ───────────────────────────────────────────────

/// Where the Settings screen's policy load sits: *loading* the initial GET,
/// *ready* with the live [RiskPolicy], or *error* if the GET failed (retryable).
enum RiskPolicyPhase { loading, ready, error }

/// Immutable snapshot of the Settings risk-policy state. [policy] is meaningful
/// only in the *ready* phase; [saving] is true while a `PUT` is in flight so the
/// screen can disable inputs and show progress without losing the shown values.
class RiskPolicyState {
  const RiskPolicyState({
    this.phase = RiskPolicyPhase.loading,
    this.policy,
    this.saving = false,
  });

  final RiskPolicyPhase phase;
  final RiskPolicy? policy;
  final bool saving;

  RiskPolicyState copyWith({
    RiskPolicyPhase? phase,
    RiskPolicy? policy,
    bool? saving,
  }) {
    return RiskPolicyState(
      phase: phase ?? this.phase,
      policy: policy ?? this.policy,
      saving: saving ?? this.saving,
    );
  }
}

/// Owns the Settings risk-policy load/save cycle (Step 6.4). Loads the singleton
/// on creation; [save] persists an edited copy and replaces the state with the
/// server's round-tripped result so the UI always reflects what was actually
/// stored. A failed save leaves the last-saved policy in place and returns false
/// so the screen can surface a retry snackbar (never a silent failure).
class RiskPolicyController extends StateNotifier<RiskPolicyState> {
  RiskPolicyController(this._ref) : super(const RiskPolicyState()) {
    load();
  }

  final Ref _ref;

  /// (Re)load the singleton policy — moves loading → ready, or → error on fail.
  Future<void> load() async {
    state = const RiskPolicyState(phase: RiskPolicyPhase.loading);
    try {
      final policy = await _ref.read(bankRepositoryProvider).riskPolicy();
      if (!mounted) return;
      state = RiskPolicyState(phase: RiskPolicyPhase.ready, policy: policy);
    } catch (_) {
      if (mounted) state = const RiskPolicyState(phase: RiskPolicyPhase.error);
    }
  }

  /// Persist [edited] via `PUT`. Returns true and adopts the round-tripped result
  /// on success; on failure clears [saving], keeps the prior policy, returns
  /// false so the screen can revert the control and show a retry.
  Future<bool> save(RiskPolicy edited) async {
    if (state.phase != RiskPolicyPhase.ready || state.saving) return false;
    state = state.copyWith(saving: true);
    try {
      final saved =
          await _ref.read(bankRepositoryProvider).updateRiskPolicy(edited);
      if (!mounted) return true;
      state = RiskPolicyState(phase: RiskPolicyPhase.ready, policy: saved);
      return true;
    } catch (_) {
      if (mounted) state = state.copyWith(saving: false);
      return false;
    }
  }
}

/// Recreated per Settings visit (autoDispose) so it always starts from a fresh
/// GET of the live policy.
final riskPolicyProvider =
    StateNotifierProvider.autoDispose<RiskPolicyController, RiskPolicyState>(
      (ref) => RiskPolicyController(ref),
    );

// ── Underwrite detail state machine ──────────────────────────────────────────

/// The discrete phases the right-hand detail pane moves through (DESIGN §7.5):
/// *empty* (nothing selected) → *generating* (underwrite POST in flight) →
/// *report* (live report), plus *reportError* (underwrite POST failed, retryable).
enum BankDetailPhase { empty, generating, report, reportError }

/// Immutable snapshot of the Underwrite detail pane. [selected] is the request
/// the operator picked; [phase] governs which of the remaining fields are
/// meaningful. [declined] overlays the *report* phase (the request was rejected);
/// [deciding] is true while the decline POST is in flight. Approval is no longer
/// an underwrite action — pricing (the next tab) is the approve path — so this
/// stage's only lifecycle action is decline.
class BankDetailState {
  const BankDetailState({
    this.selected,
    this.phase = BankDetailPhase.empty,
    this.report,
    this.declined = false,
    this.deciding = false,
  });

  final LoanRequestRow? selected;
  final BankDetailPhase phase;
  final UnderwritingReport? report;

  /// True once the request has been declined at this stage (drives the
  /// confirmation line and disables the decline button); false otherwise.
  final bool declined;

  /// Decline POST in flight — the pressed button shows progress.
  final bool deciding;

  BankDetailState _copyWith({
    LoanRequestRow? selected,
    BankDetailPhase? phase,
    UnderwritingReport? report,
    bool? declined,
    bool? deciding,
  }) {
    return BankDetailState(
      selected: selected ?? this.selected,
      phase: phase ?? this.phase,
      report: report ?? this.report,
      declined: declined ?? this.declined,
      deciding: deciding ?? this.deciding,
    );
  }
}

/// Owns the Underwrite detail state machine (Step 12.6). Selecting a request
/// resets to the empty→generating→report flow; generating awaits the underwrite
/// POST; declining rejects the request and refreshes the queue so it drops out.
/// The repository is read fresh per call so requests carry the current locale's
/// `Accept-Language`.
class BankDetailController extends StateNotifier<BankDetailState> {
  BankDetailController(this._ref) : super(const BankDetailState());

  final Ref _ref;

  /// Select a row: show its detail header with the "generate report" action,
  /// clearing any prior report/decline. Selecting the same row again is a no-op
  /// so an in-flight generation isn't interrupted.
  void select(LoanRequestRow request) {
    if (state.selected?.requestId == request.requestId) return;
    state = BankDetailState(
      selected: request,
      phase: BankDetailPhase.empty,
    );
  }

  /// Underwrite the selected request. Shows the `bsr-spin` loader while the POST
  /// is in flight, then the live report; on failure moves to
  /// [BankDetailPhase.reportError] with a retry.
  Future<void> generateReport() async {
    final request = state.selected;
    if (request == null || state.phase == BankDetailPhase.generating) return;
    state = state._copyWith(
      phase: BankDetailPhase.generating,
      report: null,
      declined: false,
    );
    try {
      final report =
          await _ref.read(bankRepositoryProvider).underwrite(request.requestId);
      if (!mounted || state.selected?.requestId != request.requestId) return;
      state = state._copyWith(phase: BankDetailPhase.report, report: report);
    } catch (_) {
      if (mounted && state.selected?.requestId == request.requestId) {
        state = state._copyWith(phase: BankDetailPhase.reportError);
      }
    }
  }

  /// Decline the selected request at the underwrite stage. On success marks the
  /// pane [declined] and invalidates the queue so the request drops out; returns
  /// true. On failure clears [deciding] and returns false so the screen can
  /// surface a retry snackbar (never a stuck spinner).
  Future<bool> declineRequest() async {
    final request = state.selected;
    if (request == null ||
        state.phase != BankDetailPhase.report ||
        state.deciding ||
        state.declined) {
      return false;
    }
    state = state._copyWith(deciding: true);
    try {
      final updated = await _ref
          .read(bankRepositoryProvider)
          .declineRequest(request.requestId);
      if (!mounted || state.selected?.requestId != request.requestId) return true;
      state = state._copyWith(
        selected: updated,
        declined: true,
        deciding: false,
      );
      _ref.invalidate(bankLoanRequestsProvider);
      return true;
    } catch (_) {
      if (mounted && state.selected?.requestId == request.requestId) {
        state = state._copyWith(deciding: false);
      }
      return false;
    }
  }
}

/// Recreated per screen visit (autoDispose) so the detail always starts empty.
final bankDetailProvider =
    StateNotifierProvider.autoDispose<BankDetailController, BankDetailState>(
      (ref) => BankDetailController(ref),
    );
