package com.example.stockai.stock;

interface AiProviderAdapter {
    AiProviderResult analyze(StockRecord stock, RagContext context, String provider, int index);

    AiChatResult chatMessage(StockRecord stock, RagContext context, String provider, String message);
}
