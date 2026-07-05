import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format.dart';
import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../data/applicant_models.dart';
import '../state/bank_providers.dart';
import 'applicant_list.dart';

/// Right pane of the Applications split view (DESIGN §7.5): the applicant detail,
/// driven by [bankDetailProvider]. Three states —
///   • **empty**: nothing selected → a neutral placeholder;
///   • **generating**: the `bsr-spin` (0.8 s) loader + "Analyzing 24-month
///     telemetry…" while the report POST is in flight;
///   • **report**: the verdict panel, band-coloured stamina box, three KPI boxes,
///     the 12-month cash-flow chart, and Approve / Decline actions.
/// A failed report shows an inline retry; a decision shows a confirmation and
/// disables the actions.
class ReportDetail extends ConsumerWidget {
  const ReportDetail({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final state = ref.watch(bankDetailProvider);
    final selected = state.selected;

    if (selected == null) return _EmptyDetail(l: l);

    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: BaseerahTokens.darkText.withValues(alpha: 0.06)),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _DetailHeader(applicant: selected, state: state),
          Flexible(child: _DetailBody(state: state)),
        ],
      ),
    );
  }
}

/// Neutral empty state shown until an applicant is selected.
class _EmptyDetail extends StatelessWidget {
  const _EmptyDetail({required this.l});

  final AppLocalizations l;

  @override
  Widget build(BuildContext context) {
    return DottedPlaceholder(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(
            Icons.description_outlined,
            size: 46,
            color: Color(0xFFC3CEC9),
          ),
          const SizedBox(height: 14),
          Text(
            l.bankSelectApplicant,
            textAlign: TextAlign.center,
            style: Theme.of(
              context,
            ).textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted),
          ),
        ],
      ),
    );
  }
}

/// A dashed-border, centred placeholder card (reused for the empty state).
class DottedPlaceholder extends StatelessWidget {
  const DottedPlaceholder({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(vertical: 70, horizontal: 20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: BaseerahTokens.darkText.withValues(alpha: 0.14),
          width: 1.5,
        ),
      ),
      child: child,
    );
  }
}

/// Report header: avatar + name + request line, plus the "generate report"
/// action while no report has been generated yet.
class _DetailHeader extends ConsumerWidget {
  const _DetailHeader({required this.applicant, required this.state});

  final Applicant applicant;
  final BankDetailState state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final fmt = Fmt(Localizations.localeOf(context), l);
    final textTheme = Theme.of(context).textTheme;
    final canGenerate = state.phase == BankDetailPhase.empty;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 18),
      decoration: BoxDecoration(
        border: Border(
          bottom: BorderSide(
            color: BaseerahTokens.darkText.withValues(alpha: 0.06),
          ),
        ),
      ),
      child: Row(
        children: [
          BankAvatar(initials: applicant.initials),
          const SizedBox(width: 13),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  applicant.applicantName,
                  style: textTheme.titleMedium?.copyWith(
                    color: BaseerahTokens.darkText,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 1),
                Text(
                  '${applicant.purpose} · ${fmt.money(applicant.amount)}',
                  style: textTheme.bodySmall?.copyWith(
                    color: BaseerahTokens.muted,
                  ),
                ),
              ],
            ),
          ),
          if (canGenerate) ...[
            const SizedBox(width: 12),
            ElevatedButton(
              onPressed: () =>
                  ref.read(bankDetailProvider.notifier).generateReport(),
              child: Text(l.bankGenerateReport),
            ),
          ],
        ],
      ),
    );
  }
}

/// The header-below body: switches on the detail phase.
class _DetailBody extends StatelessWidget {
  const _DetailBody({required this.state});

  final BankDetailState state;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    switch (state.phase) {
      case BankDetailPhase.empty:
        return const SizedBox.shrink();
      case BankDetailPhase.generating:
        return _GeneratingBody(l: l);
      case BankDetailPhase.reportError:
        return _ReportError(l: l);
      case BankDetailPhase.report:
        return _ReportBody(state: state);
    }
  }
}

/// The `bsr-spin` (0.8 s) loader with "Analyzing 24-month telemetry…".
class _GeneratingBody extends StatelessWidget {
  const _GeneratingBody({required this.l});

  final AppLocalizations l;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 60, horizontal: 20),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(
            width: 44,
            height: 44,
            child: CircularProgressIndicator(
              strokeWidth: 3,
              color: BaseerahTokens.teal,
            ),
          ),
          const SizedBox(height: 16),
          Text(
            l.bankAnalyzing,
            style: Theme.of(
              context,
            ).textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted),
          ),
        ],
      ),
    );
  }
}

/// Inline error + retry when the report POST fails.
class _ReportError extends ConsumerWidget {
  const _ReportError({required this.l});

  final AppLocalizations l;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 48, horizontal: 20),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.error_outline, color: BaseerahTokens.alertRed),
          const SizedBox(height: 10),
          Text(
            l.loadError,
            style: Theme.of(
              context,
            ).textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted),
          ),
          const SizedBox(height: 12),
          OutlinedButton(
            onPressed: () =>
                ref.read(bankDetailProvider.notifier).generateReport(),
            child: Text(l.retry),
          ),
        ],
      ),
    );
  }
}

/// The live report: verdict panel + stamina box, three KPI boxes, the 12-month
/// cash-flow chart, Approve/Decline, and (once decided) a confirmation line.
class _ReportBody extends ConsumerWidget {
  const _ReportBody({required this.state});

  final BankDetailState state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final fmt = Fmt(Localizations.localeOf(context), l);
    final report = state.report!;
    final visuals = VerdictVisuals.of(report.verdict, l);
    final staminaColor = _bandColor(report.staminaScore);

    return SingleChildScrollView(
      padding: const EdgeInsets.all(22),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // ── Verdict panel + stamina box ─────────────────────────────────────
          IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Expanded(child: _VerdictPanel(visuals: visuals, l: l)),
                const SizedBox(width: 16),
                _StaminaBox(
                  score: report.staminaScore,
                  color: staminaColor,
                  l: l,
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),
          // ── Three KPI boxes ─────────────────────────────────────────────────
          Row(
            children: [
              Expanded(
                child: _KpiBox(
                  label: l.bankForecastDti,
                  value: fmt.percent(report.forecastDti),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _KpiBox(
                  label: l.bankIncomeStability,
                  value: fmt.percent(report.incomeStability),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _KpiBox(
                  label: l.bankDefaultProb,
                  value: fmt.percent(report.defaultProb12mo),
                  valueColor: staminaColor,
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),
          // ── 12-month cash-flow chart ────────────────────────────────────────
          _CashFlowCard(
            points: report.cashFlow,
            lineColor: visuals.accent,
            fmt: fmt,
            l: l,
          ),
          const SizedBox(height: 20),
          // ── Approve / Decline ───────────────────────────────────────────────
          _DecisionActions(state: state),
          if (state.decision != null) ...[
            const SizedBox(height: 14),
            _DecisionConfirmation(decision: state.decision!, l: l),
          ],
        ],
      ),
    );
  }
}

/// The coloured verdict panel (gradient by verdict): headline title + subtitle.
class _VerdictPanel extends StatelessWidget {
  const _VerdictPanel({required this.visuals, required this.l});

  final VerdictVisuals visuals;
  final AppLocalizations l;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        gradient: visuals.gradient,
        borderRadius: BorderRadius.circular(14),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l.bankUnderwritingVerdict,
            style: const TextStyle(color: Colors.white70, fontSize: 12),
          ),
          const SizedBox(height: 6),
          Text(
            visuals.title,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 19,
              fontWeight: FontWeight.w700,
              height: 1.25,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            visuals.subtitle,
            style: const TextStyle(
              color: Colors.white70,
              fontSize: 12,
              height: 1.4,
            ),
          ),
        ],
      ),
    );
  }
}

/// The stamina box: the 0–100 cash-flow stamina score, coloured by its band.
class _StaminaBox extends StatelessWidget {
  const _StaminaBox({
    required this.score,
    required this.color,
    required this.l,
  });

  final int score;
  final Color color;
  final AppLocalizations l;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 150,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: BaseerahTokens.lightBg,
        borderRadius: BorderRadius.circular(14),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(
            l.bankStaminaScore,
            textAlign: TextAlign.center,
            style: const TextStyle(color: BaseerahTokens.muted, fontSize: 11.5),
          ),
          const SizedBox(height: 2),
          Text(
            '$score',
            style: TextStyle(
              color: color,
              fontSize: 40,
              fontWeight: FontWeight.w700,
            ),
          ),
          Text(
            l.bankOutOf100,
            style: const TextStyle(color: BaseerahTokens.muted, fontSize: 10.5),
          ),
        ],
      ),
    );
  }
}

/// One KPI box (label + value on the light-beige fill).
class _KpiBox extends StatelessWidget {
  const _KpiBox({required this.label, required this.value, this.valueColor});

  final String label;
  final String value;
  final Color? valueColor;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: BaseerahTokens.lightBg,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: const TextStyle(color: BaseerahTokens.muted, fontSize: 11),
          ),
          const SizedBox(height: 3),
          Text(
            value,
            style: TextStyle(
              color: valueColor ?? BaseerahTokens.darkText,
              fontSize: 20,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}

/// The Approve / Decline action row. Approve is disabled for a BAD verdict and
/// while a decision is in flight; the pressed button shows progress.
class _DecisionActions extends ConsumerWidget {
  const _DecisionActions({required this.state});

  final BankDetailState state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final decided = state.decision != null;
    final approveEnabled = !decided && !state.deciding && !state.approveDisabled;
    final declineEnabled = !decided && !state.deciding;

    Future<void> decide(Decision d) async {
      final ok = await ref.read(bankDetailProvider.notifier).decide(d);
      if (!ok && context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l.bankDecisionError)),
        );
      }
    }

    final deciding = state.deciding;
    return Row(
      children: [
        Expanded(
          child: ElevatedButton(
            onPressed: approveEnabled ? () => decide(Decision.approve) : null,
            style: ElevatedButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 13),
            ),
            child: deciding && state.decision == null
                ? const _BtnSpinner(color: Colors.white)
                : Text(l.bankApprove),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: OutlinedButton(
            onPressed: declineEnabled ? () => decide(Decision.decline) : null,
            style: OutlinedButton.styleFrom(
              foregroundColor: BaseerahTokens.alertRed,
              padding: const EdgeInsets.symmetric(vertical: 13),
              side: BorderSide(
                color: BaseerahTokens.alertRed.withValues(alpha: 0.4),
                width: 1.5,
              ),
            ),
            child: Text(l.bankDecline),
          ),
        ),
      ],
    );
  }
}

/// Small in-button progress spinner.
class _BtnSpinner extends StatelessWidget {
  const _BtnSpinner({required this.color});

  final Color color;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 18,
      height: 18,
      child: CircularProgressIndicator(strokeWidth: 2, color: color),
    );
  }
}

/// The persisted-decision confirmation line (green for approved, red-ish for
/// declined), shown once a decision is recorded.
class _DecisionConfirmation extends StatelessWidget {
  const _DecisionConfirmation({required this.decision, required this.l});

  final Decision decision;
  final AppLocalizations l;

  @override
  Widget build(BuildContext context) {
    final approved = decision == Decision.approve;
    return Center(
      child: Text(
        approved ? l.bankApprovedMsg : l.bankDeclinedMsg,
        textAlign: TextAlign.center,
        style: TextStyle(
          color: approved ? BaseerahTokens.successGreen : BaseerahTokens.alertRed,
          fontSize: 13,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

/// The 12-month projected free-cash-flow chart card.
class _CashFlowCard extends StatelessWidget {
  const _CashFlowCard({
    required this.points,
    required this.lineColor,
    required this.fmt,
    required this.l,
  });

  final List<CashFlowPoint> points;
  final Color lineColor;
  final Fmt fmt;
  final AppLocalizations l;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: BaseerahTokens.lightBg,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l.bankCashflow12,
            style: const TextStyle(
              color: BaseerahTokens.muted,
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 10),
          SizedBox(
            width: double.infinity,
            height: 130,
            child: points.isEmpty
                ? Center(
                    child: Text(
                      l.forecastComingSoon,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: BaseerahTokens.muted,
                      ),
                    ),
                  )
                : CustomPaint(
                    size: Size.infinite,
                    painter: _CashFlowPainter(
                      balances: points
                          .map((p) => p.balance)
                          .toList(growable: false),
                      lineColor: lineColor,
                      isRtl: Directionality.of(context) == TextDirection.rtl,
                      textDirection: Directionality.of(context),
                      endLabel: fmt.money(points.last.balance),
                      labelStyle:
                          Theme.of(context).textTheme.labelSmall?.copyWith(
                            fontWeight: FontWeight.w600,
                          ) ??
                          const TextStyle(fontSize: 11),
                    ),
                  ),
          ),
        ],
      ),
    );
  }
}

/// A compact projected-balance line chart: gradient fill under a verdict-coloured
/// line, a dashed zero-line, and a trailing end-of-year value label. Mirrors for
/// RTL. Pure geometry — all colours/labels are passed in.
class _CashFlowPainter extends CustomPainter {
  _CashFlowPainter({
    required this.balances,
    required this.lineColor,
    required this.isRtl,
    required this.textDirection,
    required this.endLabel,
    required this.labelStyle,
  });

  final List<double> balances;
  final Color lineColor;
  final bool isRtl;
  final TextDirection textDirection;
  final String endLabel;
  final TextStyle labelStyle;

  static const double _padX = 8;
  static const double _padT = 20;
  static const double _padB = 10;

  @override
  void paint(Canvas canvas, Size size) {
    final n = balances.length;
    if (n == 0) return;

    final plotLeft = _padX;
    final plotRight = size.width - _padX;
    final plotTop = _padT;
    final plotBottom = size.height - _padB;
    final plotW = (plotRight - plotLeft) <= 0 ? 1.0 : plotRight - plotLeft;
    final plotH = (plotBottom - plotTop) <= 0 ? 1.0 : plotBottom - plotTop;

    var lo = 0.0;
    var hi = 0.0;
    for (final b in balances) {
      if (b < lo) lo = b;
      if (b > hi) hi = b;
    }
    if (hi == lo) hi = lo + 1;
    hi += (hi - lo) * 0.08;

    double xFor(int i) {
      final frac = n == 1 ? 0.0 : i / (n - 1);
      return isRtl ? plotLeft + plotW * (1 - frac) : plotLeft + plotW * frac;
    }

    double yFor(double v) => plotTop + plotH * (1 - (v - lo) / (hi - lo));

    final linePath = Path();
    for (var i = 0; i < n; i++) {
      final dx = xFor(i);
      final dy = yFor(balances[i]);
      i == 0 ? linePath.moveTo(dx, dy) : linePath.lineTo(dx, dy);
    }

    final baselineY = yFor(lo);
    final fillPath = Path.from(linePath)
      ..lineTo(xFor(n - 1), baselineY)
      ..lineTo(xFor(0), baselineY)
      ..close();
    canvas.drawPath(
      fillPath,
      Paint()
        ..shader = LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            lineColor.withValues(alpha: 0.28),
            lineColor.withValues(alpha: 0.02),
          ],
        ).createShader(Rect.fromLTRB(plotLeft, plotTop, plotRight, plotBottom)),
    );

    if (lo <= 0 && 0 <= hi) {
      final zeroY = yFor(0);
      _dashed(
        canvas,
        Offset(plotLeft, zeroY),
        Offset(plotRight, zeroY),
        Paint()
          ..color = BaseerahTokens.muted.withValues(alpha: 0.5)
          ..strokeWidth = 1,
      );
    }

    canvas.drawPath(
      linePath,
      Paint()
        ..color = lineColor
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2.5
        ..strokeJoin = StrokeJoin.round
        ..strokeCap = StrokeCap.round,
    );
    canvas.drawCircle(
      Offset(xFor(0), yFor(balances[0])),
      3,
      Paint()..color = lineColor,
    );

    final tp = TextPainter(
      text: TextSpan(text: endLabel, style: labelStyle.copyWith(color: lineColor)),
      textDirection: textDirection,
    )..layout();
    var dx = xFor(n - 1) - tp.width / 2;
    final maxX = plotRight - tp.width;
    if (dx < plotLeft) dx = plotLeft;
    if (dx > maxX) dx = maxX < plotLeft ? plotLeft : maxX;
    tp.paint(canvas, Offset(dx, 0));
  }

  void _dashed(Canvas canvas, Offset from, Offset to, Paint paint) {
    const dash = 4.0, gap = 3.0;
    final total = (to - from).distance;
    if (total == 0) return;
    final dir = (to - from) / total;
    var drawn = 0.0;
    while (drawn < total) {
      final segEnd = (drawn + dash) > total ? total : drawn + dash;
      canvas.drawLine(from + dir * drawn, from + dir * segEnd, paint);
      drawn += dash + gap;
    }
  }

  @override
  bool shouldRepaint(_CashFlowPainter old) =>
      old.balances != balances ||
      old.lineColor != lineColor ||
      old.isRtl != isRtl ||
      old.endLabel != endLabel;
}

/// Per-verdict presentation for the report's verdict panel (DESIGN §7.5): the
/// gradient background, headline title, subtitle, and accent colour that also
/// tints the cash-flow line. Green/gold/red map to the OK/WARN/BAD verdicts —
/// consistent with the zone colours used across the app. All colours from the
/// single theme token file.
class VerdictVisuals {
  const VerdictVisuals._(this.gradient, this.title, this.subtitle, this.accent);

  final Gradient gradient;
  final String title;
  final String subtitle;
  final Color accent;

  factory VerdictVisuals.of(Verdict verdict, AppLocalizations l) {
    return switch (verdict) {
      Verdict.ok => VerdictVisuals._(
        BaseerahTokens.tealGradient,
        l.bankVerdictOkTitle,
        l.bankVerdictOkSub,
        BaseerahTokens.successGreen,
      ),
      Verdict.warn => VerdictVisuals._(
        BaseerahTokens.goldGradient,
        l.bankVerdictWarnTitle,
        l.bankVerdictWarnSub,
        BaseerahTokens.warningOrange,
      ),
      Verdict.bad => VerdictVisuals._(
        BaseerahTokens.redWarningGradient,
        l.bankVerdictBadTitle,
        l.bankVerdictBadSub,
        BaseerahTokens.alertRed,
      ),
    };
  }
}

/// Map a 0–100 stamina score to its band colour (DESIGN §5.1 zone thresholds):
/// ≥70 OPTIMAL (green), 40–69 WARNING (orange), <40 CRITICAL (red). This is a
/// presentational band mapping, not a re-computation of the server's report.
Color _bandColor(int stamina) {
  if (stamina >= 70) return BaseerahTokens.successGreen;
  if (stamina >= 40) return BaseerahTokens.warningOrange;
  return BaseerahTokens.alertRed;
}
