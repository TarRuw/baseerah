import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../../home/widgets/stress_gauge.dart';
import '../data/rescue_models.dart';

/// The before→after score recovery for the Rescue *complete* state (DESIGN
/// §7.3), realized by **reusing the Home [StressGauge]**: the gauge mounts at
/// [RescueOutcome.scoreBefore], then — once it has settled — swaps to
/// [RescueOutcome.scoreAfter], and the gauge's own 1000 ms cubic ease-out
/// `didUpdateWidget` tween plays the recovery sweep (marker + count-up) for free.
///
/// The zone colour/label are derived client-side from the displayed score using
/// the §5.1 thresholds (the recovery response carries no zone), so the centre
/// number is coloured by the band it lands in — the same visual language as Home.
class RecoveryGauge extends StatefulWidget {
  const RecoveryGauge({super.key, required this.outcome});

  final RescueOutcome outcome;

  @override
  State<RecoveryGauge> createState() => _RecoveryGaugeState();
}

class _RecoveryGaugeState extends State<RecoveryGauge> {
  bool _showAfter = false;

  @override
  void initState() {
    super.initState();
    // Let the gauge finish its initial settle on `before` (its own 1000 ms
    // animation), then trigger the recovery sweep to `after`.
    Future.delayed(BaseerahTokens.scoreAnimation + const Duration(milliseconds: 150), () {
      if (mounted) setState(() => _showAfter = true);
    });
  }

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
    final score = _showAfter
        ? widget.outcome.scoreAfter
        : widget.outcome.scoreBefore;
    final (color, zoneLabel) = _zone(score, l);

    return StressGauge(
      score: score,
      color: color,
      zoneLabel: zoneLabel,
      caption: l.outOf100,
    );
  }
}
