import 'dart:ui';

import 'package:flutter_riverpod/flutter_riverpod.dart';

/// The app's active locale. Arabic is the primary/default locale (DESIGN.md §7,
/// ORCHESTRATION Global Rules); text direction follows it automatically (RTL for
/// `ar`, LTR for `en`) because [MaterialApp] derives direction from the locale.
class LocaleNotifier extends StateNotifier<Locale> {
  LocaleNotifier() : super(const Locale('ar'));

  static const Locale arabic = Locale('ar');
  static const Locale english = Locale('en');

  /// Flip between the two supported locales (drives the toolbar language toggle).
  void toggle() {
    state = state.languageCode == 'ar' ? english : arabic;
  }

  void set(Locale locale) => state = locale;
}

final localeProvider = StateNotifierProvider<LocaleNotifier, Locale>(
  (ref) => LocaleNotifier(),
);
