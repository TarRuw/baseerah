// Client-side view of a single gamified challenge (FR-09/10, DESIGN §5.6/§7.4),
// mirroring the backend `ChallengeDto` served by
// `GET /api/v1/clients/{id}/challenges` and echoed inside the claim response.
//
// `pct` is the completion percentage in [0,100]; the three mutually-exclusive
// button states the Goals screen renders are derived from `pct`/`claimable`/
// `claimed` via [buttonState] so the widget never re-derives the rule itself.

/// The action button's state for a challenge card (DESIGN §7.4):
/// *in-progress* (disabled) · *claim* (enabled) · *claimed* (disabled).
enum ClaimButtonState { inProgress, claim, claimed }

/// A challenge as the Goals screen consumes it — the projection of a backend
/// `Challenge` joined with its progress. [id] is the claim target; [icon] is a
/// semantic token ("restaurant", "savings", …) the card maps to a glyph.
class Challenge {
  const Challenge({
    required this.id,
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.reward,
    required this.pct,
    required this.progressText,
    required this.claimable,
    required this.claimed,
  });

  /// Challenge id (UUID string) — the claim endpoint's `{cid}` path segment.
  final String id;

  /// Semantic icon token the card maps to a Material glyph; unknown → default.
  final String icon;
  final String title;
  final String subtitle;

  /// Points awarded on claim (the challenge's `reward_points`).
  final int reward;

  /// Completion percentage in [0,100]; drives the progress bar and its colour.
  final int pct;

  /// Server-formatted, locale-aware progress line, e.g. `"541 / 2,000 SAR"`.
  final String progressText;

  /// True when the reward can be claimed now (`pct >= 100 && !claimed`).
  final bool claimable;

  /// Whether the reward has already been claimed.
  final bool claimed;

  /// True once complete, whether or not it's been claimed — drives the
  /// gold → success-green progress-bar transition (`pct >= 100`).
  bool get isComplete => pct >= 100;

  /// The mutually-exclusive button state (single source of the §7.4 rule):
  /// claimed wins, then a claimable/complete goal, else in-progress.
  ClaimButtonState get buttonState {
    if (claimed) return ClaimButtonState.claimed;
    if (claimable) return ClaimButtonState.claim;
    return ClaimButtonState.inProgress;
  }

  factory Challenge.fromJson(Map<String, dynamic> json) {
    return Challenge(
      id: json['id'] as String,
      icon: (json['icon'] as String?) ?? 'savings',
      title: json['title'] as String,
      subtitle: (json['subtitle'] as String?) ?? '',
      reward: (json['reward'] as num).toInt(),
      pct: (json['pct'] as num).toInt(),
      progressText: json['progressText'] as String,
      claimable: json['claimable'] as bool,
      claimed: json['claimed'] as bool,
    );
  }
}
