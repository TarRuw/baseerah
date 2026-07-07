import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'l10n/app_localizations.dart';
import 'l10n/locale_provider.dart';
import 'router/app_router.dart';
import 'theme/baseerah_theme.dart';

Future<void> main() async {
  // Load persisted preferences before the first frame so the restored locale
  // (QA UI-07) is present synchronously when the app mounts — no Arabic flash.
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await SharedPreferences.getInstance();
  runApp(
    ProviderScope(
      overrides: [sharedPreferencesProvider.overrideWithValue(prefs)],
      child: const BaseerahApp(),
    ),
  );
}

class BaseerahApp extends ConsumerStatefulWidget {
  const BaseerahApp({super.key});

  @override
  ConsumerState<BaseerahApp> createState() => _BaseerahAppState();
}

class _BaseerahAppState extends ConsumerState<BaseerahApp> {
  // The router outlives rebuilds; create it once.
  final _router = createRouter();

  @override
  Widget build(BuildContext context) {
    // Active locale drives both the strings and the text direction (RTL for ar):
    // MaterialApp derives Directionality from `locale`, so no manual wrapping.
    final locale = ref.watch(localeProvider);

    return MaterialApp.router(
      onGenerateTitle: (context) => AppLocalizations.of(context).appTitle,
      debugShowCheckedModeBanner: false,
      theme: BaseerahTheme.light(locale),
      locale: locale,
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      routerConfig: _router,
    );
  }
}
