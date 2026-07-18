import '../../../api/api_client.dart';
import '../../../core/api_envelope.dart';
import 'periodic_expense.dart';

/// All declared-periodic-expense backend calls in one place (Phase 11 /
/// GitLab backend#1 write API + backend#3 category keys), each unwrapping the
/// shared `{status, data}` envelope. The controller depends on this repository —
/// widgets never call dio directly (Global Rule: API calls out of widgets).
class ExpensesRepository {
  const ExpensesRepository(this._api);

  final ApiClient _api;

  /// `GET /clients/{id}/declared-expenses` — the client's **active** declared
  /// expenses (the backend never returns soft-deleted rows).
  Future<List<PeriodicExpense>> fetchExpenses(String clientId) async {
    final response =
        await _api.dio.get<dynamic>('/clients/$clientId/declared-expenses');
    final data = unwrapEnvelope(response) as List<dynamic>;
    return data
        .map((e) => PeriodicExpense.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// `POST /clients/{id}/declared-expenses` — declare a new recurring expense,
  /// returning the created row (with its server-assigned id). [request] is a
  /// [PeriodicExpense.toRequestJson] body.
  Future<PeriodicExpense> createExpense(
    String clientId,
    Map<String, dynamic> request,
  ) async {
    final response = await _api.dio.post<dynamic>(
      '/clients/$clientId/declared-expenses',
      data: request,
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return PeriodicExpense.fromJson(data);
  }

  /// `PUT /clients/{id}/declared-expenses/{expenseId}` — update in place,
  /// returning the updated row.
  Future<PeriodicExpense> updateExpense(
    String clientId,
    String expenseId,
    Map<String, dynamic> request,
  ) async {
    final response = await _api.dio.put<dynamic>(
      '/clients/$clientId/declared-expenses/$expenseId',
      data: request,
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return PeriodicExpense.fromJson(data);
  }

  /// `DELETE /clients/{id}/declared-expenses/{expenseId}` — soft-delete
  /// (idempotent). Responds `204 No Content`, so there is **no envelope to
  /// unwrap** — a success is simply the absence of a thrown [DioException].
  /// Deactivating genuinely removes the expense's score/forecast/DTI effect
  /// (Step 11.3), which is the manual guard against double-counting an expense
  /// that is both declared and already visible in the bank feed.
  Future<void> deactivateExpense(String clientId, String expenseId) async {
    await _api.dio
        .delete<dynamic>('/clients/$clientId/declared-expenses/$expenseId');
  }

  /// `GET /categories/expense` — the declarable-expense category **keys**
  /// (16 expense categories + `OTHER`), role-agnostic reference data. Keys, not
  /// localized strings — [ExpenseCategory] resolves each to an ARB label.
  Future<List<String>> fetchExpenseCategories() async {
    final response = await _api.dio.get<dynamic>('/categories/expense');
    final data = unwrapEnvelope(response) as List<dynamic>;
    return data.map((e) => e as String).toList();
  }
}
