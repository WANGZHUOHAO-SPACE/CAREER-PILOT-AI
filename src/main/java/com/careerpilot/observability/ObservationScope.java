package com.careerpilot.observability;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Collects safe metadata for one synchronous ChatClient call; never holds prompt or tool arguments. */
public final class ObservationScope {
    private static final ThreadLocal<Trace> ACTIVE = new ThreadLocal<>();

    private ObservationScope() { }

    public static Trace begin(String requestId, long userId, String conversationId) {
        Trace trace = detached(requestId, userId, conversationId);
        ACTIVE.set(trace);
        return trace;
    }

    public static Trace detached(String requestId, long userId, String conversationId) {
        Trace trace = new Trace(requestId, userId, conversationId);
        trace.event("AI_REQUEST", "AI_REQUEST_START", "STARTED", 0, null, null, null);
        return trace;
    }

    public static void end() { ACTIVE.remove(); }

    public static Trace current() { return ACTIVE.get(); }
    public static void restore(Trace trace) { if (trace == null) ACTIVE.remove(); else ACTIVE.set(trace); }

    public static <T> T tool(String name, Supplier<T> operation) {
        Trace trace = current();
        if (trace == null) return operation.get();
        long started = System.nanoTime();
        com.careerpilot.stream.StreamEvents.emit("tool.started", java.util.Map.of("tool", name));
        try {
            T result = operation.get();
            trace.tool(name, "SUCCESS", elapsed(started));
            com.careerpilot.stream.StreamEvents.emit("tool.completed", java.util.Map.of("tool", name, "status", "SUCCESS", "durationMs", elapsed(started)));
            return result;
        }
        catch (RuntimeException | Error failure) {
            trace.tool(name, "FAILED", elapsed(started));
            com.careerpilot.stream.StreamEvents.emit("tool.completed", java.util.Map.of("tool", name, "status", "FAILED", "durationMs", elapsed(started)));
            throw failure;
        }
    }

    public static long elapsed(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    public static final class Trace {
        private final String requestId;
        private final long userId;
        private final String conversationId;
        private final LocalDateTime createdAt = LocalDateTime.now();
        private final long started = System.nanoTime();
        private final List<AiTraceEvent> events = new ArrayList<>();
        private int toolCalls;
        private boolean ragUsed;
        private boolean ragFailed;
        private Long timeToFirstTokenMs;
        private Long streamDurationMs;
        private Integer outputChunkCount;
        private String streamStatus;

        private Trace(String requestId, long userId, String conversationId) {
            this.requestId = requestId;
            this.userId = userId;
            this.conversationId = conversationId;
        }

        public void memory(int count) {
            event("MEMORY", "MEMORY_CONTEXT", "SUCCESS", 0, null, null, count);
        }

        public void rag(int topK, int chunks, long durationMs, boolean success) {
            ragUsed = true;
            if (!success) ragFailed = true;
            event("RAG_SEARCH", "RAG_SEARCH", success ? "SUCCESS" : "FAILED", durationMs,
                    topK, success ? chunks : null, null);
        }

        private void tool(String name, String status, long durationMs) {
            toolCalls++;
            event("TOOL_CALL", name, status, durationMs, null, null, null);
        }

        public synchronized void event(String type, String name, String status, long durationMs,
                Integer topK, Integer chunks, Integer memoryCount) {
            AiTraceEvent event = new AiTraceEvent();
            event.setRequestId(requestId);
            event.setUserId(userId);
            event.setEventType(type);
            event.setEventName(name);
            event.setStatus(status);
            event.setDurationMs(durationMs);
            event.setTopK(topK);
            event.setChunkCount(chunks);
            event.setMemoryMessageCount(memoryCount);
            event.setCreatedAt(LocalDateTime.now());
            events.add(event);
        }

        public String requestId() { return requestId; }
        public void streaming(Long ttft, long duration, int chunks, String status) {
            timeToFirstTokenMs = ttft; streamDurationMs = duration; outputChunkCount = chunks; streamStatus = status;
        }
        public Long timeToFirstTokenMs() { return timeToFirstTokenMs; }
        public Long streamDurationMs() { return streamDurationMs; }
        public Integer outputChunkCount() { return outputChunkCount; }
        public String streamStatus() { return streamStatus; }
        public long userId() { return userId; }
        public String conversationId() { return conversationId; }
        public LocalDateTime createdAt() { return createdAt; }
        public long latencyMs() { return elapsed(started); }
        public int toolCalls() { return toolCalls; }
        public boolean ragUsed() { return ragUsed; }
        public boolean ragFailed() { return ragFailed; }
        public synchronized List<AiTraceEvent> events() { return List.copyOf(events); }
    }
}
