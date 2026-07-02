import '../../../api/api_client.dart';
import '../../../core/api_envelope.dart';
import 'client.dart';

/// Fetches the seeded personas via `GET /api/v1/clients` (DESIGN §6). The Home
/// screen uses this to resolve a "current client" until a persona picker exists.
class ClientRepository {
  const ClientRepository(this._api);

  final ApiClient _api;

  Future<List<Client>> fetchAll() async {
    final response = await _api.dio.get<dynamic>('/clients');
    final data = unwrapEnvelope(response) as List<dynamic>;
    return data
        .map((e) => Client.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
  }
}
