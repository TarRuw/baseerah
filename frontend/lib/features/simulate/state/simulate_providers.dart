import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../api/api_client.dart';
import '../../home/state/home_providers.dart';
import '../data/chat_message.dart';
import '../data/invoice_result.dart';
import '../data/loan_result.dart';
import '../data/simulate_repository.dart';

// ── Repository + current client ─────────────────────────────────────────────

/// Rebuilds with the api client so `Accept-Language` tracks the active locale.
final simulateRepositoryProvider = Provider<SimulateRepository>(
  (ref) => SimulateRepository(ref.watch(apiClientProvider)),
);

/// The current persona's id, reusing Home's resolver (Step 2.3). Null while the
/// `/clients` call is still in flight — the notifiers stay idle until it lands.
final currentClientIdProvider = Provider<String?>(
  (ref) => ref.watch(currentClientProvider).valueOrNull?.id,
);

// ── Loan Affordability tab ──────────────────────────────────────────────────

/// The three loan inputs, now free numeric fields rather than capped sliders so
/// the simulation can model real-world facilities. Bounds are guardrails the
/// input fields clamp to, not prototype slider ticks: principal 1k–10M SAR ·
/// rate 0–100% · term 1–600 months (up to 50 years).
class LoanInputs {
  const LoanInputs({
    required this.principal,
    required this.rate,
    required this.term,
  });

  final double principal;
  final double rate;
  final int term;

  static const double principalMin = 1000;
  static const double principalMax = 10000000;
  static const double rateMin = 0;
  static const double rateMax = 100;
  static const int termMin = 1;
  static const int termMax = 600;

  /// Prototype defaults so the result card is meaningful the moment the tab opens.
  static const LoanInputs defaults = LoanInputs(
    principal: 45000,
    rate: 5.5,
    term: 36,
  );

  LoanInputs copyWith({double? principal, double? rate, int? term}) {
    return LoanInputs(
      principal: principal ?? this.principal,
      rate: rate ?? this.rate,
      term: term ?? this.term,
    );
  }
}

/// Slider inputs paired with the latest (debounced) simulation result. Keeping
/// them together lets one `StateNotifier` own both without provider ping-pong.
class LoanState {
  const LoanState({required this.inputs, required this.result});

  final LoanInputs inputs;
  final AsyncValue<LoanResult> result;

  LoanState copyWith({LoanInputs? inputs, AsyncValue<LoanResult>? result}) {
    return LoanState(
      inputs: inputs ?? this.inputs,
      result: result ?? this.result,
    );
  }
}

/// Owns the loan sliders and their live simulation. Slider setters update
/// [LoanState.inputs] instantly (smooth labels) but coalesce API calls behind a
/// [debounceDelay] timer, so a rapid drag makes one request, not fifty — staying
/// well inside the analytics NFR (DESIGN §9). The repository is read fresh per
/// fetch so requests always carry the current locale's `Accept-Language`.
class LoanController extends StateNotifier<LoanState> {
  LoanController(
    this._ref,
    this._clientId, {
    this.debounceDelay = const Duration(milliseconds: 350),
  }) : super(const LoanState(inputs: LoanInputs.defaults, result: AsyncLoading())) {
    // Seed the result card immediately on open — no debounce for the first fetch.
    if (_clientId != null) {
      _fetch();
    }
  }

  final Ref _ref;
  final String? _clientId;
  final Duration debounceDelay;
  Timer? _debounce;

  void setPrincipal(double value) => _update(state.inputs.copyWith(
        principal:
            value.clamp(LoanInputs.principalMin, LoanInputs.principalMax),
      ));
  void setRate(double value) => _update(state.inputs.copyWith(
        rate: value.clamp(LoanInputs.rateMin, LoanInputs.rateMax),
      ));
  void setTerm(int value) => _update(state.inputs.copyWith(
        term: value.clamp(LoanInputs.termMin, LoanInputs.termMax),
      ));

  /// Apply new inputs at once (label tracks the thumb) and (re)arm the debounce.
  void _update(LoanInputs inputs) {
    state = state.copyWith(inputs: inputs);
    _debounce?.cancel();
    _debounce = Timer(debounceDelay, _fetch);
  }

  Future<void> _fetch() async {
    final id = _clientId;
    if (id == null) return;
    final inputs = state.inputs;
    // Keep the previous result visible while reloading, so dragging never flashes
    // the card back to a spinner.
    state = state.copyWith(
      result: const AsyncLoading<LoanResult>().copyWithPrevious(state.result),
    );
    try {
      final result = await _ref.read(simulateRepositoryProvider).simulate(
        id,
        principal: inputs.principal,
        rate: inputs.rate,
        term: inputs.term,
      );
      if (mounted) state = state.copyWith(result: AsyncData(result));
    } catch (error, stack) {
      if (mounted) state = state.copyWith(result: AsyncError(error, stack));
    }
  }

  @override
  void dispose() {
    _debounce?.cancel();
    super.dispose();
  }
}

final loanControllerProvider =
    StateNotifierProvider.autoDispose<LoanController, LoanState>((ref) {
  final id = ref.watch(currentClientIdProvider);
  return LoanController(ref, id);
});

// ── Ask Baseerah AI tab ─────────────────────────────────────────────────────

/// The growing transcript plus whether the assistant is "typing" (drives the
/// `bsr-blink` dots). Append-only — every turn adds a bubble.
class ChatState {
  const ChatState({required this.messages, required this.isTyping});

  final List<ChatMessage> messages;
  final bool isTyping;

  static const ChatState initial = ChatState(messages: [], isTyping: false);

  /// Suggestion chips show only before the conversation starts (prototype).
  bool get showSuggestions => messages.isEmpty && !isTyping;

  ChatState copyWith({List<ChatMessage>? messages, bool? isTyping}) {
    return ChatState(
      messages: messages ?? this.messages,
      isTyping: isTyping ?? this.isTyping,
    );
  }
}

/// Owns the chat transcript. Each [send] appends the user bubble, shows the
/// typing indicator, then appends the reply (or a graceful error bubble). The
/// mock backend answers instantly, so a short floor keeps the dots on screen
/// long enough to read as intentional feedback (Step 3.5 note).
class ChatController extends StateNotifier<ChatState> {
  ChatController(this._ref, this._clientId) : super(ChatState.initial);

  final Ref _ref;
  final String? _clientId;

  /// Minimum time the typing dots stay visible even though the mock is instant.
  static const Duration _typingFloor = Duration(milliseconds: 500);

  Future<void> send(String text, {required String errorReply}) async {
    final message = text.trim();
    final id = _clientId;
    if (message.isEmpty || id == null || state.isTyping) return;

    _append(ChatMessage.user(message));
    state = state.copyWith(isTyping: true);
    await _withReply(
      () => _ref.read(simulateRepositoryProvider).chat(id, message),
      errorReply: errorReply,
    );
  }

  /// Pick-then-upload an invoice image: the parsed stub returns as an AI bubble.
  /// [render] turns the parsed result into localized bubble text (l10n/Fmt live
  /// in the widget, so formatting is injected rather than pulled into state).
  Future<void> sendInvoice(
    List<int> bytes, {
    required String filename,
    required String Function(InvoiceResult) render,
    required String errorReply,
  }) async {
    final id = _clientId;
    if (id == null || state.isTyping) return;

    state = state.copyWith(isTyping: true);
    await _withReply(
      () async {
        final parsed = await _ref
            .read(simulateRepositoryProvider)
            .parseInvoice(id, bytes, filename: filename);
        return render(parsed);
      },
      errorReply: errorReply,
    );
  }

  /// Run [replyText], keeping the typing dots up for at least [_typingFloor],
  /// then append the AI bubble — or a graceful [errorReply] bubble on failure so
  /// a dropped backend never leaves the dots spinning forever. Shared by text
  /// and invoice turns so both feel identical.
  Future<void> _withReply(
    Future<String> Function() replyText, {
    required String errorReply,
  }) async {
    try {
      final results = await Future.wait([
        replyText(),
        Future<void>.delayed(_typingFloor),
      ]);
      final reply = results.first as String;
      if (!mounted) return;
      state = state.copyWith(
        messages: [...state.messages, ChatMessage.ai(reply)],
        isTyping: false,
      );
    } catch (_) {
      if (!mounted) return;
      state = state.copyWith(
        messages: [...state.messages, ChatMessage.ai(errorReply)],
        isTyping: false,
      );
    }
  }

  void _append(ChatMessage message) {
    state = state.copyWith(messages: [...state.messages, message]);
  }
}

final chatControllerProvider =
    StateNotifierProvider.autoDispose<ChatController, ChatState>((ref) {
  final id = ref.watch(currentClientIdProvider);
  return ChatController(ref, id);
});
