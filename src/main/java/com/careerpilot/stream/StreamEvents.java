package com.careerpilot.stream;

import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.Message;

/** Observable execution events only; never model reasoning or tool arguments/results. */
public final class StreamEvents {
    private static final ThreadLocal<Sink> ACTIVE = new ThreadLocal<>();
    private StreamEvents() { }
    public static Sink current() { return ACTIVE.get(); }
    public static void set(Sink sink) { if (sink == null) ACTIVE.remove(); else ACTIVE.set(sink); }
    public static void emit(String event, Map<String, Object> data) {
        Sink sink = current();
        if (sink != null) sink.emit(event, data);
    }
    public interface Sink {
        void emit(String event, Map<String, Object> data);
        void deferAssistant(List<Message> messages);
    }
}
