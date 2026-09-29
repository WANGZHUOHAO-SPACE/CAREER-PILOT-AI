package com.careerpilot.rag;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Keeps legacy in-memory vector tests independent of a running PostgreSQL server. */
final class KnowledgeTestFixture {
    private KnowledgeTestFixture() { }

    static VectorDocumentRepository countsFrom(SimpleVectorStore store) {
        VectorDocumentRepository repository = mock(VectorDocumentRepository.class);
        when(repository.countForUser(anyLong())).thenAnswer(invocation -> {
            long userId = invocation.getArgument(0);
            List<Document> chunks = store.similaritySearch(SearchRequest.builder()
                    .query("count").topK(1000).similarityThreshold(0)
                    .filterExpression("userId == '" + userId + "'").build());
            int documents = (int) chunks.stream()
                    .map(chunk -> chunk.getMetadata().get("documentId")).distinct().count();
            return new VectorDocumentRepository.Counts(documents, chunks.size());
        });
        return repository;
    }
}
