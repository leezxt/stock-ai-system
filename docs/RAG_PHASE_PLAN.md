# RAG Phase Plan

Goal: add document-grounded analysis on top of the current Stock AI MVP without breaking the existing `/app` and `/api/v1` flow.

## Scope

Target document sources:

- News
- Financial reports
- Earnings call transcripts
- Research reports
- Company announcements

Target frontend features:

- RAG-backed AI Chat Q&A
- RAG-backed AI Analysis
- Shared-context multi-model comparison

## Data Flow

```text
Document Source
  -> Document Ingestion
  -> Embedding Model
  -> Vector Store (PostgreSQL + pgvector)
  -> Retriever
  -> Spring AI RAG Agent
  -> AI Analysis / Model Comparison / Chat Q&A
```

## Minimum Document Schema

Each stored document chunk should include:

- `symbol`
- `market`
- `docType`
- `title`
- `source`
- `publishedAt`
- `content`
- `chunkId`

Optional later:

- `author`
- `url`
- `reportingPeriod`
- `language`
- `embeddingVersion`

## Build Order

### Phase A: MVP close-out first

1. Finish current MVP smoke test.
2. Finish current MVP delivery check.

Do not start pgvector or ingestion work before those two steps are closed.

### Phase B: First RAG slice

3. Add document metadata model in backend.
4. Add document ingestion pipeline: import, clean, chunk.
5. Add embedding pipeline.
6. Add PostgreSQL + pgvector storage.
7. Add retriever filters:
   - `symbol`
   - `market`
   - date range
   - `docType`

### Phase C: First user-visible RAG feature

8. Connect retriever to Spring AI RAG Agent.
9. Add RAG context to `AI Chat Q&A` first.

Reason: Chat is the shortest path to verify retrieval quality and citation usefulness.

### Phase D: Analysis features

10. Add retrieved evidence to `AI Analysis`.
11. Add shared retrieved evidence to `Model Comparison`.
12. Compare OpenAI / Claude / Gemini / DeepSeek with the same retrieved context.

## Source Priority

Do not ingest every source type at once.

Recommended order:

1. News
2. Company announcements
3. Earnings call transcripts
4. Financial reports
5. Research reports

Reason:

- News and announcements are easiest to validate against recent events.
- Earnings call transcripts are strong for management commentary.
- Financial reports and research reports need heavier parsing and normalization.

## Retrieval Rules

Retriever should support:

- same symbol exact match first
- market filter
- recent-first date bias
- document type filter
- top-k chunk retrieval

Avoid cross-symbol mixing in the first version unless the user explicitly asks for sector or peer analysis.

## Agent Composition

The Spring AI RAG Agent should combine:

- technical indicators
- ML prediction output
- retrieved document evidence

The first version should keep these as separate prompt sections instead of building a new planner or orchestration layer.

## Model Comparison Rule

All models must use the same retrieved chunk set for one comparison run.

That is required if the output is used to compare model differences rather than retrieval differences.

## Frontend Requirements

When RAG is added, the frontend should show:

- cited source title
- document type
- published date
- short evidence snippet

Do not ship RAG output without visible evidence metadata.

## Non-Goals For First RAG Slice

- full autonomous agent workflow
- user auth
- multi-tenant document ownership
- report upload UI
- live crawling pipeline for every source
- advanced reranker stack

## Recommended Next Step

After MVP step 55, start with:

1. backend document metadata schema
2. ingestion for news + company announcements
3. RAG-enabled AI Chat
