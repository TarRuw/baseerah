import 'package:flutter/material.dart';

import '../../../theme/baseerah_theme.dart';
import '../data/chat_message.dart';

/// One chat bubble (DESIGN §7.2). User messages align to the trailing edge in
/// teal; AI messages to the leading edge in a light grey. Both alignment and the
/// bubble's "tail" corner use directional geometry (`AlignmentDirectional` +
/// `BorderRadiusDirectional`), so the whole conversation mirrors automatically
/// under RTL — user on the left, AI on the right, in Arabic.
class ChatBubble extends StatelessWidget {
  const ChatBubble({super.key, required this.message});

  final ChatMessage message;

  static const Color _aiBg = Color(0xFFEEF2F0);

  @override
  Widget build(BuildContext context) {
    final isUser = message.role == ChatRole.user;
    final radius = isUser
        // Tail at the trailing-bottom corner (next to the sender).
        ? const BorderRadiusDirectional.only(
            topStart: Radius.circular(16),
            topEnd: Radius.circular(16),
            bottomStart: Radius.circular(16),
            bottomEnd: Radius.circular(4),
          )
        : const BorderRadiusDirectional.only(
            topStart: Radius.circular(16),
            topEnd: Radius.circular(16),
            bottomEnd: Radius.circular(16),
            bottomStart: Radius.circular(4),
          );

    return Align(
      alignment: isUser
          ? AlignmentDirectional.centerEnd
          : AlignmentDirectional.centerStart,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: MediaQuery.sizeOf(context).width * 0.82,
        ),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
          decoration: BoxDecoration(
            color: isUser ? BaseerahTokens.teal : _aiBg,
            borderRadius: radius,
          ),
          child: Text(
            message.text,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: isUser ? Colors.white : BaseerahTokens.darkText,
              height: 1.5,
            ),
          ),
        ),
      ),
    );
  }
}
