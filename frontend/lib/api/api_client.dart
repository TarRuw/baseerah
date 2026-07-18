import 'dart:ui';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../features/auth/data/auth_store.dart';
import '../features/auth/state/auth_providers.dart';
import '../l10n/locale_provider.dart';

/// Thin dio wrapper for the Baseerah backend.
///
/// Since Step 9.4 it also carries the auth plumbing: an interceptor attaches
/// `Authorization: Bearer <jwt>` (read from [tokenReader]) to every request
/// except the public `auth/otp/**` endpoints, and calls [onUnauthorized] when a
/// request comes back `401` so the session can be cleared. `Accept-Language`
/// still tracks the active locale.
class ApiClient {
  ApiClient({
    required String baseUrl,
    required Locale locale,
    String? Function()? tokenReader,
    void Function()? onUnauthorized,
  }) : dio = Dio(
          BaseOptions(
            baseUrl: baseUrl,
            connectTimeout: const Duration(seconds: 10),
            // Analytics endpoints must answer < 2.5 s (DESIGN.md §9); give headroom.
            receiveTimeout: const Duration(seconds: 15),
            contentType: 'application/json',
            headers: {'Accept-Language': locale.languageCode},
          ),
        ) {
    dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) {
          if (!_isPublicAuthPath(options.path)) {
            final token = tokenReader?.call();
            if (token != null && token.isNotEmpty) {
              options.headers['Authorization'] = 'Bearer $token';
            }
          }
          handler.next(options);
        },
        onError: (error, handler) {
          // A 401 on any authenticated call means our token is gone/expired —
          // clear the session. The public otp/** calls handle their own 401
          // (wrong code) as a typed failure, so don't treat those as a logout.
          if (error.response?.statusCode == 401 &&
              !_isPublicAuthPath(error.requestOptions.path)) {
            onUnauthorized?.call();
          }
          handler.next(error);
        },
      ),
    );
  }

  final Dio dio;

  /// The public, unauthenticated auth endpoints (`/auth/otp/request|verify`) that
  /// must not carry a bearer and whose `401` is not a session-expiry signal.
  static bool _isPublicAuthPath(String path) => path.contains('/auth/otp/');

  /// Base URL of the backend API. Injected at build time and MUST be provided —
  /// there is no hardcoded default so no environment endpoint is baked into the
  /// source. Supply it via `--dart-define=BASEERAH_API_BASE_URL=https://.../api/v1`
  /// (the Dockerfile forwards it as a build-arg). Android emulators use `10.0.2.2`.
  static const String baseUrl = String.fromEnvironment('BASEERAH_API_BASE_URL');
}

/// Provides an [ApiClient] whose `Accept-Language` tracks the active locale and
/// whose interceptor reads the token from the [authStoreProvider] and reports a
/// `401` to the [authControllerProvider]. Rebuilds when the locale toggles.
///
/// The `onUnauthorized` closure reads the auth controller **lazily** (not at
/// build time), so there is no provider cycle even though the auth repository
/// depends on this client.
final apiClientProvider = Provider<ApiClient>((ref) {
  final locale = ref.watch(localeProvider);
  final store = ref.watch(authStoreProvider);
  return ApiClient(
    baseUrl: ApiClient.baseUrl,
    locale: locale,
    tokenReader: store.readToken,
    onUnauthorized: () => ref.read(authControllerProvider.notifier).onUnauthorized(),
  );
});
