package com.example.stockai.rag;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class InMemoryVectorStore implements VectorStore {
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, VectorDocument> documents = new LinkedHashMap<>();

    public InMemoryVectorStore() {
        this.jdbcTemplate = null;
    }

    @Autowired
    public InMemoryVectorStore(Optional<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate.orElse(null);
    }

    @Override
    public synchronized void upsert(List<VectorDocument> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        if (jdbcTemplate != null) {
            upsertDatabase(items);
            return;
        }
        for (VectorDocument item : items) {
            if (item == null) {
                throw new IllegalArgumentException("documents must not contain null");
            }
            documents.put(item.chunk().chunkId(), item);
        }
    }

    @Override
    public synchronized List<VectorSearchHit> search(VectorSearchQuery query) {
        if (jdbcTemplate != null) {
            return searchDatabase(query);
        }
        return documents.values().stream()
            .filter(document -> matches(document, query))
            .map(document -> new VectorSearchHit(document, cosineSimilarity(query.embedding(), document.embedding())))
            .sorted(Comparator.comparingDouble(VectorSearchHit::score).reversed())
            .limit(query.topK())
            .toList();
    }

    private void upsertDatabase(List<VectorDocument> items) {
        for (VectorDocument item : items) {
            if (item == null) {
                throw new IllegalArgumentException("documents must not contain null");
            }
            DocumentChunk chunk = item.chunk();
            jdbcTemplate.update("""
                INSERT INTO stockai_document_chunks (
                    chunk_id, symbol, market, doc_type, title, source, published_at, content, embedding_model, embedding, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector, now())
                ON CONFLICT (chunk_id) DO UPDATE SET
                    symbol = EXCLUDED.symbol,
                    market = EXCLUDED.market,
                    doc_type = EXCLUDED.doc_type,
                    title = EXCLUDED.title,
                    source = EXCLUDED.source,
                    published_at = EXCLUDED.published_at,
                    content = EXCLUDED.content,
                    embedding_model = EXCLUDED.embedding_model,
                    embedding = EXCLUDED.embedding,
                    updated_at = now()
                """,
                chunk.chunkId(),
                chunk.symbol(),
                chunk.market().name(),
                chunk.docType().name(),
                chunk.title(),
                chunk.source(),
                Timestamp.from(chunk.publishedAt()),
                chunk.content(),
                item.embeddingModel(),
                toPgVector(item.embedding())
            );
        }
    }

    private List<VectorSearchHit> searchDatabase(VectorSearchQuery query) {
        List<Object> params = new ArrayList<>();
        String vector = toPgVector(query.embedding());
        params.add(vector);
        StringBuilder sql = new StringBuilder("""
            SELECT chunk_id, symbol, market, doc_type, title, source, published_at, content, embedding_model,
                   1 - (embedding <=> ?::vector) AS score
            FROM stockai_document_chunks
            WHERE 1 = 1
            """);
        if (query.symbol() != null) {
            sql.append(" AND lower(symbol) = lower(?)");
            params.add(query.symbol());
        }
        if (query.market() != null) {
            sql.append(" AND market = ?");
            params.add(query.market().name());
        }
        if (query.docType() != null) {
            sql.append(" AND doc_type = ?");
            params.add(query.docType().name());
        }
        if (query.publishedFrom() != null) {
            sql.append(" AND published_at >= ?");
            params.add(Timestamp.from(query.publishedFrom()));
        }
        if (query.publishedTo() != null) {
            sql.append(" AND published_at <= ?");
            params.add(Timestamp.from(query.publishedTo()));
        }
        sql.append(" ORDER BY embedding <=> ?::vector LIMIT ?");
        params.add(vector);
        params.add(query.topK());
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            DocumentChunk chunk = new DocumentChunk(
                rs.getString("chunk_id"),
                rs.getString("symbol"),
                com.example.stockai.market.Market.valueOf(rs.getString("market")),
                DocumentType.valueOf(rs.getString("doc_type")),
                rs.getString("title"),
                rs.getString("source"),
                rs.getTimestamp("published_at").toInstant(),
                rs.getString("content")
            );
            return new VectorSearchHit(new VectorDocument(chunk, rs.getString("embedding_model"), query.embedding()), rs.getDouble("score"));
        }, params.toArray());
    }

    private static boolean matches(VectorDocument document, VectorSearchQuery query) {
        DocumentChunk chunk = document.chunk();
        if (query.symbol() != null && !query.symbol().equalsIgnoreCase(chunk.symbol())) {
            return false;
        }
        if (query.market() != null && query.market() != chunk.market()) {
            return false;
        }
        if (query.docType() != null && query.docType() != chunk.docType()) {
            return false;
        }
        if (query.publishedFrom() != null && chunk.publishedAt().isBefore(query.publishedFrom())) {
            return false;
        }
        return query.publishedTo() == null || !chunk.publishedAt().isAfter(query.publishedTo());
    }

    private static double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left.size() != right.size()) {
            throw new IllegalArgumentException("embedding dimensions must match");
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.size(); i++) {
            double a = left.get(i);
            double b = right.get(i);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static String toPgVector(List<Double> values) {
        return "[" + values.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + "]";
    }
}
