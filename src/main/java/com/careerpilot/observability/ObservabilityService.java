package com.careerpilot.observability;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careerpilot.common.ResourceNotFoundException;
import com.careerpilot.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.stereotype.Service;

@Service
public class ObservabilityService {
    private static final Logger log = LoggerFactory.getLogger(ObservabilityService.class);
    private final AiRequestLogMapper requests;
    private final AiTraceEventMapper events;

    public ObservabilityService(AiRequestLogMapper requests, AiTraceEventMapper events) {
        this.requests = requests;
        this.events = events;
    }

    /** Observation writes are best effort and must never replace an AI answer or its original failure. */
    public void recordSafely(ObservationScope.Trace trace, ChatResponse response,
            String configuredModel, Throwable failure) {
        try {
            AiRequestLog row = toRow(trace, response, configuredModel, failure);
            requests.insert(row);
            for (AiTraceEvent event : trace.events()) events.insert(event);
        }
        catch (RuntimeException exception) {
            log.warn("Observation persistence failed: requestId={} userId={} type={}",
                    trace.requestId(), trace.userId(), exception.getClass().getSimpleName());
        }
    }

    static AiRequestLog toRow(ObservationScope.Trace trace, ChatResponse response,
            String configuredModel, Throwable failure) {
        AiRequestLog row = new AiRequestLog();
        row.setRequestId(trace.requestId());
        row.setUserId(trace.userId());
        row.setConversationId(trace.conversationId());
        ChatResponseMetadata metadata = response == null ? null : response.getMetadata();
        String returnedModel = metadata == null ? null : metadata.getModel();
        row.setModelName(returnedModel == null || returnedModel.isBlank() ? configuredModel : returnedModel);
        row.setProvider(metadata != null && ("LOCAL_TEST".equals(metadata.get("provider"))
                || (returnedModel != null && returnedModel.startsWith("local-test-")))
                ? "LOCAL_TEST" : "OPENAI");
        row.setPromptVersion("career-agent-v1");
        row.setStatus(failure == null ? "SUCCESS" : "FAILED");
        if (trace.streamStatus() != null) row.setStatus(switch (trace.streamStatus()) {
            case "COMPLETED" -> "SUCCESS"; case "CANCELLED" -> "CANCELLED"; default -> "FAILED";
        });
        row.setTimeToFirstTokenMs(trace.timeToFirstTokenMs());
        row.setStreamDurationMs(trace.streamDurationMs());
        row.setOutputChunkCount(trace.outputChunkCount());
        row.setCancelled("CANCELLED".equals(trace.streamStatus()));
        Usage usage = metadata == null ? null : metadata.getUsage();
        boolean usageReported = usage != null && !(usage.getNativeUsage() == null
                && Integer.valueOf(0).equals(usage.getPromptTokens()) && Integer.valueOf(0).equals(usage.getCompletionTokens()));
        if (usageReported && !"LOCAL_TEST".equals(row.getProvider())) {
            row.setInputTokens(usage.getPromptTokens());
            row.setOutputTokens(usage.getCompletionTokens());
            Integer total = usage.getTotalTokens();
            if (total == null && usage.getPromptTokens() != null && usage.getCompletionTokens() != null)
                total = usage.getPromptTokens() + usage.getCompletionTokens();
            row.setTotalTokens(total);
        }
        row.setLatencyMs(trace.streamDurationMs() == null ? trace.latencyMs() : trace.streamDurationMs());
        row.setToolCallCount(trace.toolCalls());
        row.setRagUsed(trace.ragUsed());
        row.setErrorType(classify(failure, trace.ragFailed()));
        row.setCreatedAt(trace.createdAt());
        return row;
    }

    private static String classify(Throwable failure, boolean ragFailed) {
        if (failure == null) return null;
        if (ragFailed) return "RAG_ERROR";
        if (failure instanceof ToolExecutionException) return "TOOL_ERROR";
        if (failure instanceof IllegalArgumentException) return "VALIDATION_ERROR";
        String type = failure.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        if (type.contains("timeout")) return "MODEL_TIMEOUT";
        if (type.contains("api") || type.contains("model") || type.contains("resourceaccess"))
            return "MODEL_API_ERROR";
        return "UNKNOWN";
    }

    public UsageSummary summary(String range) {
        Map<String, Object> row = requests.summary(CurrentUser.id(), since(range));
        return new UsageSummary(number(row, "total_requests"), number(row, "successful_requests"),
                number(row, "failed_requests"), nullableNumber(row, "total_tokens"),
                number(row, "average_latency_ms"), number(row, "tool_calls"),
                number(row, "rag_requests"));
    }

    public List<ToolStatistics> tools(String range) {
        return events.toolStatistics(CurrentUser.id(), since(range)).stream()
                .map(row -> new ToolStatistics(String.valueOf(row.get("tool_name")),
                        number(row, "call_count"), number(row, "success_count"),
                        number(row, "failure_count"), number(row, "avg_latency_ms")))
                .toList();
    }

    public RecentRequests recent(int page, int size) {
        return recent(page, size, "recent");
    }

    public RecentRequests recent(int page, int size, String sort) {
        if (page < 1 || size < 1 || size > 50) throw new IllegalArgumentException("page须大于0，size须介于1和50");
        if (!"recent".equals(sort) && !"slowest".equals(sort))
            throw new IllegalArgumentException("sort仅支持recent或slowest");
        long offset = (long) (page - 1) * size;
        if (offset > Integer.MAX_VALUE) throw new IllegalArgumentException("page过大");
        LambdaQueryWrapper<AiRequestLog> query = new LambdaQueryWrapper<AiRequestLog>()
                .eq(AiRequestLog::getUserId, CurrentUser.id());
        if ("slowest".equals(sort)) query.orderByDesc(AiRequestLog::getLatencyMs);
        else query.orderByDesc(AiRequestLog::getCreatedAt);
        List<AiRequestLog> rows = requests.selectList(query.orderByDesc(AiRequestLog::getId)
                .last("LIMIT " + (size + 1) + " OFFSET " + offset));
        return new RecentRequests(page, size, rows.size() > size,
                rows.stream().limit(size).map(ObservabilityService::requestView).toList());
    }

    public TraceResponse trace(String requestId) {
        long userId = CurrentUser.id();
        AiRequestLog row = requests.selectOne(new LambdaQueryWrapper<AiRequestLog>()
                .eq(AiRequestLog::getRequestId, requestId)
                .eq(AiRequestLog::getUserId, userId));
        if (row == null) throw new ResourceNotFoundException("未找到该执行轨迹");
        List<TraceEventView> traceEvents = events.selectList(new LambdaQueryWrapper<AiTraceEvent>()
                .eq(AiTraceEvent::getRequestId, requestId)
                .eq(AiTraceEvent::getUserId, userId)
                .orderByAsc(AiTraceEvent::getId)).stream()
                .map(event -> new TraceEventView(event.getEventType(), event.getEventName(),
                        event.getStatus(), event.getDurationMs(), event.getTopK(),
                        event.getChunkCount(), event.getMemoryMessageCount(), event.getCreatedAt()))
                .toList();
        return new TraceResponse(requestView(row), row.getProvider(), row.getPromptVersion(),
                row.getErrorType(), new TokenUsage(row.getInputTokens(), row.getOutputTokens(),
                        row.getTotalTokens()), traceEvents);
    }

    private static RequestView requestView(AiRequestLog row) {
        return new RequestView(row.getRequestId(), row.getModelName(), row.getStatus(),
                row.getLatencyMs(), row.getTotalTokens(), row.getToolCallCount(),
                row.getRagUsed(), row.getCreatedAt(), row.getTimeToFirstTokenMs(), row.getStreamDurationMs(),
                row.getOutputChunkCount(), row.getCancelled());
    }

    private static LocalDateTime since(String range) {
        return switch (range == null ? "7d" : range) {
            case "today" -> LocalDate.now().atStartOfDay();
            case "7d" -> LocalDateTime.now().minusDays(7);
            case "30d" -> LocalDateTime.now().minusDays(30);
            default -> throw new IllegalArgumentException("range仅支持today、7d、30d");
        };
    }

    private static long number(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        return value instanceof Number number ? Math.round(number.doubleValue()) : 0;
    }

    private static Long nullableNumber(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        return value instanceof Number number ? number.longValue() : null;
    }

    public record UsageSummary(long totalRequests, long successfulRequests, long failedRequests,
            Long totalTokens, long averageLatencyMs, long toolCalls, long ragRequests) { }
    public record ToolStatistics(String toolName, long callCount, long successCount,
            long failureCount, long avgLatencyMs) { }
    public record RequestView(String requestId, String model, String status, Long latencyMs,
            Integer totalTokens, Integer toolCallCount, Boolean ragUsed, LocalDateTime createdAt,
            Long timeToFirstTokenMs, Long streamDurationMs, Integer outputChunkCount, Boolean cancelled) { }
    public record RecentRequests(int page, int size, boolean hasMore, List<RequestView> items) { }
    public record TokenUsage(Integer input, Integer output, Integer total) { }
    public record TraceEventView(String type, String name, String status, Long durationMs,
            Integer topK, Integer chunkCount, Integer memoryMessageCount, LocalDateTime createdAt) { }
    public record TraceResponse(RequestView request, String provider, String promptVersion,
            String errorType, TokenUsage tokenUsage, List<TraceEventView> events) { }
}
