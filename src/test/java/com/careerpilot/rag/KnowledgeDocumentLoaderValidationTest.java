package com.careerpilot.rag;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeDocumentLoaderValidationTest {

    private final KnowledgeDocumentLoader loader = new KnowledgeDocumentLoader();

    @Test void rejectsDangerousFileNames() {
        for (String name : java.util.List.of("../notes.md", "C:\\notes.md", "folder/notes.txt", "notes\n.md", "a\0.txt")) {
            var file = new MockMultipartFile("file", name, "text/plain", new byte[] {'a'});
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(KnowledgeException.class, () -> loader.load(file)).status());
        }
    }

    @Test void rejectsEmptyOversizedAndUnsupportedFiles() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(KnowledgeException.class, () -> loader.load(
                new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]))).status());
        assertEquals(HttpStatus.CONTENT_TOO_LARGE, assertThrows(KnowledgeException.class, () -> loader.load(
                new MockMultipartFile("file", "large.txt", "text/plain", new byte[10 * 1024 * 1024 + 1]))).status());
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, assertThrows(KnowledgeException.class, () -> loader.load(
                new MockMultipartFile("file", "run.exe", "application/octet-stream", new byte[] {1}))).status());
    }

    @Test
    void rejectsMismatchedContentType() {
        var file = new MockMultipartFile("file", "notes.md", "image/png",
                "text".getBytes(StandardCharsets.UTF_8));
        KnowledgeException error = assertThrows(KnowledgeException.class, () -> loader.load(file));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, error.status());
    }

    @Test
    void rejectsFakePdfHeader() {
        var file = new MockMultipartFile("file", "resume.pdf", "application/pdf",
                "not a PDF".getBytes(StandardCharsets.UTF_8));
        KnowledgeException error = assertThrows(KnowledgeException.class, () -> loader.load(file));
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, error.status());
    }

    @Test
    void rejectsBinaryText() {
        var file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {'a', 0, 'b'});
        KnowledgeException error = assertThrows(KnowledgeException.class, () -> loader.load(file));
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, error.status());
    }
}
