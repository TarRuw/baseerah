/// Client-side view of a seeded persona from `GET /api/v1/clients` (DESIGN §6).
///
/// The Home screen needs a client to scope its score/accounts calls; until a
/// persona picker exists, the first seeded client is used (see
/// `currentClientProvider`). [profileLabel] is the Arabic display label from the
/// mock feed and is shown under the greeting.
class Client {
  const Client({
    required this.id,
    required this.externalId,
    required this.profileLabel,
    required this.persona,
  });

  final String id;
  final String externalId;
  final String profileLabel;
  final String persona;

  factory Client.fromJson(Map<String, dynamic> json) {
    return Client(
      id: json['id'] as String,
      externalId: json['externalId'] as String,
      profileLabel: json['profileLabel'] as String,
      persona: json['persona'] as String,
    );
  }
}
