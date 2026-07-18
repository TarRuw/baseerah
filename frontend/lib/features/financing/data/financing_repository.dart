import 'package:dio/dio.dart';

import '../../../api/api_client.dart';
import '../../../core/api_envelope.dart';
import 'financing_models.dart';

/// All consumer financing-RFP backend calls in one place, each unwrapping the
/// shared `{status, data}` envelope. The notifier depends on this repository —
/// widgets never call dio directly (Global Rule: API calls out of widgets).
class FinancingRepository {
  const FinancingRepository(this._api);

  final ApiClient _api;

  /// `POST /clients/{id}/financing/requests` — raise a request and fan it out to
  /// [banks]; returns the created request (all proposals pending). [origin] tags
  /// how it was raised (`RESCUE` from Smart Rescue, `DIRECT` from the Simulate
  /// "request this financing" CTA); [purpose] is the free-text reason shown to the
  /// bank — both default server-side when omitted.
  Future<FinancingRequest> create(
    String clientId, {
    required double amount,
    required int deficitInDays,
    required List<String> banks,
    String origin = 'RESCUE',
    String? purpose,
  }) async {
    final response = await _api.dio.post<dynamic>(
      '/clients/$clientId/financing/requests',
      data: {
        'amount': amount,
        'deficitInDays': deficitInDays,
        'banks': banks,
        'origin': origin,
        if (purpose != null) 'purpose': purpose,
      },
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return FinancingRequest.fromJson(data);
  }

  /// `GET /clients/{id}/financing/requests` — all the client's requests (newest
  /// first), each with the full affordability impact on its replied proposals.
  /// Backs the requests history/list and is polled while any is still pending.
  Future<List<FinancingRequest>> list(String clientId) async {
    final response =
        await _api.dio.get<dynamic>('/clients/$clientId/financing/requests');
    final data = unwrapEnvelope(response) as List<dynamic>;
    return data
        .map((e) => FinancingRequest.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// `POST /clients/{id}/financing/requests/{rid}/accept` — accept a replied
  /// offer's terms (awaiting disbursement); returns the before/after stress score.
  Future<FinancingOutcome> accept(
    String clientId,
    String requestId,
    String proposalId,
  ) async {
    final Response<dynamic> response = await _api.dio.post<dynamic>(
      '/clients/$clientId/financing/requests/$requestId/accept',
      data: {'proposalId': proposalId},
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return FinancingOutcome.fromJson(data);
  }
}
