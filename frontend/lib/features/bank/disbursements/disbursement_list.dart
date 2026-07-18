import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format.dart';
import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../data/disbursement_models.dart';
import '../state/bank_providers.dart';

/// Left pane of the Disbursements split view: the queue of accepted offers from
/// `GET /bank/financing-disbursements`, each a selectable card with the applicant,
/// the amount, and the accepted terms. Loads/degrades on its own.
class DisbursementList extends ConsumerWidget {
  const DisbursementList({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final fmt = Fmt(Localizations.localeOf(context), l);
    final rows = ref.watch(bankDisbursementsProvider);
    final selectedId = ref.watch(
      bankDisbursementProvider.select((s) => s.selected?.proposalId),
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: Text(
            l.bankDisbursementsPending,
            style: Theme.of(context).textTheme.titleSmall?.copyWith(
              color: BaseerahTokens.muted,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        Expanded(
          child: rows.when(
            skipLoadingOnReload: true,
            data: (items) => items.isEmpty
                ? _Empty(l: l)
                : ListView.separated(
                    itemCount: items.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 11),
                    itemBuilder: (context, i) {
                      final row = items[i];
                      return _Card(
                        row: row,
                        selected: row.proposalId == selectedId,
                        amountLabel: fmt.money(row.amount),
                        onTap: () =>
                            ref.read(bankDisbursementProvider.notifier).select(row),
                      );
                    },
                  ),
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (_, __) => _ListError(
              l: l,
              onRetry: () => ref.invalidate(bankDisbursementsProvider),
            ),
          ),
        ),
      ],
    );
  }
}

class _Card extends StatelessWidget {
  const _Card({
    required this.row,
    required this.selected,
    required this.amountLabel,
    required this.onTap,
  });

  final DisbursementRow row;
  final bool selected;
  final String amountLabel;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(14),
        child: Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(14),
            border: Border.all(
              color: selected
                  ? BaseerahTokens.teal
                  : BaseerahTokens.darkText.withValues(alpha: 0.06),
              width: 2,
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                row.applicantLabel,
                style: textTheme.titleSmall?.copyWith(
                  color: BaseerahTokens.darkText,
                  fontWeight: FontWeight.w700,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 2),
              Text(
                row.bankName,
                style: textTheme.bodySmall?.copyWith(color: BaseerahTokens.muted),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 10),
              Row(
                children: [
                  Text(
                    amountLabel,
                    style: textTheme.titleSmall?.copyWith(
                      color: BaseerahTokens.teal,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    '${row.rate.toStringAsFixed(2)}% · ${l.loanTermMonths(row.termMonths)}',
                    style: textTheme.bodySmall?.copyWith(color: BaseerahTokens.muted),
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

class _Empty extends StatelessWidget {
  const _Empty({required this.l});

  final AppLocalizations l;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Text(
        l.bankDisbursementsEmpty,
        textAlign: TextAlign.center,
        style: Theme.of(context)
            .textTheme
            .bodyMedium
            ?.copyWith(color: BaseerahTokens.muted),
      ),
    );
  }
}

class _ListError extends StatelessWidget {
  const _ListError({required this.l, required this.onRetry});

  final AppLocalizations l;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.inbox_outlined, color: BaseerahTokens.muted),
          const SizedBox(height: 8),
          Text(l.loadError,
              style: Theme.of(context)
                  .textTheme
                  .bodySmall
                  ?.copyWith(color: BaseerahTokens.muted)),
          const SizedBox(height: 8),
          OutlinedButton(onPressed: onRetry, child: Text(l.retry)),
        ],
      ),
    );
  }
}
