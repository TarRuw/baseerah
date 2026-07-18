import '../../../api/api_client.dart';
import '../../../core/api_envelope.dart';
import 'applicant_models.dart';
import 'disbursement_models.dart';
import 'financing_request_models.dart';
import 'portfolio_models.dart';
import 'risk_policy_model.dart';

/// All Bank Portal backend calls in one place (Step 6.3), each unwrapping the
/// shared `{status, data}` envelope. The providers/notifier depend on this
/// repository — widgets never call dio directly (Global Rule: API out of
/// widgets). The `Accept-Language` header follows the active locale because the
/// [ApiClient] is rebuilt per locale.
class BankRepository {
  const BankRepository(this._api);

  final ApiClient _api;

  /// `GET /bank/loan-requests?stage=underwrite` — the underwrite-stage queue:
  /// un-underwritten OPEN loan requests awaiting a risk report (oldest first).
  Future<List<LoanRequestRow>> loanRequests() async {
    final response = await _api.dio.get<dynamic>(
      '/bank/loan-requests',
      queryParameters: {'stage': 'underwrite'},
    );
    final data = unwrapEnvelope(response) as List<dynamic>;
    return data
        .map((e) => LoanRequestRow.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// `POST /bank/loan-requests/{id}/underwrite` — run + stamp the predictive
  /// report (verdict, stamina, KPIs, 12-month cash flow). Backend answers < 2.5s.
  Future<UnderwritingReport> underwrite(String requestId) async {
    final response = await _api.dio.post<dynamic>(
      '/bank/loan-requests/$requestId/underwrite',
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return UnderwritingReport.fromJson(data);
  }

  /// `POST /bank/loan-requests/{id}/decline` — reject the request at the
  /// underwrite stage; returns the updated row (now `DECLINED`, dropped from the
  /// queue). Approval is no longer an underwrite action — it is pricing (next tab).
  Future<LoanRequestRow> declineRequest(String requestId) async {
    final response = await _api.dio.post<dynamic>(
      '/bank/loan-requests/$requestId/decline',
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return LoanRequestRow.fromJson(data);
  }

  /// `GET /bank/portfolio` — the four KPI figures plus the monitoring rows for
  /// the active book (Step 6.4 Portfolio screen). All figures server-computed.
  Future<Portfolio> portfolio() async {
    final response = await _api.dio.get<dynamic>('/bank/portfolio');
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return Portfolio.fromJson(data);
  }

  /// `GET /bank/financing-requests` — the pending financing inbox (oldest first),
  /// each row carrying the applicant context and a risk hint (client score).
  Future<List<FinancingRequestRow>> financingRequests() async {
    final response = await _api.dio.get<dynamic>('/bank/financing-requests');
    final data = unwrapEnvelope(response) as List<dynamic>;
    return data
        .map((e) => FinancingRequestRow.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// `POST /bank/financing-requests/{id}/reply` — record the offered rate (%) and
  /// term (months); returns the updated row (now `REPLIED`).
  Future<FinancingRequestRow> replyFinancing(
    String proposalId, {
    required double rate,
    required int term,
  }) async {
    final response = await _api.dio.post<dynamic>(
      '/bank/financing-requests/$proposalId/reply',
      data: {'rate': rate, 'term': term},
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return FinancingRequestRow.fromJson(data);
  }

  /// `POST /bank/financing-requests/{id}/decline` — decline without an offer.
  Future<FinancingRequestRow> declineFinancing(String proposalId) async {
    final response = await _api.dio.post<dynamic>(
      '/bank/financing-requests/$proposalId/decline',
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return FinancingRequestRow.fromJson(data);
  }

  /// `GET /bank/financing-disbursements` — accepted offers awaiting a funding
  /// decision, each with a final affordability check.
  Future<List<DisbursementRow>> disbursements() async {
    final response = await _api.dio.get<dynamic>('/bank/financing-disbursements');
    final data = unwrapEnvelope(response) as List<dynamic>;
    return data
        .map((e) => DisbursementRow.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// `POST /bank/financing-disbursements/{id}/disburse` — fund the accepted offer.
  Future<DisbursementRow> disburse(String proposalId) async {
    final response = await _api.dio.post<dynamic>(
      '/bank/financing-disbursements/$proposalId/disburse',
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return DisbursementRow.fromJson(data);
  }

  /// `POST /bank/financing-disbursements/{id}/decline` — decline at the final stage.
  Future<DisbursementRow> declineDisbursement(String proposalId) async {
    final response = await _api.dio.post<dynamic>(
      '/bank/financing-disbursements/$proposalId/decline',
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return DisbursementRow.fromJson(data);
  }

  /// `GET /bank/risk-policy` — the current singleton risk policy (Settings load).
  Future<RiskPolicy> riskPolicy() async {
    final response = await _api.dio.get<dynamic>('/bank/risk-policy');
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return RiskPolicy.fromJson(data);
  }

  /// `PUT /bank/risk-policy` — persist the whole edited policy and return the
  /// round-tripped result the server saved (Settings reflects the saved values).
  Future<RiskPolicy> updateRiskPolicy(RiskPolicy policy) async {
    final response = await _api.dio.put<dynamic>(
      '/bank/risk-policy',
      data: policy.toJson(),
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return RiskPolicy.fromJson(data);
  }
}
