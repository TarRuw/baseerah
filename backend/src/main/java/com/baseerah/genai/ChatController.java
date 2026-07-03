package com.baseerah.genai;

import com.baseerah.common.ApiResponse;
import com.baseerah.genai.GenAiClient.ChatReply;
import com.baseerah.genai.GenAiClient.InvoiceParseResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Conversational-AI endpoints (FR-03, DESIGN.md §6). Thin: validates input and delegates to
 * {@link ChatService}, wrapping the result in the shared {@link ApiResponse} envelope — no business logic
 * and no direct dependency on a concrete {@link GenAiClient} (Global Rule). The {@code id} is resolved
 * (strict UUID) inside the service, so an unknown/malformed id yields the shared 404 envelope, matching the
 * other client-scoped controllers.
 */
@RestController
@RequestMapping("/api/v1/clients")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** {@code POST /api/v1/clients/{id}/chat} — body {@code {message}} → enveloped {@code {reply}}. */
    @PostMapping("/{id}/chat")
    public ApiResponse<ChatReply> chat(@PathVariable String id, @Valid @RequestBody ChatRequest request) {
        return ApiResponse.ok(chatService.chat(id, request.message()));
    }

    /**
     * {@code POST /api/v1/clients/{id}/chat/invoice} — multipart image upload → enveloped parsed-action stub.
     * The image is sent as the multipart part {@code image}.
     */
    @PostMapping("/{id}/chat/invoice")
    public ApiResponse<InvoiceParseResult> parseInvoice(
            @PathVariable String id, @RequestParam("image") MultipartFile image) {
        return ApiResponse.ok(chatService.parseInvoice(id, readBytes(image)));
    }

    /** Read the uploaded part's bytes, translating the rare I/O failure into an unchecked error. */
    private static byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read uploaded invoice image", ex);
        }
    }

    /** Chat request body. {@code message} must be present and non-blank (→ Step 0.4 400 VALIDATION_ERROR). */
    public record ChatRequest(@NotBlank String message) {
    }
}
