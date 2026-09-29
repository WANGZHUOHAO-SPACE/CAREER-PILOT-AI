package com.careerpilot.rag;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import com.careerpilot.TestSecurity;
import com.careerpilot.config.RagConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Opt in with -Pvector-integration-test against a dedicated test database. No paid API calls. */
class PgVectorStoreIT {
    @Test
    void persistsAcrossStoreRecreationAndIsolatesSameKeywordOwners() {
        try (HikariDataSource source = source()) {
            JdbcTemplate jdbc = new JdbcTemplate(source);
            PgVectorStore store = store(jdbc);
            long ownerA = owner();
            long ownerB = owner();
            try {
                KnowledgeService service = service(jdbc, store);
                TestSecurity.as(ownerA);
                service.upload(file("a.md", "Java project database is MySQL-A. CareerPilot Persistent RAG Test ABC123."));
                service.upload(file("secret-a.md", "My private vector secret is VECTOR-A-12345."));
                assertThat(service.status().documentCount()).isEqualTo(2);

                TestSecurity.as(ownerB);
                service.upload(file("b.md", "Java project database is PostgreSQL-B. My private vector secret is VECTOR-B-98765."));
                assertThat(service.status().documentCount()).isEqualTo(1);
                assertThat(service.search("Java project database")).isNotEmpty().allSatisfy(hit -> {
                    assertThat(hit.content()).contains("PostgreSQL-B").doesNotContain("MySQL-A", "VECTOR-A-12345");
                });

                // New pool and Store simulate loss of all backend in-memory state.
                try (HikariDataSource restarted = source()) {
                    JdbcTemplate restartedJdbc = new JdbcTemplate(restarted);
                    KnowledgeService afterRestart = service(restartedJdbc, store(restartedJdbc));
                    TestSecurity.as(ownerA);
                    assertThat(afterRestart.status().documentCount()).isEqualTo(2);
                    assertThat(afterRestart.search("ABC123")).anySatisfy(hit -> assertThat(hit.content()).contains("ABC123"));
                    assertThat(afterRestart.search("Java project database")).allSatisfy(hit ->
                            assertThat(hit.content()).doesNotContain("PostgreSQL-B", "VECTOR-B-98765"));
                    assertThat(afterRestart.clear().chunkCount()).isZero();
                    assertThat(afterRestart.search("ABC123")).isEmpty();
                    TestSecurity.as(ownerB);
                    assertThat(afterRestart.status().documentCount()).isEqualTo(1);
                    assertThat(afterRestart.search("Java project database")).isNotEmpty();
                }
                assertThat(jdbc.queryForObject("SELECT extversion FROM pg_extension WHERE extname='vector'", String.class)).isNotBlank();
                assertThat(jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE tablename='vector_store'", String.class))
                        .anyMatch(sql -> sql.contains("hnsw") && sql.contains("vector_cosine_ops"));
            }
            finally {
                store.delete("userId == '" + ownerA + "'");
                store.delete("userId == '" + ownerB + "'");
            }
        }
    }

    @Test
    void localDevelopmentSearchBenchmarkWithMockEmbeddings() {
        try (HikariDataSource source = source()) {
            PgVectorStore store = store(new JdbcTemplate(source));
            long owner = owner();
            try {
                for (int size : new int[] {100, 500, 1000}) {
                    store.delete("userId == '" + owner + "'");
                    store.add(IntStream.range(0, size).mapToObj(index -> Document.builder()
                            .id(UUID.randomUUID().toString()).text("Local benchmark chunk " + index)
                            .metadata(Map.of("userId", Long.toString(owner), "documentId", "benchmark")).build()).toList());
                    long duration = 0;
                    for (int query = 0; query < 10; query++) {
                        long started = System.nanoTime();
                        assertThat(store.similaritySearch(SearchRequest.builder().query("benchmark")
                                .topK(4).filterExpression("userId == '" + owner + "'").build())).isNotEmpty();
                        duration += System.nanoTime() - started;
                    }
                    org.slf4j.LoggerFactory.getLogger(getClass()).info(
                            "Local Development Benchmark mockEmbedding=true chunks={} queries=10 averageMs={}",
                            size, duration / 10_000_000.0);
                }
            }
            finally { store.delete("userId == '" + owner + "'"); }
        }
    }

    private static HikariDataSource source() {
        String password = System.getenv("PGVECTOR_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalStateException("Set PGVECTOR_PASSWORD for an isolated test database");
        HikariDataSource source = new HikariDataSource();
        source.setJdbcUrl("jdbc:postgresql://" + env("PGVECTOR_HOST", "localhost") + ":"
                + env("PGVECTOR_PORT", "5432") + "/" + env("PGVECTOR_DATABASE", "career_pilot_vector_test"));
        source.setUsername(env("PGVECTOR_USERNAME", "career_pilot"));
        source.setPassword(password);
        source.setMaximumPoolSize(2);
        source.setConnectionTimeout(5000);
        return source;
    }

    private static PgVectorStore store(JdbcTemplate jdbc) {
        int dimensions = Integer.parseInt(env("PGVECTOR_DIMENSIONS", "1536"));
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.getEmbeddingContent(any(Document.class))).thenAnswer(inv -> inv.<Document>getArgument(0).getText());
        when(model.call(any(EmbeddingRequest.class))).thenAnswer(inv -> {
            EmbeddingRequest request = inv.getArgument(0);
            float[] vector = new float[dimensions];
            vector[0] = 1;
            return new EmbeddingResponse(IntStream.range(0, request.getInstructions().size())
                    .mapToObj(index -> new Embedding(vector, index)).toList());
        });
        PgVectorStore store = new RagConfig().knowledgeVectorStore(jdbc, model, dimensions, true, true);
        store.afterPropertiesSet();
        return store;
    }

    private static KnowledgeService service(JdbcTemplate jdbc, PgVectorStore store) {
        return new KnowledgeService(new KnowledgeDocumentLoader(), new RagConfig().knowledgeTextSplitter(),
                store, new VectorDocumentRepository(jdbc));
    }

    private static long owner() { return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE; }
    private static String env(String name, String fallback) { return System.getenv().getOrDefault(name, fallback); }
    private static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "text/markdown", content.getBytes(StandardCharsets.UTF_8));
    }
}
