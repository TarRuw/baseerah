import '../../../api/api_client.dart';
import '../../../core/api_envelope.dart';
import 'rescue_models.dart';

/// All Rescue-screen backend calls in one place (Step 4.3), each unwrapping the
/// shared `{status, data}` envelope. The notifier depends on this repository —
/// widgets never call dio directly (Global Rule: API calls out of widgets).
class RescueRepository {
  const RescueRepository(this._api);

  final ApiClient _api;

  /// `GET /clients/{id}/rescue` — predicted shortfall + bridge options, or the
  /// explicit no-deficit state for a healthy persona (FR-06/07).
  Future<RescueState> fetch(String clientId) async {
    final response = await _api.dio.get<dynamic>('/clients/$clientId/rescue');
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return RescueState.fromJson(data);
  }

  /// `POST /clients/{id}/rescue/confirm` — confirm the chosen bridge, returning
  /// the before→after score recovery (FR-07). The wire body is `{option}`.
  Future<RescueOutcome> confirm(String clientId, RescueOptionType option) async {
    final response = await _api.dio.post<dynamic>(
      '/clients/$clientId/rescue/confirm',
      data: {'option': option.wire},
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return RescueOutcome.fromJson(data);
  }
}
