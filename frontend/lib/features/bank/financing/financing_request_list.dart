import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format.dart';
import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../data/financing_request_models.dart';
import '../state/bank_providers.dart';

/// Left pane of the Financing Requests split view: the pending inbox from
/// `GET /bank/financing-requests`, each row a selectable card showing the
/// targeted bank, the applicant, the requested amount, and a client-score risk
/// hint. Loads/degrades on its own (spinner / inline retry) so a dropped inbox
/// call never takes down the whole screen.
class FinancingRequestList extends ConsumerWidget {
  const FinancingRequestList({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final fmt = Fmt(Localizations.localeOf(context), l);
    final rows = ref.watch(bankFinancingRequestsProvider);
    final selectedId = ref.watch(
      bankFinancingProvider.select((s) => s.selected?.proposalId),
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: Text(
            l.bankFinancingPending,
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
                ? _EmptyInbox(l: l)
                : ListView.separated(
                    itemCount: items.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 11),
                    itemBuilder: (context, i) {
                      final row = items[i];
                      return _RequestCard(
                        row: row,
                        selected: row.proposalId == selectedId,
                        amountLabel: fmt.money(row.amount),
                        onTap: () =>
                            ref.read(bankFinancingProvider.notifier).select(row),
                      );
                    },
                  ),
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (_, __) => _InboxError(
              l: l,
              onRetry: () => ref.invalidate(bankFinancingRequestsProvider),
            ),
          ),
        ),
      ],
    );
  }
}

/// A single selectable financing-request card. Border turns teal when selected.
class _RequestCard extends StatelessWidget {
  const _RequestCard({
    required this.row,
    required this.selected,
    required this.amountLabel,
    required this.onTap,
  });

  final FinancingRequestRow row;
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
                row.bankName,
                style: textTheme.titleSmall?.copyWith(
                  color: BaseerahTokens.darkText,
                  fontWeight: FontWeight.w700,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 2),
              Text(
                row.applicantLabel,
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
                  const Spacer(),
                  _ScorePill(score: row.clientScore, label: l.bankFinancingClientScore),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// A compact client-score risk hint pill, colour-banded like the stress zones:
/// critical (<40) red, warning (<70) orange, else green.
class _ScorePill extends StatelessWidget {
  const _ScorePill({required this.score, required this.label});

  final int score;
  final String label;

  Color get _color {
    if (score < 40) return BaseerahTokens.alertRed;
    if (score < 70) return BaseerahTokens.warningOrange;
    return BaseerahTokens.successGreen;
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
      decoration: BoxDecoration(
        color: _color.withValues(alpha: 0.13),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        '$label $score',
        style: TextStyle(color: _color, fontWeight: FontWeight.w700, fontSize: 10.5),
      ),
    );
  }
}

class _EmptyInbox extends StatelessWidget {
  const _EmptyInbox({required this.l});

  final AppLocalizations l;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Text(
        l.bankFinancingQueueEmpty,
        textAlign: TextAlign.center,
        style: Theme.of(context)
            .textTheme
            .bodyMedium
            ?.copyWith(color: BaseerahTokens.muted),
      ),
    );
  }
}

class _InboxError extends StatelessWidget {
  const _InboxError({required this.l, required this.onRetry});

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
          Text(
            l.loadError,
            style: Theme.of(context)
                .textTheme
                .bodySmall
                ?.copyWith(color: BaseerahTokens.muted),
          ),
          const SizedBox(height: 8),
          OutlinedButton(onPressed: onRetry, child: Text(l.retry)),
        ],
      ),
    );
  }
}
