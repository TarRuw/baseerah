// QA UI-07: the chosen locale must survive a full reload/restart. The provider
// restores the persisted language on init and writes every change back.
import 'package:baseerah/l10n/locale_provider.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<(ProviderContainer, SharedPreferences)> build(
    Map<String, Object> initial,
  ) async {
    SharedPreferences.setMockInitialValues(initial);
    final prefs = await SharedPreferences.getInstance();
    final container = ProviderContainer(
      overrides: [sharedPreferencesProvider.overrideWithValue(prefs)],
    );
    addTearDown(container.dispose);
    return (container, prefs);
  }

  test('defaults to Arabic when nothing is persisted', () async {
    final (container, _) = await build({});
    expect(container.read(localeProvider), LocaleNotifier.arabic);
  });

  test('restores a persisted English locale on init (survives reload)', () async {
    final (container, _) = await build({LocaleNotifier.prefsKey: 'en'});
    expect(container.read(localeProvider), LocaleNotifier.english);
  });

  test('toggling persists the new locale for the next launch', () async {
    final (container, prefs) = await build({});

    container.read(localeProvider.notifier).toggle(); // ar -> en

    expect(container.read(localeProvider), LocaleNotifier.english);
    expect(prefs.getString(LocaleNotifier.prefsKey), 'en');
  });
}
