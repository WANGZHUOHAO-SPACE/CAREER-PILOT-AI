package com.careerpilot.mcp;

import java.time.Duration;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.careerpilot.chat.JobService;
import com.careerpilot.chat.JobAnalysis;
import com.careerpilot.chat.ResumeProfile;
import com.careerpilot.rag.KnowledgeService;
import com.careerpilot.service.ApplicationService;
import com.careerpilot.service.ApplicationSummary;
import com.careerpilot.service.UserProfileService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = CareerPilotMcpProtocolTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.ai.model.chat=none", "spring.ai.model.embedding=none",
                "OPENAI_API_KEY=test-only", "DB_PASSWORD=test-only",
                "spring.ai.mcp.server.enabled=true", "app.mcp.private-tools-enabled=true"})
@ContextConfiguration(initializers = CareerPilotMcpProtocolTest.WindowsTempInitializer.class)
class CareerPilotMcpProtocolTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private UserProfileService profileService;

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private JobService jobService;

    @Autowired
    private ApplicationService applicationService;

    @Test
    void streamableHttpListsOnlyReadOnlyToolsAndInvokesServices() {
        when(profileService.getResume()).thenReturn(new ResumeProfile(1L, "林同学", "软件工程",
                "Java后端实习", List.of("Java", "MySQL"), "求职简介"));
        when(knowledgeService.search("项目使用什么数据库"))
                .thenReturn(List.of(new KnowledgeService.KnowledgeHit("projects.md", "md",
                        "CareerPilot AI 使用 MySQL", 0.95)));
        when(jobService.analyzeJob("Java 岗位要求 Spring Boot 和 MySQL"))
                .thenReturn(new JobAnalysis("Java后端实习", List.of("Spring Boot", "MySQL"),
                        List.of("Spring Boot", "MySQL"), List.of(), "技能匹配"));
        when(applicationService.searchApplications("SAVED"))
                .thenReturn(List.of(new ApplicationSummary(1L, 2L, "示例公司", "Java实习", "SAVED", null, null)));

        var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                .endpoint("/mcp").build();
        try (McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10)).build()) {
            client.initialize();
            Set<String> names = client.listTools().tools().stream()
                    .map(McpSchema.Tool::name).collect(Collectors.toSet());
            assertEquals(Set.of("getResume", "analyzeJob", "searchApplications", "searchKnowledge"), names);
            assertFalse(names.contains("saveJob"));
            assertFalse(names.contains("updateApplicationStatus"));
            assertTrue(client.listTools().tools().stream()
                    .allMatch(tool -> Boolean.TRUE.equals(tool.annotations().readOnlyHint())
                            && Boolean.FALSE.equals(tool.annotations().destructiveHint())));

            McpSchema.CallToolResult resume = client.callTool(
                    new McpSchema.CallToolRequest("getResume", Map.of()));
            assertFalse(Boolean.TRUE.equals(resume.isError()));
            assertTrue(resume.content().toString().contains("林同学"));

            McpSchema.CallToolResult knowledge = client.callTool(
                    new McpSchema.CallToolRequest("searchKnowledge", Map.of("query", "项目使用什么数据库")));
            assertFalse(Boolean.TRUE.equals(knowledge.isError()));
            assertTrue(knowledge.content().toString().contains("MySQL"));

            McpSchema.CallToolResult analysis = client.callTool(new McpSchema.CallToolRequest(
                    "analyzeJob", Map.of("jd", "Java 岗位要求 Spring Boot 和 MySQL")));
            assertFalse(Boolean.TRUE.equals(analysis.isError()));
            assertTrue(analysis.content().toString().contains("技能匹配"));

            McpSchema.CallToolResult applications = client.callTool(new McpSchema.CallToolRequest(
                    "searchApplications", Map.of("status", "SAVED")));
            assertFalse(Boolean.TRUE.equals(applications.isError()));
            assertTrue(applications.content().toString().contains("示例公司"));
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import(CareerPilotMcpTools.class)
    static class TestApplication {
        @Bean
        SecurityFilterChain testOnlySecurity(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
        }
        @Bean
        UserProfileService userProfileService() {
            return mock(UserProfileService.class);
        }

        @Bean
        JobService jobService() {
            return mock(JobService.class);
        }

        @Bean
        ApplicationService applicationService() {
            ApplicationService service = mock(ApplicationService.class);
            when(service.searchApplications(any())).thenReturn(List.of());
            return service;
        }

        @Bean
        KnowledgeService knowledgeService() {
            return mock(KnowledgeService.class);
        }
    }

    static class WindowsTempInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            if (System.getProperty("os.name").startsWith("Windows") && Runtime.version().feature() >= 26) {
                System.setProperty("jdk.net.unixdomain.tmpdir", Path.of("target").toAbsolutePath().toString());
            }
        }
    }
}
