import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format.dart';
import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../data/applicant_models.dart';
import '../state/bank_providers.dart';

/// Left pane of the Underwrite split view (DESIGN §7.5): the underwrite-stage
/// queue from `GET /bank/loan-requests?stage=underwrite`, each row a selectable
/// card with avatar (initials), name and `purpose · amount`. The queue lists
/// un-underwritten requests, so a row carries no verdict badge — the verdict is
/// produced by the report the detail pane generates. The selected row is
/// highlighted with a teal border. Loads/degrades on its own — a spinner while
/// fetching, an inline retry on failure — so a dropped queue call never takes
/// down the whole screen.
class ApplicantList extends ConsumerWidget {
  const ApplicantList({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final fmt = Fmt(Localizations.localeOf(context), l);
    final requests = ref.watch(bankLoanRequestsProvider);
    final selectedId = ref.watch(
      bankDetailProvider.select((s) => s.selected?.requestId),
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: Text(
            l.bankPendingReview,
            style: Theme.of(context).textTheme.titleSmall?.copyWith(
              color: BaseerahTokens.muted,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        Expanded(
          child: requests.when(
            // A decline invalidates this provider to drop the request from the
            // queue; keep the list on screen during that reload rather than
            // flashing a spinner.
            skipLoadingOnReload: true,
            data: (rows) => rows.isEmpty
                ? _EmptyQueue(l: l)
                : ListView.separated(
                    itemCount: rows.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 11),
                    itemBuilder: (context, i) {
                      final r = rows[i];
                      return _RequestCard(
                        request: r,
                        selected: r.requestId == selectedId,
                        amountLabel: fmt.money(r.amount),
                        onTap: () =>
                            ref.read(bankDetailProvider.notifier).select(r),
                      );
                    },
                  ),
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (_, __) => _QueueError(
              l: l,
              onRetry: () => ref.invalidate(bankLoanRequestsProvider),
            ),
          ),
        ),
      ],
    );
  }
}

/// A single selectable loan-request card. Border turns teal when [selected].
class _RequestCard extends StatelessWidget {
  const _RequestCard({
    required this.request,
    required this.selected,
    required this.amountLabel,
    required this.onTap,
  });

  final LoanRequestRow request;
  final bool selected;
  final String amountLabel;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    final purpose = request.purpose;
    final subtitle =
        purpose == null ? amountLabel : '$purpose · $amountLabel';

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
          child: Row(
            children: [
              _Avatar(initials: request.initials, size: 40, fontSize: 14),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      request.applicantLabel,
                      style: textTheme.titleSmall?.copyWith(
                        color: BaseerahTokens.darkText,
                        fontWeight: FontWeight.w600,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 1),
                    Text(
                      subtitle,
                      style: textTheme.bodySmall?.copyWith(
                        color: BaseerahTokens.muted,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// The teal, rounded initials avatar shared by the list row and the report
/// header (DESIGN §7.5). [size] and [fontSize] scale it between the two uses.
class _Avatar extends StatelessWidget {
  const _Avatar({
    required this.initials,
    required this.size,
    required this.fontSize,
  });

  final String initials;
  final double size;
  final double fontSize;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: BaseerahTokens.teal,
        borderRadius: BorderRadius.circular(size * 0.28),
      ),
      child: Text(
        initials,
        style: TextStyle(
          color: Colors.white,
          fontWeight: FontWeight.w600,
          fontSize: fontSize,
        ),
      ),
    );
  }
}

/// The initials avatar used by the report detail header (exported for reuse).
class BankAvatar extends StatelessWidget {
  const BankAvatar({
    super.key,
    required this.initials,
    this.size = 48,
    this.fontSize = 16,
  });

  final String initials;
  final double size;
  final double fontSize;

  @override
  Widget build(BuildContext context) =>
      _Avatar(initials: initials, size: size, fontSize: fontSize);
}

/// Colour-coded risk badge derived from the [Verdict] (DESIGN §7.5). OK→low
/// (green), WARN→medium (orange), BAD→high (red), and an un-underwritten request
/// (null verdict) → a neutral "unscored" chip. Used by the Price tab's detail as
/// a pricing risk hint (the underwrite queue itself is un-underwritten, so it
/// shows no badge). All colours from the single theme token file; labels localised.
class RiskBadge {
  const RiskBadge._(this.color, this.label);

  final Color color;
  final String label;

  factory RiskBadge.of(Verdict? verdict, AppLocalizations l) {
    return switch (verdict) {
      Verdict.ok => RiskBadge._(BaseerahTokens.successGreen, l.bankRiskLow),
      Verdict.warn => RiskBadge._(BaseerahTokens.warningOrange, l.bankRiskMed),
      Verdict.bad => RiskBadge._(BaseerahTokens.alertRed, l.bankRiskHigh),
      null => RiskBadge._(BaseerahTokens.muted, l.bankRiskUnscored),
    };
  }

  Widget build(BuildContext context) => _StatusPill(color: color, label: label);
}

/// The compact colour-coded pill used by the risk badge: a tinted rounded rect
/// with a bold coloured label (DESIGN §7.5).
class _StatusPill extends StatelessWidget {
  const _StatusPill({required this.color, required this.label});

  final Color color;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.13),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: color,
          fontWeight: FontWeight.w700,
          fontSize: 10.5,
        ),
      ),
    );
  }
}

/// Neutral empty state when the queue comes back with no requests to underwrite.
class _EmptyQueue extends StatelessWidget {
  const _EmptyQueue({required this.l});

  final AppLocalizations l;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Text(
        l.bankQueueEmpty,
        textAlign: TextAlign.center,
        style: Theme.of(
          context,
        ).textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted),
      ),
    );
  }
}

/// Inline error + retry for the queue slot — keeps the detail pane usable.
class _QueueError extends StatelessWidget {
  const _QueueError({required this.l, required this.onRetry});

  final AppLocalizations l;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.people_outline, color: BaseerahTokens.muted),
          const SizedBox(height: 8),
          Text(
            l.loadError,
            style: Theme.of(
              context,
            ).textTheme.bodySmall?.copyWith(color: BaseerahTokens.muted),
          ),
          const SizedBox(height: 8),
          OutlinedButton(onPressed: onRetry, child: Text(l.retry)),
        ],
      ),
    );
  }
}
