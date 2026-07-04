import 'challenge.dart';
import 'rewards.dart';

// Client-side view of the claim response (FR-10, `POST …/challenges/{cid}/claim`),
// mirroring the backend `ClaimResponse {points, riskTier, challenge}`: the new
// balance/tier after the award plus the just-claimed challenge (`claimed:true`),
// so the Goals screen flips the card and the points header in place — no re-fetch.

/// The outcome of a successful claim: the updated [rewards] summary and the
/// [challenge] now in its `claimed` state.
class ClaimResult {
  const ClaimResult({required this.rewards, required this.challenge});

  final Rewards rewards;
  final Challenge challenge;

  factory ClaimResult.fromJson(Map<String, dynamic> json) {
    return ClaimResult(
      // The claim response carries points + riskTier at its top level — the same
      // shape `Rewards` parses from `GET /rewards`, so reuse its factory.
      rewards: Rewards.fromJson(json),
      challenge: Challenge.fromJson(json['challenge'] as Map<String, dynamic>),
    );
  }
}
