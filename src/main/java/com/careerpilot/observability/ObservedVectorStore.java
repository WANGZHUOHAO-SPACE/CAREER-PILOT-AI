package com.careerpilot.observability;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

/** Only the chat retrieval Advisor uses this wrapper; uploads still use the underlying store. */
public class ObservedVectorStore implements VectorStore {
    private final VectorStore delegate;

    public ObservedVectorStore(VectorStore delegate) { this.delegate = delegate; }

    @Override public void add(List<Document> documents) { delegate.add(documents); }
    @Override public void delete(List<String> ids) { delegate.delete(ids); }
    @Override public void delete(Filter.Expression expression) { delegate.delete(expression); }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        ObservationScope.Trace trace = ObservationScope.current();
        long started = System.nanoTime();
        com.careerpilot.stream.StreamEvents.emit("rag.started", java.util.Map.of("topK", request.getTopK()));
        try {
            List<Document> result = delegate.similaritySearch(request);
            if (trace != null) trace.rag(request.getTopK(), result.size(), ObservationScope.elapsed(started), true);
            com.careerpilot.stream.StreamEvents.emit("rag.completed", java.util.Map.of("status", "SUCCESS", "chunkCount", result.size(), "durationMs", ObservationScope.elapsed(started)));
            return result;
        }
        catch (RuntimeException exception) {
            if (trace != null) trace.rag(request.getTopK(), 0, ObservationScope.elapsed(started), false);
            com.careerpilot.stream.StreamEvents.emit("rag.completed", java.util.Map.of("status", "FAILED", "chunkCount", 0, "durationMs", ObservationScope.elapsed(started)));
            throw exception;
        }
    }
}
