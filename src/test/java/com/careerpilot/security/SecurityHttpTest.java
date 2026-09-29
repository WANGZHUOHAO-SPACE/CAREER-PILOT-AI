package com.careerpilot.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;
import com.careerpilot.chat.ChatController;
import com.careerpilot.chat.ChatService;
import com.careerpilot.service.JobQueryService;
import com.careerpilot.service.UserService;
import com.careerpilot.entity.User;
import com.careerpilot.web.AuthController;
import com.careerpilot.web.JobController;
import com.careerpilot.observability.ObservabilityController;
import com.careerpilot.observability.ObservabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = SecurityHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.ai.model.chat=none", "spring.ai.model.embedding=none",
                "spring.ai.mcp.server.enabled=false", "OPENAI_API_KEY=test-only", "DB_PASSWORD=test-only"})
@ContextConfiguration(initializers = SecurityHttpTest.WindowsTempInitializer.class)
class SecurityHttpTest {
    @Value("${local.server.port}") int port;
    @Autowired UserService users;
    @Autowired JwtService tokens;
    @Autowired ObservabilityService observability;

    @Test void publicLoginAndProtectedBusinessEndpoints() throws Exception {
        User user = new User();
        user.setId(8L); user.setUsername("demo"); user.setEmail("demo@example.com");
        user.setDisplayName("Demo");
        when(users.login("demo@example.com", "Example123!")).thenReturn(user);
        when(users.get(8L)).thenReturn(UserService.view(user));
        when(users.register("demo", "demo@example.com", "Example123!"))
                .thenReturn(UserService.view(user));

        assertEquals(200, request("POST", "/api/auth/register",
                "{\"username\":\"demo\",\"email\":\"demo@example.com\",\"password\":\"Example123!\"}",
                null).statusCode());

        assertEquals(401, request("GET", "/api/jobs", null, null).statusCode());
        assertEquals(401, request("POST", "/api/chat", "{}", null).statusCode());
        assertEquals(401, request("GET", "/api/knowledge/status", null, null).statusCode());
        assertEquals(401, request("GET", "/api/auth/me", null, null).statusCode());
        assertEquals(401, request("GET", "/api/observability/summary", null, null).statusCode());
        assertEquals(401, request("GET", "/api/auth/me", null, "Bearer invalid.jwt.value").statusCode());
        var key = new javax.crypto.spec.SecretKeySpec(
                "test-only-jwt-secret-at-least-thirty-two-bytes"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
        String expired = org.springframework.security.oauth2.jwt.NimbusJwtEncoder.withSecretKey(key)
                .algorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build()
                .encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                        org.springframework.security.oauth2.jwt.JwsHeader
                                .with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(),
                        org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().subject("8")
                                .issuedAt(Instant.now().minusSeconds(7200))
                                .expiresAt(Instant.now().minusSeconds(3600)).build())).getTokenValue();
        assertEquals(401, request("GET", "/api/auth/me", null, "Bearer " + expired).statusCode());

        var login = request("POST", "/api/auth/login",
                "{\"email\":\"demo@example.com\",\"password\":\"Example123!\"}", null);
        assertEquals(200, login.statusCode());
        String token = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"")
                .matcher(login.body()).results().findFirst().orElseThrow().group(1);
        assertEquals(8, tokens.extractUserId(token));
        var me = request("GET", "/api/auth/me", null, "Bearer " + token);
        assertEquals(200, me.statusCode());
        assertTrue(me.body().contains("demo@example.com"));
        assertFalse(me.body().contains("passwordHash"));
        assertEquals(200, request("GET", "/api/jobs", null, "Bearer " + token).statusCode());
        when(observability.summary("7d")).thenReturn(new ObservabilityService.UsageSummary(1, 1, 0, 150L, 20, 1, 1));
        var summary = request("GET", "/api/observability/summary", null, "Bearer " + token);
        assertEquals(200, summary.statusCode());
        assertTrue(summary.body().contains("\"totalTokens\":150"));
        assertEquals(404, request("GET", "/mcp", null, "Bearer " + token).statusCode());
    }

    @Test void rateLimitsShareChatBudgetAndErrorsCarryRequestId() throws Exception {
        String token = tokens.generateToken(99L);
        var unauthenticated = request("GET", "/api/jobs", null, null);
        assertEquals(401, unauthenticated.statusCode());
        assertTrue(unauthenticated.body().contains(unauthenticated.headers().firstValue("X-Request-Id").orElseThrow()));
        assertEquals("nosniff", unauthenticated.headers().firstValue("X-Content-Type-Options").orElseThrow());
        assertEquals("DENY", unauthenticated.headers().firstValue("X-Frame-Options").orElseThrow());
        assertEquals("same-origin", unauthenticated.headers().firstValue("Referrer-Policy").orElseThrow());
        for (int i = 0; i < 20; i++) {
            assertEquals(i % 2 == 0 ? 400 : 404, request("POST", i % 2 == 0 ? "/api/chat" : "/api/chat/stream", "{}", "Bearer " + token).statusCode());
        }
        var denied = request("POST", "/api/chat/stream", "{}", "Bearer " + token);
        assertEquals(429, denied.statusCode());
        assertTrue(denied.headers().firstValue("Retry-After").isPresent());
        assertTrue(denied.body().contains(denied.headers().firstValue("X-Request-Id").orElseThrow()));
        assertEquals(400, request("POST", "/api/chat", "{}", "Bearer " + tokens.generateToken(100L)).statusCode());
    }

    private HttpResponse<String> request(String method, String path, String body, String authorization)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));
        if (authorization != null) builder.header("Authorization", authorization);
        if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
        else builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({SecurityConfig.class, JwtService.class, AuthController.class, ChatController.class,
            JobController.class, ObservabilityController.class, PerUserRateLimiter.class,
            com.careerpilot.web.RequestIdFilter.class, com.careerpilot.web.ApiExceptionHandler.class})
    static class TestApplication {
        @Bean UserService users() { return mock(UserService.class); }
        @Bean ChatService chat() { return mock(ChatService.class); }
        @Bean JobQueryService jobs() { return mock(JobQueryService.class); }
        @Bean ObservabilityService observability() { return mock(ObservabilityService.class); }
    }

    static class WindowsTempInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override public void initialize(ConfigurableApplicationContext context) {
            if (System.getProperty("os.name").startsWith("Windows") && Runtime.version().feature() >= 26) {
                System.setProperty("jdk.net.unixdomain.tmpdir", Path.of("target").toAbsolutePath().toString());
            }
        }
    }
}
