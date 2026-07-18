import 'package:flutter/material.dart';

import '../../../core/format.dart';
import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../data/expense_category.dart';
import '../data/periodic_expense.dart';

/// A single declared-expense row in the Expenses list (Step 11.5): a leading
/// gold spending glyph, the user's [PeriodicExpense.label], its **localized**
/// category (via [ExpenseCategory]) and recurrence day, the monthly amount
/// (formatted per locale via [Fmt]), and an overflow menu to edit or remove
/// (deactivate) it. Presentation-only: while [mutating] its own edit/deactivate
/// is in flight, so the row locks and shows a spinner in place of the menu.
class ExpenseCard extends StatelessWidget {
  const ExpenseCard({
    super.key,
    required this.expense,
    required this.mutating,
    required this.onEdit,
    required this.onRemove,
    required this.fmt,
  });

  final PeriodicExpense expense;

  /// This row's mutation is in flight — lock its actions and show progress.
  final bool mutating;
  final VoidCallback onEdit;
  final VoidCallback onRemove;
  final Fmt fmt;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
        border: Border.all(color: const Color(0xFFE4E0D6)),
        boxShadow: BaseerahTokens.shadowSoft,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: BaseerahTokens.gold.withValues(alpha: 0.12),
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.receipt_long_rounded,
              color: BaseerahTokens.goldDark,
              size: 22,
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  expense.label,
                  style: textTheme.titleMedium?.copyWith(
                    color: BaseerahTokens.darkText,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  ExpenseCategory.labelFor(l, expense.category),
                  style: textTheme.bodySmall?.copyWith(
                    color: BaseerahTokens.muted,
                  ),
                ),
                const SizedBox(height: 6),
                // Wraps instead of overflowing on a narrow card (long amount +
                // caption under RTL/large text).
                Wrap(
                  crossAxisAlignment: WrapCrossAlignment.center,
                  spacing: 6,
                  children: [
                    Text(
                      fmt.money(expense.amount),
                      style: textTheme.titleSmall?.copyWith(
                        color: BaseerahTokens.goldDark,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    Text(
                      '· ${l.expensesMonthly}',
                      style: textTheme.bodySmall?.copyWith(
                        color: BaseerahTokens.muted,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 2),
                Text(
                  l.expensesDayOfMonthLabel(fmt.fmt(expense.dayOfMonth)),
                  style: textTheme.bodySmall?.copyWith(
                    color: BaseerahTokens.muted,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          if (mutating)
            const Padding(
              padding: EdgeInsets.all(8),
              child: SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            )
          else
            PopupMenuButton<_ExpenseAction>(
              tooltip: expense.label,
              icon: const Icon(Icons.more_vert, color: BaseerahTokens.muted),
              onSelected: (action) => switch (action) {
                _ExpenseAction.edit => onEdit(),
                _ExpenseAction.remove => onRemove(),
              },
              itemBuilder: (context) => [
                PopupMenuItem<_ExpenseAction>(
                  value: _ExpenseAction.edit,
                  child: Row(
                    children: [
                      const Icon(Icons.edit_outlined, size: 18),
                      const SizedBox(width: 12),
                      Text(l.expensesEdit),
                    ],
                  ),
                ),
                PopupMenuItem<_ExpenseAction>(
                  value: _ExpenseAction.remove,
                  child: Row(
                    children: [
                      const Icon(Icons.delete_outline,
                          size: 18, color: BaseerahTokens.alertRed),
                      const SizedBox(width: 12),
                      Text(l.expensesRemove),
                    ],
                  ),
                ),
              ],
            ),
        ],
      ),
    );
  }
}

/// Overflow-menu actions on an expense card.
enum _ExpenseAction { edit, remove }
