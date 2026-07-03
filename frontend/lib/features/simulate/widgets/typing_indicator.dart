import 'package:flutter/material.dart';

import '../../../theme/baseerah_theme.dart';

/// The `bsr-blink` typing dots (DESIGN §8): three dots whose opacity pulses
/// `.35 → 1 → .35` over 1s, each staggered by 0.2s so the blink travels left to
/// right. Shown in an AI-aligned bubble while the assistant is "typing".
class TypingIndicator extends StatefulWidget {
  const TypingIndicator({super.key});

  @override
  State<TypingIndicator> createState() => _TypingIndicatorState();
}

class _TypingIndicatorState extends State<TypingIndicator>
    with SingleTickerProviderStateMixin {
  // One 1s loop drives all three dots; the design's per-dot delay becomes a
  // phase offset so they never drift out of sync.
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(seconds: 1),
  )..repeat();

  static const List<double> _offsets = [0.0, 0.2, 0.4];

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  /// Triangle wave over [0,1) matching `0%/100% → .35`, `50% → 1`.
  double _opacityAt(double t) {
    final phase = t % 1.0;
    final triangle = phase < 0.5 ? phase * 2 : (1 - phase) * 2;
    return 0.35 + triangle * 0.65;
  }

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: AlignmentDirectional.centerStart,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: const BoxDecoration(
          color: Color(0xFFEEF2F0),
          borderRadius: BorderRadiusDirectional.only(
            topStart: Radius.circular(16),
            topEnd: Radius.circular(16),
            bottomEnd: Radius.circular(16),
            bottomStart: Radius.circular(4),
          ),
        ),
        child: AnimatedBuilder(
          animation: _controller,
          builder: (context, _) {
            return Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                for (var i = 0; i < _offsets.length; i++) ...[
                  if (i > 0) const SizedBox(width: 4),
                  Opacity(
                    opacity: _opacityAt(_controller.value + _offsets[i]),
                    child: Container(
                      width: 6,
                      height: 6,
                      decoration: const BoxDecoration(
                        color: BaseerahTokens.muted,
                        shape: BoxShape.circle,
                      ),
                    ),
                  ),
                ],
              ],
            );
          },
        ),
      ),
    );
  }
}
