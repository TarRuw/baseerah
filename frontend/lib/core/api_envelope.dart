import 'package:dio/dio.dart';

/// Thrown when the backend returns an error envelope (`{"status":"ERROR",...}`)
/// or a payload that doesn't match the shared success envelope (DESIGN §6).
class ApiEnvelopeException implements Exception {
  ApiEnvelopeException(this.message);
  final String message;
  @override
  String toString() => 'ApiEnvelopeException: $message';
}

/// Unwrap the shared `{ "status":"OK", "data": ... }` envelope, returning `data`.
///
/// Repositories call this so every feature parses the envelope one way. On an
/// error status or a shape mismatch it throws [ApiEnvelopeException] — callers
/// (Riverpod `FutureProvider`s) surface that as an error state.
dynamic unwrapEnvelope(Response<dynamic> response) {
  final body = response.data;
  if (body is! Map || body['status'] != 'OK' || !body.containsKey('data')) {
    throw ApiEnvelopeException('Unexpected response envelope: ${response.data}');
  }
  return body['data'];
}
