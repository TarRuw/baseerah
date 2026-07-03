import '../../../api/api_client.dart';
import '../../../core/api_envelope.dart';
import 'forecast.dart';

/// Fetches a client's cash-flow forecast via
/// `GET /api/v1/clients/{id}/forecast?horizonDays=N` and unwraps the shared
/// envelope into a [Forecast] (DESIGN §5.2/§6). The Home chart uses the 30-day
/// horizon; the same call serves the 3/6/12-month scenario horizons.
class ForecastRepository {
  const ForecastRepository(this._api);

  final ApiClient _api;

  Future<Forecast> fetch(String clientId, {int horizonDays = 30}) async {
    final response = await _api.dio.get<dynamic>(
      '/clients/$clientId/forecast',
      queryParameters: {'horizonDays': horizonDays},
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return Forecast.fromJson(data);
  }
}
