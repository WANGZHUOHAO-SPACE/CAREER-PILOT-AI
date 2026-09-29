package com.careerpilot.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;
import com.careerpilot.observability.ObservedVectorStore;
import com.careerpilot.rag.DimensionCheckedEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class RagConfig {

    public static final int TOP_K = 4;
    public static final double SIMILARITY_THRESHOLD = 0.75;
    public static final int CHUNK_TOKENS = 800;
    public static final int MAX_CHUNKS_PER_UPLOAD = 200;

    @Bean
    public TokenTextSplitter knowledgeTextSplitter() {
        return TokenTextSplitter.builder()
                .withChunkSize(CHUNK_TOKENS)
                .withMinChunkSizeChars(200)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(MAX_CHUNKS_PER_UPLOAD + 1)
                .build();
    }

    @Bean
    @Primary
    public PgVectorStore knowledgeVectorStore(
            @Qualifier("vectorJdbcTemplate") JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
            @Value("${app.pgvector.dimensions}") int dimensions,
            @Value("${app.pgvector.initialize-schema}") boolean initializeSchema,
            @Value("${app.pgvector.validate-schema}") boolean validateSchema) {
        if (dimensions < 1) {
            throw new IllegalArgumentException("PGVECTOR_DIMENSIONS must be positive and match OPENAI_EMBEDDING_MODEL");
        }
        return PgVectorStore.builder(jdbcTemplate, new DimensionCheckedEmbeddingModel(embeddingModel, dimensions))
                .dimensions(dimensions)
                .distanceType(PgDistanceType.COSINE_DISTANCE)
                .indexType(PgIndexType.HNSW)
                .initializeSchema(initializeSchema)
                .vectorTableValidationsEnabled(validateSchema)
                .removeExistingVectorStoreTable(false)
                .build();
    }

    @Bean
    public VectorStore observedRetrievalStore(PgVectorStore vectorStore) {
        return new ObservedVectorStore(vectorStore);
    }

    @Bean
    public QuestionAnswerAdvisor knowledgeAdvisor(@Qualifier("observedRetrievalStore") VectorStore vectorStore) {
        PromptTemplate template = PromptTemplate.builder().template("""
                用户问题：{query}

                以下是个人知识库检索结果（可能为空）：
                {question_answer_context}

                如问题涉及用户本人经历、技能、项目或教育，仅依据检索结果和明确的工具返回值回答。
                检索结果没有相关事实时，明确说“当前知识库没有找到相关资料。”，不得推测项目经历。
                普通知识问题可以使用通用知识回答。
                """).build();
        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder().topK(TOP_K)
                        .similarityThreshold(SIMILARITY_THRESHOLD).build())
                .promptTemplate(template)
                // Memory 先加入历史，RAG 再检索；Tool Calling 仍在后续 Advisor 中执行。
                .order(Ordered.HIGHEST_PRECEDENCE + 250)
                .build();
    }
}
