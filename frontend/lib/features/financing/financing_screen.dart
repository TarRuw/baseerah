import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/format.dart';
import '../../l10n/app_localizations.dart';
import '../../theme/baseerah_theme.dart';
import '../home/state/home_providers.dart';
import 'data/financing_models.dart';
import 'state/financing_providers.dart';
import 'widgets/proposal_card.dart';

/// Arguments handed to the financing flow when it's launched: the amount to
/// finance and the deficit lead time (for the audited event on choose). Passed
/// via GoRouter `extra` and used when raising a new request.
///
/// Phase 12 (unified pipeline) adds an [origin]/[purpose] pair so the flow can be
/// entered directly from the Simulate "request this financing" CTA (origin
/// `DIRECT`), and [openPicker] so that entry lands straight on the bank picker
/// instead of the history list. Launched from Rescue these keep their defaults
/// (origin `RESCUE`, list first), so that path is unchanged.
class FinancingArgs {
  const FinancingArgs({
    required this.amount,
    required this.deficitInDays,
    this.origin = 'RESCUE',
    this.purpose,
    this.openPicker = false,
  });

  final double amount;
  final int deficitInDays;

  /// How a request raised from this entry is tagged (`RESCUE` / `DIRECT`).
  final String origin;

  /// Free-text purpose for the request; null lets the backend default it.
  final String? purpose;

  /// When true, the flow opens the bank picker directly (the direct-apply entry).
  final bool openPicker;
}

/// The consumer financing request-for-proposal screen (Smart Rescue). Shows the
/// client's request history (each a status pill), lets them raise a new request
/// to the banks they hold accounts with, and — as banks reply (polled) — compare
/// offers with full affordability impact and choose one. Rendered inside the
/// consumer shell with an in-content back affordance.
class FinancingScreen extends ConsumerStatefulWidget {
  const FinancingScreen({super.key, required this.args});

  final FinancingArgs args;

  @override
  ConsumerState<FinancingScreen> createState() => _FinancingScreenState();
}

class _FinancingScreenState extends ConsumerState<FinancingScreen> {
  /// One-shot guard: a direct-apply entry ([FinancingArgs.openPicker]) jumps to
  /// the bank picker the first time the history finishes loading, then never
  /// re-triggers (so returning to the list via Back stays on the list).
  bool _pickerOpened = false;

  @override
  Widget build(BuildContext context) {
    final args = widget.args;
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final state = ref.watch(financingControllerProvider);
    final notifier = ref.read(financingControllerProvider.notifier);

    // Direct-apply entry: once the initial history load lands on the list, open
    // the picker straight away (guarded so it fires only once).
    if (args.openPicker && !_pickerOpened && state.view == FinancingView.list) {
      _pickerOpened = true;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) notifier.newRequest();
      });
    }

    // Back returns to the history list from a sub-view; from the list it pops to Rescue.
    void onBack() {
      if (state.view == FinancingView.picker ||
          state.view == FinancingView.proposals ||
          state.view == FinancingView.done) {
        notifier.backToList();
      } else if (context.canPop()) {
        context.pop();
      }
    }

    return SafeArea(
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
            Row(
              children: [
                IconButton(
                  onPressed: onBack,
                  icon: const Icon(Icons.arrow_back),
                  tooltip: l.back,
                  color: BaseerahTokens.darkText,
                ),
                const SizedBox(width: 4),
                Expanded(
                  child: Text(
                    l.financingTitle,
                    style: textTheme.headlineSmall?.copyWith(
                      color: BaseerahTokens.darkText,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Expanded(child: _body(context, ref, state)),
          ],
        ),
      ),
    );
  }

  Widget _body(BuildContext context, WidgetRef ref, FinancingScreenState state) {
    switch (state.view) {
      case FinancingView.loading:
        return const Center(child: CircularProgressIndicator());
      case FinancingView.list:
        return _RequestsList(args: widget.args);
      case FinancingView.picker:
        return _PickBanks(args: widget.args);
      case FinancingView.proposals:
        return _RequestDetail(state: state);
      case FinancingView.done:
        return _DoneView(outcome: state.outcome!);
      case FinancingView.error:
        return const _ErrorView();
    }
  }
}

/// The history: a card per request (status pill), and a button to raise a new one.
class _RequestsList extends ConsumerWidget {
  const _RequestsList({required this.args});

  final FinancingArgs args;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final fmt = Fmt(Localizations.localeOf(context), l);
    final textTheme = Theme.of(context).textTheme;
    final requests = ref.watch(
      financingControllerProvider.select((s) => s.requests),
    );
    final notifier = ref.read(financingControllerProvider.notifier);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          l.financingRequestsTitle,
          style: textTheme.titleMedium?.copyWith(
            color: BaseerahTokens.darkText,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 12),
        Expanded(
          child: requests.isEmpty
              ? Center(
                  child: Text(
                    l.financingNoRequests,
                    textAlign: TextAlign.center,
                    style: textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted),
                  ),
                )
              : ListView.separated(
                  itemCount: requests.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 10),
                  itemBuilder: (context, i) {
                    final r = requests[i];
                    return _RequestCard(
                      request: r,
                      amountLabel: fmt.money(r.amount),
                      onTap: () => notifier.open(r.id),
                    );
                  },
                ),
        ),
        const SizedBox(height: 8),
        SizedBox(
          width: double.infinity,
          child: ElevatedButton.icon(
            onPressed: notifier.newRequest,
            icon: const Icon(Icons.add),
            label: Text(l.financingNewRequest),
            style: ElevatedButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 15),
            ),
          ),
        ),
      ],
    );
  }
}

/// One request row: amount, bank count, and an at-a-glance status pill.
class _RequestCard extends StatelessWidget {
  const _RequestCard({
    required this.request,
    required this.amountLabel,
    required this.onTap,
  });

  final FinancingRequest request;
  final String amountLabel;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
            border: Border.all(color: const Color(0xFFE4E0D6)),
          ),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      amountLabel,
                      style: textTheme.titleMedium?.copyWith(
                        color: BaseerahTokens.darkText,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      l.financingBankCount(request.proposals.length),
                      style: textTheme.bodySmall?.copyWith(color: BaseerahTokens.muted),
                    ),
                  ],
                ),
              ),
              _RequestStatusPill(status: request.uiStatus),
              const SizedBox(width: 4),
              const Icon(Icons.chevron_right, color: Color(0xFFCFC9BC)),
            ],
          ),
        ),
      ),
    );
  }
}

/// Maps a request's [FinancingRequestUiStatus] to a coloured status pill.
class _RequestStatusPill extends StatelessWidget {
  const _RequestStatusPill({required this.status});

  final FinancingRequestUiStatus status;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final (String label, Color color, IconData icon) = switch (status) {
      FinancingRequestUiStatus.pending =>
        (l.financingStatusPending, BaseerahTokens.gold, Icons.schedule),
      FinancingRequestUiStatus.offersReady =>
        (l.financingStatusOffers, BaseerahTokens.teal, Icons.local_offer_outlined),
      FinancingRequestUiStatus.accepted =>
        (l.financingStatusAccepted, BaseerahTokens.gold, Icons.hourglass_bottom),
      FinancingRequestUiStatus.active =>
        (l.financingStatusActive, BaseerahTokens.successGreen, Icons.account_balance_wallet_outlined),
      FinancingRequestUiStatus.declined =>
        (l.financingDeclined, BaseerahTokens.muted, Icons.block),
    };
    return _StatusPill(label: label, color: color, icon: icon);
  }
}

/// Phase — pick which of the client's banks to send a new request to.
class _PickBanks extends ConsumerStatefulWidget {
  const _PickBanks({required this.args});

  final FinancingArgs args;

  @override
  ConsumerState<_PickBanks> createState() => _PickBanksState();
}

class _PickBanksState extends ConsumerState<_PickBanks> {
  final Set<String> _selected = {};

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final fmt = Fmt(Localizations.localeOf(context), l);
    final textTheme = Theme.of(context).textTheme;
    final accounts = ref.watch(accountsProvider);
    final submitting = ref.watch(
      financingControllerProvider.select((s) => s.submitting),
    );

    Future<void> onSubmit() async {
      final ok = await ref.read(financingControllerProvider.notifier).submit(
            amount: widget.args.amount,
            deficitInDays: widget.args.deficitInDays,
            banks: _selected.toList(),
            origin: widget.args.origin,
            purpose: widget.args.purpose,
          );
      if (!ok && context.mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text(l.financingChooseError)));
      }
    }

    return accounts.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (_, __) => const _ErrorView(),
      data: (list) {
        final banks = <String>{for (final a in list) a.bankName}.toList();
        if (banks.isEmpty) {
          return Center(
            child: Text(
              l.financingNoBanks,
              textAlign: TextAlign.center,
              style: textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted),
            ),
          );
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              l.financingPickBanksTitle,
              style: textTheme.titleMedium?.copyWith(
                color: BaseerahTokens.darkText,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              l.financingPickBanksSubtitle(fmt.money(widget.args.amount)),
              style: textTheme.bodySmall?.copyWith(color: BaseerahTokens.muted),
            ),
            const SizedBox(height: 16),
            Expanded(
              child: ListView(
                children: [
                  for (final bank in banks)
                    Card(
                      elevation: 0,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                        side: BorderSide(
                          color: _selected.contains(bank)
                              ? BaseerahTokens.teal
                              : const Color(0xFFE4E0D6),
                          width: _selected.contains(bank) ? 2 : 1,
                        ),
                      ),
                      child: CheckboxListTile(
                        value: _selected.contains(bank),
                        onChanged: submitting
                            ? null
                            : (on) => setState(() {
                                  if (on ?? false) {
                                    _selected.add(bank);
                                  } else {
                                    _selected.remove(bank);
                                  }
                                }),
                        title: Text(bank),
                        activeColor: BaseerahTokens.teal,
                        controlAffinity: ListTileControlAffinity.leading,
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: (_selected.isEmpty || submitting) ? null : onSubmit,
                style: ElevatedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 15),
                ),
                child: submitting
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : Text(l.financingSubmit),
              ),
            ),
          ],
        );
      },
    );
  }
}

/// A request's detail. Once accepted/active it shows the facility (accepted terms
/// or the funded facility with its repayment schedule); while open it lists the
/// per-bank offers — a full offer card (with accept) for a replied bank, or a
/// status-pill row for a bank still to reply / declined.
class _RequestDetail extends ConsumerWidget {
  const _RequestDetail({required this.state});

  final FinancingScreenState state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final request = state.active;
    if (request == null) {
      return Center(
        child: Text(l.loadError,
            style: textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted)),
      );
    }

    // Accepted or funded → show the facility, not the offer list.
    if (request.uiStatus == FinancingRequestUiStatus.accepted ||
        request.uiStatus == FinancingRequestUiStatus.active) {
      return _FacilityView(request: request);
    }

    final choosing = state.choosingId != null;

    Future<void> onAccept(FinancingProposal proposal) async {
      final confirmed = await _confirmTerms(context, proposal);
      if (confirmed != true) return;
      final ok = await ref.read(financingControllerProvider.notifier).accept(proposal.id);
      if (!ok && context.mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text(l.financingChooseError)));
      }
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          l.financingProposalsTitle,
          style: textTheme.titleMedium?.copyWith(
            color: BaseerahTokens.darkText,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 12),
        Expanded(
          child: ListView(
            children: [
              for (final p in request.proposals)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: p.status == FinancingProposalStatus.replied
                      ? ProposalCard(
                          proposal: p,
                          busy: choosing,
                          choosing: choosing && state.choosingId == p.id,
                          onChoose: () => onAccept(p),
                        )
                      : _BankStatusRow(proposal: p),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

/// A Murabaha terms-confirmation sheet shown before accepting — the commitment
/// point (stands in for the e-agreement). Returns true if the client confirms.
Future<bool?> _confirmTerms(BuildContext context, FinancingProposal proposal) {
  final l = AppLocalizations.of(context);
  final fmt = Fmt(Localizations.localeOf(context), l);
  final impact = proposal.impact;
  return showDialog<bool>(
    context: context,
    builder: (context) {
      final textTheme = Theme.of(context).textTheme;
      return AlertDialog(
        title: Text(l.financingConfirmTitle),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(proposal.bankName,
                style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
            const SizedBox(height: 10),
            _TermsRow(label: l.financingConfirmAmount, value: fmt.money(proposal.amount)),
            _TermsRow(
                label: l.financingConfirmRate,
                value: '${(proposal.rate ?? 0).toStringAsFixed(2)}%'),
            if (impact != null)
              _TermsRow(label: l.financingMonthly, value: fmt.money(impact.installment)),
            _TermsRow(label: l.financingConfirmTerm, value: l.loanTermMonths(proposal.termMonths ?? 0)),
            if (impact != null)
              _TermsRow(label: l.financingTotal, value: fmt.money(impact.total)),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: Text(l.financingConfirmCancel),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(l.financingConfirmAccept),
          ),
        ],
      );
    },
  );
}

class _TermsRow extends StatelessWidget {
  const _TermsRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted)),
          Text(value,
              style: textTheme.bodyMedium?.copyWith(
                  color: BaseerahTokens.darkText, fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}

/// The accepted/funded facility: while accepted it shows the terms + an
/// "awaiting disbursement" note; once active it shows the funded facility and its
/// repayment schedule (monthly instalment, term, first payment date, total).
class _FacilityView extends StatelessWidget {
  const _FacilityView({required this.request});

  final FinancingRequest request;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final fmt = Fmt(Localizations.localeOf(context), l);
    final textTheme = Theme.of(context).textTheme;
    final facility = request.facility;
    if (facility == null) {
      return Center(
        child: Text(l.loadError,
            style: textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted)),
      );
    }
    final active = request.uiStatus == FinancingRequestUiStatus.active;
    final impact = facility.impact;
    final accent = active ? BaseerahTokens.successGreen : BaseerahTokens.gold;

    return ListView(
      children: [
        Row(
          children: [
            Icon(active ? Icons.check_circle : Icons.hourglass_bottom, color: accent, size: 22),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                active ? l.financingFacilityTitle : l.financingStatusAccepted,
                style: textTheme.titleMedium?.copyWith(
                  color: BaseerahTokens.darkText,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 4),
        Text(
          active ? l.financingFacilitySubtitle : l.financingAwaitingDisbursement,
          style: textTheme.bodySmall?.copyWith(color: BaseerahTokens.muted),
        ),
        const SizedBox(height: 16),
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
            border: Border.all(color: const Color(0xFFE4E0D6)),
            boxShadow: BaseerahTokens.shadowSoft,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(facility.bankName,
                  style: textTheme.titleMedium?.copyWith(
                      color: BaseerahTokens.darkText, fontWeight: FontWeight.w700)),
              const SizedBox(height: 14),
              Wrap(
                spacing: 24,
                runSpacing: 14,
                children: [
                  _FacilityMetric(label: l.financingConfirmAmount, value: fmt.money(facility.amount)),
                  if (impact != null)
                    _FacilityMetric(label: l.financingMonthly, value: fmt.money(impact.installment)),
                  _FacilityMetric(
                      label: l.financingConfirmTerm, value: l.loanTermMonths(facility.termMonths ?? 0)),
                  if (impact != null)
                    _FacilityMetric(label: l.financingTotal, value: fmt.money(impact.total)),
                  if (active && facility.firstPaymentDate != null)
                    _FacilityMetric(
                        label: l.financingFirstPayment,
                        value: fmt.date(facility.firstPaymentDate!)),
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _FacilityMetric extends StatelessWidget {
  const _FacilityMetric({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: textTheme.bodySmall?.copyWith(color: BaseerahTokens.muted)),
        const SizedBox(height: 2),
        Text(value,
            style: textTheme.titleSmall?.copyWith(
                color: BaseerahTokens.darkText, fontWeight: FontWeight.w700)),
      ],
    );
  }
}

/// A pending/declined bank row inside a request detail (no offer to show yet).
class _BankStatusRow extends StatelessWidget {
  const _BankStatusRow({required this.proposal});

  final FinancingProposal proposal;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final declined = proposal.status == FinancingProposalStatus.declined;
    return ListTile(
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: const BorderSide(color: Color(0xFFE4E0D6)),
      ),
      title: Text(proposal.bankName),
      trailing: declined
          ? _StatusPill(label: l.financingDeclined, color: BaseerahTokens.muted, icon: Icons.block)
          : _StatusPill(label: l.financingAwaiting, color: BaseerahTokens.gold, icon: Icons.schedule),
    );
  }
}

/// Chosen: the before→after stress score.
class _DoneView extends StatelessWidget {
  const _DoneView({required this.outcome});

  final FinancingOutcome outcome;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 72,
            height: 72,
            decoration: BoxDecoration(
              color: BaseerahTokens.successGreen.withValues(alpha: 0.12),
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.check_circle_rounded,
                color: BaseerahTokens.successGreen, size: 42),
          ),
          const SizedBox(height: 16),
          Text(
            l.financingAcceptedTitle,
            style: textTheme.headlineSmall?.copyWith(
              color: BaseerahTokens.darkText,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 6),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24),
            child: Text(
              l.financingAwaitingDisbursement,
              textAlign: TextAlign.center,
              style: textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted),
            ),
          ),
          const SizedBox(height: 10),
          Text(
            l.financingScoreChange(outcome.scoreBefore, outcome.scoreAfter),
            style: textTheme.titleMedium?.copyWith(color: BaseerahTokens.teal),
          ),
        ],
      ),
    );
  }
}

class _ErrorView extends ConsumerWidget {
  const _ErrorView();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.cloud_off_rounded, size: 44, color: BaseerahTokens.muted),
          const SizedBox(height: 12),
          Text(l.loadError,
              style: Theme.of(context)
                  .textTheme
                  .bodyLarge
                  ?.copyWith(color: BaseerahTokens.muted)),
          const SizedBox(height: 16),
          ElevatedButton.icon(
            onPressed: () => ref.read(financingControllerProvider.notifier).retry(),
            icon: const Icon(Icons.refresh),
            label: Text(l.retry),
          ),
        ],
      ),
    );
  }
}

/// A compact, tinted status pill (static — never a spinner: replies are async and
/// can take days, so an indeterminate loader would misrepresent the state).
class _StatusPill extends StatelessWidget {
  const _StatusPill({required this.label, required this.color, required this.icon});

  final String label;
  final Color color;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.13),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 13, color: color),
          const SizedBox(width: 5),
          Text(
            label,
            style: TextStyle(color: color, fontWeight: FontWeight.w700, fontSize: 11),
          ),
        ],
      ),
    );
  }
}
