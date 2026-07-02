import 'dart:ui';

import 'package:intl/intl.dart';

import '../l10n/app_localizations.dart';

/// Number/currency formatting helpers (DESIGN.md §8).
///
/// The backend deliberately ships raw `numeric(14,2)` amounts + a currency code
/// (see `AccountDto`) and leaves presentation to this layer. These helpers are
/// locale-aware: under `ar` they emit Arabic-Indic digits and grouping via
/// [NumberFormat], under `en` Western digits — matching the prototype
/// (`fmt(n)=round(n).toLocaleString(...)`, `money(n)=fmt(n)+' '+unit`).
class Fmt {
  const Fmt(this.locale, this.l10n);

  final Locale locale;
  final AppLocalizations l10n;

  /// Rounded integer with locale grouping, e.g. `12,345` / `١٢٬٣٤٥`.
  String fmt(num n) =>
      NumberFormat.decimalPattern(locale.toLanguageTag()).format(n.round());

  /// [fmt] suffixed with the localized currency unit (`SAR` / `ر.س`).
  String money(num n) => '${fmt(n)} ${l10n.currencySar}';
}
