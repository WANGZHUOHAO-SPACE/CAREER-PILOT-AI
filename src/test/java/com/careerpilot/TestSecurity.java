package com.careerpilot;

import java.time.Instant;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class TestSecurity {
    private TestSecurity() { }
    private static boolean mappingInitialized;

    public static void as(long userId) {
        initializeMappings();
        Jwt jwt = Jwt.withTokenValue("test-only")
                .header("alg", "HS256")
                .subject(Long.toString(userId))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));
    }

    private static synchronized void initializeMappings() {
        if (mappingInitialized) return;
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,
                com.careerpilot.entity.Job.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,
                com.careerpilot.entity.Application.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,
                com.careerpilot.entity.UserProfile.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,
                com.careerpilot.observability.AiRequestLog.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,
                com.careerpilot.observability.AiTraceEvent.class);
        mappingInitialized = true;
    }
}
