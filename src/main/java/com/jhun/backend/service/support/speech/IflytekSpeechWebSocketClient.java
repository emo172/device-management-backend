package com.jhun.backend.service.support.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jhun.backend.config.speech.SpeechProperties;
import java.io.IOException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.net.http.HttpTimeoutException;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocketListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 讯飞 WebSocket 听写客户端。
 * <p>
 * 该客户端只承担上游协议细节：
 * 1) 在后端生成 query-signature 鉴权参数；
 * 2) 按 40ms / 1280B 节奏发送原始 PCM 的 `status=0/1/2` 帧；
 * 3) 只在拿到最终状态后聚合完整 transcript，不把中间识别片段泄漏给调用方；
 * 4) 把鉴权失败、业务失败和超时收口成稳定内部异常。
 */
@Component
public class IflytekSpeechWebSocketClient {

    private static final String HOST = "iat-api.xfyun.cn";

    private static final String REQUEST_PATH = "/v2/iat";

    private static final String ENDPOINT = "wss://" + HOST + REQUEST_PATH;

    private static final String LANGUAGE = "zh_cn";

    private static final String DOMAIN = "iat";

    private static final String ACCENT = "mandarin";

    private static final String AUDIO_ENCODING = "raw";

    private static final int FIRST_FRAME_STATUS = 0;

    private static final int CONTINUE_FRAME_STATUS = 1;

    private static final int LAST_FRAME_STATUS = 2;

    private static final int PCM_CHUNK_SIZE_BYTES = 1280;

    private static final Duration FRAME_PACING = Duration.ofMillis(40);

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private static final Duration DEFAULT_RESULT_TIMEOUT = Duration.ofSeconds(65);

    private static final int NORMAL_CLOSE_STATUS = 1000;

    private static final String NORMAL_CLOSE_REASON = "client completed";

    private static final DateTimeFormatter RFC_1123_GMT = DateTimeFormatter.ofPattern(
                    "EEE, dd MMM yyyy HH:mm:ss 'GMT'",
                    Locale.ENGLISH)
            .withZone(ZoneId.of("GMT"));

    private final WebSocketConnector webSocketConnector;

    private final ObjectMapper objectMapper;

    private final Clock clock;

    private final FrameSleeper frameSleeper;

    private final Duration connectTimeout;

    private final Duration resultTimeout;

    @Autowired
    public IflytekSpeechWebSocketClient(ObjectMapper objectMapper) {
        this(
                new OkHttpWebSocketConnector(new OkHttpClient.Builder()
                        .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                        .readTimeout(0, TimeUnit.MILLISECONDS)
                        .build()),
                objectMapper,
                Clock.systemUTC(),
                duration -> Thread.sleep(duration.toMillis()),
                DEFAULT_CONNECT_TIMEOUT,
                DEFAULT_RESULT_TIMEOUT);
    }

    public IflytekSpeechWebSocketClient(
            WebSocketConnector webSocketConnector,
            ObjectMapper objectMapper,
            Clock clock,
            FrameSleeper frameSleeper,
            Duration connectTimeout,
            Duration resultTimeout) {
        this.webSocketConnector = Objects.requireNonNull(webSocketConnector, "WebSocket 连接器不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
        this.clock = Objects.requireNonNull(clock, "Clock 不能为空");
        this.frameSleeper = Objects.requireNonNull(frameSleeper, "分帧节流器不能为空");
        this.connectTimeout = Objects.requireNonNull(connectTimeout, "连接超时不能为空");
        this.resultTimeout = Objects.requireNonNull(resultTimeout, "结果超时不能为空");
    }

    public SpeechTranscriptionResult transcribe(
            SpeechProperties.IflytekProperties iflytekProperties,
            SpeechTranscriptionRequest request,
            String providerName) {
        Objects.requireNonNull(iflytekProperties, "讯飞配置不能为空");
        Objects.requireNonNull(request, "语音转写请求不能为空");
        Objects.requireNonNull(providerName, "provider 名称不能为空");
        if (request.audioBytes() == null || request.audioBytes().length == 0) {
            throw new FailureException("讯飞语音转写音频不能为空");
        }

        IflytekResponseListener responseListener = new IflytekResponseListener(objectMapper);
        URI authorizedUri = buildAuthorizedUri(iflytekProperties);
        WebSocket webSocket = connect(authorizedUri, responseListener);
        boolean completedSuccessfully = false;
        try {
            streamAudioFrames(webSocket, iflytekProperties.getAppId(), request.audioBytes());
            String transcript = responseListener.awaitFinalTranscript(resultTimeout);
            completedSuccessfully = true;
            return new SpeechTranscriptionResult(transcript, request.locale(), providerName);
        } catch (TimeoutException | FailureException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new FailureException("讯飞语音转写调用失败", exception);
        } finally {
            cleanupWebSocket(webSocket, completedSuccessfully);
        }
    }

    /**
     * 讯飞在最终结果后通常会自行回收会话，但客户端仍需显式结束本次出站连接。
     * <p>
     * 成功路径优先走 `sendClose(...)`，让协议正常收尾；失败/超时路径直接 `abort()`，
     * 避免把资源释放完全依赖给上游超时清理。
     */
    private void cleanupWebSocket(WebSocket webSocket, boolean completedSuccessfully) {
        if (completedSuccessfully) {
            closeWebSocketGracefully(webSocket);
            return;
        }
        abortWebSocketQuietly(webSocket);
    }

    private void closeWebSocketGracefully(WebSocket webSocket) {
        try {
            webSocket.sendClose(NORMAL_CLOSE_STATUS, NORMAL_CLOSE_REASON)
                    .get(connectTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException exception) {
            abortWebSocketQuietly(webSocket);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            abortWebSocketQuietly(webSocket);
        } catch (ExecutionException | RuntimeException exception) {
            abortWebSocketQuietly(webSocket);
        }
    }

    private void abortWebSocketQuietly(WebSocket webSocket) {
        try {
            webSocket.abort();
        } catch (RuntimeException ignored) {
        }
    }

    private WebSocket connect(URI authorizedUri, IflytekResponseListener responseListener) {
        try {
            return webSocketConnector.connect(authorizedUri, responseListener)
                    .get(connectTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException exception) {
            throw new TimeoutException("讯飞语音连接超时", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FailureException("讯飞语音连接线程被中断", exception);
        } catch (ExecutionException exception) {
            throw mapTransportFailure(exception.getCause() == null ? exception : exception.getCause());
        }
    }

    /**
     * 讯飞要求裸 PCM 以 40ms 节奏发送，16k/16bit/单声道时每帧恰好 1280 字节。
     * <p>
     * 多分片场景直接走 `0 -> 1 -> 2`；单分片场景则先发携带音频的首帧，再补一个空 `status=2` 结束帧，
     * 这样既满足首帧必须带 `common` / `business` 的约束，也能显式结束会话。
     */
    private void streamAudioFrames(WebSocket webSocket, String appId, byte[] audioBytes) {
        List<byte[]> chunks = splitPcmChunks(audioBytes);
        if (chunks.size() == 1) {
            sendFrame(webSocket, buildFirstFrame(appId, chunks.getFirst()));
            sleepBetweenFrames();
            sendFrame(webSocket, buildLastFrame(null));
            return;
        }

        sendFrame(webSocket, buildFirstFrame(appId, chunks.getFirst()));
        for (int index = 1; index < chunks.size() - 1; index++) {
            sleepBetweenFrames();
            sendFrame(webSocket, buildDataFrame(CONTINUE_FRAME_STATUS, chunks.get(index)));
        }
        sleepBetweenFrames();
        sendFrame(webSocket, buildLastFrame(chunks.getLast()));
    }

    private void sendFrame(WebSocket webSocket, String payload) {
        try {
            webSocket.sendText(payload, true).get(connectTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException exception) {
            throw new TimeoutException("讯飞语音发送音频超时", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FailureException("讯飞语音发送线程被中断", exception);
        } catch (ExecutionException exception) {
            throw mapTransportFailure(exception.getCause() == null ? exception : exception.getCause());
        }
    }

    private void sleepBetweenFrames() {
        try {
            frameSleeper.sleep(FRAME_PACING);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FailureException("讯飞语音分帧发送被中断", exception);
        }
    }

    private URI buildAuthorizedUri(SpeechProperties.IflytekProperties iflytekProperties) {
        String requestDate = RFC_1123_GMT.format(clock.instant());
        String signatureOrigin = "host: " + HOST + "\n"
                + "date: " + requestDate + "\n"
                + "GET " + REQUEST_PATH + " HTTP/1.1";
        String signature = hmacSha256Base64(iflytekProperties.getApiSecret(), signatureOrigin);
        String authorizationOrigin = "api_key=\"" + iflytekProperties.getApiKey()
                + "\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\""
                + signature + "\"";
        String authorization = Base64.getEncoder()
                .encodeToString(authorizationOrigin.getBytes(StandardCharsets.UTF_8));
        String query = "authorization=" + urlEncode(authorization)
                + "&date=" + urlEncode(requestDate)
                + "&host=" + urlEncode(HOST);
        return URI.create(ENDPOINT + "?" + query);
    }

    private String hmacSha256Base64(String apiSecret, String signatureOrigin) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(signatureOrigin.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new FailureException("讯飞语音签名生成失败", exception);
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private List<byte[]> splitPcmChunks(byte[] audioBytes) {
        List<byte[]> chunks = new ArrayList<>();
        for (int offset = 0; offset < audioBytes.length; offset += PCM_CHUNK_SIZE_BYTES) {
            int end = Math.min(offset + PCM_CHUNK_SIZE_BYTES, audioBytes.length);
            byte[] chunk = new byte[end - offset];
            System.arraycopy(audioBytes, offset, chunk, 0, chunk.length);
            chunks.add(chunk);
        }
        return chunks;
    }

    private String buildFirstFrame(String appId, byte[] audioChunk) {
        ObjectNode frame = objectMapper.createObjectNode();
        frame.putObject("common").put("app_id", appId);
        ObjectNode business = frame.putObject("business");
        business.put("language", LANGUAGE);
        business.put("domain", DOMAIN);
        business.put("accent", ACCENT);
        attachAudioData(frame.putObject("data"), FIRST_FRAME_STATUS, audioChunk);
        return writeFrame(frame);
    }

    private String buildDataFrame(int status, byte[] audioChunk) {
        ObjectNode frame = objectMapper.createObjectNode();
        attachAudioData(frame.putObject("data"), status, audioChunk);
        return writeFrame(frame);
    }

    private String buildLastFrame(byte[] audioChunk) {
        return buildDataFrame(LAST_FRAME_STATUS, audioChunk);
    }

    private void attachAudioData(ObjectNode dataNode, int status, byte[] audioChunk) {
        dataNode.put("status", status);
        dataNode.put("format", WavPcmAudioParser.PROVIDER_PCM_CONTENT_TYPE);
        dataNode.put("encoding", AUDIO_ENCODING);
        dataNode.put("audio", audioChunk == null ? "" : Base64.getEncoder().encodeToString(audioChunk));
    }

    private String writeFrame(ObjectNode frame) {
        try {
            return objectMapper.writeValueAsString(frame);
        } catch (Exception exception) {
            throw new FailureException("讯飞语音请求序列化失败", exception);
        }
    }

    private RuntimeException mapTransportFailure(Throwable throwable) {
        Throwable rootCause = unwrap(throwable);
        if (rootCause instanceof HandshakeFailureException handshakeFailureException) {
            if (handshakeFailureException.statusCode() == 401 || handshakeFailureException.statusCode() == 403) {
                return new AuthenticationException(
                        buildHandshakeFailureMessage(
                                handshakeFailureException.statusCode(),
                                handshakeFailureException.responseBody()),
                        rootCause);
            }
            return new FailureException(
                    "讯飞语音连接失败：HTTP " + handshakeFailureException.statusCode()
                            + buildOptionalResponseBodySuffix(handshakeFailureException.responseBody()),
                    rootCause);
        }
        if (rootCause instanceof WebSocketHandshakeException handshakeException
                && handshakeException.getResponse() != null
                && (handshakeException.getResponse().statusCode() == 401
                        || handshakeException.getResponse().statusCode() == 403)) {
            return new AuthenticationException(
                    "讯飞语音鉴权失败：HTTP " + handshakeException.getResponse().statusCode(),
                    rootCause);
        }
        if (rootCause instanceof HttpTimeoutException
                || rootCause instanceof java.net.http.HttpConnectTimeoutException
                || rootCause instanceof SocketTimeoutException) {
            return new TimeoutException("讯飞语音连接超时", rootCause);
        }
        return new FailureException("讯飞语音连接失败", rootCause);
    }

    private String buildHandshakeFailureMessage(int statusCode, String responseBody) {
        return "讯飞语音鉴权失败：HTTP " + statusCode + buildOptionalResponseBodySuffix(responseBody);
    }

    private String buildOptionalResponseBodySuffix(String responseBody) {
        return normalizeOptionalResponseBody(responseBody).map(body -> ", body=" + body).orElse("");
    }

    private java.util.Optional<String> normalizeOptionalResponseBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(responseBody.trim());
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null
                && (current instanceof ExecutionException || current instanceof CompletionException)) {
            current = current.getCause();
        }
        return current;
    }

    @FunctionalInterface
    public interface WebSocketConnector {

        CompletableFuture<WebSocket> connect(URI uri, WebSocket.Listener listener);
    }

    @FunctionalInterface
    public interface FrameSleeper {

        void sleep(Duration duration) throws InterruptedException;
    }

    public static final class OkHttpWebSocketConnector implements WebSocketConnector {

        private final OkHttpClient okHttpClient;

        public OkHttpWebSocketConnector(OkHttpClient okHttpClient) {
            this.okHttpClient = Objects.requireNonNull(okHttpClient, "OkHttpClient 不能为空");
        }

        @Override
        public CompletableFuture<WebSocket> connect(URI uri, WebSocket.Listener listener) {
            CompletableFuture<WebSocket> connectionFuture = new CompletableFuture<>();
            Request request = new Request.Builder().url(uri.toString()).build();
            okHttpClient.newWebSocket(request, new ForwardingWebSocketListener(listener, connectionFuture));
            return connectionFuture;
        }
    }

    private static final class OkHttpWebSocketAdapter implements WebSocket {

        private final okhttp3.WebSocket delegate;

        private final String subprotocol;

        private final AtomicBoolean inputClosed = new AtomicBoolean();

        private final AtomicBoolean outputClosed = new AtomicBoolean();

        private OkHttpWebSocketAdapter(okhttp3.WebSocket delegate, String subprotocol) {
            this.delegate = Objects.requireNonNull(delegate, "OkHttp WebSocket 不能为空");
            this.subprotocol = subprotocol;
        }

        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            if (!last) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException("当前不支持分片文本帧发送"));
            }
            if (!delegate.send(data.toString())) {
                outputClosed.set(true);
                return CompletableFuture.failedFuture(new IllegalStateException("WebSocket 输出已关闭，文本帧发送失败"));
            }
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(java.nio.ByteBuffer data, boolean last) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("讯飞语音客户端不使用二进制帧发送"));
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(java.nio.ByteBuffer message) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("讯飞语音客户端不使用 ping 帧"));
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(java.nio.ByteBuffer message) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("讯飞语音客户端不使用 pong 帧"));
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            if (!delegate.close(statusCode, reason)) {
                outputClosed.set(true);
                return CompletableFuture.failedFuture(new IllegalStateException("WebSocket 关闭失败"));
            }
            outputClosed.set(true);
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long n) {
        }

        @Override
        public String getSubprotocol() {
            return subprotocol;
        }

        @Override
        public boolean isOutputClosed() {
            return outputClosed.get();
        }

        @Override
        public boolean isInputClosed() {
            return inputClosed.get();
        }

        @Override
        public void abort() {
            inputClosed.set(true);
            outputClosed.set(true);
            delegate.cancel();
        }

        private void markInputClosed() {
            inputClosed.set(true);
        }

        private void markOutputClosed() {
            outputClosed.set(true);
        }
    }

    private static final class ForwardingWebSocketListener extends WebSocketListener {

        private final WebSocket.Listener delegateListener;

        private final CompletableFuture<WebSocket> connectionFuture;

        private final AtomicBoolean closeForwarded = new AtomicBoolean();

        private volatile OkHttpWebSocketAdapter adapter;

        private ForwardingWebSocketListener(
                WebSocket.Listener delegateListener,
                CompletableFuture<WebSocket> connectionFuture) {
            this.delegateListener = Objects.requireNonNull(delegateListener, "WebSocket 监听器不能为空");
            this.connectionFuture = Objects.requireNonNull(connectionFuture, "连接 future 不能为空");
        }

        @Override
        public void onOpen(okhttp3.WebSocket webSocket, Response response) {
            OkHttpWebSocketAdapter createdAdapter = new OkHttpWebSocketAdapter(
                    webSocket,
                    response.header("Sec-WebSocket-Protocol"));
            this.adapter = createdAdapter;
            try {
                delegateListener.onOpen(createdAdapter);
                connectionFuture.complete(createdAdapter);
            } catch (RuntimeException exception) {
                connectionFuture.completeExceptionally(exception);
                createdAdapter.abort();
            }
        }

        @Override
        public void onMessage(okhttp3.WebSocket webSocket, String text) {
            OkHttpWebSocketAdapter currentAdapter = adapter;
            if (currentAdapter == null) {
                return;
            }
            try {
                delegateListener.onText(currentAdapter, text, true);
            } catch (RuntimeException exception) {
                delegateListener.onError(currentAdapter, exception);
            }
        }

        @Override
        public void onClosing(okhttp3.WebSocket webSocket, int code, String reason) {
            forwardClose(code, reason);
        }

        @Override
        public void onClosed(okhttp3.WebSocket webSocket, int code, String reason) {
            forwardClose(code, reason);
        }

        @Override
        public void onFailure(okhttp3.WebSocket webSocket, Throwable t, Response response) {
            OkHttpWebSocketAdapter currentAdapter = adapter;
            if (currentAdapter != null) {
                currentAdapter.markInputClosed();
                currentAdapter.markOutputClosed();
            }
            Throwable failure = response == null
                    ? t
                    : new HandshakeFailureException(response.code(), readResponseBodyQuietly(response), t);
            if (!connectionFuture.isDone()) {
                connectionFuture.completeExceptionally(failure);
                return;
            }
            if (currentAdapter != null) {
                delegateListener.onError(currentAdapter, failure);
            }
        }

        private void forwardClose(int code, String reason) {
            if (!closeForwarded.compareAndSet(false, true)) {
                return;
            }
            OkHttpWebSocketAdapter currentAdapter = adapter;
            if (currentAdapter == null) {
                return;
            }
            currentAdapter.markInputClosed();
            currentAdapter.markOutputClosed();
            delegateListener.onClose(currentAdapter, code, reason);
        }

        private String readResponseBodyQuietly(Response response) {
            try (Response closableResponse = response) {
                if (closableResponse.body() == null) {
                    return "";
                }
                return closableResponse.body().string();
            } catch (IOException exception) {
                return "";
            }
        }
    }

    /**
     * 讯飞可能把一个 JSON 响应拆成多段 `onText(...)` 回调。
     * <p>
     * 这里先拼完整消息，再根据 `code` 与 `data.status` 推进状态机；只有拿到最终状态才完成 future，
     * 从而保证调用方永远只拿到最终 transcript，而不会看到中间片段。
     */
    private static final class IflytekResponseListener implements WebSocket.Listener {

        private static final List<Integer> TIMEOUT_CODES = List.of(10014, 10114, 10019, 10101, 10200);

        private final ObjectMapper objectMapper;

        private final StringBuilder textBuffer = new StringBuilder();

        private final StringBuilder transcriptBuilder = new StringBuilder();

        private final CompletableFuture<String> finalTranscriptFuture = new CompletableFuture<>();

        private IflytekResponseListener(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                try {
                    handleMessage(textBuffer.toString());
                } catch (RuntimeException exception) {
                    finalTranscriptFuture.completeExceptionally(exception);
                } finally {
                    textBuffer.setLength(0);
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            finalTranscriptFuture.completeExceptionally(new FailureException("讯飞语音连接异常", error));
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (!finalTranscriptFuture.isDone()) {
                finalTranscriptFuture.completeExceptionally(new FailureException(
                        "讯飞语音连接提前关闭：status=" + statusCode + ", reason=" + reason));
            }
            return CompletableFuture.completedFuture(null);
        }

        private void handleMessage(String payload) {
            try {
                JsonNode root = objectMapper.readTree(payload);
                int code = root.path("code").asInt(0);
                String message = root.path("message").asText("");
                if (code != 0) {
                    throw classifyBusinessError(code, message);
                }

                JsonNode dataNode = root.path("data");
                appendTranscript(dataNode.path("result"));
                if (dataNode.path("status").asInt(-1) == LAST_FRAME_STATUS) {
                    String transcript = transcriptBuilder.toString().trim();
                    if (transcript.isEmpty()) {
                        throw new FailureException("讯飞语音未返回有效转写结果");
                    }
                    finalTranscriptFuture.complete(transcript);
                }
            } catch (TimeoutException | FailureException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new FailureException("解析讯飞语音响应失败", exception);
            }
        }

        private void appendTranscript(JsonNode resultNode) {
            if (resultNode.isMissingNode() || resultNode.isNull()) {
                return;
            }
            JsonNode wsNode = resultNode.path("ws");
            if (!wsNode.isArray()) {
                return;
            }
            for (JsonNode wsItem : wsNode) {
                JsonNode cwNode = wsItem.path("cw");
                if (!cwNode.isArray()) {
                    continue;
                }
                for (JsonNode cwItem : cwNode) {
                    JsonNode wordNode = cwItem.get("w");
                    if (wordNode != null && !wordNode.asText("").isBlank()) {
                        transcriptBuilder.append(wordNode.asText());
                    }
                }
            }
        }

        private RuntimeException classifyBusinessError(int code, String message) {
            if (TIMEOUT_CODES.contains(code)) {
                return new TimeoutException("讯飞语音会话超时：code=" + code + ", message=" + normalizeMessage(message));
            }
            String normalizedMessage = normalizeMessage(message).toLowerCase();
            if (code == 401 || code == 403
                    || normalizedMessage.contains("auth")
                    || normalizedMessage.contains("signature")
                    || normalizedMessage.contains("unauthorized")
                    || normalizedMessage.contains("鉴权")) {
                return new AuthenticationException(
                        "讯飞语音鉴权失败：code=" + code + ", message=" + normalizeMessage(message));
            }
            return new FailureException("讯飞语音转写失败：code=" + code + ", message=" + normalizeMessage(message));
        }

        private String normalizeMessage(String message) {
            return (message == null || message.isBlank()) ? "<empty>" : message.trim();
        }

        private String awaitFinalTranscript(Duration resultTimeout) {
            try {
                return finalTranscriptFuture.get(resultTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException exception) {
                throw new TimeoutException("等待讯飞语音最终结果超时", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new FailureException("等待讯飞语音结果线程被中断", exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof AuthenticationException authenticationException) {
                    throw authenticationException;
                }
                if (cause instanceof TimeoutException timeoutException) {
                    throw timeoutException;
                }
                if (cause instanceof FailureException failureException) {
                    throw failureException;
                }
                throw new FailureException("讯飞语音结果处理失败", cause == null ? exception : cause);
            }
        }
    }

    public static class AuthenticationException extends FailureException {

        public AuthenticationException(String message) {
            super(message);
        }

        public AuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class HandshakeFailureException extends RuntimeException {

        private final int statusCode;

        private final String responseBody;

        public HandshakeFailureException(int statusCode, String responseBody, Throwable cause) {
            super("HTTP " + statusCode + (responseBody == null || responseBody.isBlank() ? "" : ", body=" + responseBody.trim()), cause);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        public int statusCode() {
            return statusCode;
        }

        public String responseBody() {
            return responseBody;
        }
    }

    public static class TimeoutException extends RuntimeException {

        public TimeoutException(String message) {
            super(message);
        }

        public TimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class FailureException extends RuntimeException {

        public FailureException(String message) {
            super(message);
        }

        public FailureException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
