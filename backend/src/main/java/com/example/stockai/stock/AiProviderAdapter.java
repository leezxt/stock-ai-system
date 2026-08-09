package com.example.stockai.stock;

import java.util.List;

interface AiProviderAdapter {
    AiProviderResult analyze(StockRecord stock, RagContext context, String provider, int index);

    AiChatResult chatMessage(StockRecord stock, RagContext context, String provider, String message);

    default AiChatResult chatMessage(
        StockRecord stock,
        RagContext context,
        String provider,
        String message,
        List<ChatTurn> history
    ) {
        return chatMessage(stock, context, provider, message);
    }
}
