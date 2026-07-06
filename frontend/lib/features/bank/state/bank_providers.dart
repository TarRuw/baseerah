import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../api/api_client.dart';
import '../data/applicant_models.dart';
import '../data/bank_repository.dart';
import '../data/portfolio_models.dart';
import '../data/risk_policy_model.dart';

// ── Repository ───────────────────────────────────────────────────────────────

/// Rebuilds with the api client so `Accept-Language` tracks the active locale.
final bankRepositoryProvider = Provider<BankRepository>(
  (ref) => BankRepository(ref.watch(apiClientProvider)),
);

/// The underwriting queue (`GET /bank/applicants`). A [FutureProvider] like
/// Home's per-section loads: the left list shows a spinner / inline retry on its
/// own, and [BankDetailController.decide] invalidates it so a persisted decision
/// is reflected in the row badge without a manual reload. autoDispose so a fresh
/// visit re-fetches the live queue.
final bankApplicantsProvider = FutureProvider.autoDispose<List<Applicant>>(
  (ref) => ref.watch(bankRepositoryProvider).applicants(),
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

// ── Applications detail state machine ────────────────────────────────────────

/// The discrete phases the right-hand detail pane moves through (DESIGN §7.5):
/// *empty* (nothing selected) → *generating* (report POST in flight) →
/// *report* (live report), plus *reportError* (report POST failed, with retry).
enum BankDetailPhase { empty, generating, report, reportError }

/// Immutable snapshot of the Applications detail pane. [selected] is the row the
/// banker picked; [phase] governs which of the remaining fields are meaningful.
/// [decision] overlays the *report* phase (the recorded outcome once a banker
/// acts); [deciding] is true while the decision POST is in flight.
class BankDetailState {
  const BankDetailState({
    this.selected,
    this.phase = BankDetailPhase.empty,
    this.report,
    this.decision,
    this.deciding = false,
  });

  final Applicant? selected;
  final BankDetailPhase phase;
  final UnderwritingReport? report;

  /// The persisted decision once recorded (drives the confirmation message and
  /// disables the action buttons); null until the banker approves/declines.
  final Decision? decision;

  /// Decision POST in flight — the pressed button shows progress.
  final bool deciding;

  /// A BAD verdict can't be approved (mirrors the prototype's disabled Approve).
  bool get approveDisabled => report?.verdict == Verdict.bad;

  BankDetailState _copyWith({
    Applicant? selected,
    BankDetailPhase? phase,
    UnderwritingReport? report,
    Decision? decision,
    bool? deciding,
  }) {
    return BankDetailState(
      selected: selected ?? this.selected,
      phase: phase ?? this.phase,
      report: report ?? this.report,
      decision: decision ?? this.decision,
      deciding: deciding ?? this.deciding,
    );
  }
}

/// Owns the Applications detail state machine (Step 6.3). Selecting an applicant
/// resets to the empty→generating→report flow; generating awaits the report
/// POST; deciding records the outcome and refreshes the queue so the row badge
/// updates. The repository is read fresh per call so requests carry the current
/// locale's `Accept-Language`.
class BankDetailController extends StateNotifier<BankDetailState> {
  BankDetailController(this._ref) : super(const BankDetailState());

  final Ref _ref;

  /// Select a row: show its detail header with the "generate report" action,
  /// clearing any prior report/decision. Selecting the same row again is a no-op
  /// so an in-flight generation isn't interrupted.
  void select(Applicant applicant) {
    if (state.selected?.id == applicant.id) return;
    state = BankDetailState(
      selected: applicant,
      phase: BankDetailPhase.empty,
    );
  }

  /// Generate the predictive report for the selected applicant. Shows the
  /// `bsr-spin` loader while the POST is in flight, then the live report; on
  /// failure moves to [BankDetailPhase.reportError] with a retry.
  Future<void> generateReport() async {
    final applicant = state.selected;
    if (applicant == null || state.phase == BankDetailPhase.generating) return;
    state = state._copyWith(
      phase: BankDetailPhase.generating,
      report: null,
      decision: null,
    );
    try {
      final report = await _ref.read(bankRepositoryProvider).report(applicant.id);
      if (!mounted || state.selected?.id != applicant.id) return;
      state = state._copyWith(phase: BankDetailPhase.report, report: report);
    } catch (_) {
      if (mounted && state.selected?.id == applicant.id) {
        state = state._copyWith(phase: BankDetailPhase.reportError);
      }
    }
  }

  /// Record an approve/decline decision. On success stores the persisted
  /// decision, updates the selected applicant, and invalidates the queue so the
  /// row reflects it; returns true. On failure clears [deciding] and returns
  /// false so the screen can surface a retry snackbar (never a stuck spinner).
  Future<bool> decide(Decision decision) async {
    final applicant = state.selected;
    if (applicant == null ||
        state.phase != BankDetailPhase.report ||
        state.deciding ||
        (decision == Decision.approve && state.approveDisabled)) {
      return false;
    }
    state = state._copyWith(deciding: true);
    try {
      final updated =
          await _ref.read(bankRepositoryProvider).decide(applicant.id, decision);
      if (!mounted || state.selected?.id != applicant.id) return true;
      state = state._copyWith(
        selected: updated,
        decision: updated.decision ?? decision,
        deciding: false,
      );
      _ref.invalidate(bankApplicantsProvider);
      return true;
    } catch (_) {
      if (mounted && state.selected?.id == applicant.id) {
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
