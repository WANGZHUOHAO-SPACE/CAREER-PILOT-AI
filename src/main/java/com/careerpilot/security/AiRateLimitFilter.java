package com.careerpilot.security;

import java.io.IOException;
import java.util.Set;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.careerpilot.web.ApiExceptionHandler;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/** Registered only in the security chain, after JWT authentication. */
final class AiRateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> PATHS = Set.of("/api/chat", "/api/chat/stream", "/api/knowledge/upload");
    private final PerUserRateLimiter limiter;
    AiRateLimitFilter(PerUserRateLimiter limiter) { this.limiter = limiter; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if ("POST".equals(request.getMethod()) && PATHS.contains(request.getServletPath())
                && authentication instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated()) {
            var decision = limiter.acquire(Long.parseLong(jwt.getToken().getSubject()),
                    "/api/knowledge/upload".equals(request.getServletPath()));
            if (!decision.allowed()) {
                response.setHeader("Retry-After", Integer.toString(decision.retryAfterSeconds()));
                ApiExceptionHandler.writeError(response, 429, "Too many requests. Please try again later.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
