package com.jhun.backend.unit.service.support.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhun.backend.config.speech.SpeechProperties;
import com.jhun.backend.service.support.speech.IflytekSpeechProvider;
import com.jhun.backend.service.support.speech.IflytekSpeechWebSocketClient;
import com.jhun.backend.service.support.speech.SpeechTranscriptionRequest;
import com.jhun.backend.service.support.speech.SpeechTranscriptionResult;
import com.jhun.backend.service.support.speech.WavPcmAudioParser;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

class IflytekSpeechWebSocketClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBuildSignedQueryStreamPcmFramesAndAggregateFinalTranscript() throws Exception {
        SpeechProperties.IflytekProperties iflytekProperties = createIflytekProperties();
        List<Duration> pacing = new ArrayList<>();
        List<String> frames = new ArrayList<>();
        RecordingWebSocket webSocket = new RecordingWebSocket();
        URI[] capturedUri = new URI[1];
        WebSocket.Listener[] capturedListener = new WebSocket.Listener[1];

        IflytekSpeechWebSocketClient client = new IflytekSpeechWebSocketClient(
                (uri, listener) -> {
                    capturedUri[0] = uri;
                    capturedListener[0] = listener;
                    listener.onOpen(webSocket);
                    webSocket.setAfterSend(frame -> {
                        frames.add(frame);
                        JsonNode frameNode = objectMapper.readTree(frame);
                        if (frameNode.path("data").path("status").asInt() == 2) {
                            listener.onText(
                                    webSocket,
                                    """
                                            {"code":0,"message":"success","data":{"status":2,"result":{"ws":[{"cw":[{"w":"你好"}]},{"cw":[{"w":"世界"}]}]}}}
                                            """,
                                    true);
                        }
                    });
                    return CompletableFuture.completedFuture(webSocket);
                },
                objectMapper,
                Clock.fixed(Instant.parse("2026-04-07T08:00:00Z"), ZoneOffset.UTC),
                duration -> pacing.add(duration),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1));

        byte[] audioBytes = buildAudioBytes(1280 * 3);
        SpeechTranscriptionResult result = client.transcribe(
                iflytekProperties,
                new SpeechTranscriptionRequest(audioBytes, WavPcmAudioParser.PROVIDER_PCM_CONTENT_TYPE, "zh-CN"),
                IflytekSpeechProvider.IFLYTEK_PROVIDER);

        assertThat(result).isEqualTo(new SpeechTranscriptionResult(
                "你好世界",
                "zh-CN",
                IflytekSpeechProvider.IFLYTEK_PROVIDER));
        assertThat(webSocket.sendCloseCount()).isEqualTo(1);
        assertThat(webSocket.lastCloseStatusCode()).isEqualTo(1000);
        assertThat(webSocket.lastCloseReason()).isEqualTo("client completed");
        assertThat(webSocket.abortCount()).isZero();
        assertThat(capturedListener[0]).isNotNull();
        assertThat(pacing).containsExactly(Duration.ofMillis(40), Duration.ofMillis(40));
        assertThat(frames).hasSize(3);

        JsonNode firstFrame = objectMapper.readTree(frames.get(0));
        JsonNode middleFrame = objectMapper.readTree(frames.get(1));
        JsonNode lastFrame = objectMapper.readTree(frames.get(2));
        assertThat(firstFrame.path("common").path("app_id").asText()).isEqualTo("test-app-id");
        assertThat(firstFrame.path("business").path("language").asText()).isEqualTo("zh_cn");
        assertThat(firstFrame.path("business").path("domain").asText()).isEqualTo("iat");
        assertThat(firstFrame.path("business").path("accent").asText()).isEqualTo("mandarin");
        assertThat(firstFrame.path("data").path("status").asInt()).isEqualTo(0);
        assertThat(middleFrame.path("common").isMissingNode()).isTrue();
        assertThat(middleFrame.path("business").isMissingNode()).isTrue();
        assertThat(middleFrame.path("data").path("status").asInt()).isEqualTo(1);
        assertThat(lastFrame.path("data").path("status").asInt()).isEqualTo(2);
        assertThat(decodeAudio(firstFrame)).hasSize(1280);
        assertThat(decodeAudio(middleFrame)).hasSize(1280);
        assertThat(decodeAudio(lastFrame)).hasSize(1280);

        Map<String, String> query = parseQuery(capturedUri[0]);
        assertThat(capturedUri[0].getRawQuery()).contains("date=Tue%2C%2007%20Apr%202026%2008%3A00%3A00%20GMT");
        assertThat(query.get("host")).isEqualTo("iat-api.xfyun.cn");
        assertThat(query.get("date")).isEqualTo("Tue, 07 Apr 2026 08:00:00 GMT");
        assertThat(new String(Base64.getDecoder().decode(query.get("authorization")), StandardCharsets.UTF_8))
                .isEqualTo(buildExpectedAuthorization(query.get("date"), iflytekProperties));
    }

    @Test
    void shouldMapAuthenticationResponseIntoAuthenticationException() {
        TestClientHandle clientHandle = createClientRespondingWith(
                """
                        {"code":401,"message":"authentication failed","data":{"status":2}}
                        """,
                Duration.ofSeconds(1));

        assertThatThrownBy(() -> clientHandle.client().transcribe(
                        createIflytekProperties(),
                        new SpeechTranscriptionRequest(buildAudioBytes(1280), WavPcmAudioParser.PROVIDER_PCM_CONTENT_TYPE, "zh-CN"),
                        IflytekSpeechProvider.IFLYTEK_PROVIDER))
                .isInstanceOf(IflytekSpeechWebSocketClient.AuthenticationException.class)
                .hasMessageContaining("鉴权失败");
        assertThat(clientHandle.webSocket().sendCloseCount()).isZero();
        assertThat(clientHandle.webSocket().abortCount()).isEqualTo(1);
    }

    @Test
    void shouldMapNonZeroBusinessCodeIntoFailureException() {
        TestClientHandle clientHandle = createClientRespondingWith(
                """
                        {"code":10165,"message":"invalid status","data":{"status":2}}
                        """,
                Duration.ofSeconds(1));

        assertThatThrownBy(() -> clientHandle.client().transcribe(
                        createIflytekProperties(),
                        new SpeechTranscriptionRequest(buildAudioBytes(1280), WavPcmAudioParser.PROVIDER_PCM_CONTENT_TYPE, "zh-CN"),
                        IflytekSpeechProvider.IFLYTEK_PROVIDER))
                .isInstanceOf(IflytekSpeechWebSocketClient.FailureException.class)
                .hasMessageContaining("code=10165");
        assertThat(clientHandle.webSocket().sendCloseCount()).isZero();
        assertThat(clientHandle.webSocket().abortCount()).isEqualTo(1);
    }

    @Test
    void shouldMapIdleOrSessionTimeoutIntoTimeoutException() {
        TestClientHandle clientHandle = createClientRespondingWith(
                """
                        {"code":10200,"message":"read data timeout","data":{"status":2}}
                        """,
                Duration.ofSeconds(1));

        assertThatThrownBy(() -> clientHandle.client().transcribe(
                        createIflytekProperties(),
                        new SpeechTranscriptionRequest(buildAudioBytes(1280), WavPcmAudioParser.PROVIDER_PCM_CONTENT_TYPE, "zh-CN"),
                        IflytekSpeechProvider.IFLYTEK_PROVIDER))
                .isInstanceOf(IflytekSpeechWebSocketClient.TimeoutException.class)
                .hasMessageContaining("会话超时");
        assertThat(clientHandle.webSocket().sendCloseCount()).isZero();
        assertThat(clientHandle.webSocket().abortCount()).isEqualTo(1);
    }

    @Test
    void shouldTimeoutWhenNoFinalMessageArrives() {
        RecordingWebSocket webSocket = new RecordingWebSocket();
        IflytekSpeechWebSocketClient client = new IflytekSpeechWebSocketClient(
                (uri, listener) -> {
                    listener.onOpen(webSocket);
                    return CompletableFuture.completedFuture(webSocket);
                },
                objectMapper,
                Clock.fixed(Instant.parse("2026-04-07T08:00:00Z"), ZoneOffset.UTC),
                duration -> {
                },
                Duration.ofSeconds(1),
                Duration.ofMillis(5));

        assertThatThrownBy(() -> client.transcribe(
                        createIflytekProperties(),
                        new SpeechTranscriptionRequest(buildAudioBytes(1280), WavPcmAudioParser.PROVIDER_PCM_CONTENT_TYPE, "zh-CN"),
                        IflytekSpeechProvider.IFLYTEK_PROVIDER))
                .isInstanceOf(IflytekSpeechWebSocketClient.TimeoutException.class)
                .hasMessageContaining("等待讯飞语音最终结果超时");
        assertThat(webSocket.sendCloseCount()).isZero();
        assertThat(webSocket.abortCount()).isEqualTo(1);
    }

    @Test
    void shouldBridgeOkHttpConnectorMessagesIntoJdkWebSocketContract() throws Exception {
        try (MockWebServer mockWebServer = new MockWebServer()) {
            CompletableFuture<String> serverReceivedMessage = new CompletableFuture<>();
            CompletableFuture<String> clientReceivedMessage = new CompletableFuture<>();
            CompletableFuture<String> clientCloseEvent = new CompletableFuture<>();
            AtomicReference<WebSocket> openedWebSocket = new AtomicReference<>();

            mockWebServer.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onMessage(okhttp3.WebSocket webSocket, String text) {
                    serverReceivedMessage.complete(text);
                    webSocket.send("{\"code\":0,\"message\":\"success\"}");
                    webSocket.close(1000, "server completed");
                }
            }));

            IflytekSpeechWebSocketClient.OkHttpWebSocketConnector connector = new IflytekSpeechWebSocketClient.OkHttpWebSocketConnector(
                    new OkHttpClient.Builder()
                            .connectTimeout(Duration.ofSeconds(1))
                            .readTimeout(0, TimeUnit.MILLISECONDS)
                            .build());

            WebSocket webSocket = connector.connect(mockWebServer.url("/iat").uri(), new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) {
                            openedWebSocket.set(webSocket);
                        }

                        @Override
                        public java.util.concurrent.CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            clientReceivedMessage.complete(data.toString());
                            return CompletableFuture.completedFuture(null);
                        }

                        @Override
                        public java.util.concurrent.CompletionStage<?> onClose(
                                WebSocket webSocket,
                                int statusCode,
                                String reason) {
                            clientCloseEvent.complete(statusCode + ":" + reason);
                            return CompletableFuture.completedFuture(null);
                        }
                    })
                    .get(1, TimeUnit.SECONDS);

            assertThat(openedWebSocket.get()).isSameAs(webSocket);
            webSocket.sendText("{\"ping\":1}", true).get(1, TimeUnit.SECONDS);

            assertThat(serverReceivedMessage.get(1, TimeUnit.SECONDS)).isEqualTo("{\"ping\":1}");
            assertThat(clientReceivedMessage.get(1, TimeUnit.SECONDS)).isEqualTo("{\"code\":0,\"message\":\"success\"}");
            assertThat(clientCloseEvent.get(1, TimeUnit.SECONDS)).isEqualTo("1000:server completed");
        }
    }

    @Test
    void shouldMapOkHttpHandshakeFailureIntoAuthenticationException() {
        IflytekSpeechWebSocketClient client = new IflytekSpeechWebSocketClient(
                (uri, listener) -> CompletableFuture.failedFuture(
                        new IflytekSpeechWebSocketClient.HandshakeFailureException(
                                403,
                                "{\"message\":\"Your IP address is not allowed\"}",
                                null)),
                objectMapper,
                Clock.fixed(Instant.parse("2026-04-07T08:00:00Z"), ZoneOffset.UTC),
                duration -> {
                },
                Duration.ofSeconds(1),
                Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.transcribe(
                        createIflytekProperties(),
                        new SpeechTranscriptionRequest(buildAudioBytes(1280), WavPcmAudioParser.PROVIDER_PCM_CONTENT_TYPE, "zh-CN"),
                        IflytekSpeechProvider.IFLYTEK_PROVIDER))
                .isInstanceOf(IflytekSpeechWebSocketClient.AuthenticationException.class)
                .hasMessageContaining("HTTP 403")
                .hasMessageContaining("Your IP address is not allowed");
    }

    private TestClientHandle createClientRespondingWith(String responseBody, Duration resultTimeout) {
        RecordingWebSocket webSocket = new RecordingWebSocket();
        IflytekSpeechWebSocketClient client = new IflytekSpeechWebSocketClient(
                (uri, listener) -> {
                    listener.onOpen(webSocket);
                    webSocket.setAfterSend(frame -> listener.onText(webSocket, responseBody, true));
                    return CompletableFuture.completedFuture(webSocket);
                },
                objectMapper,
                Clock.fixed(Instant.parse("2026-04-07T08:00:00Z"), ZoneOffset.UTC),
                duration -> {
                },
                Duration.ofSeconds(1),
                resultTimeout);
        return new TestClientHandle(client, webSocket);
    }

    private SpeechProperties.IflytekProperties createIflytekProperties() {
        SpeechProperties.IflytekProperties iflytekProperties = new SpeechProperties.IflytekProperties();
        iflytekProperties.setAppId("test-app-id");
        iflytekProperties.setApiKey("test-api-key");
        iflytekProperties.setApiSecret("test-api-secret");
        return iflytekProperties;
    }

    private byte[] buildAudioBytes(int length) {
        byte[] audioBytes = new byte[length];
        for (int index = 0; index < length; index++) {
            audioBytes[index] = (byte) (index % 127);
        }
        return audioBytes;
    }

    private byte[] decodeAudio(JsonNode frameNode) {
        return Base64.getDecoder().decode(frameNode.path("data").path("audio").asText());
    }

    private Map<String, String> parseQuery(URI uri) {
        Map<String, String> query = new LinkedHashMap<>();
        for (String segment : uri.getRawQuery().split("&")) {
            String[] pair = segment.split("=", 2);
            query.put(
                    URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "");
        }
        return query;
    }

    private String buildExpectedAuthorization(String requestDate, SpeechProperties.IflytekProperties iflytekProperties) throws Exception {
        String signatureOrigin = "host: iat-api.xfyun.cn\n"
                + "date: " + requestDate + "\n"
                + "GET /v2/iat HTTP/1.1";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(iflytekProperties.getApiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(mac.doFinal(signatureOrigin.getBytes(StandardCharsets.UTF_8)));
        return "api_key=\"" + iflytekProperties.getApiKey()
                + "\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\""
                + signature + "\"";
    }

    private static final class RecordingWebSocket implements WebSocket {

        private FrameObserver afterSend;

        private int sendCloseCount;

        private int abortCount;

        private Integer lastCloseStatusCode;

        private String lastCloseReason;

        void setAfterSend(FrameObserver afterSend) {
            this.afterSend = afterSend;
        }

        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            try {
                if (afterSend != null) {
                    afterSend.accept(data.toString());
                }
                return CompletableFuture.completedFuture(this);
            } catch (Exception exception) {
                CompletableFuture<WebSocket> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(exception);
                return failedFuture;
            }
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(java.nio.ByteBuffer data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(java.nio.ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(java.nio.ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            sendCloseCount++;
            lastCloseStatusCode = statusCode;
            lastCloseReason = reason;
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long n) {
        }

        @Override
        public String getSubprotocol() {
            return null;
        }

        @Override
        public boolean isOutputClosed() {
            return false;
        }

        @Override
        public boolean isInputClosed() {
            return false;
        }

        @Override
        public void abort() {
            abortCount++;
        }

        int sendCloseCount() {
            return sendCloseCount;
        }

        int abortCount() {
            return abortCount;
        }

        Integer lastCloseStatusCode() {
            return lastCloseStatusCode;
        }

        String lastCloseReason() {
            return lastCloseReason;
        }
    }

    private record TestClientHandle(IflytekSpeechWebSocketClient client, RecordingWebSocket webSocket) {
    }

    @FunctionalInterface
    private interface FrameObserver {

        void accept(String frame) throws Exception;
    }
}
