package com.careerpilot.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/** Verifies actual provider vectors without making an extra paid embedding request. */
public final class DimensionCheckedEmbeddingModel implements EmbeddingModel {
    private final EmbeddingModel delegate;
    private final int expectedDimensions;

    public DimensionCheckedEmbeddingModel(EmbeddingModel delegate, int expectedDimensions) {
        this.delegate = delegate;
        this.expectedDimensions = expectedDimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        EmbeddingResponse response = delegate.call(request);
        response.getResults().forEach(result -> check(result.getOutput()));
        return response;
    }

    @Override
    public float[] embed(Document document) {
        return check(delegate.embed(document));
    }

    @Override
    public String getEmbeddingContent(Document document) {
        return delegate.getEmbeddingContent(document);
    }

    @Override
    public int dimensions() {
        return expectedDimensions;
    }

    private float[] check(float[] vector) {
        if (vector == null || vector.length != expectedDimensions) {
            throw new IllegalStateException("Embedding dimension mismatch: check PGVECTOR_DIMENSIONS and OPENAI_EMBEDDING_MODEL");
        }
        return vector;
    }
}
