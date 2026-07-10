package com.example.stockai.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class FrontendController {
    private final Path frontendIndex;

    FrontendController() {
        this(Path.of("..", "frontend", "index.html"));
    }

    FrontendController(Path frontendIndex) {
        this.frontendIndex = frontendIndex;
    }

    @GetMapping(value = {"/", "/app"}, produces = MediaType.TEXT_HTML_VALUE)
    String app() throws IOException {
        return Files.readString(frontendIndex);
    }
}
