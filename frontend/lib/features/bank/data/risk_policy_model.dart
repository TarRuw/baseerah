// Client-side view of the bank-wide risk-policy singleton (DESIGN §4.2/§7.7),
// mirroring the backend `RiskPolicyDto` used as BOTH the `GET` response and the
// `PUT` request body for `/api/v1/bank/risk-policy`. The Settings screen loads
// it, edits the toggles/sliders, and writes the whole record back — so this
// model round-trips every field, including [samaLastSync], which the server
// copies verbatim from the request (dropping it would erase the last-sync time).

/// The risk policy the Settings screen reads and persists. [staminaFloor] and
/// [autoDeclineThreshold] carry the same bounds the backend validates (0–100 and
/// 0–200 %) so the sliders never submit an out-of-range value; [samaLastSync] is
/// the last SAMA Open-Banking sync timestamp shown in Settings (null = never).
class RiskPolicy {
  const RiskPolicy({
    required this.staminaFloor,
    required this.autoDeclineThreshold,
    required this.ndmoResidency,
    required this.tokenization,
    required this.samaLastSync,
  });

  /// Minimum stamina to pass policy (0–100).
  final int staminaFloor;

  /// Forecast-DTI percentage at/above which to auto-decline (0–200).
  final int autoDeclineThreshold;

  /// NDMO data-residency compliance toggle.
  final bool ndmoResidency;

  /// SAMA account-tokenization compliance toggle.
  final bool tokenization;

  /// Last SAMA Open-Banking sync timestamp, or null if never synced.
  final DateTime? samaLastSync;

  factory RiskPolicy.fromJson(Map<String, dynamic> json) {
    final lastSync = json['samaLastSync'] as String?;
    return RiskPolicy(
      staminaFloor: (json['staminaFloor'] as num).toInt(),
      autoDeclineThreshold: (json['autoDeclineThreshold'] as num).toInt(),
      ndmoResidency: json['ndmoResidency'] as bool,
      tokenization: json['tokenization'] as bool,
      samaLastSync: lastSync == null ? null : DateTime.parse(lastSync),
    );
  }

  /// The PUT body. [samaLastSync] is echoed back as an ISO-8601 instant so the
  /// server preserves it (it copies the request value straight onto the entity).
  Map<String, dynamic> toJson() => {
    'staminaFloor': staminaFloor,
    'autoDeclineThreshold': autoDeclineThreshold,
    'ndmoResidency': ndmoResidency,
    'tokenization': tokenization,
    'samaLastSync': samaLastSync?.toUtc().toIso8601String(),
  };

  /// A copy with individual fields overridden — the Settings screen edits one
  /// toggle/slider at a time and PUTs the whole resulting policy.
  RiskPolicy copyWith({
    int? staminaFloor,
    int? autoDeclineThreshold,
    bool? ndmoResidency,
    bool? tokenization,
  }) {
    return RiskPolicy(
      staminaFloor: staminaFloor ?? this.staminaFloor,
      autoDeclineThreshold: autoDeclineThreshold ?? this.autoDeclineThreshold,
      ndmoResidency: ndmoResidency ?? this.ndmoResidency,
      tokenization: tokenization ?? this.tokenization,
      samaLastSync: samaLastSync,
    );
  }
}
