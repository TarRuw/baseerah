import 'dart:ui';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../l10n/locale_provider.dart';

/// Thin dio wrapper for the Baseerah backend. Feature calls are added in later
/// phases; this step only wires the configured client.
class ApiClient {
  ApiClient({required String baseUrl, required Locale locale})
    : dio = Dio(
        BaseOptions(
          baseUrl: baseUrl,
          connectTimeout: const Duration(seconds: 10),
          // Analytics endpoints must answer < 2.5 s (DESIGN.md §9); give headroom.
          receiveTimeout: const Duration(seconds: 15),
          contentType: 'application/json',
          headers: {'Accept-Language': locale.languageCode},
        ),
      );

  final Dio dio;

  /// Base URL of the local backend (Step 0.2). Overridable at build time with
  /// `--dart-define=BASEERAH_API_BASE_URL=...` so it can point at a deployed
  /// backend later. Android emulators must use `10.0.2.2` instead of localhost.
  static const String baseUrl = String.fromEnvironment(
    'BASEERAH_API_BASE_URL',
    defaultValue: 'http://localhost:8080/api/v1',
  );
}

/// Provides an [ApiClient] whose `Accept-Language` tracks the active locale.
/// Rebuilds when the locale toggles so requests always match the UI language.
final apiClientProvider = Provider<ApiClient>((ref) {
  final locale = ref.watch(localeProvider);
  return ApiClient(baseUrl: ApiClient.baseUrl, locale: locale);
});
