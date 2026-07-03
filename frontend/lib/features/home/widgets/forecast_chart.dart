import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format.dart';
import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../data/forecast.dart';
import '../state/home_providers.dart';

/// Live 30-day cash-flow forecast chart (DESIGN §7.1/§8), replacing the Step 2.3
/// placeholder. Watches [forecastProvider] and renders a titled card whose body
/// is a hand-painted line chart: a gradient fill under the line, a dashed
/// zero-line, and — when the projection crosses zero — a red deficit marker at
/// `deficitDate`. The line colour follows the API `trend` (all colours from the
/// single theme token file). Amounts are formatted with `Fmt.money`
/// (`SAR`/`ر.س`) and the time axis mirrors for RTL/Arabic.
///
/// Degrades per section like the rest of Home: a failed call shows an inline
/// retry in the chart slot without taking down the screen.
class ForecastChart extends ConsumerWidget {
  const ForecastChart({super.key});

  static const double _chartHeight = 150;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final fmt = Fmt(Localizations.localeOf(context), l);
    final textTheme = Theme.of(context).textTheme;
    final forecast = ref.watch(forecastProvider);

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
        boxShadow: BaseerahTokens.shadowSoft,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l.forecast30Title,
            style: textTheme.titleMedium?.copyWith(
              color: BaseerahTokens.darkText,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            height: _chartHeight,
            child: forecast.when(
              data: (data) => _ChartBody(forecast: data, fmt: fmt, l: l),
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (_, __) => _ChartError(
                l: l,
                onRetry: () => ref.invalidate(currentClientProvider),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// The painted chart for a loaded [Forecast]. Prepares colours + formatted
/// labels, then hands geometry to [_ForecastPainter].
class _ChartBody extends StatelessWidget {
  const _ChartBody({required this.forecast, required this.fmt, required this.l});

  final Forecast forecast;
  final Fmt fmt;
  final AppLocalizations l;

  @override
  Widget build(BuildContext context) {
    final points = forecast.points;
    if (points.isEmpty) {
      return Center(
        child: Text(
          l.forecastComingSoon,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
            color: BaseerahTokens.muted,
          ),
        ),
      );
    }

    final isRtl = Directionality.of(context) == TextDirection.rtl;
    final balances = points.map((p) => p.balance).toList(growable: false);

    // The deficit marker sits on the first projected day the balance goes
    // negative; map that date to its series index.
    int? deficitIndex;
    final deficitDate = forecast.deficitDate;
    if (deficitDate != null) {
      final idx = points.indexWhere((p) => !p.date.isBefore(deficitDate));
      deficitIndex = idx < 0 ? points.length - 1 : idx;
    }

    final labelStyle = Theme.of(context).textTheme.labelSmall?.copyWith(
      color: BaseerahTokens.muted,
      fontWeight: FontWeight.w600,
    ) ?? const TextStyle(fontSize: 11);

    return CustomPaint(
      size: Size.infinite,
      painter: _ForecastPainter(
        balances: balances,
        lineColor: forecast.trend.color,
        deficitIndex: deficitIndex,
        isRtl: isRtl,
        textDirection: Directionality.of(context),
        labelStyle: labelStyle,
        endLabel: fmt.money(balances.last),
        deficitLabel: deficitIndex == null
            ? null
            : fmt.money(forecast.minProjectedBalance),
      ),
    );
  }
}

/// Inline error + retry for the chart slot — keeps the rest of Home usable.
class _ChartError extends StatelessWidget {
  const _ChartError({required this.l, required this.onRetry});

  final AppLocalizations l;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        const Icon(Icons.show_chart, color: BaseerahTokens.muted, size: 24),
        const SizedBox(height: 6),
        Text(
          l.loadError,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
            color: BaseerahTokens.muted,
          ),
        ),
        const SizedBox(height: 6),
        OutlinedButton(onPressed: onRetry, child: Text(l.retry)),
      ],
    );
  }
}

/// Paints the projected-balance line, its gradient fill, a dashed zero-line and
/// (optionally) a deficit marker. Pure geometry: all colours/labels are passed
/// in so it stays free of theme/l10n lookups.
class _ForecastPainter extends CustomPainter {
  _ForecastPainter({
    required this.balances,
    required this.lineColor,
    required this.deficitIndex,
    required this.isRtl,
    required this.textDirection,
    required this.labelStyle,
    required this.endLabel,
    required this.deficitLabel,
  });

  final List<double> balances;
  final Color lineColor;
  final int? deficitIndex;
  final bool isRtl;
  final TextDirection textDirection;
  final TextStyle labelStyle;
  final String endLabel;
  final String? deficitLabel;

  // Plot insets: headroom on top for the trailing value label, a little below
  // for the deficit dot/label.
  static const double _padL = 8;
  static const double _padR = 8;
  static const double _padT = 20;
  static const double _padB = 18;

  @override
  void paint(Canvas canvas, Size size) {
    final n = balances.length;
    if (n == 0) return;

    final double plotLeft = _padL;
    final double plotRight = size.width - _padR;
    final double plotTop = _padT;
    final double plotBottom = size.height - _padB;
    final double plotW = (plotRight - plotLeft) <= 0 ? 1.0 : plotRight - plotLeft;
    final double plotH = (plotBottom - plotTop) <= 0 ? 1.0 : plotBottom - plotTop;

    // Y domain always includes zero so the zero-line is meaningful, with a touch
    // of headroom above the peak.
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

    // ── Line path ────────────────────────────────────────────────────────────
    final linePath = Path();
    for (var i = 0; i < n; i++) {
      final dx = xFor(i);
      final dy = yFor(balances[i]);
      if (i == 0) {
        linePath.moveTo(dx, dy);
      } else {
        linePath.lineTo(dx, dy);
      }
    }

    // ── Gradient fill under the line ─────────────────────────────────────────
    final baselineY = yFor(lo);
    final fillPath = Path.from(linePath)
      ..lineTo(xFor(n - 1), baselineY)
      ..lineTo(xFor(0), baselineY)
      ..close();
    final fillPaint = Paint()
      ..shader = LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [
          lineColor.withValues(alpha: 0.28),
          lineColor.withValues(alpha: 0.02),
        ],
      ).createShader(Rect.fromLTRB(plotLeft, plotTop, plotRight, plotBottom));
    canvas.drawPath(fillPath, fillPaint);

    // ── Dashed zero-line (only when zero is inside the visible domain) ───────
    if (lo <= 0 && 0 <= hi) {
      final zeroY = yFor(0);
      _drawDashedLine(
        canvas,
        Offset(plotLeft, zeroY),
        Offset(plotRight, zeroY),
        Paint()
          ..color = BaseerahTokens.muted.withValues(alpha: 0.5)
          ..strokeWidth = 1,
      );
    }

    // ── The line itself, coloured by trend ───────────────────────────────────
    canvas.drawPath(
      linePath,
      Paint()
        ..color = lineColor
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2.5
        ..strokeJoin = StrokeJoin.round
        ..strokeCap = StrokeCap.round,
    );

    // Leading dot at the current balance.
    canvas.drawCircle(
      Offset(xFor(0), yFor(balances[0])),
      3,
      Paint()..color = lineColor,
    );

    // ── Deficit marker ───────────────────────────────────────────────────────
    final di = deficitIndex;
    if (di != null && di >= 0 && di < n) {
      final markerX = xFor(di);
      _drawDashedLine(
        canvas,
        Offset(markerX, plotTop),
        Offset(markerX, plotBottom),
        Paint()
          ..color = BaseerahTokens.alertRed
          ..strokeWidth = 1.5,
      );
      canvas.drawCircle(
        Offset(markerX, yFor(balances[di])),
        4,
        Paint()..color = BaseerahTokens.alertRed,
      );
      final label = deficitLabel;
      if (label != null) {
        _paintLabel(
          canvas,
          label,
          anchorX: markerX,
          top: plotBottom + 2,
          color: BaseerahTokens.alertRed,
          plotLeft: plotLeft,
          plotRight: plotRight,
        );
      }
    }

    // ── Trailing projected-balance label ─────────────────────────────────────
    _paintLabel(
      canvas,
      endLabel,
      anchorX: xFor(n - 1),
      top: 0,
      color: lineColor,
      plotLeft: plotLeft,
      plotRight: plotRight,
    );
  }

  /// Paint [text] centred on [anchorX] at vertical [top], clamped inside the
  /// plot so edge labels never clip.
  void _paintLabel(
    Canvas canvas,
    String text, {
    required double anchorX,
    required double top,
    required Color color,
    required double plotLeft,
    required double plotRight,
  }) {
    final tp = TextPainter(
      text: TextSpan(text: text, style: labelStyle.copyWith(color: color)),
      textDirection: textDirection,
    )..layout();
    final double maxX = plotRight - tp.width;
    double dx = anchorX - tp.width / 2;
    if (maxX < plotLeft) {
      dx = plotLeft;
    } else if (dx < plotLeft) {
      dx = plotLeft;
    } else if (dx > maxX) {
      dx = maxX;
    }
    tp.paint(canvas, Offset(dx, top));
  }

  /// Draw a dashed straight line between [from] and [to] (used for the zero-line
  /// and the deficit marker).
  void _drawDashedLine(Canvas canvas, Offset from, Offset to, Paint paint) {
    const dash = 4.0;
    const gap = 3.0;
    final double total = (to - from).distance;
    if (total == 0) return;
    final Offset dir = (to - from) / total;
    double drawn = 0.0;
    while (drawn < total) {
      final double segEnd = (drawn + dash) > total ? total : drawn + dash;
      canvas.drawLine(from + dir * drawn, from + dir * segEnd, paint);
      drawn += dash + gap;
    }
  }

  @override
  bool shouldRepaint(_ForecastPainter old) {
    return old.balances != balances ||
        old.lineColor != lineColor ||
        old.deficitIndex != deficitIndex ||
        old.isRtl != isRtl ||
        old.endLabel != endLabel ||
        old.deficitLabel != deficitLabel;
  }
}
