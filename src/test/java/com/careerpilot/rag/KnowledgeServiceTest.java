package com.careerpilot.rag;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.careerpilot.TestSecurity;
import org.junit.jupiter.api.BeforeEach;

import com.careerpilot.config.RagConfig;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class KnowledgeServiceTest {
    @BeforeEach void authenticate() { TestSecurity.as(1); }

    @Test
    void pdfIsParsedSplitEmbeddedAndStored() throws Exception {
        Fixture fixture = fixture();
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes());

        KnowledgeService.UploadResult result = fixture.service.upload(file);
        List<Document> retrieved = fixture.store.similaritySearch(SearchRequest.builder()
                .query("CareerPilot AI MySQL project").topK(4).build());

        assertEquals("resume.pdf", result.fileName());
        assertEquals("success", result.status());
        assertTrue(result.chunks() >= 1);
        assertFalse(retrieved.isEmpty());
        assertTrue(retrieved.getFirst().getText().contains("MySQL"));
        assertEquals("resume.pdf", retrieved.getFirst().getMetadata().get("fileName"));
        assertEquals("pdf", retrieved.getFirst().getMetadata().get("documentType"));
        assertTrue(retrieved.getFirst().getMetadata().containsKey("uploadTime"));
        assertEquals("1", retrieved.getFirst().getMetadata().get("userId"));
        java.util.UUID.fromString((String) retrieved.getFirst().getMetadata().get("documentId"));
        java.util.UUID.fromString(retrieved.getFirst().getId());
        assertEquals(1, fixture.service.status().documentCount());
        assertEquals(result.chunks(), fixture.service.status().chunkCount());
        assertEquals("resume.pdf", fixture.service.search("CareerPilot AI MySQL project").getFirst().fileName());
    }

    @Test
    void textAndMarkdownAreIndexedAndCanBeCleared() {
        Fixture fixture = fixture();
        assertTrue(fixture.service.search("MySQL").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> fixture.service.search("  "));
        fixture.service.upload(new MockMultipartFile("file", "projects.md", "text/markdown",
                "CareerPilot AI项目使用Spring Boot、Spring AI、MySQL开发。".getBytes(StandardCharsets.UTF_8)));
        fixture.service.upload(new MockMultipartFile("file", "notes.txt", "text/plain",
                "Java 后端实习求职资料".getBytes(StandardCharsets.UTF_8)));
        assertEquals(2, fixture.service.status().documentCount());
        assertEquals("PgVectorStore", fixture.service.status().vectorStoreType());

        assertEquals(0, fixture.service.clear().chunkCount());
        assertTrue(fixture.store.similaritySearch(SearchRequest.builder()
                .query("CareerPilot AI MySQL").build()).isEmpty());
    }

    @Test
    void rejectsEmptyUnsupportedAndUnreadablePdf() {
        Fixture fixture = fixture();
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(KnowledgeException.class,
                () -> fixture.service.upload(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0])))
                .status());
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, assertThrows(KnowledgeException.class,
                () -> fixture.service.upload(new MockMultipartFile("file", "image.png", "image/png", new byte[] {1})))
                .status());
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, assertThrows(KnowledgeException.class,
                () -> fixture.service.upload(new MockMultipartFile("file", "bad.pdf", "application/pdf",
                        "not a pdf".getBytes(StandardCharsets.UTF_8)))).status());
    }

    @Test
    void reportsEmbeddingFailureWithoutChangingStatistics() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.dimensions()).thenReturn(2);
        when(embeddingModel.embed(any(String.class))).thenReturn(new float[] {1, 0});
        when(embeddingModel.embed(any(Document.class))).thenThrow(new IllegalStateException("provider error"));
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        KnowledgeService service = new KnowledgeService(new KnowledgeDocumentLoader(),
                new RagConfig().knowledgeTextSplitter(), store, KnowledgeTestFixture.countsFrom(store));

        KnowledgeException exception = assertThrows(KnowledgeException.class,
                () -> service.upload(new MockMultipartFile("file", "projects.md", "text/markdown",
                        "CareerPilot AI项目使用MySQL开发。".getBytes(StandardCharsets.UTF_8))));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.status());
        assertEquals(0, service.status().documentCount());
    }

    @Test
    void reportsVectorStoreWriteFailureWithoutChangingStatistics() {
        SimpleVectorStore store = mock(SimpleVectorStore.class);
        doThrow(new IllegalStateException("write error")).when(store).add(any(List.class));
        VectorDocumentRepository repository = mock(VectorDocumentRepository.class);
        when(repository.countForUser(1)).thenReturn(new VectorDocumentRepository.Counts(0, 0));
        KnowledgeService service = new KnowledgeService(new KnowledgeDocumentLoader(),
                new RagConfig().knowledgeTextSplitter(), store, repository);

        KnowledgeException exception = assertThrows(KnowledgeException.class,
                () -> service.upload(new MockMultipartFile("file", "projects.md", "text/markdown",
                        "CareerPilot AI项目使用MySQL开发。".getBytes(StandardCharsets.UTF_8))));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.status());
        assertEquals(0, service.status().chunkCount());
    }

    @Test
    void usersCannotSearchCountOrClearEachOthersKnowledge() {
        Fixture fixture = fixture();
        fixture.service.upload(new MockMultipartFile("file", "secret.md", "text/markdown",
                "MySecretProject 使用特殊技术 ABC123。".getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, fixture.service.status().documentCount());

        TestSecurity.as(2);
        assertEquals(0, fixture.service.status().documentCount());
        assertTrue(fixture.service.search("ABC123").isEmpty());
        fixture.service.upload(new MockMultipartFile("file", "user-b.md", "text/markdown",
                "User B 的独立项目使用 XYZ789。".getBytes(StandardCharsets.UTF_8)));
        List<KnowledgeService.KnowledgeHit> userBHits = fixture.service.search("ABC123");
        assertEquals(1, userBHits.size());
        assertTrue(userBHits.getFirst().content().contains("XYZ789"));
        assertFalse(userBHits.getFirst().content().contains("ABC123"));
        fixture.service.clear();

        TestSecurity.as(1);
        assertEquals(1, fixture.service.status().documentCount());
        List<KnowledgeService.KnowledgeHit> userAHits = fixture.service.search("ABC123");
        assertEquals(1, userAHits.size());
        assertTrue(userAHits.getFirst().content().contains("ABC123"));
    }

    private static Fixture fixture() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.dimensions()).thenReturn(2);
        when(embeddingModel.embed(any(Document.class))).thenReturn(new float[] {1, 0});
        when(embeddingModel.embed(any(String.class))).thenReturn(new float[] {1, 0});
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        return new Fixture(new KnowledgeService(new KnowledgeDocumentLoader(),
                new RagConfig().knowledgeTextSplitter(), store,
                KnowledgeTestFixture.countsFrom(store)), store);
    }

    private static byte[] pdfBytes() throws Exception {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("CareerPilot AI project uses Spring Boot, Spring AI and MySQL.");
                stream.endText();
            }
            pdf.save(bytes);
            return bytes.toByteArray();
        }
    }

    private record Fixture(KnowledgeService service, SimpleVectorStore store) {
    }
}
