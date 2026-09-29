package com.careerpilot.rag;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Counts persisted chunks by owner; similarity search and deletion remain in PgVectorStore. */
@Repository
public class VectorDocumentRepository {
    private final JdbcTemplate jdbcTemplate;

    public VectorDocumentRepository(@Qualifier("vectorJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Counts countForUser(long userId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT metadata->>'documentId') AS documents, COUNT(*) AS chunks
                FROM public.vector_store
                WHERE metadata->>'userId' = ?
                """, (result, row) -> new Counts(result.getInt("documents"), result.getInt("chunks")),
                Long.toString(userId));
    }

    public record Counts(int documents, int chunks) {
    }
}
