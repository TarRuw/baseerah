import '../../../api/api_client.dart';
import '../../../core/api_envelope.dart';
import 'applicant_models.dart';
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

  /// `GET /bank/applicants` — the underwriting queue (oldest first).
  Future<List<Applicant>> applicants() async {
    final response = await _api.dio.get<dynamic>('/bank/applicants');
    final data = unwrapEnvelope(response) as List<dynamic>;
    return data
        .map((e) => Applicant.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// `POST /bank/applicants/{id}/report` — generate + persist the predictive
  /// report (verdict, stamina, KPIs, 12-month cash flow). Backend answers < 2.5s.
  Future<UnderwritingReport> report(String applicationId) async {
    final response = await _api.dio.post<dynamic>(
      '/bank/applicants/$applicationId/report',
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return UnderwritingReport.fromJson(data);
  }

  /// `POST /bank/applicants/{id}/decision` — record an approve/decline decision;
  /// returns the updated applicant echo (with [Applicant.decision] persisted).
  /// The wire body is `{decision}` (`APPROVE` | `DECLINE`).
  Future<Applicant> decide(String applicationId, Decision decision) async {
    final response = await _api.dio.post<dynamic>(
      '/bank/applicants/$applicationId/decision',
      data: {'decision': decision.wire},
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return Applicant.fromJson(data);
  }

  /// `GET /bank/portfolio` — the four KPI figures plus the monitoring rows for
  /// the active book (Step 6.4 Portfolio screen). All figures server-computed.
  Future<Portfolio> portfolio() async {
    final response = await _api.dio.get<dynamic>('/bank/portfolio');
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return Portfolio.fromJson(data);
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
