import '../../../api/api_client.dart';
import '../../../core/api_envelope.dart';
import 'account.dart';

/// Fetches a client's linked accounts via `GET /api/v1/clients/{id}/accounts`
/// and unwraps the shared envelope into a list of [Account] (DESIGN §6).
class AccountsRepository {
  const AccountsRepository(this._api);

  final ApiClient _api;

  Future<List<Account>> fetch(String clientId) async {
    final response = await _api.dio.get<dynamic>('/clients/$clientId/accounts');
    final data = unwrapEnvelope(response) as List<dynamic>;
    return data
        .map((e) => Account.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
  }
}
