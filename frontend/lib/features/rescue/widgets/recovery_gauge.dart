import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../../home/widgets/stress_gauge.dart';
import '../data/rescue_models.dart';

/// The score recovery for the Rescue *complete* state (DESIGN §7.3), realized by
/// **reusing the Home [StressGauge]**: the gauge is driven by the **recovered**
/// score ([RescueOutcome.scoreAfter]) so the headline number matches the
/// "recovers X → Y" caption beneath it (QA UI-03). The gauge's own 1000 ms cubic
/// ease-out count-up animates up to the recovered value on mount for free.
///
/// It reads the recovered score rather than the *before* score on purpose: for a
/// screen titled "score recovery" the emphatic number must be the improved score,
/// never the pre-rescue one (which would contradict the caption). The before value
/// is still communicated by the caption/message text, not the gauge headline.
///
/// The zone colour/label are derived client-side from the recovered score using
/// the §5.1 thresholds (the recovery response carries no zone), so the centre
/// number is coloured by the band it lands in — the same visual language as Home.
class RecoveryGauge extends StatelessWidget {
  const RecoveryGauge({super.key, required this.outcome});

  final RescueOutcome outcome;

  /// §5.1 zone → (centre colour, localized label): CRITICAL <40, WARNING 40–70,
  /// OPTIMAL ≥70. Mirrors the boundaries the gauge painter draws.
  (Color, String) _zone(int score, AppLocalizations l) {
    if (score < 40) return (BaseerahTokens.alertRed, l.zoneCritical);
    if (score < 70) return (BaseerahTokens.warningOrange, l.zoneWarning);
    return (BaseerahTokens.successGreen, l.zoneOptimal);
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final score = outcome.scoreAfter;
    final (color, zoneLabel) = _zone(score, l);

    return StressGauge(
      score: score,
      color: color,
      zoneLabel: zoneLabel,
      caption: l.outOf100,
    );
  }
}
