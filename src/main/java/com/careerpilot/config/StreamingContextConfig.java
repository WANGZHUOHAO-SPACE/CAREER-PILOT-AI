package com.careerpilot.config;

import com.careerpilot.observability.ObservationScope;
import com.careerpilot.stream.StreamEvents;
import io.micrometer.context.ContextRegistry;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import reactor.core.publisher.Hooks;

/** Propagates the captured JWT owner and safe trace context across Reactor advisor/model schedulers. */
@Configuration
public class StreamingContextConfig {
    public StreamingContextConfig() {
        var registry = ContextRegistry.getInstance();
        registry.registerThreadLocalAccessor("career.security", SecurityContextHolder::getContext,
                SecurityContextHolder::setContext, SecurityContextHolder::clearContext);
        registry.registerThreadLocalAccessor("career.trace", ObservationScope::current,
                ObservationScope::restore, ObservationScope::end);
        registry.registerThreadLocalAccessor("career.stream", StreamEvents::current, StreamEvents::set,
                () -> StreamEvents.set(null));
        registry.registerThreadLocalAccessor("career.requestId", () -> MDC.get("requestId"),
                value -> MDC.put("requestId", value), () -> MDC.remove("requestId"));
        registry.registerThreadLocalAccessor("career.userId", () -> MDC.get("userId"),
                value -> MDC.put("userId", value), () -> MDC.remove("userId"));
        Hooks.enableAutomaticContextPropagation();
    }
}
