package com.finantrack.common.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Única fonte do usuário autenticado: o "sub" do JWT, nunca o corpo ou a URL. */
@Component
public class CurrentUser {

    public UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            return UUID.fromString(jwt.getSubject());
        }
        throw new IllegalStateException("Requisição sem usuário autenticado por JWT.");
    }
}
