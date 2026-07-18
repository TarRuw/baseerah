import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/format.dart';
import '../../l10n/app_localizations.dart';
import '../../theme/baseerah_theme.dart';
import 'state/expenses_providers.dart';
import 'widgets/expense_card.dart';
import 'widgets/expense_form.dart';
import 'data/periodic_expense.dart';

/// Consumer **Expenses** screen (Phase 11 / GitLab frontend#1) — the 5th
/// bottom-nav tab. Lists the user's declared periodic expenses (spending the
/// SAMA bank feed can't see) and drives the add / edit / deactivate flow.
///
/// A title/subtitle header over one of [expensesControllerProvider]'s phases:
/// *loading* / *error* (retry) / *ready* (the honesty explainer + the expense
/// list, or a purpose-explaining empty state). A FAB opens the add form; each
/// card's overflow menu edits or removes (deactivates) its expense — the manual
/// double-count guard, whose UI half is the honesty copy here.
class ExpensesScreen extends ConsumerWidget {
  const ExpensesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final state = ref.watch(expensesControllerProvider);

    return Scaffold(
      backgroundColor: Colors.transparent,
      floatingActionButton: state.phase == ExpensesPhase.ready
          ? FloatingActionButton.extended(
              onPressed: () => _openForm(context, ref),
              backgroundColor: BaseerahTokens.teal,
              foregroundColor: Colors.white,
              icon: const Icon(Icons.add),
              label: Text(l.expensesAddCta),
            )
          : null,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(
            BaseerahTokens.screenPadding,
            6,
            BaseerahTokens.screenPadding,
            BaseerahTokens.screenPadding,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                l.expensesTitle,
                style: textTheme.headlineSmall?.copyWith(
                  color: BaseerahTokens.darkText,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                l.expensesSubtitle,
                style:
                    textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted),
              ),
              const SizedBox(height: 18),
              Expanded(
                child: switch (state.phase) {
                  ExpensesPhase.loading =>
                    const Center(child: CircularProgressIndicator()),
                  ExpensesPhase.error => const _ExpensesError(),
                  ExpensesPhase.ready => _ReadyView(state: state),
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Open the add (or edit, when [existing] is set) form in a modal sheet. The
/// form drives the controller directly and pops on success; the list is already
/// wired to the controller, so nothing more is needed here.
Future<void> _openForm(
  BuildContext context,
  WidgetRef ref, {
  PeriodicExpense? existing,
}) {
  return showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.white,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
    ),
    builder: (_) => ExpenseForm(existing: existing),
  );
}

/// Fetch-failed path — the shared load error message + a retry.
class _ExpensesError extends ConsumerWidget {
  const _ExpensesError();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.cloud_off_rounded,
              size: 44, color: BaseerahTokens.muted),
          const SizedBox(height: 12),
          Text(
            l.loadError,
            style: textTheme.bodyLarge?.copyWith(color: BaseerahTokens.muted),
          ),
          const SizedBox(height: 16),
          ElevatedButton.icon(
            onPressed: () =>
                ref.read(expensesControllerProvider.notifier).retry(),
            icon: const Icon(Icons.refresh),
            label: Text(l.retry),
          ),
        ],
      ),
    );
  }
}

/// Ready state: the honesty explainer pinned above the expense list, or a
/// purpose-explaining empty state when nothing has been declared yet.
class _ReadyView extends ConsumerWidget {
  const _ReadyView({required this.state});

  final ExpensesState state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final fmt = Fmt(Localizations.localeOf(context), l);

    Future<void> onRemove(String expenseId) async {
      final ok =
          await ref.read(expensesControllerProvider.notifier).deactivate(expenseId);
      if (!ok && context.mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text(l.expensesRemoveError)));
      }
    }

    if (state.expenses.isEmpty) {
      return _EmptyExpenses(onAdd: () => _openForm(context, ref));
    }

    return ListView(
      // Room for the FAB not to cover the last row.
      padding: const EdgeInsets.only(bottom: 88),
      children: [
        const _HonestyNote(),
        const SizedBox(height: 16),
        for (final expense in state.expenses)
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: ExpenseCard(
              expense: expense,
              mutating: state.isMutating(expense.id),
              onEdit: () => _openForm(context, ref, existing: expense),
              onRemove: () => onRemove(expense.id),
              fmt: fmt,
            ),
          ),
      ],
    );
  }
}

/// The list-level double-count guard (Step 11.3, UI half): declared expenses are
/// spending the bank feed can't see, so users shouldn't duplicate what's already
/// tracked in their linked accounts.
class _HonestyNote extends StatelessWidget {
  const _HonestyNote();

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: BaseerahTokens.gold.withValues(alpha: 0.10),
        borderRadius: BorderRadius.circular(BaseerahTokens.radiusControl),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.visibility_off_outlined,
              size: 18, color: BaseerahTokens.goldDark),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              l.expensesHonestyNote,
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

/// Purpose-explaining empty state — what declared expenses *are* and why they
/// matter, not merely "no expenses".
class _EmptyExpenses extends StatelessWidget {
  const _EmptyExpenses({required this.onAdd});

  final VoidCallback onAdd;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    return Center(
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 72,
              height: 72,
              decoration: BoxDecoration(
                color: BaseerahTokens.gold.withValues(alpha: 0.12),
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.receipt_long_outlined,
                  size: 34, color: BaseerahTokens.goldDark),
            ),
            const SizedBox(height: 16),
            Text(
              l.expensesEmptyTitle,
              textAlign: TextAlign.center,
              style: textTheme.titleMedium?.copyWith(
                color: BaseerahTokens.darkText,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              l.expensesEmptyBody,
              textAlign: TextAlign.center,
              style: textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted),
            ),
            const SizedBox(height: 20),
            ElevatedButton.icon(
              onPressed: onAdd,
              icon: const Icon(Icons.add),
              label: Text(l.expensesAddCta),
              style: ElevatedButton.styleFrom(
                padding:
                    const EdgeInsets.symmetric(horizontal: 20, vertical: 13),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
