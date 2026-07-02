import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'l10n/app_localizations.dart';
import 'l10n/locale_provider.dart';
import 'router/app_router.dart';
import 'theme/baseerah_theme.dart';

void main() {
  runApp(const ProviderScope(child: BaseerahApp()));
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
