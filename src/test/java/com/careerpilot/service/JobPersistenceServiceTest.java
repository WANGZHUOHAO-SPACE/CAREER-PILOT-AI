package com.careerpilot.service;

import com.careerpilot.entity.Application;
import com.careerpilot.TestSecurity;
import org.junit.jupiter.api.BeforeEach;
import com.careerpilot.entity.Job;
import com.careerpilot.mapper.ApplicationMapper;
import com.careerpilot.mapper.JobMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPersistenceServiceTest {
    @BeforeEach void authenticate() { TestSecurity.as(1); }

    @Test
    void savesJobAndCreatesSavedApplication() {
        JobMapper jobMapper = mock(JobMapper.class);
        ApplicationMapper applicationMapper = mock(ApplicationMapper.class);
        when(jobMapper.insert(any(Job.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Job.class).setId(10L);
            return 1;
        });
        when(applicationMapper.insert(any(Application.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Application.class).setId(20L);
            return 1;
        });

        JobSaveResult result = new JobPersistenceService(jobMapper, applicationMapper)
                .saveJob(" Java 后端岗位 ", "某公司", "实习生", null, null);

        ArgumentCaptor<Job> job = ArgumentCaptor.forClass(Job.class);
        ArgumentCaptor<Application> application = ArgumentCaptor.forClass(Application.class);
        verify(jobMapper).insert(job.capture());
        verify(applicationMapper).insert(application.capture());
        assertEquals("Java 后端岗位", job.getValue().getJd());
        assertEquals(1L, job.getValue().getUserId());
        assertEquals(1L, application.getValue().getUserId());
        assertEquals(10L, application.getValue().getJobId());
        assertEquals("SAVED", application.getValue().getStatus());
        assertEquals(new JobSaveResult(10L, 20L, "SAVED"), result);
    }

    @Test
    void rejectsEmptyJd() {
        JobPersistenceService service = new JobPersistenceService(mock(JobMapper.class), mock(ApplicationMapper.class));
        assertThrows(IllegalArgumentException.class, () -> service.saveJob(" ", null, null, null, null));
    }
}
