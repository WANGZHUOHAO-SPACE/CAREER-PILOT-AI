package com.careerpilot.service;

import java.util.List;
import com.careerpilot.TestSecurity;
import org.junit.jupiter.api.BeforeEach;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.careerpilot.entity.Application;
import com.careerpilot.entity.Job;
import com.careerpilot.mapper.ApplicationMapper;
import com.careerpilot.mapper.JobMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.careerpilot.common.ResourceNotFoundException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationServiceTest {
    @BeforeEach void authenticate() { TestSecurity.as(1); }

    @Test
    void searchesApplicationsWithJobDetails() {
        ApplicationMapper applicationMapper = mock(ApplicationMapper.class);
        JobMapper jobMapper = mock(JobMapper.class);
        Application application = application(20L, 10L, "SAVED");
        Job job = job(10L);
        when(applicationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(application));
        when(jobMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(job));

        List<ApplicationSummary> results = new ApplicationService(applicationMapper, jobMapper)
                .searchApplications("SAVED");

        assertEquals(1, results.size());
        assertEquals("某公司", results.getFirst().company());
        assertEquals("Java实习生", results.getFirst().position());
        assertEquals("SAVED", results.getFirst().status());
        org.mockito.ArgumentCaptor<LambdaQueryWrapper<Application>> query = org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(applicationMapper).selectList(query.capture());
        org.junit.jupiter.api.Assertions.assertTrue(query.getValue().getSqlSegment().endsWith("LIMIT 200"));
        // One batched job lookup, never one lookup for each row.
        verify(jobMapper).selectList(any(QueryWrapper.class));
    }

    @Test
    void setsFirstApplyTimeWhenStatusBecomesApplied() {
        ApplicationMapper applicationMapper = mock(ApplicationMapper.class);
        JobMapper jobMapper = mock(JobMapper.class);
        Application application = application(20L, 10L, "SAVED");
        when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(application);
        when(applicationMapper.update(any(Application.class), any())).thenReturn(1);
        when(jobMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(job(10L));

        ApplicationSummary result = new ApplicationService(applicationMapper, jobMapper)
                .updateApplicationStatus(20L, "applied", "已投递");

        verify(applicationMapper).update(any(Application.class), any());
        assertEquals("APPLIED", result.status());
        assertEquals("已投递", result.remark());
        assertNotNull(result.applyTime());
    }

    @Test
    void rejectsUnknownStatus() {
        ApplicationService service = new ApplicationService(mock(ApplicationMapper.class), mock(JobMapper.class));
        assertThrows(IllegalArgumentException.class, () -> service.updateApplicationStatus(20L, "UNKNOWN", null));
        assertThrows(IllegalArgumentException.class, () -> service.searchApplications("UNKNOWN"));
    }

    @Test
    void anotherUsersApplicationIdCannotBeUpdated() {
        ApplicationMapper mapper = mock(ApplicationMapper.class);
        JobMapper jobs = mock(JobMapper.class);
        ApplicationService service = new ApplicationService(mapper, jobs);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        TestSecurity.as(2);
        assertThrows(ResourceNotFoundException.class,
                () -> service.updateApplicationStatus(20L, "APPLIED", "attempt"));
        org.mockito.ArgumentCaptor<LambdaQueryWrapper<Application>> query =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectOne(query.capture());
        query.getValue().getSqlSegment();
        org.junit.jupiter.api.Assertions.assertTrue(query.getValue().getParamNameValuePairs().containsValue(2L),
                query.getValue().getParamNameValuePairs().toString());
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).update(any(), any());
    }

    private static Application application(Long id, Long jobId, String status) {
        Application application = new Application();
        application.setId(id);
        application.setJobId(jobId);
        application.setStatus(status);
        return application;
    }

    private static Job job(Long id) {
        Job job = new Job();
        job.setId(id);
        job.setCompany("某公司");
        job.setPosition("Java实习生");
        return job;
    }
}
