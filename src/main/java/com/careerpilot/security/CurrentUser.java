package com.careerpilot.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {
    private CurrentUser() { }

    public static long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException(
                    "Authentication required");
        }
        try {
            long id = Long.parseLong(jwt.getSubject());
            if (id <= 0) throw new NumberFormatException();
            return id;
        }
        catch (NumberFormatException exception) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid token subject");
        }
    }
}
