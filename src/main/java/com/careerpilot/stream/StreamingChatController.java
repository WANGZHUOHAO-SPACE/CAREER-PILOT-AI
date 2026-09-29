package com.careerpilot.stream;

import com.careerpilot.chat.ChatRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/chat")
public class StreamingChatController {
    private final StreamingChatService service;
    public StreamingChatController(StreamingChatService service) { this.service = service; }
    @PostMapping(value="/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache"); response.setHeader("X-Accel-Buffering", "no");
        return service.start(request);
    }
    @GetMapping("/runs/{runId}") public RunRepository.RunView get(@PathVariable String runId) { return service.get(runId); }
    @PostMapping("/runs/{runId}/cancel") public RunRepository.RunView cancel(@PathVariable String runId) { return service.cancel(runId); }
}
