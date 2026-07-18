import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../data/expense_category.dart';

/// The expense-category picker for the add/edit form (Step 11.5). A themed
/// [DropdownButtonFormField] whose options are the declarable-expense category
/// **keys** supplied by the caller (from `GET /categories/expense`, Step 11.4),
/// each rendered through [ExpenseCategory.labelFor] so only **localized labels**
/// show — never a raw `SCREAMING_SNAKE` key, and never an income category like
/// "Salary" (the backend never offers them; the UI must not reintroduce them by
/// rendering the full enum). `OTHER` is offered as a first-class choice.
///
/// Presentation + selection only: [keys] is the source of truth for what's
/// offered, [value] the current selection, [onChanged] the callback. Validation
/// (a category is required) lives on the field via [validator].
class CategoryPicker extends StatelessWidget {
  const CategoryPicker({
    super.key,
    required this.keys,
    required this.value,
    required this.onChanged,
    this.enabled = true,
  });

  /// The declarable category keys to offer, in backend order.
  final List<String> keys;

  /// The currently selected key, or null when nothing is picked yet.
  final String? value;

  final ValueChanged<String?> onChanged;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return DropdownButtonFormField<String>(
      initialValue: value,
      isExpanded: true,
      decoration: InputDecoration(
        labelText: l.expensesCategoryField,
        hintText: l.expensesCategoryHint,
        prefixIcon: const Icon(Icons.category_outlined),
      ),
      items: [
        for (final key in keys)
          DropdownMenuItem<String>(
            value: key,
            child: Text(
              ExpenseCategory.labelFor(l, key),
              overflow: TextOverflow.ellipsis,
            ),
          ),
      ],
      onChanged: enabled ? onChanged : null,
      validator: (v) => v == null ? l.expensesCategoryRequired : null,
      // Match the gold spending accent used across the Expenses surface.
      iconEnabledColor: BaseerahTokens.goldDark,
    );
  }
}
