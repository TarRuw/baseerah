package com.baseerah.genai;

import com.baseerah.genai.GenAiClient.ChatContext;
import com.baseerah.genai.GenAiClient.ChatReply;
import com.baseerah.genai.GenAiClient.InvoiceParseResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * Real {@link GenAiClient} that calls the provider's Messages API (DESIGN.md §2, §5, §9), selected by
 * {@code GENAI_PROVIDER=remote} with a key present (see {@link GenAiConfig}). It also implements
 * {@link StreamingGenAiClient}, so the chat endpoint can surface a token stream and hit the DESIGN §9
 * time-to-first-token &lt; 1.0&nbsp;s target.
 *
 * <p><strong>No secrets or endpoints are hardcoded:</strong> base URL, model, API key, version, and the
 * max-tokens cap all arrive from configuration ({@code baseerah.genai.remote.*}, keyed off env vars). The
 * request is built directly against the documented Messages wire format using the JDK's built-in
 * {@link HttpClient} and the Jackson mapper already on the classpath — no extra dependency, and no
 * concrete-provider type leaks past the {@link GenAiClient} interface (Global Rule).
 *
 * <p><strong>Grounding stays leak-safe (Step 7.1):</strong> only the {@link ChatContext} summary — persona
 * label, score, zone, monthly cash flow — is sent. Raw account ids and transactions never reach the
 * provider, because the interface never carries them.
 */
public class RemoteGenAi implements GenAiClient, StreamingGenAiClient {

    private static final Logger log = LoggerFactory.getLogger(RemoteGenAi.class);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final String messagesUri;
    private final String model;
    private final String apiKey;
    private final int maxTokens;
    private final String version;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RemoteGenAi(String baseUrl, String model, String apiKey, int maxTokens, String version) {
        this.messagesUri = stripTrailingSlash(baseUrl) + "/v1/messages";
        this.model = model;
        this.apiKey = apiKey;
        this.maxTokens = maxTokens;
        this.version = version;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /**
     * Grounded reply, buffered. Streams under the hood (so time-to-first-token at the provider stays low) but
     * accumulates the deltas and returns the whole reply — the non-streaming {@link GenAiClient} contract
     * every existing caller relies on is unchanged.
     */
    @Override
    public ChatReply chat(ChatContext context, String message) {
        StringBuilder reply = new StringBuilder();
        streamChat(context, message, reply::append);
        return new ChatReply(reply.toString());
    }

    @Override
    public void streamChat(ChatContext context, String message, Consumer<String> onChunk) {
        HttpRequest request = messagesRequest(chatBody(context, message));
        try {
            HttpResponse<Stream<String>> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (!isSuccess(response.statusCode())) {
                throw new GenAiException("GenAI provider returned HTTP " + response.statusCode());
            }
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> {
                    String delta = textDelta(line);
                    if (delta != null && !delta.isEmpty()) {
                        onChunk.accept(delta);
                    }
                });
            }
        } catch (IOException e) {
            throw new GenAiException("Streaming request to GenAI provider failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenAiException("Streaming request to GenAI provider was interrupted", e);
        }
    }

    /**
     * Real invoice OCR (unlike the mock's stub): sends the image to the provider's vision model and asks for
     * the four action fields as JSON. Defensive — any unexpected model output falls back to a clearly-labelled
     * manual-entry result so the Step 3.5 invoice-upload flow never fails hard in remote mode.
     */
    @Override
    public InvoiceParseResult parseInvoice(byte[] image) {
        HttpRequest request = messagesRequest(invoiceBody(image));
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (!isSuccess(response.statusCode())) {
                throw new GenAiException(
                        "GenAI provider returned HTTP " + response.statusCode() + " for invoice parse");
            }
            return parseInvoiceResponse(response.body());
        } catch (IOException e) {
            throw new GenAiException("Invoice-parse request to GenAI provider failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenAiException("Invoice-parse request was interrupted", e);
        }
    }

    // --- request building -------------------------------------------------------------------------------

    private HttpRequest messagesRequest(String body) {
        return HttpRequest.newBuilder(URI.create(messagesUri))
                .timeout(REQUEST_TIMEOUT)
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", version)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    /** Streaming chat request: telemetry grounding in {@code system}, the user text as the single message. */
    private String chatBody(ChatContext context, String message) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", maxTokens);
        root.put("stream", true);
        root.put("system", systemPrompt(context));
        ObjectNode user = root.putArray("messages").addObject();
        user.put("role", "user");
        user.put("content", message == null ? "" : message);
        return writeJson(root);
    }

    /** Non-streaming vision request: image block + a JSON-only instruction. */
    private String invoiceBody(byte[] image) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", maxTokens);
        ObjectNode user = root.putArray("messages").addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");

        ObjectNode imageBlock = content.addObject();
        imageBlock.put("type", "image");
        ObjectNode source = imageBlock.putObject("source");
        source.put("type", "base64");
        source.put("media_type", mediaType(image));
        source.put("data", Base64.getEncoder().encodeToString(image == null ? new byte[0] : image));

        ObjectNode textBlock = content.addObject();
        textBlock.put("type", "text");
        textBlock.put("text", "Extract this invoice as compact JSON with exactly these keys: "
                + "merchant (string), amount (number, in SAR), category (string), "
                + "suggestedAction (string — one actionable next step for the user). "
                + "Reply with the JSON object only, no prose.");
        return writeJson(root);
    }

    private static String systemPrompt(ChatContext ctx) {
        // Pin the reply language to the request locale (Accept-Language), so a real reply follows the same
        // locale the mock and every other content string does (Step 8.1, I18N-01).
        boolean arabic = "ar".equalsIgnoreCase(LocaleContextHolder.getLocale().getLanguage());
        String language = arabic ? "Arabic" : "English";
        return "You are Baseerah, a Saudi personal-finance assistant. Reply strictly in " + language
                + ", concisely, grounded ONLY in the telemetry below. Never invent "
                + "account numbers, transactions, or figures beyond these.\n"
                + "Client profile: " + ctx.profileLabel() + "\n"
                + "Financial health score: " + ctx.currentScore() + "/100 (" + zoneLabel(ctx.zone()) + ")\n"
                + "Mean monthly income: " + money(ctx.monthlyIncome()) + " SAR\n"
                + "Mean monthly outflow: " + money(ctx.monthlyOutflow()) + " SAR\n"
                + "Mean monthly surplus: " + money(ctx.surplus()) + " SAR";
    }

    // --- response parsing -------------------------------------------------------------------------------

    /**
     * Extract the text of one streamed SSE line, or {@code null} for non-text lines. Anthropic emits one
     * compact JSON object per {@code data:} line; we forward {@code content_block_delta / text_delta} text
     * and raise on an {@code error} event. Malformed/partial lines are skipped rather than aborting the run.
     */
    private String textDelta(String line) {
        if (line == null || !line.startsWith("data:")) {
            return null;
        }
        String json = line.substring("data:".length()).trim();
        if (json.isEmpty() || "[DONE]".equals(json)) {
            return null;
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return null;
        }
        String type = node.path("type").asText();
        if ("content_block_delta".equals(type)) {
            JsonNode delta = node.path("delta");
            if ("text_delta".equals(delta.path("type").asText())) {
                return delta.path("text").asText("");
            }
        } else if ("error".equals(type)) {
            throw new GenAiException(
                    "GenAI provider stream error: " + node.path("error").path("message").asText("unknown"));
        }
        return null;
    }

    private InvoiceParseResult parseInvoiceResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String text = "";
            for (JsonNode block : root.path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    text = block.path("text").asText("");
                    break;
                }
            }
            JsonNode parsed = objectMapper.readTree(extractJson(text));
            return new InvoiceParseResult(
                    parsed.path("merchant").asText("Unknown merchant"),
                    new BigDecimal(parsed.path("amount").asText("0")),
                    parsed.path("category").asText("Uncategorized"),
                    parsed.path("suggestedAction").asText("Review this expense against your 30-day forecast."));
        } catch (IOException | NumberFormatException e) {
            log.warn("Could not parse remote invoice response; returning a manual-entry fallback.", e);
            return new InvoiceParseResult("Unknown merchant", BigDecimal.ZERO, "Uncategorized",
                    "Could not read this invoice automatically — enter the amount manually.");
        }
    }

    // --- small helpers ----------------------------------------------------------------------------------

    /** Trim a code-fenced / prose-wrapped reply down to its first {@code {...}} JSON object. */
    private static String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : "{}";
    }

    /** Detect PNG vs JPEG from the magic bytes so the {@code media_type} is correct; default JPEG. */
    private static String mediaType(byte[] image) {
        if (image != null && image.length >= 4
                && (image[0] & 0xFF) == 0x89 && image[1] == 'P' && image[2] == 'N' && image[3] == 'G') {
            return "image/png";
        }
        return "image/jpeg";
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String zoneLabel(com.baseerah.stress.Zone zone) {
        String name = zone.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String money(BigDecimal value) {
        return String.format(Locale.US, "%,d", Math.round(value.doubleValue()));
    }

    private String writeJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new GenAiException("Failed to serialize GenAI request", e);
        }
    }

    /** Unchecked failure from the remote provider (network, non-2xx, or a stream {@code error} event). */
    public static final class GenAiException extends RuntimeException {
        GenAiException(String message) {
            super(message);
        }

        GenAiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
