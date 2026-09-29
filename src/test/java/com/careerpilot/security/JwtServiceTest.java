package com.careerpilot.security;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private static final String SECRET = "test-only-jwt-secret-at-least-thirty-two-bytes";

    @Test void generatesAndParsesSignedExpiringToken() {
        JwtService service = new JwtService(SECRET);
        String token = service.generateToken(42);
        assertEquals(42, service.extractUserId(token));
        assertNotNull(service.validateToken(token).getIssuedAt());
        assertTrue(service.extractExpiration(token).isAfter(Instant.now()));
        assertThrows(JwtException.class, () -> new JwtService(SECRET + "wrong").validateToken(token));
    }

    @Test void rejectsExpiredTokenAndShortSecret() {
        JwtService service = new JwtService(SECRET);
        var encoder = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(
                SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"))
                .algorithm(MacAlgorithm.HS256).build();
        String expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject("42").issuedAt(Instant.now().minusSeconds(7200))
                        .expiresAt(Instant.now().minusSeconds(3600)).build())).getTokenValue();
        assertThrows(JwtException.class, () -> service.validateToken(expired));
        assertThrows(IllegalStateException.class, () -> new JwtService("too-short"));
    }

    @Test void missingAuthenticationFailsClosed() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        assertThrows(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,
                CurrentUser::id);
    }
}
