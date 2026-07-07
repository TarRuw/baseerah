import 'package:baseerah/l10n/locale_provider.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Builds a [sharedPreferencesProvider] override backed by in-memory mock prefs,
/// so widget tests that pump the real app (which reads the locale) don't hit the
/// throwing default. Seed [initial] to simulate a previously-persisted choice,
/// e.g. `{LocaleNotifier.prefsKey: 'en'}`.
Future<Override> prefsOverride([Map<String, Object> initial = const {}]) async {
  SharedPreferences.setMockInitialValues(initial);
  final prefs = await SharedPreferences.getInstance();
  return sharedPreferencesProvider.overrideWithValue(prefs);
}
