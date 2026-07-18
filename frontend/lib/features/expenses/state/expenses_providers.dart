import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../api/api_client.dart';
import '../../home/state/home_providers.dart';
import '../data/expenses_repository.dart';
import '../data/periodic_expense.dart';

// ── Repository + current client ─────────────────────────────────────────────

/// Rebuilds with the api client so `Accept-Language` tracks the active locale
/// (mirrors `goalsRepositoryProvider`). The declared-expenses API is key-based,
/// but keeping the pattern identical means the whole feature reads one way.
final expensesRepositoryProvider = Provider<ExpensesRepository>(
  (ref) => ExpensesRepository(ref.watch(apiClientProvider)),
);

/// The current persona's id, reusing Home's resolver (Step 2.3). Null while the
/// `/clients` call is in flight — the controller stays in [ExpensesPhase.loading]
/// until it lands. Public so widget tests can override it with a fixed id.
final expensesClientIdProvider = Provider<String?>(
  (ref) => ref.watch(currentClientProvider).valueOrNull?.id,
);

/// The declarable-expense category keys from `GET /categories/expense`, cached
/// for the picker (Step 11.5). Rebuilt only when the api client changes; the
/// keys are locale-free, so the language toggle re-labels them (via
/// `ExpenseCategory`) with no refetch.
final expenseCategoriesProvider = FutureProvider<List<String>>(
  (ref) => ref.watch(expensesRepositoryProvider).fetchExpenseCategories(),
);

// ── Screen state ────────────────────────────────────────────────────────────

/// The discrete phases the Expenses screen moves through: initial fetch →
/// *ready* (the expense list) or *error* (load failed + retry).
enum ExpensesPhase { loading, error, ready }

/// Immutable snapshot of the Expenses screen. In [ExpensesPhase.ready],
/// [expenses] holds the client's active declared expenses; [mutatingIds] holds
/// the ids whose edit/deactivate is in flight (so those rows lock + spin) — a
/// Set, not a single id, so independent mutations never clobber each other.
/// [adding] is separate since a create has no id yet.
class ExpensesState {
  const ExpensesState._({
    required this.phase,
    this.expenses = const [],
    this.mutatingIds = const {},
    this.adding = false,
    this.failure,
  });

  const ExpensesState.loading() : this._(phase: ExpensesPhase.loading);

  const ExpensesState.error(Object failure)
    : this._(phase: ExpensesPhase.error, failure: failure);

  const ExpensesState.ready(
    List<PeriodicExpense> expenses, {
    Set<String> mutatingIds = const {},
    bool adding = false,
  }) : this._(
         phase: ExpensesPhase.ready,
         expenses: expenses,
         mutatingIds: mutatingIds,
         adding: adding,
       );

  final ExpensesPhase phase;
  final List<PeriodicExpense> expenses;
  final Set<String> mutatingIds;
  final bool adding;

  /// The exception from a failed initial fetch (error phase); retained for
  /// diagnostics — the view renders a generic message, not this object.
  final Object? failure;

  bool isMutating(String expenseId) => mutatingIds.contains(expenseId);
}

/// Owns the Expenses screen's state machine (Step 11.4). Loads the active
/// declared expenses on creation and drives the live add / edit / deactivate
/// flow with in-place list updates — mirroring [GoalsController]. The repository
/// is read fresh per call so requests always carry the current locale's
/// `Accept-Language`.
///
/// Each mutation returns a `bool`: `true` on success (the list has been updated
/// in place), `false` on a rejected/failed call, leaving state untouched so the
/// screen can surface a non-blocking error (there is nothing to roll back —
/// the list is merged only on a confirmed server response).
class ExpensesController extends StateNotifier<ExpensesState> {
  ExpensesController(this._ref, this._clientId)
    : super(const ExpensesState.loading()) {
    if (_clientId != null) _load();
  }

  final Ref _ref;
  final String? _clientId;

  Future<void> _load() async {
    final id = _clientId;
    if (id == null) return;
    state = const ExpensesState.loading();
    try {
      final expenses =
          await _ref.read(expensesRepositoryProvider).fetchExpenses(id);
      if (!mounted) return;
      state = ExpensesState.ready(expenses);
    } catch (error) {
      if (mounted) state = ExpensesState.error(error);
    }
  }

  /// Declare a new expense. On success the created row (with its server id) is
  /// prepended to the list and `true` is returned; on failure state is left
  /// unchanged and `false` is returned. [draft]'s `id` is ignored (server owns
  /// it). Guards against overlapping creates via [ExpensesState.adding].
  Future<bool> add(PeriodicExpense draft) async {
    final current = state;
    final id = _clientId;
    if (id == null || current.phase != ExpensesPhase.ready || current.adding) {
      return false;
    }

    state = ExpensesState.ready(
      current.expenses,
      mutatingIds: current.mutatingIds,
      adding: true,
    );

    try {
      final created = await _ref
          .read(expensesRepositoryProvider)
          .createExpense(id, draft.toRequestJson());
      if (!mounted) return false;
      final latest = state;
      state = ExpensesState.ready(
        [created, ...latest.expenses],
        mutatingIds: latest.mutatingIds,
      );
      return true;
    } catch (_) {
      if (mounted) {
        final latest = state;
        state = ExpensesState.ready(
          latest.expenses,
          mutatingIds: latest.mutatingIds,
        );
      }
      return false;
    }
  }

  /// Update an existing expense in place. [expense] carries the id to update and
  /// the new field values. On success the matching row is swapped for the
  /// server's response; on failure state is untouched. Guards against editing a
  /// row whose mutation is already in flight.
  Future<bool> edit(PeriodicExpense expense) async {
    final current = state;
    final id = _clientId;
    if (id == null ||
        current.phase != ExpensesPhase.ready ||
        expense.id.isEmpty ||
        current.mutatingIds.contains(expense.id)) {
      return false;
    }

    state = ExpensesState.ready(
      current.expenses,
      mutatingIds: {...current.mutatingIds, expense.id},
      adding: current.adding,
    );

    try {
      final updated = await _ref
          .read(expensesRepositoryProvider)
          .updateExpense(id, expense.id, expense.toRequestJson());
      if (!mounted) return false;
      final latest = state;
      state = ExpensesState.ready(
        [
          for (final e in latest.expenses)
            if (e.id == expense.id) updated else e,
        ],
        mutatingIds: latest.mutatingIds.difference({expense.id}),
        adding: latest.adding,
      );
      return true;
    } catch (_) {
      if (mounted) {
        final latest = state;
        state = ExpensesState.ready(
          latest.expenses,
          mutatingIds: latest.mutatingIds.difference({expense.id}),
          adding: latest.adding,
        );
      }
      return false;
    }
  }

  /// Deactivate (soft-delete) an expense — the manual double-count guard. On
  /// success the row is removed from the active list (the backend list is
  /// active-only) and `true` is returned; on failure state is untouched. Guards
  /// against deactivating a row whose mutation is already in flight.
  Future<bool> deactivate(String expenseId) async {
    final current = state;
    final id = _clientId;
    if (id == null ||
        current.phase != ExpensesPhase.ready ||
        current.mutatingIds.contains(expenseId)) {
      return false;
    }

    state = ExpensesState.ready(
      current.expenses,
      mutatingIds: {...current.mutatingIds, expenseId},
      adding: current.adding,
    );

    try {
      await _ref
          .read(expensesRepositoryProvider)
          .deactivateExpense(id, expenseId);
      if (!mounted) return false;
      final latest = state;
      state = ExpensesState.ready(
        [
          for (final e in latest.expenses)
            if (e.id != expenseId) e,
        ],
        mutatingIds: latest.mutatingIds.difference({expenseId}),
        adding: latest.adding,
      );
      return true;
    } catch (_) {
      if (mounted) {
        final latest = state;
        state = ExpensesState.ready(
          latest.expenses,
          mutatingIds: latest.mutatingIds.difference({expenseId}),
          adding: latest.adding,
        );
      }
      return false;
    }
  }

  /// Retry after a failed initial fetch.
  Future<void> retry() => _load();
}

/// Recreated per screen visit (autoDispose) so Expenses always reflects current
/// data, and rebuilt when the async client id resolves (null → id).
final expensesControllerProvider =
    StateNotifierProvider.autoDispose<ExpensesController, ExpensesState>((ref) {
      final id = ref.watch(expensesClientIdProvider);
      return ExpensesController(ref, id);
    });
