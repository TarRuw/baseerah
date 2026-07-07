import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../../theme/baseerah_theme.dart';

/// Animated Financial Stress Score gauge (DESIGN §7.1/§8).
///
/// A 270° arc (90° gap at the bottom) split into the three §5.1 zones —
/// CRITICAL `0–40` (red), WARNING `40–70` (orange), OPTIMAL `70–100` (green) —
/// with a marker that eases to [score] over 1000 ms (cubic ease-out). The centre
/// readout uses [color] (the server-resolved zone hex) so the number and the
/// served zone never disagree. The count-up number and marker share one curve.
class StressGauge extends StatefulWidget {
  const StressGauge({
    super.key,
    required this.score,
    required this.color,
    required this.zoneLabel,
    required this.caption,
  });

  /// 0–100 stress index (higher = healthier).
  final int score;

  /// Zone colour served by the API (`#RRGGBB`), used for the centre number.
  final Color color;

  /// Localized zone name (Optimal/Warning/Critical).
  final String zoneLabel;

  /// Localized "out of 100" caption under the number.
  final String caption;

  @override
  State<StressGauge> createState() => _StressGaugeState();
}

class _StressGaugeState extends State<StressGauge>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late Animation<double> _animation;

  // The value the marker animates *from* — 0 on first render, then the previous
  // score whenever a new live score arrives, so updates tween smoothly.
  double _from = 0;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: BaseerahTokens.scoreAnimation,
    );
    _animation = CurvedAnimation(
      parent: _controller,
      curve: BaseerahTokens.scoreAnimationCurve,
    );
    _controller.forward();
  }

  @override
  void didUpdateWidget(covariant StressGauge oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.score != widget.score) {
      _from = oldWidget.score.toDouble();
      _controller
        ..reset()
        ..forward();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    // Cap the diameter so the 1:1 gauge can never dominate the screen, even in a
    // container wider than a phone (QA UI-01, defense in depth). No-op at phone
    // width, where the available width is already below the cap.
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(
          maxWidth: BaseerahTokens.gaugeMaxDiameter,
          maxHeight: BaseerahTokens.gaugeMaxDiameter,
        ),
        child: AspectRatio(
          aspectRatio: 1,
          child: AnimatedBuilder(
            animation: _animation,
            builder: (context, _) {
              final displayed =
                  _from + (widget.score - _from) * _animation.value;
              return CustomPaint(
                painter: _GaugePainter(
                  fraction: (displayed / 100).clamp(0.0, 1.0),
                ),
                child: Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        displayed.round().toString(),
                        style: textTheme.displaySmall?.copyWith(
                          color: widget.color,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      Text(
                        widget.caption,
                        style: textTheme.bodySmall?.copyWith(
                          color: BaseerahTokens.muted,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: widget.color.withValues(alpha: 0.12),
                          borderRadius: BorderRadius.circular(
                            BaseerahTokens.radiusControl,
                          ),
                        ),
                        child: Text(
                          widget.zoneLabel,
                          style: textTheme.labelLarge?.copyWith(
                            color: widget.color,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              );
            },
          ),
        ),
      ),
    );
  }
}

/// Paints the static 3-zone arc plus the animated marker at [fraction] (0–1).
class _GaugePainter extends CustomPainter {
  _GaugePainter({required this.fraction});

  /// Position of the marker along the arc, 0 (score 0) → 1 (score 100).
  final double fraction;

  // 270° arc with the gap centred at the bottom: start down-left, sweep clockwise
  // over the top to down-right (Flutter canvas: 0 = 3 o'clock, +angle clockwise).
  static const double _startAngle = math.pi * 0.75;
  static const double _sweepAngle = math.pi * 1.5;
  static const double _stroke = 16;

  // Zone boundaries on the 0–1 track (§5.1: <40 critical, 40–70 warning, ≥70 optimal).
  static const double _criticalEnd = 0.40;
  static const double _warningEnd = 0.70;

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = math.min(size.width, size.height) / 2 - _stroke;
    final rect = Rect.fromCircle(center: center, radius: radius);

    void arc(double from, double to, Color color) {
      final paint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = _stroke
        ..strokeCap = StrokeCap.round
        ..color = color;
      canvas.drawArc(
        rect,
        _startAngle + _sweepAngle * from,
        _sweepAngle * (to - from),
        false,
        paint,
      );
    }

    // Three zone segments (drawn slightly inset from each other via round caps).
    arc(0.0, _criticalEnd, BaseerahTokens.alertRed);
    arc(_criticalEnd, _warningEnd, BaseerahTokens.warningOrange);
    arc(_warningEnd, 1.0, BaseerahTokens.successGreen);

    // Marker: a white dot with a dark ring, centred on the arc at [fraction].
    final markerAngle = _startAngle + _sweepAngle * fraction;
    final markerCenter = Offset(
      center.dx + radius * math.cos(markerAngle),
      center.dy + radius * math.sin(markerAngle),
    );
    canvas.drawCircle(
      markerCenter,
      _stroke * 0.62,
      Paint()..color = Colors.white,
    );
    canvas.drawCircle(
      markerCenter,
      _stroke * 0.62,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 3
        ..color = BaseerahTokens.darkText,
    );
  }

  @override
  bool shouldRepaint(covariant _GaugePainter oldDelegate) =>
      oldDelegate.fraction != fraction;
}
