import '../../../api/api_client.dart';
import '../../../core/api_envelope.dart';
import 'cashflow_summary.dart';

/// Fetches a client's average monthly income and spending via
/// `GET /api/v1/clients/{id}/cashflow-summary` and unwraps the shared envelope
/// into a [CashflowSummary] (DESIGN §6).
class CashflowRepository {
  const CashflowRepository(this._api);

  final ApiClient _api;

  Future<CashflowSummary> fetch(String clientId) async {
    final response = await _api.dio.get<dynamic>(
      '/clients/$clientId/cashflow-summary',
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return CashflowSummary.fromJson(data);
  }
}
