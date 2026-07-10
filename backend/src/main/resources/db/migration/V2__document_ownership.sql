ALTER TABLE stockai_document_chunks
ADD COLUMN IF NOT EXISTS owner_email TEXT NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS stockai_document_chunks_owner_filter_idx
ON stockai_document_chunks (owner_email, symbol, market, doc_type, published_at);
