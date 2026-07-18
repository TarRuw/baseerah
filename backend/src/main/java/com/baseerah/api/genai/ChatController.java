package com.baseerah.api.genai;

import com.baseerah.application.genai.ChatService;
import com.baseerah.api.support.OwnershipGuard;
import com.baseerah.shared.ApiResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Conversational-AI endpoints (FR-03, DESIGN.md §6). Thin: validates input and delegates to
 * {@link ChatService}, wrapping the result in the shared {@link ApiResponse} envelope — no business logic
 * and no direct dependency on a concrete {@link com.baseerah.application.infrastructure.gateway.genai.GenAiClient}
 * (Global Rule). The {@code id} is resolved
 * (strict UUID) inside the service, so an unknown/malformed id yields the shared 404 envelope, matching the
 * other client-scoped controllers.
 */
@RestController
@RequestMapping("/api/v1/clients")
public class ChatController {

    /** Timeout for a streamed chat before the SSE connection is closed. */
    private static final long STREAM_TIMEOUT_MS = 60_000L;

    /** Daemon pool that pumps SSE streams off the request thread (Step 7.2 streaming). */
    private static final ExecutorService STREAM_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "genai-chat-stream");
        thread.setDaemon(true);
        return thread;
    });

    private final ChatService chatService;
    private final OwnershipGuard ownershipGuard;

    public ChatController(ChatService chatService, OwnershipGuard ownershipGuard) {
        this.chatService = chatService;
        this.ownershipGuard = ownershipGuard;
    }

    /** {@code POST /api/v1/clients/{id}/chat} — body {@code {message}} → enveloped {@code {reply}}. */
    @PostMapping("/{id}/chat")
    public ApiResponse<ChatReplyResponse> chat(
            @PathVariable String id, @Valid @RequestBody ChatRequest request) {
        ownershipGuard.assertOwns(id);
        return ApiResponse.ok(ChatWebMapper.toChatResponse(chatService.chat(id, request.message())));
    }

    /**
     * {@code POST /api/v1/clients/{id}/chat/stream} — body {@code {message}} → a Server-Sent Event stream of
     * reply chunks (DESIGN §9 time-to-first-token &lt; 1.0&nbsp;s). Each {@code data:} event carries one text
     * delta; the stream closes when the reply completes. This surfaces the remote adapter's token stream to
     * the Flutter client while the buffered {@link #chat} endpoint keeps working for callers that don't
     * consume a stream (the mock provider emits its whole reply as a single event here).
     */
    @PostMapping(value = "/{id}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@PathVariable String id, @Valid @RequestBody ChatRequest request) {
        ownershipGuard.assertOwns(id);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        STREAM_EXECUTOR.execute(() -> {
            try {
                chatService.streamChat(id, request.message(), chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                });
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    /**
     * {@code POST /api/v1/clients/{id}/chat/invoice} — multipart image upload → enveloped parsed-action stub.
     * The image is sent as the multipart part {@code image}.
     */
    @PostMapping("/{id}/chat/invoice")
    public ApiResponse<InvoiceResponse> parseInvoice(
            @PathVariable String id, @RequestParam("image") MultipartFile image) {
        ownershipGuard.assertOwns(id);
        return ApiResponse.ok(ChatWebMapper.toInvoiceResponse(chatService.parseInvoice(id, readBytes(image))));
    }

    /** Read the uploaded part's bytes, translating the rare I/O failure into an unchecked error. */
    private static byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read uploaded invoice image", ex);
        }
    }
}
