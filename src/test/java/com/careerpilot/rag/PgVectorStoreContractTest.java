package com.careerpilot.rag;

import java.util.List;
import com.careerpilot.TestSecurity;
import com.careerpilot.config.RagConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PgVectorStoreContractTest {
    @Test
    void uploadUsesOfficialPgStoreBatchWithOwnerAndDocumentMetadata() throws Exception {
        TestSecurity.as(42);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var store = new RagConfig().knowledgeVectorStore(jdbc, model(3), 3, false, false);
        KnowledgeService service = service(store, mock(VectorDocumentRepository.class));
        assertThat(service.upload(new MockMultipartFile("file", "projects.md", "text/markdown",
                "CareerPilot uses MySQL.".getBytes(StandardCharsets.UTF_8))).chunks()).isEqualTo(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BatchPreparedStatementSetter> batch = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbc).batchUpdate(sql.capture(), batch.capture());
        assertThat(sql.getValue()).contains("INSERT INTO public.vector_store", "metadata", "embedding");
        PreparedStatement statement = mock(PreparedStatement.class);
        batch.getValue().setValues(statement, 0);
        assertThat(mockingDetails(statement).getInvocations()).anyMatch(invocation ->
                java.util.Arrays.stream(invocation.getArguments()).anyMatch(value ->
                        value instanceof String metadata && metadata.contains("\"userId\":\"42\"")
                                && metadata.contains("\"documentId\"") && metadata.contains("projects.md")));
    }

    @Test
    void officialSchemaInitializerCreatesVectorExtensionTableAndHnswIndex() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var store = new RagConfig().knowledgeVectorStore(jdbc, model(3), 3, true, false);
        store.afterPropertiesSet();
        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).execute(statements.capture());
        assertThat(statements.getAllValues()).anyMatch(sql -> sql.equals("CREATE EXTENSION IF NOT EXISTS vector"));
        assertThat(statements.getAllValues()).anyMatch(sql -> sql.contains("embedding vector(3)"));
        assertThat(statements.getAllValues()).anyMatch(sql -> sql.contains("USING HNSW") && sql.contains("vector_cosine_ops"));
        assertThat(statements.getAllValues()).noneMatch(sql -> sql.contains("DROP TABLE"));
    }

    @Test
    void officialSearchAndDeletePutOwnerFilterInSql() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var store = new RagConfig().knowledgeVectorStore(jdbc, model(3), 3, false, false);
        store.similaritySearch(SearchRequest.builder().query("shared keyword").topK(4)
                .filterExpression("userId == '42'").build());
        ArgumentCaptor<String> search = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(search.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(search.getValue()).contains("WHERE", "userId", "42", "LIMIT");
        assertThat(search.getValue().indexOf("userId")).isLessThan(search.getValue().indexOf("LIMIT"));

        store.delete("userId == '42'");
        ArgumentCaptor<String> deletion = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(deletion.capture());
        assertThat(deletion.getValue()).contains("DELETE FROM public.vector_store WHERE", "userId", "42");
    }

    @Test
    void statusComesFromRepositoryAfterServiceRecreationAndClearIsOwnerScoped() {
        TestSecurity.as(42);
        VectorStore store = mock(VectorStore.class);
        VectorDocumentRepository repository = mock(VectorDocumentRepository.class);
        when(repository.countForUser(42)).thenReturn(new VectorDocumentRepository.Counts(2, 7));
        KnowledgeService first = service(store, repository);
        KnowledgeService restarted = service(store, repository);
        assertThat(first.status()).isEqualTo(restarted.status());
        assertThat(restarted.status().documentCount()).isEqualTo(2);
        assertThat(restarted.status().chunkCount()).isEqualTo(7);
        restarted.clear();
        verify(store).delete("userId == '42'");
        verify(store, never()).delete(anyList());
        verify(repository, atLeastOnce()).countForUser(42);
    }

    @Test
    void realProviderDimensionMismatchIsRejectedBeforeSqlQuery() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var store = new RagConfig().knowledgeVectorStore(jdbc, model(2), 3, false, false);
        assertThatThrownBy(() -> store.similaritySearch("query"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("dimension mismatch");
        verifyNoInteractions(jdbc);
        assertThatThrownBy(() -> new RagConfig().knowledgeVectorStore(jdbc, model(2), 0, false, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void persistentCountsUseBoundCurrentUserId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new VectorDocumentRepository(jdbc).countForUser(42);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), any(RowMapper.class), eq("42"));
        assertThat(sql.getValue()).contains("COUNT(DISTINCT metadata->>'documentId')", "metadata->>'userId' = ?");
    }

    private static EmbeddingModel model(int dimensions) {
        EmbeddingModel model = mock(EmbeddingModel.class);
        float[] vector = new float[dimensions];
        vector[0] = 1;
        when(model.call(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new Embedding(vector, 0))));
        when(model.getEmbeddingContent(any(Document.class))).thenAnswer(inv -> inv.<Document>getArgument(0).getText());
        return model;
    }

    private static KnowledgeService service(VectorStore store, VectorDocumentRepository repository) {
        return new KnowledgeService(new KnowledgeDocumentLoader(), new RagConfig().knowledgeTextSplitter(), store, repository);
    }
}
