// Client-side auth identity models (Step 9.4).
//
// Parsed from the backend `MeDto` / `AuthResponse` (Step 9.2). The consumer app
// uses `AuthUser.role` to pick its shell and `AuthUser.clientId` /
// `clientExternalId` to scope every `/clients/{id}/…` call (the ownership guard
// from Step 9.3 verifies it). Both are JSON-(de)serializable so an `AuthSession`
// can be persisted across restarts.

/// The two roles the backend issues (`MeDto.role` is the uppercase enum name).
enum AuthRole {
  consumer,
  bank;

  /// Parse the wire value (`CONSUMER` / `BANK`, case-insensitive). Unknown values
  /// throw — a mismatch means the client and backend contracts have drifted, and
  /// silently guessing a role would be a security-relevant bug.
  static AuthRole fromWire(String value) {
    switch (value.toUpperCase()) {
      case 'CONSUMER':
        return AuthRole.consumer;
      case 'BANK':
        return AuthRole.bank;
      default:
        throw ArgumentError.value(value, 'role', 'Unknown auth role');
    }
  }

  /// The wire value to persist (`CONSUMER` / `BANK`).
  String get wire => name.toUpperCase();
}

/// The authenticated identity, a projection of the backend `MeDto`.
///
/// [clientId] / [clientExternalId] are populated for a [AuthRole.consumer] and
/// `null` for a bank officer.
class AuthUser {
  const AuthUser({
    required this.userId,
    required this.displayName,
    required this.displayNameAr,
    required this.role,
    this.clientId,
    this.clientExternalId,
  });

  final String userId;
  final String displayName;
  final String displayNameAr;
  final AuthRole role;
  final String? clientId;
  final String? clientExternalId;

  bool get isConsumer => role == AuthRole.consumer;
  bool get isBank => role == AuthRole.bank;

  /// Parse a backend `MeDto` (from `/auth/me` or embedded in `AuthResponse`).
  factory AuthUser.fromMeJson(Map<String, dynamic> json) {
    return AuthUser(
      userId: json['userId'] as String,
      displayName: json['displayName'] as String,
      displayNameAr: json['displayNameAr'] as String,
      role: AuthRole.fromWire(json['role'] as String),
      clientId: json['clientId'] as String?,
      clientExternalId: json['clientExternalId'] as String?,
    );
  }

  /// Round-trips through [fromMeJson]; the wire shape doubles as the persisted
  /// shape so there is a single mapping to keep in sync with the backend.
  Map<String, dynamic> toJson() => {
        'userId': userId,
        'displayName': displayName,
        'displayNameAr': displayNameAr,
        'role': role.wire,
        'clientId': clientId,
        'clientExternalId': clientExternalId,
      };

  factory AuthUser.fromJson(Map<String, dynamic> json) =>
      AuthUser.fromMeJson(json);
}

/// A signed-in session: the bearer [token] plus the [user] it authenticates.
/// Persisted by `AuthStore` so a restart restores the session without a re-login.
class AuthSession {
  const AuthSession({required this.token, required this.user});

  final String token;
  final AuthUser user;

  /// Parse `AuthResponse` (`{ token, user: MeDto }`) from `/auth/otp/verify`.
  factory AuthSession.fromAuthResponse(Map<String, dynamic> json) {
    return AuthSession(
      token: json['token'] as String,
      user: AuthUser.fromMeJson(json['user'] as Map<String, dynamic>),
    );
  }

  Map<String, dynamic> toJson() => {'token': token, 'user': user.toJson()};

  factory AuthSession.fromJson(Map<String, dynamic> json) => AuthSession(
        token: json['token'] as String,
        user: AuthUser.fromJson(json['user'] as Map<String, dynamic>),
      );
}
