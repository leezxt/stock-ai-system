package com.example.stockai.rag;

import java.util.List;

public interface EmbeddingModel {
    String modelName();

    List<Double> embed(String text);
}
