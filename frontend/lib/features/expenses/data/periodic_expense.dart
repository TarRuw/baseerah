// Client-side view of a user-declared periodic expense (Phase 11 / GitLab
// backend#1, frontend#1), mirroring the backend `DeclaredExpenseDto` served by
// `GET/POST/PUT /api/v1/clients/{id}/declared-expenses`.
//
// A declared expense is a recurring cost the SAMA bank feed can't see (cash
// rent, family support إعالة, private tuition). Cadence is MONTHLY-only and the
// currency SAR-only today, so — like the backend domain record — neither is
// modelled here: the request omits them and the server defaults them.

/// An immutable declared periodic expense as the Expenses feature consumes it.
///
/// [category] is the canonical category **key** (upper-case constant name, e.g.
/// `UTILITIES`, `OTHER`) — never a localized string; the picker/list resolve it
/// to a label via [ExpenseCategory] so the language toggle re-renders with no
/// refetch. [amount] is the raw monthly SAR figure (formatted at the widget
/// layer via `core/format.dart`, Step 11.5 — never pre-formatted here).
class PeriodicExpense {
  const PeriodicExpense({
    required this.id,
    required this.label,
    required this.category,
    required this.amount,
    required this.dayOfMonth,
    required this.active,
  });

  /// The declared-expense identity (UUID string); empty (`''`) for an unsaved
  /// draft the user is about to create.
  final String id;

  /// The user's own words for the expense (Arabic-first).
  final String label;

  /// The canonical category key (e.g. `UTILITIES`, `OTHER`) — resolve to a
  /// localized label via [ExpenseCategory], never render this raw.
  final String category;

  /// The recurring monthly amount in SAR (raw; strictly positive server-side).
  final double amount;

  /// The recurrence day of month, 1..31.
  final int dayOfMonth;

  /// Whether the expense is live. Soft-deleted (`active == false`) rows are
  /// never returned by the list read, so a fetched list is all-active; the flag
  /// is retained so a draft/edit form and the double-count guard can reason
  /// about it explicitly.
  final bool active;

  factory PeriodicExpense.fromJson(Map<String, dynamic> json) {
    return PeriodicExpense(
      id: json['id'] as String,
      label: json['label'] as String,
      category: json['category'] as String,
      amount: (json['amount'] as num).toDouble(),
      dayOfMonth: (json['dayOfMonth'] as num).toInt(),
      active: json['active'] as bool,
    );
  }

  /// The `POST`/`PUT` write body. `currency`/`cadence` are intentionally omitted
  /// — the backend defaults them to SAR / MONTHLY (the only values the product
  /// supports today), and `id`/`active` are server-owned (id is the path, and a
  /// declared expense is created active / deactivated via `DELETE`).
  Map<String, dynamic> toRequestJson() {
    return {
      'label': label,
      'category': category,
      'amount': amount,
      'dayOfMonth': dayOfMonth,
    };
  }

  PeriodicExpense copyWith({
    String? id,
    String? label,
    String? category,
    double? amount,
    int? dayOfMonth,
    bool? active,
  }) {
    return PeriodicExpense(
      id: id ?? this.id,
      label: label ?? this.label,
      category: category ?? this.category,
      amount: amount ?? this.amount,
      dayOfMonth: dayOfMonth ?? this.dayOfMonth,
      active: active ?? this.active,
    );
  }
}
