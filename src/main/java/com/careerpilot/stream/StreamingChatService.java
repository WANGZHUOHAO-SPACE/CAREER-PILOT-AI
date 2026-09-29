package com.careerpilot.stream;

import com.careerpilot.chat.ChatRequest;
import com.careerpilot.chat.ChatService;
import com.careerpilot.conversation.ConversationOperations;
import com.careerpilot.conversation.ConversationService;
import com.careerpilot.observability.ObservationScope;
import com.careerpilot.observability.ObservabilityService;
import com.careerpilot.security.CurrentUser;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

@Service
public class StreamingChatService {
    private final ChatService chat;
    private final ConversationService conversations;
    private final RunRepository runs;
    private final ObservabilityService observations;
    private final ConcurrentMap<String, Handle> active = new ConcurrentHashMap<>();
    private final Semaphore capacity = new Semaphore(32);
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), r -> { Thread t = new Thread(r, "career-stream"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.AbortPolicy());
    @Value("${spring.ai.openai.chat.model:}") private String configuredModel;
    @Value("${app.streaming.recover-on-startup:true}") private boolean recoverOnStartup;

    public StreamingChatService(ChatService chat, ConversationService conversations, RunRepository runs, ObservabilityService observations) {
        this.chat = chat; this.conversations = conversations; this.runs = runs; this.observations = observations;
    }
    @EventListener(ApplicationReadyEvent.class) public void recover() { if (recoverOnStartup) runs.recoverInterrupted(); }
    @PreDestroy public void shutdown() {
        active.values().forEach(handle -> handle.end("CANCELLED", null));
        executor.shutdownNow();
    }
    public SseEmitter start(ChatRequest request) {
        long owner = CurrentUser.id();
        String requestId = MDC.get("requestId");
        if (requestId == null) requestId = UUID.randomUUID().toString();
        String conversation = request.conversationId().trim();
        if (!capacity.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "聊天任务繁忙，请稍后重试");
        ConversationOperations.Lease lease;
        try { lease = conversations.beginChat(conversation); }
        catch (RuntimeException failure) { capacity.release(); throw failure; }
        long received = MDC.get("requestStartNanos") == null ? System.nanoTime() : Long.parseLong(MDC.get("requestStartNanos"));
        Handle handle = new Handle(UUID.randomUUID().toString(), requestId, owner, conversation, lease, received);
        try {
            runs.begin(handle.id, requestId, owner, conversation, request.message());
            handle.trace = ObservationScope.detached(handle.requestId, owner, conversation);
            active.put(handle.id, handle);
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            executor.execute(() -> {
                var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
                MDC.put("requestId", handle.requestId); MDC.put("userId", Long.toString(owner));
                StreamEvents.set(handle);
                ObservationScope.restore(handle.trace);
                try {
                    handle.emit("run.started", Map.of("conversationId", conversation));
                    if (!handle.running()) return;
                    Disposable subscription = chat.stream(conversation, request.message())
                            .timeout(Duration.ofSeconds(150)).contextCapture()
                            .subscribe(handle::chunk, error -> handle.end("FAILED", error), () -> handle.end("COMPLETED", null));
                    handle.subscription.set(subscription);
                    if (!handle.running()) subscription.dispose();
                } catch (Throwable failure) { handle.end("FAILED", failure); }
                finally { StreamEvents.set(null); ObservationScope.end(); MDC.clear(); SecurityContextHolder.clearContext(); }
            });
        } catch (RejectedExecutionException failure) {
            handle.end("FAILED", failure);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "聊天任务繁忙，请稍后重试");
        } catch (RuntimeException failure) { active.remove(handle.id); lease.close(); capacity.release(); throw failure; }
        return handle.emitter;
    }
    public RunRepository.RunView get(String id) { return runs.owned(CurrentUser.id(), id); }
    public RunRepository.RunView cancel(String id) {
        var row = runs.owned(CurrentUser.id(), id);
        Handle handle = active.get(row.runId());
        if (handle != null) handle.end("CANCELLED", null);
        return runs.owned(CurrentUser.id(), row.runId());
    }

    final class Handle implements StreamEvents.Sink {
        final String id, requestId, conversation;
        final long owner;
        final long started;
        final SseEmitter emitter = new SseEmitter(180_000L);
        final ConversationOperations.Lease lease;
        final AtomicReference<Disposable> subscription = new AtomicReference<>();
        final StringBuilder text = new StringBuilder();
        volatile ObservationScope.Trace trace;
        ChatResponseMetadata metadata;
        String state = "RUNNING";
        Long ttft;
        int chunks;
        String finalAssistant;
        Handle(String id, String requestId, long owner, String conversation, ConversationOperations.Lease lease, long received) {
            this.started = received;
            this.id=id; this.requestId=requestId; this.owner=owner; this.conversation=conversation; this.lease=lease;
            emitter.onTimeout(() -> end("FAILED", new TimeoutException()));
            emitter.onError(error -> end("CANCELLED", error));
            emitter.onCompletion(() -> end("CANCELLED", null));
        }
        synchronized boolean running() { return "RUNNING".equals(state); }
        @Override public synchronized void emit(String event, Map<String,Object> data) {
            if (!running()) return;
            try { send(event, data); }
            catch (IOException | IllegalStateException failure) { end("CANCELLED", failure); }
        }
        private void send(String event, Map<String,Object> data) throws IOException {
            Map<String,Object> envelope = new HashMap<>(data);
            envelope.put("runId", id); envelope.put("requestId", requestId);
            emitter.send(SseEmitter.event().name(event).data(envelope));
        }
        @Override public synchronized void deferAssistant(List<Message> messages) {
            for (Message message : messages) if (message instanceof AssistantMessage assistant && !assistant.hasToolCalls())
                finalAssistant = assistant.getText();
        }
        synchronized void chunk(ChatResponse response) {
            if (!running()) return;
            metadata = response.getMetadata();
            if (response.getResult() == null || response.hasToolCalls()) return;
            String delta = response.getResult().getOutput().getText();
            if (delta == null || delta.isEmpty()) return;
            if ((long) text.length() + delta.length() > 1_000_000) { end("FAILED", new IllegalStateException("Output size limit")); return; }
            try {
                send("token", Map.of("delta", delta));
                if (ttft == null) ttft = ObservationScope.elapsed(started);
                chunks++;
                text.append(delta);
            } catch (IOException | IllegalStateException failure) { end("CANCELLED", failure); }
        }
        synchronized void end(String target, Throwable failure) {
            if (!running()) return;
            state = target;
            long duration = ObservationScope.elapsed(started);
            try {
                if ("COMPLETED".equals(target) && (text.isEmpty() || finalAssistant == null || !finalAssistant.equals(text.toString())))
                    throw new IllegalStateException("Incomplete stream finalization");
                runs.finish(id, owner, conversation, requestId, target, ttft, duration, chunks, text.toString());
            } catch (RuntimeException writeFailure) {
                state = "FAILED"; failure = writeFailure;
                try { runs.finish(id, owner, conversation, requestId, "FAILED", ttft, duration, chunks, ""); }
                catch (RuntimeException ignored) { /* Startup recovery marks an interrupted run failed when MySQL returns. */ }
                LoggerFactory.getLogger(getClass()).warn("event=RUN_WRITE_FAILED requestId={} userId={} type={}", requestId, owner, writeFailure.getClass().getSimpleName());
            }
            try {
                if (trace != null) {
                    trace.streaming(ttft, duration, chunks, state);
                    trace.event("LLM", "LLM_RESPONSE", state, duration, null, null, null);
                    trace.event("AI_REQUEST", "AI_REQUEST_" + state, state, duration, null, null, null);
                    ChatResponse response = metadata == null ? null : new ChatResponse(List.of(new Generation(new AssistantMessage(text.toString()))), metadata);
                    try { observations.recordSafely(trace, response, configuredModel, failure); }
                    catch (RuntimeException ignored) {
                        LoggerFactory.getLogger(getClass()).warn("event=OBSERVATION_WRITE_FAILED requestId={} userId={}", requestId, owner);
                    }
                }
                if ("COMPLETED".equals(state)) send("message.completed", Map.of("conversationId", conversation, "status", "COMPLETED"));
                send("FAILED".equals(state) ? "run.failed" : "run.completed", Map.of("status", state,
                        "message", "FAILED".equals(state) ? "AI service is temporarily unavailable." : state));
            } catch (IOException | IllegalStateException ignored) { /* Disconnected client; database state remains authoritative. */ }
            finally {
                Disposable disposable = subscription.get();
                if (disposable != null && !"COMPLETED".equals(state)) disposable.dispose();
                active.remove(id); lease.close(); capacity.release(); emitter.complete();
            }
        }
    }
}
