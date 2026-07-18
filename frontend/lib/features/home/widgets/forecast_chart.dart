import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/chart_axis.dart';
import '../../../core/format.dart';
import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../data/forecast.dart';
import '../state/home_providers.dart';

/// Live 30-day cash-flow forecast chart (DESIGN §7.1/§8), replacing the Step 2.3
/// placeholder. Watches [forecastProvider] and renders a titled card whose body
/// is a hand-painted line chart: a gradient fill under the line, a labelled
/// value axis, a dated time axis, and — when the projection crosses zero — a red
/// deficit marker at `deficitDate`. The line colour follows the API `trend` (all
/// colours from the single theme token file). Amounts are formatted with
/// [Fmt.money] (`SAR`/`ر.س`) and the time axis mirrors for RTL/Arabic, which
/// carries the value axis to the trailing edge with it.
///
/// Degrades per section like the rest of Home: a failed call shows an inline
/// retry in the chart slot without taking down the screen.
class ForecastChart extends ConsumerWidget {
  const ForecastChart({super.key});

  // Taller than the pre-axis chart: the time axis now claims a label row along
  // the bottom, and the plot itself should not pay for it.
  static const double _chartHeight = 180;

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

/// One horizontal gridline: the value it sits at and its pre-formatted label.
class _YTick {
  const _YTick(this.value, this.label);

  final double value;
  final String label;
}

/// One time-axis label: the series index it sits under and its formatted date.
class _XTick {
  const _XTick(this.index, this.label);

  final int index;
  final String label;
}

/// The painted chart for a loaded [Forecast]. Resolves the axes here — where
/// [Fmt] and the locale are in scope — and hands [_ForecastPainter] nothing but
/// geometry and ready-made strings.
class _ChartBody extends StatelessWidget {
  const _ChartBody({required this.forecast, required this.fmt, required this.l});

  final Forecast forecast;
  final Fmt fmt;
  final AppLocalizations l;

  /// Roughly how many gridlines to aim for; the nice-step rounding decides the
  /// exact count.
  static const int _targetYTicks = 4;

  /// How many dates to label. Four spans a 30-day horizon at ~10-day intervals
  /// without the labels colliding inside a phone-width card.
  static const int _targetXTicks = 4;

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

    // Zero earns its place on the axis only when it is part of the story: a
    // projected deficit, or a balance already under water. Forcing it in
    // otherwise is what flattened this chart — a healthy 2.2M balance plotted
    // against a 0-based axis is a straight line pinned to the top tick with
    // ~90% of the card empty, and axis labels would only advertise it.
    final yAxis = ValueAxis.fit(
      balances,
      includeZero: deficitIndex != null || balances.any((b) => b <= 0),
      targetTicks: _targetYTicks,
    );
    final yTicks = [
      for (final v in yAxis.values)
        _YTick(
          v,
          fmt.compactTick(
            v,
            scale: yAxis.scale,
            fractionDigits: yAxis.fractionDigits,
          ),
        ),
    ];

    return CustomPaint(
      size: Size.infinite,
      painter: _ForecastPainter(
        balances: balances,
        lineColor: forecast.trend.color,
        deficitIndex: deficitIndex,
        isRtl: isRtl,
        textDirection: Directionality.of(context),
        labelStyle: labelStyle,
        yLo: yAxis.lo,
        yHi: yAxis.hi,
        yTicks: yTicks,
        xTicks: _buildXTicks(points, fmt),
        endLabel: fmt.money(balances.last),
        deficitLabel: deficitIndex == null
            ? null
            : fmt.money(forecast.minProjectedBalance),
      ),
    );
  }

  /// Picks the dates to label, evenly spread and always including both ends —
  /// today and the end of the horizon are the two a reader looks for.
  static List<_XTick> _buildXTicks(List<ForecastPoint> points, Fmt fmt) {
    final n = points.length;
    if (n <= _targetXTicks) {
      return [
        for (var i = 0; i < n; i++) _XTick(i, fmt.axisDate(points[i].date)),
      ];
    }
    return [
      for (var t = 0; t < _targetXTicks; t++)
        () {
          final i = ((n - 1) * t / (_targetXTicks - 1)).round();
          return _XTick(i, fmt.axisDate(points[i].date));
        }(),
    ];
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

/// Paints the projected-balance line, its gradient fill, the value/time axes and
/// (optionally) a deficit marker. Pure geometry: all colours, domains and label
/// strings are passed in so it stays free of theme/l10n lookups.
class _ForecastPainter extends CustomPainter {
  _ForecastPainter({
    required this.balances,
    required this.lineColor,
    required this.deficitIndex,
    required this.isRtl,
    required this.textDirection,
    required this.labelStyle,
    required this.yLo,
    required this.yHi,
    required this.yTicks,
    required this.xTicks,
    required this.endLabel,
    required this.deficitLabel,
  });

  final List<double> balances;
  final Color lineColor;
  final int? deficitIndex;
  final bool isRtl;
  final TextDirection textDirection;
  final TextStyle labelStyle;
  final double yLo;
  final double yHi;
  final List<_YTick> yTicks;
  final List<_XTick> xTicks;
  final String endLabel;
  final String? deficitLabel;

  // Plot insets: headroom on top for the trailing value label, a row below for
  // the dated time axis, and a gutter for the value labels. The gutter sits on
  // the trailing edge — right under Arabic — so it never lands where the series
  // starts.
  static const double _padEdge = 8;
  static const double _padT = 20;
  static const double _padB = 22;
  // Wide enough for the longest tick a scale can produce ("2.225 M" / "2.225 م").
  static const double _yGutter = 54;
  static const double _labelGap = 6;

  @override
  void paint(Canvas canvas, Size size) {
    final n = balances.length;
    if (n == 0) return;

    final double plotLeft = isRtl ? _padEdge : _yGutter;
    final double plotRight = isRtl ? size.width - _yGutter : size.width - _padEdge;
    final double plotTop = _padT;
    final double plotBottom = size.height - _padB;
    final double plotW = (plotRight - plotLeft) <= 0 ? 1.0 : plotRight - plotLeft;
    final double plotH = (plotBottom - plotTop) <= 0 ? 1.0 : plotBottom - plotTop;

    final lo = yLo;
    final hi = yHi == yLo ? yLo + 1 : yHi;

    double xFor(int i) {
      final frac = n == 1 ? 0.0 : i / (n - 1);
      return isRtl ? plotLeft + plotW * (1 - frac) : plotLeft + plotW * frac;
    }

    double yFor(double v) => plotTop + plotH * (1 - (v - lo) / (hi - lo));

    // ── Value axis: gridline + label per tick ────────────────────────────────
    final gridPaint = Paint()
      ..color = BaseerahTokens.muted.withValues(alpha: 0.14)
      ..strokeWidth = 1;
    for (final tick in yTicks) {
      final y = yFor(tick.value);
      // Zero keeps its dashed emphasis — it is a threshold, not a gridline.
      if (tick.value == 0) {
        _drawDashedLine(
          canvas,
          Offset(plotLeft, y),
          Offset(plotRight, y),
          Paint()
            ..color = BaseerahTokens.muted.withValues(alpha: 0.5)
            ..strokeWidth = 1,
        );
      } else {
        canvas.drawLine(Offset(plotLeft, y), Offset(plotRight, y), gridPaint);
      }
      _paintTickLabel(
        canvas,
        tick.label,
        centerY: y,
        plotLeft: plotLeft,
        plotRight: plotRight,
        size: size,
      );
    }

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

    // ── Time axis: baseline + dated labels ───────────────────────────────────
    canvas.drawLine(
      Offset(plotLeft, plotBottom),
      Offset(plotRight, plotBottom),
      Paint()
        ..color = BaseerahTokens.muted.withValues(alpha: 0.3)
        ..strokeWidth = 1,
    );
    for (final tick in xTicks) {
      _paintLabel(
        canvas,
        tick.label,
        anchorX: xFor(tick.index),
        top: plotBottom + 4,
        color: BaseerahTokens.muted,
        plotLeft: plotLeft,
        plotRight: plotRight,
      );
    }

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
      final markerY = yFor(balances[di]);
      canvas.drawCircle(
        Offset(markerX, markerY),
        4,
        Paint()..color = BaseerahTokens.alertRed,
      );
      final label = deficitLabel;
      if (label != null) {
        // Beside the marker at the top of the plot — not centred on it (the
        // dashed rule would run through the text) and not on the dot (this is
        // the horizon's lowest balance, not the balance on the crossing day, so
        // sitting it on the crossing misreads as the value there). The top strip
        // is free: a series that reaches zero has already fallen away from it.
        _paintMarkerLabel(
          canvas,
          label,
          markerX: markerX,
          top: plotTop + 2,
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

  /// Paint a value-axis [text] in the gutter, vertically centred on [centerY]
  /// and hugging the plot edge from outside.
  void _paintTickLabel(
    Canvas canvas,
    String text, {
    required double centerY,
    required double plotLeft,
    required double plotRight,
    required Size size,
  }) {
    final tp = TextPainter(
      text: TextSpan(text: text, style: labelStyle),
      textDirection: textDirection,
    )..layout();
    final double dx = isRtl
        ? plotRight + _labelGap
        : plotLeft - _labelGap - tp.width;
    final double dy = (centerY - tp.height / 2).clamp(0.0, size.height - tp.height);
    tp.paint(canvas, Offset(dx, dy));
  }

  /// Paint [text] alongside a vertical marker at [markerX], on the trailing side
  /// by default and flipped to the leading side when that would overflow the
  /// plot.
  void _paintMarkerLabel(
    Canvas canvas,
    String text, {
    required double markerX,
    required double top,
    required Color color,
    required double plotLeft,
    required double plotRight,
  }) {
    final tp = TextPainter(
      text: TextSpan(text: text, style: labelStyle.copyWith(color: color)),
      textDirection: textDirection,
    )..layout();
    final double trailing =
        isRtl ? markerX - _labelGap - tp.width : markerX + _labelGap;
    final double leading =
        isRtl ? markerX + _labelGap : markerX - _labelGap - tp.width;
    var dx = trailing;
    if (dx < plotLeft || dx + tp.width > plotRight) dx = leading;
    final double maxX = plotRight - tp.width;
    if (maxX < plotLeft) {
      dx = plotLeft;
    } else if (dx < plotLeft) {
      dx = plotLeft;
    } else if (dx > maxX) {
      dx = maxX;
    }
    tp.paint(canvas, Offset(dx, top));
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
        old.yLo != yLo ||
        old.yHi != yHi ||
        old.yTicks != yTicks ||
        old.xTicks != xTicks ||
        old.endLabel != endLabel ||
        old.deficitLabel != deficitLabel;
  }
}
