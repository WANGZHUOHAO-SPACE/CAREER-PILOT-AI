package com.careerpilot.service;

import java.time.LocalDateTime;
import java.util.List;
import com.careerpilot.TestSecurity;
import org.junit.jupiter.api.BeforeEach;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careerpilot.entity.Job;
import com.careerpilot.mapper.JobMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobQueryServiceTest {
    @BeforeEach void authenticate() { TestSecurity.as(1); }

    @Test
    void returnsSavedJobsWithDetailFields() {
        JobMapper mapper = mock(JobMapper.class);
        Job job = new Job();
        job.setId(7L);
        job.setCompany("示例公司");
        job.setPosition("Java 实习");
        job.setJd("Spring Boot 和 MySQL");
        job.setLocation("杭州");
        job.setSalary("200/天");
        job.setCreateTime(LocalDateTime.of(2026, 9, 26, 12, 0));
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(job));

        List<JobSummary> result = new JobQueryService(mapper).listJobs();

        assertEquals(1, result.size());
        assertEquals("Spring Boot 和 MySQL", result.getFirst().jd());
        assertEquals("杭州", result.getFirst().location());
        org.mockito.ArgumentCaptor<LambdaQueryWrapper<Job>> query =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        org.mockito.Mockito.verify(mapper).selectList(query.capture());
        query.getValue().getSqlSegment();
        org.junit.jupiter.api.Assertions.assertTrue(query.getValue().getSqlSegment().endsWith("LIMIT 200"));
        org.junit.jupiter.api.Assertions.assertTrue(query.getValue().getParamNameValuePairs().containsValue(1L),
                query.getValue().getParamNameValuePairs().toString());
    }
}
