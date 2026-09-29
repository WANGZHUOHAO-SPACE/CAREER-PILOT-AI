package com.careerpilot.observability;

import java.util.List;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {
    private final ObservabilityService service;

    public ObservabilityController(ObservabilityService service) { this.service = service; }

    @GetMapping("/summary")
    public ObservabilityService.UsageSummary summary(@RequestParam(defaultValue = "7d") String range) {
        return service.summary(range);
    }

    @GetMapping("/tools")
    public List<ObservabilityService.ToolStatistics> tools(@RequestParam(defaultValue = "7d") String range) {
        return service.tools(range);
    }

    @GetMapping("/requests")
    public ObservabilityService.RecentRequests requests(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "recent") String sort) {
        return service.recent(page, size, sort);
    }

    @GetMapping("/traces/{requestId}")
    public ObservabilityService.TraceResponse trace(@PathVariable String requestId) {
        if (!requestId.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("requestId格式错误");
        return service.trace(requestId);
    }
}
