import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../../l10n/locale_provider.dart' show sharedPreferencesProvider;
import 'auth_models.dart';

/// Persists the signed-in [AuthSession] (JWT + identity) across restarts, backed
/// by [SharedPreferences] — the same store `main()` loads before the first frame,
/// so the session can be restored synchronously with no "logged-out flash"
/// (mirrors the locale-restore pattern).
///
/// This is intentionally simple for the **local demo**: the JWT lives in
/// `SharedPreferences` (localStorage on web). A real mobile build should keep the
/// token in the platform keystore via `flutter_secure_storage` — out of scope
/// here (see the Step 9.4 handoff for the upgrade path).
class AuthStore {
  const AuthStore(this._prefs);

  final SharedPreferences _prefs;

  /// Versioned key so a future shape change can be migrated/ignored cleanly.
  static const String prefsKey = 'baseerah.auth.session.v1';

  /// Persist the session (fire-and-forget write; the in-memory state is
  /// authoritative for this session, the write only needs to land before the
  /// next cold start — same contract as the locale store).
  void save(AuthSession session) {
    _prefs.setString(prefsKey, jsonEncode(session.toJson()));
  }

  /// The restored session, or `null` when signed out (or the stored blob is
  /// unreadable — a corrupt/legacy value is treated as "no session" rather than
  /// crashing the launch path).
  AuthSession? read() {
    final raw = _prefs.getString(prefsKey);
    if (raw == null || raw.isEmpty) return null;
    try {
      return AuthSession.fromJson(jsonDecode(raw) as Map<String, dynamic>);
    } catch (_) {
      return null;
    }
  }

  /// The bearer token to attach to requests, or `null` when signed out.
  String? readToken() => read()?.token;

  void clear() {
    _prefs.remove(prefsKey);
  }
}

/// The app-wide [AuthStore], backed by the [SharedPreferences] loaded in
/// `main()` (override [sharedPreferencesProvider] in tests, as elsewhere).
final authStoreProvider = Provider<AuthStore>(
  (ref) => AuthStore(ref.watch(sharedPreferencesProvider)),
);
