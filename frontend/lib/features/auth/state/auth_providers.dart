import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/auth_models.dart';
import '../data/auth_repository.dart';
import '../data/auth_store.dart';

/// The auth state machine the router (Step 9.5) and UI read.
///
/// - [AuthUnknown] — the very first, pre-restore tick (not used once `build()`
///   resolves the stored session synchronously, but kept so the type is total).
/// - [AuthAuthenticating] — an OTP verify is in flight.
/// - [AuthAuthenticated] — signed in as [user].
/// - [AuthUnauthenticated] — signed out (optionally carrying a [message] for the
///   Login screen, e.g. after a `401` expired the session).
sealed class AuthState {
  const AuthState();

  AuthUser? get user => this is AuthAuthenticated
      ? (this as AuthAuthenticated).user
      : null;

  bool get isAuthenticated => this is AuthAuthenticated;
}

class AuthUnknown extends AuthState {
  const AuthUnknown();
}

class AuthAuthenticating extends AuthState {
  const AuthAuthenticating();
}

class AuthAuthenticated extends AuthState {
  const AuthAuthenticated(this.user);
  @override
  final AuthUser user;
}

class AuthUnauthenticated extends AuthState {
  const AuthUnauthenticated([this.message]);
  final String? message;
}

/// Owns the auth session for the whole app.
///
/// On launch it resolves the stored session **synchronously** (so the router can
/// pick the first route without a logged-out flash) and, if a session exists,
/// validates the token in the background via `/auth/me`; a `401` there (or from
/// any other request, via the [ApiClient] interceptor calling [onUnauthorized])
/// clears the session and flips to [AuthUnauthenticated].
class AuthController extends Notifier<AuthState> {
  AuthRepository get _repo => ref.read(authRepositoryProvider);
  AuthStore get _store => ref.read(authStoreProvider);

  @override
  AuthState build() {
    final session = _store.read();
    if (session == null) {
      return const AuthUnauthenticated();
    }
    // Optimistically authenticated so the first frame is correct; confirm the
    // token is still valid out-of-band.
    Future.microtask(_validateRestoredSession);
    return AuthAuthenticated(session.user);
  }

  /// Confirm a restored token with `/auth/me`. Success refreshes the identity; a
  /// `401` clears the session. A transient/network error keeps the optimistic
  /// session (the next real request's `401` would still clear it).
  Future<void> _validateRestoredSession() async {
    try {
      final user = await _repo.me();
      state = AuthAuthenticated(user);
    } on DioException catch (e) {
      if (e.response?.statusCode == 401) {
        await _repo.logout();
        state = const AuthUnauthenticated();
      }
    } catch (_) {
      // Keep the optimistic session on non-HTTP failures (e.g. offline).
    }
  }

  /// Request an OTP for [mobile]. UI-facing errors propagate to the caller.
  Future<void> requestOtp(String mobile) => _repo.requestOtp(mobile);

  /// Verify [mobile] + [otp]; on success flip to authenticated. Rethrows
  /// [InvalidOtpFailure] (wrong/expired code) and other errors for the UI.
  Future<AuthUser> verifyOtp(String mobile, String otp) async {
    state = const AuthAuthenticating();
    try {
      final session = await _repo.verifyOtp(mobile, otp);
      state = AuthAuthenticated(session.user);
      return session.user;
    } catch (_) {
      state = const AuthUnauthenticated();
      rethrow;
    }
  }

  /// Sign out: clear the session and flip to unauthenticated.
  Future<void> logout() async {
    await _repo.logout();
    state = const AuthUnauthenticated();
  }

  /// Called by the [ApiClient] interceptor when any request returns `401`:
  /// clear the persisted session and flip state (the router redirect lands in
  /// Step 9.5). Idempotent — safe to call when already unauthenticated.
  void onUnauthorized() {
    if (state is AuthUnauthenticated) return;
    _store.clear();
    state = const AuthUnauthenticated();
  }
}

final authControllerProvider =
    NotifierProvider<AuthController, AuthState>(AuthController.new);

/// Convenience: the current [AuthUser] (or `null` when not authenticated).
final currentUserProvider = Provider<AuthUser?>(
  (ref) => ref.watch(authControllerProvider).user,
);
