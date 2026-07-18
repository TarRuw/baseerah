import 'package:flutter/material.dart';

import '../../../core/format.dart';
import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../data/financing_models.dart';

/// A single replied bank offer, showing how it affects the client's situation:
/// the profit rate, monthly payment, total repayment, DTI/affordability band,
/// and the projected stress score — the reused loan-affordability impact — plus
/// a "choose this offer" action.
class ProposalCard extends StatelessWidget {
  const ProposalCard({
    super.key,
    required this.proposal,
    required this.busy,
    required this.choosing,
    required this.onChoose,
  });

  final FinancingProposal proposal;

  /// True while any choose is in flight (all cards' buttons disable).
  final bool busy;

  /// True for the specific card being chosen (its button shows a spinner).
  final bool choosing;
  final VoidCallback onChoose;

  static Color _hex(String value) {
    final hex = value.replaceFirst('#', '');
    return Color(int.parse('FF$hex', radix: 16));
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final fmt = Fmt(Localizations.localeOf(context), l);
    final textTheme = Theme.of(context).textTheme;
    final impact = proposal.impact!;

    return Container(
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
          Row(
            children: [
              Expanded(
                child: Text(
                  proposal.bankName,
                  style: textTheme.titleMedium?.copyWith(
                    color: BaseerahTokens.darkText,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              _Badge(
                label: impact.verdict,
                color: _hex(impact.verdictColor),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            l.financingRateValue(
              (proposal.rate ?? 0).toStringAsFixed(2),
              proposal.termMonths ?? 0,
            ),
            style: textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted),
          ),
          const SizedBox(height: 14),
          Wrap(
            spacing: 22,
            runSpacing: 12,
            children: [
              _Metric(label: l.financingMonthly, value: fmt.money(impact.installment)),
              _Metric(label: l.financingTotal, value: fmt.money(impact.total)),
              _Metric(
                label: l.financingDti,
                value: '${(impact.dti * 100).round()}%',
                color: _hex(impact.dtiColor),
              ),
              _Metric(
                label: l.financingProjectedScore,
                value: '${impact.projectedScore}',
              ),
            ],
          ),
          const SizedBox(height: 16),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: busy ? null : onChoose,
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 13),
              ),
              child: choosing
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                    )
                  : Text(l.financingAccept),
            ),
          ),
        ],
      ),
    );
  }
}

class _Metric extends StatelessWidget {
  const _Metric({required this.label, required this.value, this.color});

  final String label;
  final String value;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: textTheme.bodySmall?.copyWith(color: BaseerahTokens.muted)),
        const SizedBox(height: 2),
        Text(
          value,
          style: textTheme.titleSmall?.copyWith(
            color: color ?? BaseerahTokens.darkText,
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    );
  }
}

class _Badge extends StatelessWidget {
  const _Badge({required this.label, required this.color});

  final String label;
  final Color color;

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
        style: TextStyle(color: color, fontWeight: FontWeight.w700, fontSize: 10.5),
      ),
    );
  }
}
