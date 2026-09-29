package com.careerpilot.web;

import java.util.List;

import com.careerpilot.service.JobQueryService;
import com.careerpilot.service.JobSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobQueryService jobQueryService;

    public JobController(JobQueryService jobQueryService) {
        this.jobQueryService = jobQueryService;
    }

    @GetMapping
    public List<JobSummary> list() {
        return jobQueryService.listJobs();
    }
}
