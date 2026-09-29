package com.careerpilot.service;

import java.time.LocalDateTime;

public record JobSummary(Long id, String company, String position, String jd,
        String location, String salary, LocalDateTime createTime) {
}
