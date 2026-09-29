package com.careerpilot.localtest;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Local integration fixture only. Keyword hashes are not semantic AI embeddings. */
@Component
@Profile("local-test-embedding")
public final class LocalTestEmbeddingModel implements EmbeddingModel {
    public static final int DIMENSIONS = 1536;
    private static final Set<String> STOP_WORDS = Set.of("my", "is", "for", "user", "a", "b", "the", "what", "are", "of", "and", "in");

    public LocalTestEmbeddingModel(@Value("${app.local-test.enabled:false}") boolean enabled,
            @Value("${app.pgvector.dimensions}") int dimensions) {
        if (!enabled) throw new IllegalStateException("Local test profile requires LOCAL_TEST_EMBEDDING_ENABLED=true");
        if (dimensions != DIMENSIONS) throw new IllegalStateException("Local test embedding requires PGVECTOR_DIMENSIONS=1536");
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> inputs = request.getInstructions();
        return new EmbeddingResponse(IntStream.range(0, inputs.size())
                .mapToObj(index -> new Embedding(vector(inputs.get(index)), index)).toList());
    }

    @Override public float[] embed(Document document) { return vector(document.getText()); }
    @Override public int dimensions() { return DIMENSIONS; }

    private static float[] vector(String input) {
        String normalized = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace("数据库", " database ").replace("我的", " ").replace("是什么", " ").replace("什么", " ");
        HashMap<Integer, Integer> frequencies = new HashMap<>();
        for (String token : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (token.isBlank() || STOP_WORDS.contains(token)) continue;
            frequencies.merge(bucket(token), 1, Integer::sum);
        }
        float[] result = new float[DIMENSIONS];
        // A common fixture feature keeps short mixed-topic documents above the unchanged 0.75 cutoff.
        // This deliberately weakens discrimination; only relative keyword similarity is meaningful here.
        result[0] = 6;
        frequencies.forEach((position, count) -> result[position] = (float) Math.sqrt(count));
        double magnitude = 0;
        for (float value : result) magnitude += value * value;
        double divisor = Math.sqrt(magnitude);
        for (int index = 0; index < result.length; index++) result[index] /= (float) divisor;
        return result;
    }

    private static int bucket(String token) {
        int hash = 0x811c9dc5;
        for (byte value : token.getBytes(StandardCharsets.UTF_8)) hash = (hash ^ (value & 0xff)) * 0x01000193;
        return 1 + Math.floorMod(hash, DIMENSIONS - 1);
    }
}
