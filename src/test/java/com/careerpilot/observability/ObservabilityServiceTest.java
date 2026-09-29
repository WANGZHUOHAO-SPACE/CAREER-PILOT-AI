package com.careerpilot.observability;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careerpilot.TestSecurity;
import com.careerpilot.common.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ObservabilityServiceTest {
    @BeforeEach void authenticate() { TestSecurity.as(1); }

    @Test void streamingMetricsAndUnknownUsageAreNotFabricated() {
        ObservationScope.Trace trace = ObservationScope.detached("stream-metrics", 1, "test-001");
        trace.streaming(10L, 30L, 3, "CANCELLED");
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage("private partial"))),
                ChatResponseMetadata.builder().model("local-test-fixture").usage(new DefaultUsage(0, 0, 0)).build());
        AiRequestLog row = ObservabilityService.toRow(trace, response, "configured", null);
        assertEquals("CANCELLED", row.getStatus());
        assertTrue(row.getCancelled());
        assertEquals(10L, row.getTimeToFirstTokenMs());
        assertEquals(30L, row.getStreamDurationMs());
        assertEquals(3, row.getOutputChunkCount());
        assertEquals("LOCAL_TEST", row.getProvider());
        assertNull(row.getTotalTokens());
    }

    @Test
    void realProviderUsageAndSuccessfulTraceAreStoredWithoutContent() {
        AiRequestLogMapper requests = mock(AiRequestLogMapper.class);
        AiTraceEventMapper events = mock(AiTraceEventMapper.class);
        ObservabilityService service = new ObservabilityService(requests, events);
        ObservationScope.Trace trace = ObservationScope.begin("req-success", 1, "conversation-a");
        try {
            trace.memory(4);
            ObservationScope.tool("getResume", () -> "private resume text");
            ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage("private answer"))),
                    ChatResponseMetadata.builder().model("provider-model")
                            .usage(new DefaultUsage(100, 50, 150)).build());
            trace.event("LLM", "LLM_RESPONSE", "SUCCESS", 1, null, null, null);
            trace.event("AI_REQUEST", "AI_REQUEST_SUCCESS", "SUCCESS", trace.latencyMs(), null, null, null);
            service.recordSafely(trace, response, "configured-model", null);
        }
        finally { ObservationScope.end(); }

        var request = org.mockito.ArgumentCaptor.forClass(AiRequestLog.class);
        verify(requests).insert(request.capture());
        AiRequestLog row = request.getValue();
        assertEquals("req-success", row.getRequestId());
        assertEquals(1L, row.getUserId());
        assertEquals("SUCCESS", row.getStatus());
        assertEquals("provider-model", row.getModelName());
        assertEquals(100, row.getInputTokens());
        assertEquals(50, row.getOutputTokens());
        assertEquals(150, row.getTotalTokens());
        assertEquals(1, row.getToolCallCount());
        assertTrue(row.getLatencyMs() >= 0);
        var event = org.mockito.ArgumentCaptor.forClass(AiTraceEvent.class);
        verify(events, atLeastOnce()).insert(event.capture());
        assertTrue(event.getAllValues().stream().anyMatch(value -> "getResume".equals(value.getEventName())));
        assertTrue(event.getAllValues().stream().anyMatch(value -> "MEMORY".equals(value.getEventType())));
        assertFalse(event.getAllValues().toString().contains("private resume text"));
        assertFalse(event.getAllValues().toString().contains("private answer"));
    }

    @Test
    void failedRequestIsRecordedAndWriteFailureDoesNotReplaceOriginalFailure() {
        AiRequestLogMapper requests = mock(AiRequestLogMapper.class);
        AiTraceEventMapper events = mock(AiTraceEventMapper.class);
        ObservabilityService service = new ObservabilityService(requests, events);
        ObservationScope.Trace trace = ObservationScope.begin("req-failed", 1, "conversation-a");
        try {
            trace.rag(4, 0, 3, false);
            service.recordSafely(trace, null, "configured-model", new IllegalStateException("secret detail"));
            var row = org.mockito.ArgumentCaptor.forClass(AiRequestLog.class);
            verify(requests).insert(row.capture());
            assertEquals("FAILED", row.getValue().getStatus());
            assertEquals("RAG_ERROR", row.getValue().getErrorType());
            assertNull(row.getValue().getTotalTokens());
            assertTrue(row.getValue().getRagUsed());
            assertTrue(row.getValue().getLatencyMs() >= 0);

            doThrow(new IllegalStateException("db down")).when(requests).insert(any(AiRequestLog.class));
            assertDoesNotThrow(() -> service.recordSafely(trace, null, null, new RuntimeException("model down")));
        }
        finally { ObservationScope.end(); }
    }

    @Test
    void vectorRetrievalRecordsOnlyTopKChunkCountAndDuration() {
        org.springframework.ai.vectorstore.VectorStore delegate = mock(org.springframework.ai.vectorstore.VectorStore.class);
        var request = org.springframework.ai.vectorstore.SearchRequest.builder().query("secret question")
                .topK(4).build();
        when(delegate.similaritySearch(request)).thenReturn(List.of(
                org.springframework.ai.document.Document.builder().text("private chunk").build()));
        ObservationScope.Trace trace = ObservationScope.begin("req-rag", 1, "conversation-a");
        try {
            assertEquals(1, new ObservedVectorStore(delegate).similaritySearch(request).size());
            AiTraceEvent rag = trace.events().stream()
                    .filter(event -> "RAG_SEARCH".equals(event.getEventType())).findFirst().orElseThrow();
            assertEquals(4, rag.getTopK());
            assertEquals(1, rag.getChunkCount());
            assertTrue(rag.getDurationMs() >= 0);
            assertFalse(trace.events().toString().contains("private chunk"));
        }
        finally { ObservationScope.end(); }
    }

    @Test
    void userCannotReadAnotherUsersTraceAndSummaryUsesCurrentUser() {
        AiRequestLogMapper requests = mock(AiRequestLogMapper.class);
        AiTraceEventMapper events = mock(AiTraceEventMapper.class);
        ObservabilityService service = new ObservabilityService(requests, events);
        when(requests.summary(eq(2L), any(LocalDateTime.class))).thenReturn(Map.of(
                "total_requests", 2, "successful_requests", 1, "failed_requests", 1,
                "total_tokens", 150, "average_latency_ms", 25,
                "tool_calls", 1, "rag_requests", 1));
        TestSecurity.as(2);
        assertEquals(2, service.summary("7d").totalRequests());
        assertEquals(150, service.summary("7d").totalTokens());
        verify(requests, times(2)).summary(eq(2L), any(LocalDateTime.class));
        assertThrows(ResourceNotFoundException.class, () -> service.trace("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        var query = org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(requests).selectOne(query.capture());
        query.getValue().getSqlSegment();
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(2L));
        verify(events, never()).selectList(any());
    }

    @Test
    void toolStatisticsAndRecentRequestsStayScopedToCurrentUser() {
        AiRequestLogMapper requests = mock(AiRequestLogMapper.class);
        AiTraceEventMapper events = mock(AiTraceEventMapper.class);
        ObservabilityService service = new ObservabilityService(requests, events);
        TestSecurity.as(2);
        when(events.toolStatistics(eq(2L), any(LocalDateTime.class))).thenReturn(List.of(Map.of(
                "tool_name", "getResume", "call_count", 2, "success_count", 2,
                "failure_count", 0, "avg_latency_ms", 7)));
        assertEquals("getResume", service.tools("30d").getFirst().toolName());
        assertEquals(2, service.tools("30d").getFirst().callCount());
        when(requests.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        assertTrue(service.recent(1, 20).items().isEmpty());
        var query = org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(requests).selectList(query.capture());
        query.getValue().getSqlSegment();
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(2L));
        assertTrue(service.recent(1, 20, "slowest").items().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.recent(1, 20, "invalid"));
    }
}
