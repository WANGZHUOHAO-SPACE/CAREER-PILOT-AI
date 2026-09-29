package com.careerpilot.rag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.careerpilot.config.RagConfig;
import com.careerpilot.security.CurrentUser;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeService {

    private final KnowledgeDocumentLoader loader;
    private final TokenTextSplitter splitter;
    private final VectorStore vectorStore;
    private final VectorDocumentRepository documents;

    public KnowledgeService(KnowledgeDocumentLoader loader, TokenTextSplitter splitter,
            @org.springframework.beans.factory.annotation.Qualifier("knowledgeVectorStore") VectorStore vectorStore,
            VectorDocumentRepository documents) {
        this.loader = loader;
        this.splitter = splitter;
        this.vectorStore = vectorStore;
        this.documents = documents;
    }

    public UploadResult upload(MultipartFile file) {
        long userId = CurrentUser.id();
        KnowledgeDocumentLoader.LoadedDocuments loaded = loader.load(file);
        String documentId = UUID.randomUUID().toString();
        List<Document> chunks;
        try {
            chunks = splitter.split(loaded.documents()).stream().map(chunk -> {
                Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
                metadata.put("userId", Long.toString(userId));
                metadata.put("documentId", documentId);
                return Document.builder().id(UUID.randomUUID().toString())
                        .text(chunk.getText()).metadata(metadata).build();
            }).toList();
        }
        catch (RuntimeException exception) {
            throw new KnowledgeException(HttpStatus.UNPROCESSABLE_CONTENT, "文档切分失败", exception);
        }
        if (chunks.isEmpty()) {
            throw new KnowledgeException(HttpStatus.UNPROCESSABLE_CONTENT, "文件切分后没有可索引的文本");
        }
        if (chunks.size() > RagConfig.MAX_CHUNKS_PER_UPLOAD) {
            throw new KnowledgeException(HttpStatus.CONTENT_TOO_LARGE,
                    "文件切分后超过 200 个片段，请拆分文件后上传");
        }
        List<String> ids = chunks.stream().map(Document::getId).toList();
        try {
            vectorStore.add(chunks);
        }
        catch (RuntimeException exception) {
            try {
                vectorStore.delete(ids);
            }
            catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw new KnowledgeException(HttpStatus.SERVICE_UNAVAILABLE,
                    "文档 Embedding 或向量存储写入失败，请检查数据库、模型及 PGVECTOR_DIMENSIONS 配置后重试", exception);
        }
        return new UploadResult(loaded.fileName(), "success", chunks.size());
    }

    public KnowledgeStatus status() {
        try {
            VectorDocumentRepository.Counts counts = documents.countForUser(CurrentUser.id());
            return new KnowledgeStatus(counts.documents(), counts.chunks(), "PgVectorStore");
        }
        catch (RuntimeException exception) {
            throw new KnowledgeException(HttpStatus.SERVICE_UNAVAILABLE, "知识库状态查询失败，请稍后重试", exception);
        }
    }

    public List<KnowledgeHit> search(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("检索问题不能为空");
        }
        if (query.length() > 1000) {
            throw new IllegalArgumentException("检索问题不能超过 1000 个字符");
        }
        long userId = CurrentUser.id();
        try {
            return vectorStore.similaritySearch(SearchRequest.builder()
                            .query(query.trim())
                            .topK(RagConfig.TOP_K)
                            .similarityThreshold(RagConfig.SIMILARITY_THRESHOLD)
                            .filterExpression("userId == '" + userId + "'")
                            .build())
                    .stream().map(document -> new KnowledgeHit(
                            String.valueOf(document.getMetadata().getOrDefault("fileName", "")),
                            String.valueOf(document.getMetadata().getOrDefault("documentType", "")),
                            document.getText(), document.getScore())).toList();
        }
        catch (RuntimeException exception) {
            throw new KnowledgeException(HttpStatus.SERVICE_UNAVAILABLE, "知识库检索失败，请稍后重试", exception);
        }
    }

    public KnowledgeStatus clear() {
        long userId = CurrentUser.id();
        try {
            vectorStore.delete("userId == '" + userId + "'");
        }
        catch (RuntimeException exception) {
            throw new KnowledgeException(HttpStatus.SERVICE_UNAVAILABLE, "清空知识库失败，请稍后重试", exception);
        }
        return status();
    }

    public record UploadResult(String fileName, String status, int chunks) {
    }

    public record KnowledgeStatus(int documentCount, int chunkCount, String vectorStoreType) {
    }

    public record KnowledgeHit(String fileName, String documentType, String content, Double score) {
    }
}
