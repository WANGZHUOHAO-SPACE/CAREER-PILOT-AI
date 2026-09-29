package com.careerpilot.localtest;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LocalTestEmbeddingModelTest {
    private final LocalTestEmbeddingModel model = new LocalTestEmbeddingModel(true, 1536);

    @Test
    void vectorsAreDeterministicNormalizedAndHaveFixedDimensions() {
        float[] first = model.embed("CareerPilot Java database");
        assertThat(first).hasSize(1536).isEqualTo(model.embed("CAREERPILOT java DATABASE"));
        assertThat(cosine(first, first)).isCloseTo(1, within(0.00001));
        assertThat(model.embed("different tokens")).isNotEqualTo(first);
    }

    @Test
    void keywordsHaveRelativeSimilarityAndAcceptanceQueryClearsExistingThreshold() {
        float[] query = model.embed("我的 CareerPilot Java database 是什么？");
        float[] related = model.embed("My private persistent vector secret is VECTOR-A-12345. CareerPilot Java database for user A is MYSQL-A.");
        float[] unrelated = model.embed("weather travel food music");
        assertThat(cosine(query, related)).isGreaterThan(0.75).isGreaterThan(cosine(query, unrelated));
    }

    @Test
    void explicitOptInAndDimensionsAreRequired() {
        assertThatThrownBy(() -> new LocalTestEmbeddingModel(false, 1536)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LocalTestEmbeddingModel(true, 3)).isInstanceOf(IllegalStateException.class);
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int index = 0; index < a.length; index++) {
            dot += a[index] * b[index]; normA += a[index] * a[index]; normB += b[index] * b[index];
        }
        return dot / Math.sqrt(normA * normB);
    }
}
