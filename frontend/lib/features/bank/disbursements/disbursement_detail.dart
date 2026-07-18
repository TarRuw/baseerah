import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format.dart';
import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../data/disbursement_models.dart';
import '../state/bank_providers.dart';

/// Right pane of the Disbursements split view: the funding decision for the
/// selected accepted offer. Shows the applicant, the accepted terms, and a final
/// affordability signal (verdict + monthly instalment + client score), then
/// Disburse / Decline actions. Empty until a row is picked; a confirmation once
/// answered.
class DisbursementDetail extends ConsumerWidget {
  const DisbursementDetail({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final state = ref.watch(bankDisbursementProvider);
    final row = state.selected;

    if (row == null) {
      return _Hint(icon: Icons.account_balance_outlined, text: l.bankDisbursementsSelectPrompt);
    }
    if (state.answered) {
      final disbursed = row.status == 'DISBURSED';
      return _Answered(
        icon: disbursed ? Icons.check_circle_rounded : Icons.block,
        color: disbursed ? BaseerahTokens.successGreen : BaseerahTokens.muted,
        text: disbursed ? l.bankDisbursed : l.bankDisbursementDeclined,
      );
    }
    return _DecisionView(row: row, submitting: state.submitting);
  }
}

class _DecisionView extends ConsumerWidget {
  const _DecisionView({required this.row, required this.submitting});

  final DisbursementRow row;
  final bool submitting;

  Color _verdictColor(String verdict) => switch (verdict) {
        'COMFORTABLE' => BaseerahTokens.successGreen,
        'STRAINS' => BaseerahTokens.warningOrange,
        _ => BaseerahTokens.alertRed,
      };

  String _verdictLabel(AppLocalizations l, String verdict) => switch (verdict) {
        'COMFORTABLE' => l.bankAffordabilityComfortable,
        'STRAINS' => l.bankAffordabilityStrains,
        _ => l.bankAffordabilityNotAffordable,
      };

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final fmt = Fmt(Localizations.localeOf(context), l);
    final textTheme = Theme.of(context).textTheme;

    void snack(String msg) => ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(msg)));

    Future<void> disburse() async {
      final ok = await ref.read(bankDisbursementProvider.notifier).disburse();
      if (!ok && context.mounted) snack(l.bankDisbursementError);
    }

    Future<void> decline() async {
      final ok = await ref.read(bankDisbursementProvider.notifier).decline();
      if (!ok && context.mounted) snack(l.bankDisbursementError);
    }

    final verdictColor = _verdictColor(row.affordabilityVerdict);

    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            row.applicantLabel,
            style: textTheme.titleLarge?.copyWith(
              color: BaseerahTokens.darkText,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 4),
          Text(row.bankName, style: textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted)),
          const SizedBox(height: 20),
          Wrap(
            spacing: 28,
            runSpacing: 14,
            children: [
              _Fact(label: l.financingConfirmAmount, value: fmt.money(row.amount)),
              _Fact(label: l.bankFinancingRate, value: '${row.rate.toStringAsFixed(2)}%'),
              _Fact(label: l.bankFinancingTerm, value: l.loanTermMonths(row.termMonths)),
              _Fact(label: l.financingMonthly, value: fmt.money(row.installment)),
              _Fact(label: l.bankFinancingClientScore, value: '${row.clientScore}'),
            ],
          ),
          const SizedBox(height: 18),
          Row(
            children: [
              Text(l.bankAffordabilityLabel,
                  style: textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted)),
              const SizedBox(width: 10),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                decoration: BoxDecoration(
                  color: verdictColor.withValues(alpha: 0.13),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  _verdictLabel(l, row.affordabilityVerdict),
                  style: TextStyle(color: verdictColor, fontWeight: FontWeight.w700, fontSize: 12),
                ),
              ),
            ],
          ),
          const SizedBox(height: 26),
          Row(
            children: [
              ElevatedButton.icon(
                onPressed: submitting ? null : disburse,
                icon: const Icon(Icons.account_balance_wallet_outlined, size: 18),
                label: Text(l.bankDisburse),
                style: ElevatedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 14),
                ),
              ),
              const SizedBox(width: 12),
              OutlinedButton(
                onPressed: submitting ? null : decline,
                child: Text(l.bankFinancingDecline),
              ),
              if (submitting) ...[
                const SizedBox(width: 16),
                const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }
}

class _Fact extends StatelessWidget {
  const _Fact({required this.label, required this.value});

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
            style: textTheme.titleMedium?.copyWith(
                color: BaseerahTokens.darkText, fontWeight: FontWeight.w700)),
      ],
    );
  }
}

class _Answered extends StatelessWidget {
  const _Answered({required this.icon, required this.color, required this.text});

  final IconData icon;
  final Color color;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: color, size: 48),
          const SizedBox(height: 12),
          Text(text,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: BaseerahTokens.darkText, fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}

class _Hint extends StatelessWidget {
  const _Hint({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 44, color: BaseerahTokens.muted),
          const SizedBox(height: 12),
          Text(text,
              textAlign: TextAlign.center,
              style: Theme.of(context)
                  .textTheme
                  .bodyMedium
                  ?.copyWith(color: BaseerahTokens.muted)),
        ],
      ),
    );
  }
}
