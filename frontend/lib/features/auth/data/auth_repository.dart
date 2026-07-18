import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../api/api_client.dart';
import '../../../core/api_envelope.dart';
import 'auth_models.dart';
import 'auth_store.dart';

/// Typed failure for a rejected OTP (wrong or expired code) — the backend answers
/// `POST /auth/otp/verify` with `401`. The Login screen (Step 9.5) catches this to
/// show an inline "invalid code" message instead of a generic error.
class InvalidOtpFailure implements Exception {
  const InvalidOtpFailure([this.message]);
  final String? message;
  @override
  String toString() => 'InvalidOtpFailure: ${message ?? 'invalid or expired code'}';
}

/// Talks to the Step 9.2 auth endpoints and owns the persisted [AuthSession].
///
/// The `otp/**` calls are public (no bearer); `me()` relies on the bearer the
/// [ApiClient] interceptor attaches from the store. On a successful verify the
/// session is saved here so a restart can restore it.
class AuthRepository {
  const AuthRepository(this._api, this._store);

  final ApiClient _api;
  final AuthStore _store;

  /// "Send me a code" for [mobile]. The backend response is generic (no user
  /// enumeration); nothing to return.
  Future<void> requestOtp(String mobile) async {
    await _api.dio.post<dynamic>('/auth/otp/request', data: {'mobile': mobile});
  }

  /// Exchange [mobile] + [otp] for a JWT session, persist it, and return it.
  /// Throws [InvalidOtpFailure] on a `401` (wrong/expired code).
  Future<AuthSession> verifyOtp(String mobile, String otp) async {
    try {
      final response = await _api.dio.post<dynamic>(
        '/auth/otp/verify',
        data: {'mobile': mobile, 'otp': otp},
      );
      final data = unwrapEnvelope(response) as Map<String, dynamic>;
      final session = AuthSession.fromAuthResponse(data);
      _store.save(session);
      return session;
    } on DioException catch (e) {
      if (e.response?.statusCode == 401) {
        throw InvalidOtpFailure(_errorMessage(e.response));
      }
      rethrow;
    }
  }

  /// Fetch the current identity (`GET /auth/me`), used to validate/refresh a
  /// restored token on launch. Propagates a `401` as a [DioException] so the
  /// caller (and the interceptor) can clear the session.
  Future<AuthUser> me() async {
    final response = await _api.dio.get<dynamic>('/auth/me');
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return AuthUser.fromMeJson(data);
  }

  /// Clear the persisted session. The JWT is stateless, so there is no server
  /// call to make (Step 9.2 added none).
  Future<void> logout() async {
    _store.clear();
  }

  /// Pull the human message out of the shared error envelope when present.
  String? _errorMessage(Response<dynamic>? response) {
    final body = response?.data;
    if (body is Map && body['error'] is Map) {
      final message = (body['error'] as Map)['message'];
      if (message is String) return message;
    }
    return null;
  }
}

final authRepositoryProvider = Provider<AuthRepository>(
  (ref) => AuthRepository(
    ref.watch(apiClientProvider),
    ref.watch(authStoreProvider),
  ),
);
