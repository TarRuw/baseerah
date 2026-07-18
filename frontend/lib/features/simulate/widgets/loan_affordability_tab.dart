import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format.dart';
import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../../financing/financing_screen.dart';
import '../../home/state/home_providers.dart';
import '../data/loan_result.dart';
import '../state/simulate_providers.dart';

/// Loan Affordability tab (DESIGN §7.2): three numeric inputs — loan amount,
/// bank profit rate, and term in months — drive a live `loan-simulate` call
/// whose instalment / DTI / verdict / score-impact render in the teal result
/// card below. The fields replaced the prototype's capped sliders so the
/// simulation can model real facilities (up to 10M SAR / 600 months). Each field
/// clamps to its guardrail range on commit; the API call is debounced in
/// [LoanController], so the card updates a beat after you stop typing and keeps
/// its last value visible meanwhile.
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
        // ── Inputs card ───────────────────────────────────────────────────
        Container(
          padding: const EdgeInsets.fromLTRB(18, 18, 18, 18),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
            boxShadow: BaseerahTokens.shadowMedium,
          ),
          child: Column(
            children: [
              _LabeledNumberField(
                label: l.loanPrincipal,
                value: inputs.principal,
                min: LoanInputs.principalMin,
                max: LoanInputs.principalMax,
                suffix: l.currencySar,
                onChanged: controller.setPrincipal,
              ),
              const SizedBox(height: 14),
              _LabeledNumberField(
                label: l.loanRate,
                value: inputs.rate,
                min: LoanInputs.rateMin,
                max: LoanInputs.rateMax,
                suffix: '%',
                decimals: 2,
                onChanged: controller.setRate,
              ),
              const SizedBox(height: 14),
              _LabeledNumberField(
                label: l.loanTerm,
                value: inputs.term.toDouble(),
                min: LoanInputs.termMin.toDouble(),
                max: LoanInputs.termMax.toDouble(),
                suffix: l.monthsUnit,
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
        const SizedBox(height: 16),

        // ── Apply CTA ─────────────────────────────────────────────────────
        // Turns the stateless what-if into a real financing request: opens the
        // financing bank picker (origin DIRECT) for the entered principal, so the
        // pipeline is fed by more than Smart Rescue. `/loan-simulate` itself stays
        // a persist-nothing calculator; this is a separate action that *creates*.
        SizedBox(
          width: double.infinity,
          child: ElevatedButton.icon(
            onPressed: () => context.go(
              '/rescue/financing',
              extra: FinancingArgs(
                amount: inputs.principal,
                deficitInDays: 0,
                origin: 'DIRECT',
                openPicker: true,
              ),
            ),
            icon: const Icon(Icons.request_quote_outlined),
            label: Text(l.loanRequestFinancing),
            style: ElevatedButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 15),
            ),
          ),
        ),
      ],
    );
  }
}

/// A labeled numeric input: the field name on the leading edge, a bordered text
/// field (with a trailing unit suffix) on the trailing edge. Replaces the
/// prototype's slider so values aren't capped by a track — the user types any
/// amount and the field clamps it to `[min, max]` when editing finishes.
///
/// Owns its own [TextEditingController]/[FocusNode] (hence stateful): the field
/// text is the source of truth while focused, so we only reconcile it with the
/// controller value on blur. Live keystrokes fire [onChanged] with the parsed
/// value (feeding the debounced simulation); the clamp + tidy-up happens once
/// the user commits, so typing a number that passes through the range (e.g. "5"
/// on the way to "5000") is never fought by the field.
class _LabeledNumberField extends StatefulWidget {
  const _LabeledNumberField({
    required this.label,
    required this.value,
    required this.min,
    required this.max,
    required this.suffix,
    required this.onChanged,
    this.decimals = 0,
  });

  final String label;
  final double value;
  final double min;
  final double max;
  final String suffix;
  final ValueChanged<double> onChanged;

  /// Decimal places the field accepts/normalizes to (0 = integer input).
  final int decimals;

  @override
  State<_LabeledNumberField> createState() => _LabeledNumberFieldState();
}

class _LabeledNumberFieldState extends State<_LabeledNumberField> {
  late final TextEditingController _controller;
  late final FocusNode _focus;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: _format(widget.value));
    _focus = FocusNode()..addListener(_onFocusChange);
  }

  @override
  void didUpdateWidget(_LabeledNumberField old) {
    super.didUpdateWidget(old);
    // Reflect programmatic value changes (e.g. clamping) only while unfocused,
    // so we never yank text out from under the cursor mid-edit.
    if (!_focus.hasFocus && widget.value != _parse(_controller.text)) {
      _controller.text = _format(widget.value);
    }
  }

  @override
  void dispose() {
    _focus.dispose();
    _controller.dispose();
    super.dispose();
  }

  /// On blur: clamp the typed value to the guardrail range, normalize the field
  /// text to match, and push the committed value out.
  void _onFocusChange() {
    if (_focus.hasFocus) return;
    final parsed = _parse(_controller.text) ?? widget.min;
    final clamped = parsed.clamp(widget.min, widget.max).toDouble();
    _controller.text = _format(clamped);
    widget.onChanged(clamped);
  }

  double? _parse(String text) => double.tryParse(text.trim());

  String _format(double value) => widget.decimals == 0
      ? value.round().toString()
      : value
          .toStringAsFixed(widget.decimals)
          .replaceFirst(RegExp(r'\.?0+$'), '');

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Row(
      children: [
        Expanded(
          child: Text(
            widget.label,
            style: textTheme.bodyMedium?.copyWith(
              color: BaseerahTokens.darkText,
              fontWeight: FontWeight.w500,
            ),
          ),
        ),
        const SizedBox(width: 12),
        SizedBox(
          width: 150,
          child: TextField(
            controller: _controller,
            focusNode: _focus,
            textAlign: TextAlign.end,
            keyboardType: TextInputType.numberWithOptions(
              decimal: widget.decimals > 0,
            ),
            inputFormatters: [
              widget.decimals > 0
                  ? FilteringTextInputFormatter.allow(RegExp(r'[0-9.]'))
                  : FilteringTextInputFormatter.digitsOnly,
            ],
            style: textTheme.bodyMedium?.copyWith(
              color: BaseerahTokens.teal,
              fontWeight: FontWeight.w700,
            ),
            decoration: InputDecoration(
              isDense: true,
              suffixText: widget.suffix,
              suffixStyle: textTheme.bodySmall?.copyWith(
                color: BaseerahTokens.muted,
                fontWeight: FontWeight.w600,
              ),
              contentPadding:
                  const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(10),
                borderSide: const BorderSide(color: Color(0xFFDDE3E0)),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(10),
                borderSide:
                    const BorderSide(color: BaseerahTokens.teal, width: 1.5),
              ),
            ),
            onChanged: (text) {
              final parsed = _parse(text);
              if (parsed != null) widget.onChanged(parsed);
            },
          ),
        ),
      ],
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
