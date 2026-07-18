import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../data/expense_category.dart';
import '../data/periodic_expense.dart';
import '../state/expenses_providers.dart';
import 'category_picker.dart';

/// The add / edit declared-expense form (Step 11.5), shown in a modal sheet.
///
/// Mirrors `login_mobile_screen.dart`'s validated-[TextFormField] pattern:
/// description, an expense-only [CategoryPicker], a monthly amount (LTR digit
/// entry under an RTL label), and a day-of-month field — each with a
/// **localized** client-side validator. A prominent **impact disclosure** makes
/// clear the expense affects the user's stress score and any bank loan
/// assessment (Step 11.3) — data entry that changes a number a bank sees should
/// never be silent.
///
/// Submit drives [ExpensesController.add] / [ExpensesController.edit]; on success
/// the sheet pops and the list updates in place. On a **failed** save the sheet
/// stays open with **every field kept** (a flaky connection must not discard the
/// Arabic label the user just typed) and a localized error is shown.
class ExpenseForm extends ConsumerStatefulWidget {
  const ExpenseForm({super.key, this.existing});

  /// The expense being edited, or null to declare a new one.
  final PeriodicExpense? existing;

  bool get isEdit => existing != null;

  @override
  ConsumerState<ExpenseForm> createState() => _ExpenseFormState();
}

class _ExpenseFormState extends ConsumerState<ExpenseForm> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _labelController;
  late final TextEditingController _amountController;
  late final TextEditingController _dayController;

  String? _category;
  bool _submitting = false;
  bool _saveFailed = false;

  @override
  void initState() {
    super.initState();
    final e = widget.existing;
    _labelController = TextEditingController(text: e?.label ?? '');
    _amountController =
        TextEditingController(text: e == null ? '' : _plainAmount(e.amount));
    _dayController =
        TextEditingController(text: e == null ? '' : e.dayOfMonth.toString());
    _category = e?.category;
  }

  /// Raw amount for the field: drop the trailing `.0` on whole SAR figures so
  /// editing a 3200 expense shows `3200`, not `3200.0`.
  static String _plainAmount(double amount) =>
      amount == amount.roundToDouble()
          ? amount.round().toString()
          : amount.toString();

  @override
  void dispose() {
    _labelController.dispose();
    _amountController.dispose();
    _dayController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _submitting = true;
      _saveFailed = false;
    });

    final draft = PeriodicExpense(
      id: widget.existing?.id ?? '',
      label: _labelController.text.trim(),
      category: _category!,
      amount: double.parse(_amountController.text.trim()),
      dayOfMonth: int.parse(_dayController.text.trim()),
      active: true,
    );

    final controller = ref.read(expensesControllerProvider.notifier);
    final ok = widget.isEdit
        ? await controller.edit(draft)
        : await controller.add(draft);

    if (!mounted) return;
    if (ok) {
      Navigator.of(context).pop(true);
    } else {
      // Keep every field; surface a non-destructive inline error.
      setState(() {
        _submitting = false;
        _saveFailed = true;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;

    // The picker's options: the fetched keys, falling back to the full known
    // vocabulary offline. Income categories are never in either set.
    final keys = ref.watch(expenseCategoriesProvider).valueOrNull ??
        ExpenseCategory.allKeys;

    // Pad for the on-screen keyboard so fields stay reachable in the sheet.
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;

    return Padding(
      padding: EdgeInsets.only(bottom: bottomInset),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 24),
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              // Grab handle.
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  margin: const EdgeInsets.only(bottom: 16),
                  decoration: BoxDecoration(
                    color: const Color(0xFFDDD8CC),
                    borderRadius: BorderRadius.circular(999),
                  ),
                ),
              ),
              Text(
                widget.isEdit
                    ? l.expensesFormEditTitle
                    : l.expensesFormAddTitle,
                style: textTheme.titleLarge?.copyWith(
                  color: BaseerahTokens.darkText,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: BaseerahTokens.gap),
              TextFormField(
                controller: _labelController,
                enabled: !_submitting,
                textInputAction: TextInputAction.next,
                textCapitalization: TextCapitalization.sentences,
                decoration: InputDecoration(
                  labelText: l.expensesLabelField,
                  hintText: l.expensesLabelHint,
                  prefixIcon: const Icon(Icons.description_outlined),
                ),
                validator: (v) => (v ?? '').trim().isEmpty
                    ? l.expensesLabelRequired
                    : null,
              ),
              const SizedBox(height: BaseerahTokens.gap),
              CategoryPicker(
                keys: keys,
                value: _category,
                enabled: !_submitting,
                onChanged: (v) => setState(() => _category = v),
              ),
              const SizedBox(height: BaseerahTokens.gap),
              TextFormField(
                controller: _amountController,
                enabled: !_submitting,
                keyboardType:
                    const TextInputType.numberWithOptions(decimal: true),
                textInputAction: TextInputAction.next,
                // Amounts read left-to-right regardless of UI language.
                textDirection: TextDirection.ltr,
                inputFormatters: [
                  FilteringTextInputFormatter.allow(RegExp(r'[0-9.]')),
                ],
                decoration: InputDecoration(
                  labelText: l.expensesAmountField,
                  hintTextDirection: TextDirection.ltr,
                  prefixIcon: const Icon(Icons.payments_outlined),
                ),
                validator: (v) {
                  final amount = double.tryParse((v ?? '').trim());
                  if (amount == null || amount <= 0) {
                    return l.expensesAmountInvalid;
                  }
                  return null;
                },
              ),
              const SizedBox(height: BaseerahTokens.gap),
              TextFormField(
                controller: _dayController,
                enabled: !_submitting,
                keyboardType: TextInputType.number,
                textInputAction: TextInputAction.done,
                textDirection: TextDirection.ltr,
                inputFormatters: [
                  FilteringTextInputFormatter.digitsOnly,
                  LengthLimitingTextInputFormatter(2),
                ],
                decoration: InputDecoration(
                  labelText: l.expensesDayField,
                  hintText: l.expensesDayHint,
                  hintTextDirection: TextDirection.ltr,
                  prefixIcon: const Icon(Icons.event_outlined),
                ),
                validator: (v) {
                  final day = int.tryParse((v ?? '').trim());
                  if (day == null || day < 1 || day > 31) {
                    return l.expensesDayInvalid;
                  }
                  return null;
                },
                onFieldSubmitted: (_) => _submitting ? null : _submit(),
              ),
              const SizedBox(height: BaseerahTokens.gap),
              const _ImpactDisclosure(),
              if (_saveFailed) ...[
                const SizedBox(height: 12),
                Text(
                  l.expensesSaveError,
                  style: textTheme.bodySmall?.copyWith(
                    color: BaseerahTokens.alertRed,
                  ),
                ),
              ],
              const SizedBox(height: BaseerahTokens.gap),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: _submitting
                          ? null
                          : () => Navigator.of(context).pop(false),
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 14),
                      ),
                      child: Text(l.expensesCancel),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: ElevatedButton(
                      onPressed: _submitting ? null : _submit,
                      style: ElevatedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 14),
                      ),
                      child: _submitting
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : Text(l.expensesSave),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// The impact-disclosure banner (Step 11.3): a declared expense counts toward
/// the stress score and a bank's loan assessment. Load-bearing copy — never
/// drop it under time pressure.
class _ImpactDisclosure extends StatelessWidget {
  const _ImpactDisclosure();

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: BaseerahTokens.teal.withValues(alpha: 0.07),
        borderRadius: BorderRadius.circular(BaseerahTokens.radiusControl),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.info_outline, size: 18, color: BaseerahTokens.teal),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              l.expensesImpactNote,
              style: textTheme.bodySmall?.copyWith(
                color: BaseerahTokens.darkText,
                height: 1.35,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
