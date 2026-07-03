import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../../../core/format.dart';
import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../data/invoice_result.dart';
import '../state/simulate_providers.dart';
import 'chat_bubble.dart';
import 'typing_indicator.dart';

/// Ask Baseerah AI tab (DESIGN §7.2): a chat card with the assistant header,
/// the bubble transcript, the `bsr-blink` typing dots, suggestion chips, and an
/// input row with an invoice-upload control. All conversation state lives in
/// [ChatController]; this widget only reads it and forwards user actions.
class AskAiTab extends ConsumerStatefulWidget {
  const AskAiTab({super.key});

  @override
  ConsumerState<AskAiTab> createState() => _AskAiTabState();
}

class _AskAiTabState extends ConsumerState<AskAiTab> {
  final _input = TextEditingController();
  final _scroll = ScrollController();
  final _picker = ImagePicker();

  @override
  void dispose() {
    _input.dispose();
    _scroll.dispose();
    super.dispose();
  }

  void _scrollToBottom() {
    // After the frame that added the new bubble, pin to the latest message.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_scroll.hasClients) return;
      _scroll.animateTo(
        _scroll.position.maxScrollExtent,
        duration: const Duration(milliseconds: 250),
        curve: Curves.easeOut,
      );
    });
  }

  void _send(String text) {
    final l = AppLocalizations.of(context);
    _input.clear();
    ref.read(chatControllerProvider.notifier).send(text, errorReply: l.loadError);
    _scrollToBottom();
  }

  Future<void> _uploadInvoice() async {
    final l = AppLocalizations.of(context);
    final fmt = Fmt(Localizations.localeOf(context), l);
    final file = await _picker.pickImage(source: ImageSource.gallery);
    if (file == null) return;
    final bytes = await file.readAsBytes();
    if (!mounted) return;
    await ref.read(chatControllerProvider.notifier).sendInvoice(
          bytes,
          filename: file.name,
          render: (InvoiceResult r) => '${l.invoiceParsed}\n'
              '${l.invoiceParsedBody(r.merchant, fmt.money(r.amount), r.category, r.suggestedAction)}',
          errorReply: l.loadError,
        );
    _scrollToBottom();
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final chat = ref.watch(chatControllerProvider);

    // Auto-scroll whenever the transcript or typing state changes.
    ref.listen(chatControllerProvider, (_, __) => _scrollToBottom());

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
        boxShadow: BaseerahTokens.shadowMedium,
      ),
      child: Column(
        children: [
          const _ChatHeader(),
          const Divider(height: 24),
          // ── Transcript ──────────────────────────────────────────────────
          Expanded(
            child: ListView(
              controller: _scroll,
              children: [
                for (final message in chat.messages) ...[
                  ChatBubble(message: message),
                  const SizedBox(height: 12),
                ],
                if (chat.isTyping) const TypingIndicator(),
              ],
            ),
          ),
          // ── Suggestion chips (before the conversation starts) ───────────
          if (chat.showSuggestions)
            Padding(
              padding: const EdgeInsets.only(top: 8, bottom: 4),
              child: Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  _SuggestionChip(label: l.suggestionLease, onTap: _send),
                  _SuggestionChip(label: l.suggestionAfford, onTap: _send),
                ],
              ),
            ),
          const SizedBox(height: 8),
          // ── Input row ───────────────────────────────────────────────────
          _InputBar(
            controller: _input,
            hint: l.askPlaceholder,
            onSend: () => _send(_input.text),
            onAttach: _uploadInvoice,
            sendLabel: l.chatSend,
            attachLabel: l.chatAttachInvoice,
          ),
        ],
      ),
    );
  }
}

/// Assistant header: gold gem avatar, name, and an online status line.
class _ChatHeader extends StatelessWidget {
  const _ChatHeader();

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    return Row(
      children: [
        Container(
          width: 30,
          height: 30,
          decoration: BoxDecoration(
            gradient: BaseerahTokens.goldGradient,
            borderRadius: BorderRadius.circular(9),
          ),
          child: const Icon(Icons.auto_awesome, color: Colors.white, size: 16),
        ),
        const SizedBox(width: 9),
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              l.aiName,
              style: textTheme.bodyMedium?.copyWith(
                color: BaseerahTokens.darkText,
                fontWeight: FontWeight.w600,
              ),
            ),
            Text(
              l.aiOnline,
              style: textTheme.labelSmall?.copyWith(
                color: BaseerahTokens.successGreen,
              ),
            ),
          ],
        ),
      ],
    );
  }
}

/// A tappable suggestion chip that sends its own label as a message.
class _SuggestionChip extends StatelessWidget {
  const _SuggestionChip({required this.label, required this.onTap});

  final String label;
  final ValueChanged<String> onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: () => onTap(label),
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          color: const Color(0xFFEEF5F2),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: BaseerahTokens.teal.withValues(alpha: 0.2)),
        ),
        child: Text(
          label,
          style: Theme.of(context).textTheme.labelMedium?.copyWith(
            color: BaseerahTokens.teal,
          ),
        ),
      ),
    );
  }
}

/// The bottom input bar: invoice-attach button, text field, send button.
class _InputBar extends StatelessWidget {
  const _InputBar({
    required this.controller,
    required this.hint,
    required this.onSend,
    required this.onAttach,
    required this.sendLabel,
    required this.attachLabel,
  });

  final TextEditingController controller;
  final String hint;
  final VoidCallback onSend;
  final VoidCallback onAttach;
  final String sendLabel;
  final String attachLabel;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsetsDirectional.only(start: 6, end: 5, top: 5, bottom: 5),
      decoration: BoxDecoration(
        color: const Color(0xFFF2F0E9),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        children: [
          IconButton(
            tooltip: attachLabel,
            onPressed: onAttach,
            icon: const Icon(Icons.image_outlined, color: BaseerahTokens.muted),
          ),
          Expanded(
            child: TextField(
              controller: controller,
              textInputAction: TextInputAction.send,
              onSubmitted: (_) => onSend(),
              decoration: InputDecoration(
                hintText: hint,
                border: InputBorder.none,
                filled: false,
                isCollapsed: true,
              ),
            ),
          ),
          const SizedBox(width: 8),
          Semantics(
            label: sendLabel,
            button: true,
            child: InkWell(
              onTap: onSend,
              borderRadius: BorderRadius.circular(11),
              child: Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  color: BaseerahTokens.teal,
                  borderRadius: BorderRadius.circular(11),
                ),
                child: const Icon(Icons.send, color: Colors.white, size: 18),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
