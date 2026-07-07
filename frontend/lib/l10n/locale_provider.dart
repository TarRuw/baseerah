import 'dart:ui';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Holds the [SharedPreferences] instance loaded once in `main()` before the app
/// mounts. Overridden there via [Provider.overrideWithValue]; the default throws
/// so a forgotten override surfaces loudly (in tests, override it too).
final sharedPreferencesProvider = Provider<SharedPreferences>(
  (ref) => throw UnimplementedError(
    'sharedPreferencesProvider must be overridden in main() (or a test).',
  ),
);

/// The app's active locale. Arabic is the primary/default locale (DESIGN.md §7,
/// ORCHESTRATION Global Rules); text direction follows it automatically (RTL for
/// `ar`, LTR for `en`) because [MaterialApp] derives direction from the locale.
///
/// The choice is **persisted** so it survives a full reload/restart (QA UI-07):
/// on construction the notifier restores the last saved language, and every
/// change writes it back to [SharedPreferences] (localStorage on web).
class LocaleNotifier extends StateNotifier<Locale> {
  LocaleNotifier(this._prefs) : super(_restore(_prefs));

  final SharedPreferences _prefs;

  /// Preferences key holding the persisted language code (`ar` / `en`).
  static const String prefsKey = 'baseerah.locale.languageCode';

  static const Locale arabic = Locale('ar');
  static const Locale english = Locale('en');

  /// Restore the persisted locale, defaulting to Arabic when nothing valid is
  /// stored (first launch, cleared storage, or an unrecognised code).
  static Locale _restore(SharedPreferences prefs) {
    return prefs.getString(prefsKey) == 'en' ? english : arabic;
  }

  /// Flip between the two supported locales (drives the toolbar language toggle).
  void toggle() => set(state.languageCode == 'ar' ? english : arabic);

  void set(Locale locale) {
    state = locale;
    // Fire-and-forget: the in-memory state is authoritative for this session;
    // the write only needs to land before the next cold start.
    _prefs.setString(prefsKey, locale.languageCode);
  }
}

final localeProvider = StateNotifierProvider<LocaleNotifier, Locale>(
  (ref) => LocaleNotifier(ref.watch(sharedPreferencesProvider)),
);
