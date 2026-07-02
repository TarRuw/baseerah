import '../../../api/api_client.dart';
import '../../../core/api_envelope.dart';
import 'stress_score.dart';

/// Fetches a client's Financial Stress Score via
/// `GET /api/v1/clients/{id}/stress-score` and unwraps the shared envelope
/// into a [StressScore] (DESIGN §5.1/§6).
class StressScoreRepository {
  const StressScoreRepository(this._api);

  final ApiClient _api;

  Future<StressScore> fetch(String clientId) async {
    final response = await _api.dio.get<dynamic>(
      '/clients/$clientId/stress-score',
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return StressScore.fromJson(data);
  }
}
