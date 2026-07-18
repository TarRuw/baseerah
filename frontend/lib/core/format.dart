import 'dart:math' as math;
import 'dart:ui';

import 'package:intl/intl.dart';

import '../l10n/app_localizations.dart';

/// The magnitude a chart axis renders its ticks in.
///
/// One scale is chosen for the whole tick set rather than per value — see
/// [Fmt.compactTick] for why that matters.
enum AxisScale {
  ones(1),
  thousands(1000),
  millions(1000000);

  const AxisScale(this.divisor);

  /// Divisor applied to a value before it is formatted.
  final num divisor;

  /// The most decimals a tick may carry before the scale itself is too coarse
  /// and should step down. Past this the labels are wider than the magnitude
  /// suffix saves.
  static const int maxFractionDigits = 3;

  /// The scale a `[lo, hi]` domain's magnitude suggests. Picked from the widest
  /// bound so every tick in the set shares one divisor.
  ///
  /// Prefer [forDomainAndStep], which also accounts for how fine the ticks are.
  static AxisScale forDomain(double lo, double hi) {
    final widest = math.max(lo.abs(), hi.abs());
    if (widest >= 1000000) return AxisScale.millions;
    if (widest >= 1000) return AxisScale.thousands;
    return AxisScale.ones;
  }

  /// The scale that renders a `[lo, hi]` domain stepped by [step] both compactly
  /// *and* distinctly.
  ///
  /// Magnitude alone is not enough: a 2.225M–2.240M domain is millions-scale by
  /// magnitude, but its 5,000 step is 0.005 of a million — three decimals just to
  /// tell one tick from the next. Where even [maxFractionDigits] cannot separate
  /// them, a coarser scale is the wrong trade and this steps down instead.
  static AxisScale forDomainAndStep(double lo, double hi, double step) {
    var scale = forDomain(lo, hi);
    while (scale.fractionDigitsFor(step) > maxFractionDigits && scale.index > 0) {
      scale = AxisScale.values[scale.index - 1];
    }
    return scale;
  }

  /// Decimals needed to keep ticks [step] apart distinct at this scale.
  ///
  /// Exactly `ceil(-log10(step / divisor))`: adjacent ticks differ by one step,
  /// so the last rendered decimal must be no coarser than the step itself. A
  /// 10,000 step under [millions] is 0.01 — two decimals; a 5,000 step is 0.005
  /// — three. Rounding to a fixed guess is what silently collapses an axis to
  /// four identical labels.
  int fractionDigitsFor(double step) {
    final scaled = (step / divisor).abs();
    if (scaled <= 0) return 0;
    // The epsilon keeps an exact power of ten (log10(0.1) == -1.0000000000000002)
    // from rounding up to a spurious extra decimal.
    final digits = (-(math.log(scaled) / math.ln10) - 1e-9).ceil();
    return digits < 0 ? 0 : digits;
  }
}

/// Number/currency formatting helpers (DESIGN.md §8).
///
/// The backend deliberately ships raw `numeric(14,2)` amounts + a currency code
/// (see `AccountDto`) and leaves presentation to this layer.
///
/// **Digits are Western in both locales.** `NumberFormat`/`DateFormat` resolve
/// `ar` to CLDR's `latn` numbering system, so every helper here emits `12,345`
/// and `2026` under `ar` exactly as under `en` — only the currency unit and
/// month names localize. Verify against real output before documenting an
/// Arabic-Indic example: this docstring long claimed the opposite, and so did
/// [date] when it was added, so the assumption is an easy one to re-introduce.
///
/// That leaves the app **mixed**: the ARB strings are hand-written with
/// Arabic-Indic digits (`٣٠ يومًا`, `٢٬١٠٠`) while every computed number beside
/// them is Western. Known divergence, not yet decided either way — settling it
/// means choosing a numbering system app-wide, not patching a call site.
class Fmt {
  const Fmt(this.locale, this.l10n);

  final Locale locale;
  final AppLocalizations l10n;

  /// Rounded integer with locale grouping — `12,345` in both locales.
  String fmt(num n) =>
      NumberFormat.decimalPattern(locale.toLanguageTag()).format(n.round());

  /// [fmt] suffixed with the localized currency unit (`SAR` / `ر.س`).
  String money(num n) => '${fmt(n)} ${l10n.currencySar}';

  /// A percentage with locale grouping and at most one decimal, suffixed with
  /// `%` — e.g. `34%` / `2.1%`, identical under `ar`. The backend ships ratio metrics
  /// (forecast DTI, income stability, default probability) already scaled to a
  /// percentage, so this only formats them for display.
  String percent(num n) {
    final f = NumberFormat.decimalPattern(locale.toLanguageTag())
      ..maximumFractionDigits = 1;
    return '${f.format(n)}%';
  }

  /// A medium calendar date localized to the active locale, e.g. `Aug 12, 2026`
  /// / `12 أغسطس 2026` — the month name localizes, the digits do not. Used for
  /// the financing facility's first-payment date.
  String date(DateTime d) =>
      DateFormat.yMMMd(locale.toLanguageTag()).format(d);

  /// A magnitude-scaled axis tick, e.g. `2.24 M` / `2.24 م`, `8 K` / `8 ألف`.
  ///
  /// Carries no currency unit: an axis names the currency once (the chart's
  /// trailing [money] label) instead of repeating it on every tick, which would
  /// roughly double the gutter it needs.
  ///
  /// [scale] and [fractionDigits] are supplied by the caller rather than derived
  /// from [n], because ticks have to read as a *set*. Deriving per value is what
  /// `NumberFormat.compact` does, and it yields a ragged `2.2 / 2.21 / 2.22`;
  /// pinning one arbitrary precision collapses a tight domain to five identical
  /// labels. Both must come from the tick step — see [AxisScale.fractionDigitsFor].
  String compactTick(
    num n, {
    required AxisScale scale,
    required int fractionDigits,
  }) {
    final f = NumberFormat.decimalPattern(locale.toLanguageTag());
    // Zero is the baseline, not a magnitude: `0` reads better than `0.00 م`, and
    // this also sidesteps a negative zero ("-0.00") on a domain that dips below.
    if (n == 0) return f.format(0);
    f
      ..minimumFractionDigits = fractionDigits
      ..maximumFractionDigits = fractionDigits;
    final text = f.format(n / scale.divisor);
    return switch (scale) {
      AxisScale.ones => text,
      AxisScale.thousands => '$text ${l10n.axisUnitThousand}',
      AxisScale.millions => '$text ${l10n.axisUnitMillion}',
    };
  }

  /// Short day-and-month for an axis tick, e.g. `Aug 15` / `15 أغسطس`.
  ///
  /// Deliberately not [date]: an axis repeats this label several times across a
  /// narrow plot, and a 30-day horizon never spans a year boundary worth naming,
  /// so the year is dropped where [date] keeps it.
  ///
  /// Date symbols for the active locale are initialized by
  /// `GlobalMaterialLocalizations` (see `main.dart`), so this is safe anywhere
  /// under the app. A test that formats dates outside a `MaterialApp` must call
  /// `initializeDateFormatting` first.
  String axisDate(DateTime d) =>
      DateFormat.MMMd(locale.toLanguageTag()).format(d);
}
