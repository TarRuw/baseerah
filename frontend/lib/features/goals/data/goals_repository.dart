import '../../../api/api_client.dart';
import '../../../core/api_envelope.dart';
import 'challenge.dart';
import 'claim_result.dart';
import 'rewards.dart';

/// All Goals-screen backend calls in one place (Step 5.3), each unwrapping the
/// shared `{status, data}` envelope. The controller depends on this repository —
/// widgets never call dio directly (Global Rule: API calls out of widgets).
class GoalsRepository {
  const GoalsRepository(this._api);

  final ApiClient _api;

  /// `GET /clients/{id}/challenges` — the client's live gamified challenges with
  /// their progress + button state (FR-09/10).
  Future<List<Challenge>> fetchChallenges(String clientId) async {
    final response =
        await _api.dio.get<dynamic>('/clients/$clientId/challenges');
    final data = unwrapEnvelope(response) as List<dynamic>;
    return data
        .map((e) => Challenge.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// `GET /clients/{id}/rewards` — the Akhtar-Points balance + tier (FR-10).
  Future<Rewards> fetchRewards(String clientId) async {
    final response = await _api.dio.get<dynamic>('/clients/$clientId/rewards');
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return Rewards.fromJson(data);
  }

  /// `POST /clients/{id}/challenges/{cid}/claim` — claim a completed challenge,
  /// returning the new balance/tier + the challenge now `claimed`. A rejected
  /// claim (already claimed / not complete) is a 409 the backend enveloped as an
  /// error; dio raises it as a `DioException`, which the controller catches to
  /// surface a non-blocking message and leave state unchanged.
  Future<ClaimResult> claimChallenge(String clientId, String challengeId) async {
    final response = await _api.dio.post<dynamic>(
      '/clients/$clientId/challenges/$challengeId/claim',
    );
    final data = unwrapEnvelope(response) as Map<String, dynamic>;
    return ClaimResult.fromJson(data);
  }
}
