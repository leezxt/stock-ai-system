package com.example.stockai.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class HashEmbeddingModel implements EmbeddingModel {
    private static final int DIMENSION = 16;

    @Override
    public String modelName() {
        return "hash-embedding-v1";
    }

    @Override
    public List<Double> embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        List<Double> values = new ArrayList<>(Collections.nCopies(DIMENSION, 0.0));
        String normalized = text.trim().toLowerCase();
        for (int i = 0; i < normalized.length(); i++) {
            int bucket = Math.floorMod(normalized.charAt(i) * 31 + i, DIMENSION);
            values.set(bucket, values.get(bucket) + 1.0);
        }
        double norm = Math.sqrt(values.stream().mapToDouble(v -> v * v).sum());
        if (norm == 0) {
            return values;
        }
        for (int i = 0; i < values.size(); i++) {
            values.set(i, values.get(i) / norm);
        }
        return List.copyOf(values);
    }
}
