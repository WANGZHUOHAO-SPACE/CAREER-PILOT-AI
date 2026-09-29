package com.careerpilot.web;

import java.time.LocalDateTime;
import java.util.List;

import com.careerpilot.service.ApplicationService;
import com.careerpilot.service.ApplicationSummary;
import com.careerpilot.service.JobQueryService;
import com.careerpilot.service.JobSummary;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReadOnlyControllerTest {

    @Test
    void exposesJobsAndFilteredApplicationsAsJson() throws Exception {
        JobQueryService jobs = mock(JobQueryService.class);
        ApplicationService applications = mock(ApplicationService.class);
        when(jobs.listJobs()).thenReturn(List.of(new JobSummary(7L, "示例公司", "Java 实习",
                "要求 Spring Boot", "杭州", "200/天", LocalDateTime.of(2026, 9, 26, 12, 0))));
        when(applications.searchApplications("APPLIED")).thenReturn(List.of(new ApplicationSummary(
                9L, 7L, "示例公司", "Java 实习", "APPLIED", LocalDateTime.of(2026, 9, 26, 13, 0), "已投递")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new JobController(jobs),
                new ApplicationController(applications)).setControllerAdvice(new ApiExceptionHandler()).build();

        mvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].jd").value("要求 Spring Boot"))
                .andExpect(jsonPath("$[0].createTime").exists());
        mvc.perform(get("/api/applications").param("status", "APPLIED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("APPLIED"))
                .andExpect(jsonPath("$[0].remark").value("已投递"));
        verify(applications).searchApplications("APPLIED");
    }
}
