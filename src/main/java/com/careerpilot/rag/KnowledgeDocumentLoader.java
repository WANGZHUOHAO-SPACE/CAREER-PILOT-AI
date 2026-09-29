package com.careerpilot.rag;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class KnowledgeDocumentLoader {

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    public LoadedDocuments load(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new KnowledgeException(HttpStatus.BAD_REQUEST, "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new KnowledgeException(HttpStatus.CONTENT_TOO_LARGE, "上传文件超过 10MB 限制");
        }
        String originalName = file.getOriginalFilename();
        String fileName = originalName == null ? "" : originalName;
        if (fileName.isBlank() || fileName.length() > 255 || fileName.contains("/")
                || fileName.contains("\\") || fileName.contains(":")
                || fileName.codePoints().anyMatch(Character::isISOControl)) {
            throw new KnowledgeException(HttpStatus.BAD_REQUEST, "文件名无效：不能包含路径或控制字符");
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        String documentType = lowerName.endsWith(".pdf") ? "pdf"
                : lowerName.endsWith(".txt") ? "txt"
                : lowerName.endsWith(".md") ? "md" : "";
        if (documentType.isEmpty()) {
            throw new KnowledgeException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "仅支持 PDF、TXT 和 MD 文件");
        }
        if (fileName.isBlank() || fileName.length() > 255) {
            throw new KnowledgeException(HttpStatus.BAD_REQUEST, "文件名无效或过长");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            boolean generic = mediaType.equals("application/octet-stream");
            boolean compatible = documentType.equals("pdf")
                    ? mediaType.equals("application/pdf") || mediaType.equals("application/x-pdf")
                    : mediaType.equals("text/plain") || mediaType.equals("text/markdown");
            if (!generic && !compatible) {
                throw new KnowledgeException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "文件类型与扩展名不匹配");
            }
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        }
        catch (IOException exception) {
            throw new KnowledgeException(HttpStatus.UNPROCESSABLE_CONTENT, "读取上传文件失败", exception);
        }
        if (documentType.equals("pdf") && (bytes.length < 5 || bytes[0] != '%'
                || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F' || bytes[4] != '-')) {
            throw new KnowledgeException(HttpStatus.UNPROCESSABLE_CONTENT, "PDF 文件头无效");
        }

        List<Document> source = documentType.equals("pdf") ? readPdf(bytes, fileName) : readText(bytes);
        String uploadTime = Instant.now().toString();
        List<Document> documents = new ArrayList<>();
        for (Document page : source) {
            if (page.getText() == null || page.getText().isBlank()) {
                continue;
            }
            Map<String, Object> metadata = new HashMap<>(page.getMetadata());
            metadata.put("fileName", fileName);
            metadata.put("documentType", documentType);
            metadata.put("uploadTime", uploadTime);
            documents.add(Document.builder().id(UUID.randomUUID().toString())
                    .text(page.getText()).metadata(metadata).build());
        }
        if (documents.isEmpty()) {
            throw new KnowledgeException(HttpStatus.UNPROCESSABLE_CONTENT, "文件没有可提取的文本内容");
        }
        return new LoadedDocuments(fileName, documents);
    }

    private List<Document> readPdf(byte[] bytes, String fileName) {
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        try {
            return new PagePdfDocumentReader(resource).read();
        }
        catch (RuntimeException exception) {
            throw new KnowledgeException(HttpStatus.UNPROCESSABLE_CONTENT, "PDF 无法解析，请检查文件是否损坏或加密", exception);
        }
    }

    private List<Document> readText(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            if (text.indexOf('\0') >= 0) {
                throw new KnowledgeException(HttpStatus.UNPROCESSABLE_CONTENT, "文本文件包含无效字符");
            }
            return List.of(Document.builder().text(text).build());
        }
        catch (CharacterCodingException exception) {
            throw new KnowledgeException(HttpStatus.UNPROCESSABLE_CONTENT, "文本文件必须使用 UTF-8 编码", exception);
        }
    }

    public record LoadedDocuments(String fileName, List<Document> documents) {
    }
}
