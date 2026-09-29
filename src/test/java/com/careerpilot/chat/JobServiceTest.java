package com.careerpilot.chat;

import com.careerpilot.service.UserProfileService;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobServiceTest {

    private final JobService jobService = createJobService();

    private static JobService createJobService() {
        UserProfileService profileService = mock(UserProfileService.class);
        when(profileService.getResume()).thenReturn(new ResumeProfile(1L, "林同学", "软件工程",
                "Java后端开发实习", List.of("Java", "Spring Boot", "MySQL", "Git", "AI辅助开发"), "求职简介"));
        return new JobService(profileService);
    }

    @Test
    void findsMatchedAndMissingSkillsInJd() {
        JobAnalysis analysis = jobService.analyzeJob("Java 后端实习，要求 Spring Boot、MySQL 和 Redis。");

        assertEquals(List.of("Java", "Spring Boot", "MySQL", "Redis"), analysis.jdSkills());
        assertEquals(List.of("Java", "Spring Boot", "MySQL"), analysis.matchedSkills());
        assertEquals(List.of("Redis"), analysis.missingSkills());
    }

    @Test
    void doesNotReadJavaFromJavaScript() {
        JobAnalysis analysis = jobService.analyzeJob("前端岗位，需要 JavaScript、Spring Cloud 和 Docker。");

        assertEquals(List.of("Docker"), analysis.jdSkills());
        assertEquals(List.of("Docker"), analysis.missingSkills());
    }

    @Test
    void greetingUsesResumeAndRelevantJdSkillsWithoutClaimingMissingSkills() {
        String greeting = jobService.generateGreeting("Java 后端岗位，要求 Spring Boot 和 Redis。");

        assertTrue(greeting.contains("林同学"));
        assertTrue(greeting.contains("Java后端开发实习"));
        assertTrue(greeting.contains("Spring Boot"));
        assertFalse(greeting.contains("Redis"));
    }

    @Test
    void asksForJdWhenItIsMissing() {
        assertTrue(jobService.analyzeJob(" ").note().contains("请先提供岗位JD"));
        assertTrue(jobService.generateGreeting(" ").contains("请先提供岗位JD"));
    }
}
