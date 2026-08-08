package com.example.stockai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;

class OpenAiEmbeddingModelTest {
    @Test
    void parsesFixedDimensionEmbeddingAndSendsBearerRequest() {
        String values = "0.01,0.02,0.03,0.04,0.05,0.06,0.07,0.08,0.09,0.10,0.11,0.12,0.13,0.14,0.15,0.16";
        FakeHttpClient httpClient = new FakeHttpClient("{\"data\":[{\"embedding\":[" + values + "]}]}");
        OpenAiEmbeddingModel model = new OpenAiEmbeddingModel(
            httpClient,
            JsonParserFactory.getJsonParser(),
            "embedding-test-key",
            "https://api.openai.com/v1/embeddings",
            "text-embedding-3-small",
            16
        );

        List<Double> embedding = model.embed("services margin growth");

        assertThat(embedding).hasSize(16);
        assertThat(embedding.get(0)).isEqualTo(0.01d);
        assertThat(model.modelName()).isEqualTo("openai-text-embedding-3-small-dim16");
        assertThat(httpClient.lastRequest.headers().firstValue("Authorization")).contains("Bearer embedding-test-key");
        assertThat(httpClient.lastRequest.bodyPublisher()).isPresent();
    }

    @Test
    void missingKeyFailsClosedInsteadOfSilentlyUsingHash() {
        OpenAiEmbeddingModel model = new OpenAiEmbeddingModel(
            new FakeHttpClient("{}"),
            JsonParserFactory.getJsonParser(),
            "",
            "https://api.openai.com/v1/embeddings",
            "text-embedding-3-small",
            16
        );

        assertThatIllegalStateException()
            .isThrownBy(() -> model.embed("hello"))
            .withMessageContaining("API key");
    }

    @Test
    void rejectsDimensionDifferentFromCurrentPgvectorSchema() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new OpenAiEmbeddingModel(
                new FakeHttpClient("{}"),
                JsonParserFactory.getJsonParser(),
                "key",
                "https://api.openai.com/v1/embeddings",
                "text-embedding-3-small",
                32
            ))
            .withMessageContaining("16");
    }

    private static final class FakeHttpClient extends HttpClient {
        private final String body;
        private HttpRequest lastRequest;

        private FakeHttpClient(String body) {
            this.body = body;
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.of(Duration.ofSeconds(1)); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<java.net.Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
            lastRequest = request;
            return (HttpResponse<T>) new FakeHttpResponse(body, request);
        }

        @Override public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) { throw new UnsupportedOperationException(); }
        @Override public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { throw new UnsupportedOperationException(); }
    }

    private record FakeHttpResponse(String body, HttpRequest request) implements HttpResponse<String> {
        @Override public int statusCode() { return 200; }
        @Override public HttpRequest request() { return request; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
        @Override public String body() { return body; }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
