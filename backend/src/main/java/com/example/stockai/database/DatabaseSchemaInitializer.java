package com.example.stockai.database;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@Conditional(DatabaseUrlCondition.class)
class DatabaseSchemaInitializer {
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    DatabaseSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void initialize() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS stockai_users (
                email TEXT PRIMARY KEY,
                password_salt TEXT NOT NULL DEFAULT '',
                password_hash TEXT NOT NULL DEFAULT '',
                created_at TIMESTAMPTZ NOT NULL,
                auth_provider TEXT NOT NULL DEFAULT 'LOCAL'
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS stockai_account_settings (
                email TEXT PRIMARY KEY REFERENCES stockai_users(email) ON DELETE CASCADE,
                preferred_provider TEXT NOT NULL DEFAULT '',
                openai_api_key TEXT NOT NULL DEFAULT '',
                gemini_api_key TEXT NOT NULL DEFAULT '',
                deepseek_api_key TEXT NOT NULL DEFAULT '',
                mimo_api_key TEXT NOT NULL DEFAULT '',
                updated_at TIMESTAMPTZ NOT NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS stockai_watchlist (
                email TEXT NOT NULL DEFAULT '',
                market TEXT NOT NULL,
                symbol TEXT NOT NULL,
                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                PRIMARY KEY (email, market, symbol)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS stockai_stock_snapshots (
                market TEXT NOT NULL,
                symbol TEXT NOT NULL,
                name TEXT NOT NULL,
                currency TEXT NOT NULL,
                last_price NUMERIC NOT NULL,
                change_percent NUMERIC NOT NULL,
                prices NUMERIC[] NOT NULL,
                source TEXT NOT NULL,
                captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                PRIMARY KEY (market, symbol)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS stockai_document_chunks (
                chunk_id TEXT PRIMARY KEY,
                symbol TEXT NOT NULL,
                market TEXT NOT NULL,
                doc_type TEXT NOT NULL,
                title TEXT NOT NULL,
                source TEXT NOT NULL,
                published_at TIMESTAMPTZ NOT NULL,
                content TEXT NOT NULL,
                embedding_model TEXT NOT NULL,
                embedding vector(16) NOT NULL,
                updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
            )
            """);
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS stockai_document_chunks_embedding_idx
            ON stockai_document_chunks USING ivfflat (embedding vector_cosine_ops)
            WITH (lists = 100)
            """);
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS stockai_document_chunks_filter_idx
            ON stockai_document_chunks (symbol, market, doc_type, published_at)
            """);
    }
}
