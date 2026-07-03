import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format.dart';
import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../../home/state/home_providers.dart';
import '../data/loan_result.dart';
import '../state/simulate_providers.dart';

/// Loan Affordability tab (DESIGN §7.2): three sliders drive a live
/// `loan-simulate` call whose instalment / DTI / verdict / score-impact render
/// in the teal result card below. Slider labels track the thumb instantly; the
/// API call is debounced in [LoanController], so the card updates a beat after
/// you stop dragging and keeps its last value visible meanwhile.
class LoanAffordabilityTab extends ConsumerWidget {
  const LoanAffordabilityTab({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final fmt = Fmt(Localizations.localeOf(context), l);
    final loan = ref.watch(loanControllerProvider);
    final controller = ref.read(loanControllerProvider.notifier);
    final inputs = loan.inputs;

    return ListView(
      padding: const EdgeInsets.only(bottom: 24),
      children: [
        // ── Sliders card ──────────────────────────────────────────────────
        Container(
          padding: const EdgeInsets.fromLTRB(18, 18, 18, 8),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
            boxShadow: BaseerahTokens.shadowMedium,
          ),
          child: Column(
            children: [
              _LabeledSlider(
                label: l.loanPrincipal,
                valueLabel: fmt.money(inputs.principal),
                value: inputs.principal,
                min: LoanInputs.principalMin,
                max: LoanInputs.principalMax,
                divisions: ((LoanInputs.principalMax - LoanInputs.principalMin) /
                        LoanInputs.principalStep)
                    .round(),
                onChanged: controller.setPrincipal,
              ),
              _LabeledSlider(
                label: l.loanRate,
                valueLabel: _rateLabel(fmt, inputs.rate),
                value: inputs.rate,
                min: LoanInputs.rateMin,
                max: LoanInputs.rateMax,
                divisions: ((LoanInputs.rateMax - LoanInputs.rateMin) /
                        LoanInputs.rateStep)
                    .round(),
                onChanged: controller.setRate,
              ),
              _LabeledSlider(
                label: l.loanTerm,
                valueLabel: l.loanTermMonths(inputs.term),
                value: inputs.term.toDouble(),
                min: LoanInputs.termMin.toDouble(),
                max: LoanInputs.termMax.toDouble(),
                divisions:
                    ((LoanInputs.termMax - LoanInputs.termMin) / LoanInputs.termStep)
                        .round(),
                onChanged: (v) => controller.setTerm(v.round()),
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),

        // ── Result card ───────────────────────────────────────────────────
        loan.result.when(
          skipLoadingOnReload: true,
          data: (result) => _ResultCard(result: result, fmt: fmt, l: l),
          loading: () => const SizedBox(
            height: 200,
            child: Center(child: CircularProgressIndicator()),
          ),
          error: (_, __) => _ResultError(l: l, onRetry: () {
            // Re-run by nudging inputs to themselves (re-arms a fetch).
            ref.read(loanControllerProvider.notifier).setTerm(
                  ref.read(loanControllerProvider).inputs.term,
                );
          }),
        ),
      ],
    );
  }

  /// Rate label with locale digits, trimming a trailing `.0` (e.g. `5.5%`, `6%`).
  String _rateLabel(Fmt fmt, double rate) {
    final whole = rate == rate.roundToDouble();
    final text = whole
        ? fmt.fmt(rate)
        : rate.toStringAsFixed(2).replaceFirst(RegExp(r'0$'), '');
    return '$text%';
  }
}

/// A slider with a label row: name on the leading edge, live value (teal) on the
/// trailing edge — matching the prototype's slider blocks.
class _LabeledSlider extends StatelessWidget {
  const _LabeledSlider({
    required this.label,
    required this.valueLabel,
    required this.value,
    required this.min,
    required this.max,
    required this.divisions,
    required this.onChanged,
  });

  final String label;
  final String valueLabel;
  final double value;
  final double min;
  final double max;
  final int divisions;
  final ValueChanged<double> onChanged;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                label,
                style: textTheme.bodyMedium?.copyWith(
                  color: BaseerahTokens.darkText,
                  fontWeight: FontWeight.w500,
                ),
              ),
              Text(
                valueLabel,
                style: textTheme.bodyMedium?.copyWith(
                  color: BaseerahTokens.teal,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
          Slider(
            value: value.clamp(min, max),
            min: min,
            max: max,
            divisions: divisions,
            activeColor: BaseerahTokens.teal,
            onChanged: onChanged,
          ),
        ],
      ),
    );
  }
}

/// The teal gradient result card: monthly instalment, total, DTI bar, verdict
/// band, and the projected-vs-current score impact. All colours come from the
/// server-resolved hexes (`dtiColor` / `verdictColor`) so the bands never
/// disagree with the backend verdict.
class _ResultCard extends ConsumerWidget {
  const _ResultCard({required this.result, required this.fmt, required this.l});

  final LoanResult result;
  final Fmt fmt;
  final AppLocalizations l;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final textTheme = Theme.of(context).textTheme;
    final dtiPct = (result.dti * 100).clamp(0, 100).toDouble();
    final currentScore = ref.watch(stressScoreProvider).valueOrNull?.score;

    return Column(
      children: [
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            gradient: BaseerahTokens.tealGradient,
            borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
            boxShadow: BaseerahTokens.shadowMedium,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: _Metric(
                      label: l.monthlyInstallment,
                      value: fmt.money(result.installment),
                      valueStyle: textTheme.headlineMedium?.copyWith(
                        color: Colors.white,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  _Metric(
                    label: l.totalRepayment,
                    value: fmt.money(result.total),
                    alignEnd: true,
                    valueStyle: textTheme.titleMedium?.copyWith(
                      color: Colors.white,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 16),
                child: Container(height: 1, color: Colors.white24),
              ),
              // ── DTI bar ─────────────────────────────────────────────────
              Text(
                '${l.dtiAfter} · ${fmt.fmt(dtiPct)}%',
                style: textTheme.bodySmall?.copyWith(color: const Color(0xFFA9C9BF)),
              ),
              const SizedBox(height: 6),
              ClipRRect(
                borderRadius: BorderRadius.circular(6),
                child: LinearProgressIndicator(
                  value: dtiPct / 100,
                  minHeight: 9,
                  backgroundColor: Colors.white24,
                  valueColor: AlwaysStoppedAnimation(result.dtiColor),
                ),
              ),
              const SizedBox(height: 16),
              // ── Verdict band ────────────────────────────────────────────
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                decoration: BoxDecoration(
                  color: result.verdictColor,
                  borderRadius: BorderRadius.circular(13),
                ),
                child: Row(
                  children: [
                    Icon(_verdictIcon(result.verdictColor),
                        color: Colors.white, size: 22),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        result.verdict,
                        style: textTheme.bodyMedium?.copyWith(
                          color: Colors.white,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        // ── Score impact ──────────────────────────────────────────────────
        _ScoreImpact(
          projected: result.projectedScore,
          current: currentScore,
          label: l.scoreIfTaken,
          fmt: fmt,
        ),
      ],
    );
  }

  /// Map the server verdict hex to the prototype's verdict glyph.
  IconData _verdictIcon(Color color) {
    if (color == BaseerahTokens.successGreen) return Icons.check_circle;
    if (color == BaseerahTokens.warningOrange) return Icons.warning_amber_rounded;
    return Icons.block;
  }
}

/// A label-over-value metric block inside the result card.
class _Metric extends StatelessWidget {
  const _Metric({
    required this.label,
    required this.value,
    required this.valueStyle,
    this.alignEnd = false,
  });

  final String label;
  final String value;
  final TextStyle? valueStyle;
  final bool alignEnd;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment:
          alignEnd ? CrossAxisAlignment.end : CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
            color: const Color(0xFFA9C9BF),
          ),
        ),
        const SizedBox(height: 4),
        Text(value, style: valueStyle),
      ],
    );
  }
}

/// "Health score if taken: projected ← current" — the projected value is
/// coloured by whether the loan improves or worsens the score, giving an instant
/// read on impact.
class _ScoreImpact extends StatelessWidget {
  const _ScoreImpact({
    required this.projected,
    required this.current,
    required this.label,
    required this.fmt,
  });

  final int projected;
  final int? current;
  final String label;
  final Fmt fmt;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    final worsens = current != null && projected < current!;
    final projColor =
        worsens ? BaseerahTokens.alertRed : BaseerahTokens.successGreen;

    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Text(
          label,
          style: textTheme.bodySmall?.copyWith(color: BaseerahTokens.muted),
        ),
        const SizedBox(width: 8),
        Text(
          fmt.fmt(projected),
          style: textTheme.titleMedium?.copyWith(
            color: projColor,
            fontWeight: FontWeight.w700,
          ),
        ),
        if (current != null) ...[
          const SizedBox(width: 8),
          const Icon(Icons.arrow_back, size: 14, color: Color(0xFFC7CFCB)),
          const SizedBox(width: 8),
          Text(
            fmt.fmt(current!),
            style: textTheme.bodyMedium?.copyWith(
              color: BaseerahTokens.darkText,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ],
    );
  }
}

/// Inline error + retry for the result card — keeps the sliders usable.
class _ResultError extends StatelessWidget {
  const _ResultError({required this.l, required this.onRetry});

  final AppLocalizations l;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
        boxShadow: BaseerahTokens.shadowSoft,
      ),
      child: Column(
        children: [
          const Icon(Icons.cloud_off_outlined,
              color: BaseerahTokens.muted, size: 28),
          const SizedBox(height: 8),
          Text(
            l.loadError,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: BaseerahTokens.muted,
            ),
          ),
          const SizedBox(height: 8),
          OutlinedButton(onPressed: onRetry, child: Text(l.retry)),
        ],
      ),
    );
  }
}
