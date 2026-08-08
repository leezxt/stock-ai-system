package com.example.stockai.rag;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DocumentRetriever {
    private static final double DEFAULT_RELEVANCE_THRESHOLD = 0.15d;
    private static final double VECTOR_ONLY_RELEVANCE_THRESHOLD = 0.90d;
    private static final double VECTOR_WEIGHT = 0.75d;
    private static final double LEXICAL_WEIGHT = 0.25d;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}_]+|[\\p{IsHan}]");

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final double relevanceThreshold;

    public DocumentRetriever(EmbeddingModel embeddingModel, VectorStore vectorStore) {
        this(embeddingModel, vectorStore, DEFAULT_RELEVANCE_THRESHOLD);
    }

    @Autowired
    public DocumentRetriever(
        EmbeddingModel embeddingModel,
        VectorStore vectorStore,
        @Value("${stockai.rag.relevance-threshold:0.15}") double relevanceThreshold
    ) {
        if (!Double.isFinite(relevanceThreshold) || relevanceThreshold < -1d || relevanceThreshold > 1d) {
            throw new IllegalArgumentException("relevanceThreshold must be between -1 and 1");
        }
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.relevanceThreshold = relevanceThreshold;
    }

    public List<RetrievedDocument> retrieve(DocumentRetrieveRequest request) {
        return retrieve(request, "");
    }

    public List<RetrievedDocument> retrieve(DocumentRetrieveRequest request, String ownerEmail) {
        VectorSearchQuery query = new VectorSearchQuery(
            embeddingModel.embed(request.queryText()),
            candidateLimit(request.topK()),
            request.symbol(),
            request.market(),
            request.docType(),
            request.publishedFrom(),
            request.publishedTo(),
            ownerEmail,
            embeddingModel.modelName()
        );
        Set<String> queryTokens = tokens(request.queryText());
        return vectorStore.search(query).stream()
            .filter(hit -> hit != null && hit.document() != null && Double.isFinite(hit.score()))
            .map(hit -> scoreDocument(hit, queryTokens))
            .filter(hit -> hit.score() >= relevanceThreshold)
            .filter(hit -> hit.lexicalScore() > 0d || hit.vectorScore() >= VECTOR_ONLY_RELEVANCE_THRESHOLD)
            .sorted(java.util.Comparator.comparingDouble(ScoredDocument::score).reversed())
            .limit(request.topK())
            .map(ScoredDocument::document)
            .toList();
    }

    private static int candidateLimit(int requestedTopK) {
        return Math.min(20, Math.max(requestedTopK, requestedTopK * 3));
    }

    private static ScoredDocument scoreDocument(VectorSearchHit hit, Set<String> queryTokens) {
        double vectorScore = Math.max(-1d, Math.min(1d, hit.score()));
        double lexicalScore = lexicalScore(hit.document(), queryTokens);
        double combinedScore = VECTOR_WEIGHT * vectorScore + LEXICAL_WEIGHT * lexicalScore;
        return new ScoredDocument(toRetrievedDocument(hit.document(), combinedScore), combinedScore, vectorScore, lexicalScore);
    }

    private static double lexicalScore(VectorDocument document, Set<String> queryTokens) {
        if (queryTokens.isEmpty()) {
            return 0d;
        }
        DocumentChunk chunk = document.chunk();
        Set<String> documentTokens = tokens(chunk.symbol() + " " + chunk.title() + " " + chunk.source() + " " + chunk.content());
        long matched = queryTokens.stream().filter(documentTokens::contains).count();
        return (double) matched / queryTokens.size();
    }

    private static Set<String> tokens(String text) {
        Set<String> tokens = new HashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text == null ? "" : text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private static RetrievedDocument toRetrievedDocument(VectorDocument document, double score) {
        DocumentChunk chunk = document.chunk();
        return new RetrievedDocument(
            chunk.chunkId(),
            chunk.symbol(),
            chunk.market(),
            chunk.docType(),
            chunk.title(),
            chunk.source(),
            chunk.publishedAt(),
            snippet(chunk.content()),
            score
        );
    }

    private static String snippet(String content) {
        String normalized = content.replace('\n', ' ').trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180).trim() + "...";
    }

    private record ScoredDocument(RetrievedDocument document, double score, double vectorScore, double lexicalScore) {}
}
