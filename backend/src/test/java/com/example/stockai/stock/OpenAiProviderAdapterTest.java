package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.json.JsonParserFactory;

import com.example.stockai.auth.AccountSettingsService;
import com.example.stockai.auth.AuthService;
import com.example.stockai.auth.UserStore;
import com.example.stockai.market.Market;

class OpenAiProviderAdapterTest {
    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void exposesSafeHttpFailureSource() {
        OpenAiProviderAdapter adapter = new OpenAiProviderAdapter(
            new FakeHttpClient(401, """
                {"error":{"code":"invalid_api_key","message":"Incorrect API key provided."}}
                """),
            JsonParserFactory.getJsonParser(),
            new ApiKeyHeaderResolver(
                new AuthService(new UserStore(tempDir.resolve("users.txt")), "test-secret", 3600),
                new AccountSettingsService(tempDir.resolve("account-settings"))
            ),
            "test-openai-key",
            "https://api.openai.com/v1/responses",
            "gpt-5.5"
        );

        Optional<AiChatResult> result = adapter.tryChatMessage(stock(), context(), "OPENAI", "hello");

        assertThat(result).isEmpty();
        assertThat(adapter.fallbackSource()).isEqualTo("mock-ai:openai-http-401-invalid_api_key");
    }

    private static StockRecord stock() {
        return new StockRecord(
            "AAPL",
            "Apple",
            Market.US,
            "USD",
            new BigDecimal("200"),
            new BigDecimal("1.2"),
            List.of(new BigDecimal("198"), new BigDecimal("200")),
            "mock"
        );
    }

    private static RagContext context() {
        StockRecord stock = stock();
        return new RagContext(
            stock,
            new TechnicalSummaryResponse("AAPL", Market.US, new BigDecimal("198"), new BigDecimal("195"), new BigDecimal("190"), new BigDecimal("61"), "BULLISH", new BigDecimal("4"), Instant.now()),
            new PredictionResponse("AAPL", Market.US, 5, new BigDecimal("0.73"), new BigDecimal("0.04"), new BigDecimal("0.02"), "MEDIUM", "test-model", Instant.now()),
            List.of()
        );
    }

    private static final class FakeHttpClient extends HttpClient {
        private final int statusCode;
        private final String body;

        private FakeHttpClient(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override
        public Optional<Duration> connectTimeout() { return Optional.of(Duration.ofSeconds(1)); }
        @Override
        public Redirect followRedirects() { return Redirect.NEVER; }
        @Override
        public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override
        public SSLContext sslContext() { return null; }
        @Override
        public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override
        public Optional<java.net.Authenticator> authenticator() { return Optional.empty(); }
        @Override
        public Version version() { return Version.HTTP_1_1; }
        @Override
        public Optional<Executor> executor() { return Optional.empty(); }
        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
            return (HttpResponse<T>) new FakeHttpResponse(statusCode, body, request);
        }
        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) { throw new UnsupportedOperationException(); }
        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { throw new UnsupportedOperationException(); }
    }

    private record FakeHttpResponse(int statusCode, String body, HttpRequest request) implements HttpResponse<String> {
        @Override
        public HttpRequest request() { return request; }
        @Override
        public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override
        public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
        @Override
        public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override
        public URI uri() { return request.uri(); }
        @Override
        public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
