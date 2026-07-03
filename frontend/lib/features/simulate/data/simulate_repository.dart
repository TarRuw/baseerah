import 'package:dio/dio.dart';

import '../../../api/api_client.dart';
import '../../../core/api_envelope.dart';
import 'invoice_result.dart';
import 'loan_result.dart';

/// All Simulate-screen backend calls in one place (Step 3.5), each unwrapping
/// the shared `{status, data}` envelope. Widgets never call dio directly — the
/// notifiers depend on this repository (Global Rule: API calls out of widgets).
class SimulateRepository {
  const SimulateRepository(this._api);

  final ApiClient _api;

  /// `POST /clients/{id}/loan-simulate` — the three slider inputs in, the live
  /// instalment/DTI/verdict/score-impact out (FR-05).
  Future<LoanResult> simulate(
    String clientId, {
    required double principal,
    required double rate,
    required int term,
  }) async {
    final response = await _api.dio.post<dynamic>(
      '/clients/$clientId/loan-simulate',
      data: {'principal': principal, 'rate': rate, 'term': term},
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return LoanResult.fromJson(data);
  }

  /// `POST /clients/{id}/chat` — a free-text message in, the assistant's reply
  /// string out (FR-03).
  Future<String> chat(String clientId, String message) async {
    final response = await _api.dio.post<dynamic>(
      '/clients/$clientId/chat',
      data: {'message': message},
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return data['reply'] as String;
  }

  /// `POST /clients/{id}/chat/invoice` — the invoice image bytes as multipart
  /// part `image`, the parsed-action stub out (FR-03, mock = deterministic).
  /// dio sets the `multipart/form-data` content-type + boundary automatically
  /// when the body is [FormData], overriding the client's JSON default.
  Future<InvoiceResult> parseInvoice(
    String clientId,
    List<int> bytes, {
    required String filename,
  }) async {
    final form = FormData.fromMap({
      'image': MultipartFile.fromBytes(bytes, filename: filename),
    });
    final response = await _api.dio.post<dynamic>(
      '/clients/$clientId/chat/invoice',
      data: form,
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return InvoiceResult.fromJson(data);
  }
}
