import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../api/api_client.dart';
import '../data/applicant_models.dart';
import '../data/bank_repository.dart';

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
