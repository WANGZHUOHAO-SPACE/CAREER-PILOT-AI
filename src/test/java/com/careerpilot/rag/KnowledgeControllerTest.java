package com.careerpilot.rag;

import java.nio.charset.StandardCharsets;
import com.careerpilot.TestSecurity;
import org.junit.jupiter.api.BeforeEach;

import com.careerpilot.config.RagConfig;
import com.careerpilot.web.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeControllerTest {
    @BeforeEach void authenticate() { TestSecurity.as(1); }

    @Test
    void multipartUploadStatusAndClearWorkOverHttp() throws Exception {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.dimensions()).thenReturn(2);
        when(embeddingModel.embed(any(Document.class))).thenReturn(new float[] {1, 0});
        when(embeddingModel.embed(any(String.class))).thenReturn(new float[] {1, 0});
        RagConfig config = new RagConfig();
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        KnowledgeService service = new KnowledgeService(new KnowledgeDocumentLoader(),
                config.knowledgeTextSplitter(), store, KnowledgeTestFixture.countsFrom(store));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new KnowledgeController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();

        mvc.perform(multipart("/api/knowledge/upload").file(new MockMultipartFile("file", "projects.md",
                "text/markdown", "CareerPilot AI使用MySQL。".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("projects.md"))
                .andExpect(jsonPath("$.chunks").value(1));
        mvc.perform(get("/api/knowledge/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentCount").value(1));
        mvc.perform(delete("/api/knowledge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunkCount").value(0));
        mvc.perform(multipart("/api/knowledge/upload").file(new MockMultipartFile("file", "empty.md",
                "text/markdown", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("上传文件不能为空"));
    }
}
