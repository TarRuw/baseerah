package com.baseerah.genai;

import static org.assertj.core.api.Assertions.assertThat;

import com.baseerah.common.Messages;
import com.baseerah.genai.GenAiClient.ChatContext;
import com.baseerah.genai.GenAiClient.ChatReply;
import com.baseerah.stress.Zone;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Step 7.2 acceptance: provider selection, keyless fallback, an unchanged {@link GenAiClient} interface, and
 * that {@link RemoteGenAi} issues a <em>streaming</em> request and yields its first chunk without buffering
 * the whole reply. No Spring, no database, and <strong>no live network</strong> — the streaming tests point
 * {@link RemoteGenAi} at a local JDK {@link HttpServer} stub that emits canned SSE deltas.
 */
class GenAiProviderSelectionTest {

    private static final ChatContext FAMILY = new ChatContext(
            "Family / dual income", 62, Zone.WARNING,
            new BigDecimal("18500"), new BigDecimal("14200"));

    private static final Messages MESSAGES = Messages.forTests();

    // --- selection + keyless fallback -------------------------------------------------------------------

    @Test
    void remoteWithoutKeyFallsBackToMockAndChatStillWorks() {
        GenAiClient client = new GenAiConfig().genAiClient(props("remote", null), MESSAGES);

        assertThat(client).isInstanceOf(MockGenAi.class);
        assertThat(client.chat(FAMILY, "How am I doing?").reply()).isNotBlank(); // no exception, valid reply
    }

    @Test
    void remoteWithBlankKeyAlsoFallsBackToMock() {
        assertThat(new GenAiConfig().genAiClient(props("remote", "   "), MESSAGES)).isInstanceOf(MockGenAi.class);
    }

    @Test
    void unsetProviderUsesMock() {
        assertThat(new GenAiConfig().genAiClient(new GenAiProperties(), MESSAGES)).isInstanceOf(MockGenAi.class);
    }

    @Test
    void explicitMockUsesMock() {
        assertThat(new GenAiConfig().genAiClient(props("mock", null), MESSAGES)).isInstanceOf(MockGenAi.class);
    }

    @Test
    void remoteWithKeyUsesRemoteGenAi() {
        assertThat(new GenAiConfig().genAiClient(props("remote", "sk-test-key"), MESSAGES))
                .isInstanceOf(RemoteGenAi.class);
    }

    // --- interface stays frozen -------------------------------------------------------------------------

    @Test
    void genAiClientInterfaceSignatureIsUnchangedAcrossProviders() throws NoSuchMethodException {
        Method chat = GenAiClient.class.getMethod("chat", ChatContext.class, String.class);
        Method parseInvoice = GenAiClient.class.getMethod("parseInvoice", byte[].class);

        assertThat(chat.getReturnType()).isEqualTo(ChatReply.class);
        assertThat(parseInvoice.getReturnType()).isEqualTo(GenAiClient.InvoiceParseResult.class);
        // Both providers implement the same, unchanged interface.
        assertThat(GenAiClient.class).isAssignableFrom(MockGenAi.class);
        assertThat(GenAiClient.class).isAssignableFrom(RemoteGenAi.class);
    }

    // --- streaming request, no buffering, no live network -----------------------------------------------

    @Test
    void remoteIssuesStreamingRequestAndYieldsFirstChunkWithoutBuffering() throws IOException {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = startStub(capturedBody,
                delta("Hello"), delta(" world"), "{\"type\":\"message_stop\"}");
        try {
            List<String> chunks = new CopyOnWriteArrayList<>();
            remoteFor(server).streamChat(FAMILY, "hi", chunks::add);

            // Two separate chunks (not one buffered blob) proves per-delta streaming.
            assertThat(chunks).containsExactly("Hello", " world");
            assertThat(capturedBody.get()).contains("\"stream\":true");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void chatBuffersStreamedDeltasIntoTheFullReply() throws IOException {
        HttpServer server = startStub(new AtomicReference<>(),
                delta("Hello"), delta(", "), delta("Baseerah"), "{\"type\":\"message_stop\"}");
        try {
            assertThat(remoteFor(server).chat(FAMILY, "hi").reply()).isEqualTo("Hello, Baseerah");
        } finally {
            server.stop(0);
        }
    }

    // --- helpers ----------------------------------------------------------------------------------------

    private static GenAiProperties props(String provider, String apiKey) {
        GenAiProperties properties = new GenAiProperties();
        properties.setProvider(provider);
        properties.getRemote().setApiKey(apiKey);
        return properties;
    }

    private static RemoteGenAi remoteFor(HttpServer server) {
        return new RemoteGenAi("http://127.0.0.1:" + server.getAddress().getPort(),
                "claude-opus-4-8", "test-key", 256, "2023-06-01");
    }

    private static String delta(String text) {
        return "{\"type\":\"content_block_delta\",\"index\":0,"
                + "\"delta\":{\"type\":\"text_delta\",\"text\":\"" + text + "\"}}";
    }

    /** Local SSE stub — captures the request body and streams the given {@code data:} events, flushing each. */
    private static HttpServer startStub(AtomicReference<String> capturedBody, String... dataEvents)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("content-type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream body = exchange.getResponseBody()) {
                for (String data : dataEvents) {
                    body.write(("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
                    body.flush();
                }
            }
        });
        server.start();
        return server;
    }
}
