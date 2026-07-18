import 'dart:math' as math;

import 'format.dart';

/// A value axis fitted to a data series: the domain to plot, and the values to
/// draw gridlines at.
///
/// Framework-free and label-free on purpose — it computes geometry and hands the
/// [scale]/[fractionDigits] a caller needs to render the ticks as a coherent set
/// via [Fmt.compactTick]. Keeping it out of the painter is what makes the tick
/// arithmetic testable without a canvas.
class ValueAxis {
  const ValueAxis({
    required this.lo,
    required this.hi,
    required this.step,
    required this.scale,
    required this.fractionDigits,
    required this.values,
  });

  /// Bottom of the plotted domain, snapped down to a whole [step].
  final double lo;

  /// Top of the plotted domain, snapped up to a whole [step].
  final double hi;

  /// Distance between adjacent ticks — always a 1/2/5×10ⁿ value.
  final double step;

  /// Magnitude every tick in this set is rendered at.
  final AxisScale scale;

  /// Decimals every tick in this set is rendered with.
  final int fractionDigits;

  /// The value at each gridline, from [lo] to [hi] inclusive.
  final List<double> values;

  /// Fits an axis to [data].
  ///
  /// When [includeZero] the domain is stretched to keep zero in view; otherwise
  /// it hugs the data. That choice belongs to the caller because it is editorial,
  /// not arithmetic: zero anchors a cash-flow story that crosses it, and wastes
  /// the plot on one that never approaches it.
  factory ValueAxis.fit(
    List<double> data, {
    required bool includeZero,
    int targetTicks = 4,
  }) {
    assert(data.isNotEmpty, 'cannot fit an axis to an empty series');
    assert(targetTicks > 0, 'targetTicks must be positive');

    var lo = data.first;
    var hi = data.first;
    for (final v in data) {
      if (v < lo) lo = v;
      if (v > hi) hi = v;
    }
    if (includeZero) {
      if (lo > 0) lo = 0;
      if (hi < 0) hi = 0;
    }

    // A dead-flat series has no span to divide. Give it a token one, opening it
    // away from zero when zero is pinned so the baseline stays put.
    if (hi == lo) {
      final pad = math.max(hi.abs() * 0.02, 1.0);
      hi += pad;
      if (!includeZero || lo < 0) lo -= pad;
    }

    final step = _niceStep((hi - lo) / targetTicks);
    final niceLo = (lo / step).floorToDouble() * step;
    final niceHi = (hi / step).ceilToDouble() * step;

    // Scale from the step as well as the magnitude: a tight domain high up the
    // number line needs a finer scale than its magnitude alone implies.
    final scale = AxisScale.forDomainAndStep(niceLo, niceHi, step);

    // Step by index rather than accumulating `+= step`: float drift would push
    // the top tick off the domain and drop or duplicate a gridline.
    final count = ((niceHi - niceLo) / step).round();
    final values = [for (var i = 0; i <= count; i++) niceLo + step * i];

    return ValueAxis(
      lo: niceLo,
      hi: niceHi,
      step: step,
      scale: scale,
      fractionDigits: scale.fractionDigitsFor(step),
      values: values,
    );
  }

  /// Rounds [raw] up to the nearest 1/2/5×10ⁿ, so gridlines land on numbers a
  /// reader can subtract in their head instead of on the raw span ÷ tick count.
  static double _niceStep(double raw) {
    if (raw <= 0) return 1;
    final exponent =
        math.pow(10, (math.log(raw) / math.ln10).floor()).toDouble();
    final fraction = raw / exponent;
    final nice = fraction <= 1
        ? 1.0
        : fraction <= 2
        ? 2.0
        : fraction <= 5
        ? 5.0
        : 10.0;
    return nice * exponent;
  }
}
