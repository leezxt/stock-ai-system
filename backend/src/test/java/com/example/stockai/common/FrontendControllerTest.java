package com.example.stockai.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FrontendControllerTest {
    @TempDir
    Path tempDir;

    @Test
    void servesFrontendHtml() throws Exception {
        Path index = tempDir.resolve("index.html");
        Files.writeString(index, "<!doctype html><title>Stock AI</title>");

        String html = new FrontendController(index).app();

        assertThat(html).contains("Stock AI");
    }
}
