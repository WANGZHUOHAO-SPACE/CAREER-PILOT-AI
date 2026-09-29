package com.careerpilot.service;

import java.time.LocalDateTime;

public record ApplicationSummary(Long id, Long jobId, String company, String position,
        String status, LocalDateTime applyTime, String remark) {
}
