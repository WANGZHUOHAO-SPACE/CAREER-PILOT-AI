package com.careerpilot.web;

import java.util.List;

import com.careerpilot.service.ApplicationService;
import com.careerpilot.service.ApplicationSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public List<ApplicationSummary> list(@RequestParam(required = false) String status) {
        return applicationService.searchApplications(status);
    }
}
