/// Who authored a chat bubble. Drives alignment (user → trailing/right,
/// AI → leading/left) and colour in the chat tab — mirrored automatically under
/// RTL because the UI aligns to start/end, not left/right.
enum ChatRole { user, ai }

/// A single chat bubble in the "Ask Baseerah AI" conversation (Step 3.5).
///
/// Deliberately tiny and presentation-agnostic: the backend `POST .../chat`
/// returns only a reply string, and invoice replies are rendered as an AI
/// message too, so one flat type covers the whole transcript.
class ChatMessage {
  const ChatMessage({required this.role, required this.text});

  const ChatMessage.user(this.text) : role = ChatRole.user;
  const ChatMessage.ai(this.text) : role = ChatRole.ai;

  final ChatRole role;
  final String text;
}
