package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
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
import org.springframework.boot.json.JsonParserFactory;

import com.example.stockai.market.Market;

class MimoProviderAdapterTest {
    @Test
    void parsesStructuredAnalysisFromChatCompletion() {
        String body = """
            {
              "choices": [
                {
                  "message": {
                    "content": "{\\"trend\\":\\"偏多\\",\\"aiScore\\":79,\\"bullishProbability\\":0.71,\\"riskLevel\\":\\"MEDIUM\\",\\"summary\\":\\"量價結構轉強。\\"}"
                  }
                }
              ]
            }
            """;
        FakeHttpClient httpClient = new FakeHttpClient(body);
        MimoProviderAdapter adapter = new MimoProviderAdapter(
            httpClient,
            JsonParserFactory.getJsonParser(),
            "mimo-test-key",
            "https://api.xiaomimimo.com/v1/chat/completions",
            "mimo-v2.5-pro"
        );

        Optional<AiProviderResult> result = adapter.tryAnalyze(
            stock(),
            context(),
            "MIMO"
        );

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().provider()).isEqualTo("MIMO");
        assertThat(result.orElseThrow().aiScore()).isEqualTo(79);
        assertThat(result.orElseThrow().source()).isEqualTo("mimo-chat-completions");
        assertThat(httpClient.lastRequest().headers().firstValue("api-key")).contains("mimo-test-key");
    }

    private static StockRecord stock() {
        return new StockRecord(
            "AAPL",
            "Apple",
            Market.US,
            "USD",
            new java.math.BigDecimal("200"),
            new java.math.BigDecimal("1.2"),
            List.of(new java.math.BigDecimal("198"), new java.math.BigDecimal("200")),
            "mock"
        );
    }

    private static RagContext context() {
        StockRecord stock = stock();
        return new RagContext(
            stock,
            new TechnicalSummaryResponse("AAPL", Market.US, new java.math.BigDecimal("198"), new java.math.BigDecimal("195"), new java.math.BigDecimal("190"), new java.math.BigDecimal("61"), "BULLISH", new java.math.BigDecimal("4"), Instant.now()),
            new PredictionResponse("AAPL", Market.US, 5, new java.math.BigDecimal("0.73"), new java.math.BigDecimal("0.04"), new java.math.BigDecimal("0.02"), "MEDIUM", "test-model", Instant.now()),
            List.of()
        );
    }

    private static final class FakeHttpClient extends HttpClient {
        private final String body;
        private HttpRequest lastRequest;

        private FakeHttpClient(String body) {
            this.body = body;
        }

        private HttpRequest lastRequest() {
            return lastRequest;
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
            this.lastRequest = request;
            return (HttpResponse<T>) new FakeHttpResponse(body, request);
        }
        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) { throw new UnsupportedOperationException(); }
        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { throw new UnsupportedOperationException(); }
    }

    private record FakeHttpResponse(String body, HttpRequest request) implements HttpResponse<String> {
        @Override
        public int statusCode() { return 200; }
        @Override
        public HttpRequest request() { return request; }
        @Override
        public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override
        public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
        @Override
        public String body() { return body; }
        @Override
        public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override
        public URI uri() { return request.uri(); }
        @Override
        public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
