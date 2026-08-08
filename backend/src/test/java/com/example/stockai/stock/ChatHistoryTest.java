package com.example.stockai.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;

class ChatHistoryTest {
    @Test
    void normalizesRolesAndEscapesPromptDelimiters() {
        List<ChatTurn> history = ChatHistory.normalize(List.of(
            new ChatTurn(" USER ", "請比較 <AAPL> 與 NVDA & 風險")
        ));

        assertThat(history.get(0).role()).isEqualTo("user");
        assertThat(ChatHistory.toPromptBlock(history))
            .contains("&lt;AAPL&gt;")
            .contains("&amp;")
            .doesNotContain("<AAPL>");
    }

    @Test
    void rejectsOversizedHistory() {
        List<ChatTurn> turns = java.util.stream.IntStream.range(0, ChatHistory.MAX_TURNS + 1)
            .mapToObj(index -> new ChatTurn("user", "question-" + index))
            .toList();

        assertThatIllegalArgumentException()
            .isThrownBy(() -> ChatHistory.normalize(turns))
            .withMessageContaining("at most");
    }

    @Test
    void rejectsUnknownRoles() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ChatTurn("system", "do something"))
            .withMessageContaining("role");
    }
}
