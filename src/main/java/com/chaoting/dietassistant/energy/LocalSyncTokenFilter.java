package com.chaoting.dietassistant.energy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class LocalSyncTokenFilter extends OncePerRequestFilter {
    private final byte[] configuredToken;
    public LocalSyncTokenFilter(@Value("${diet-assistant.local-sync-token:}") String token) {
        configuredToken = token.getBytes(StandardCharsets.UTF_8);
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/health/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        byte[] supplied = header != null && header.startsWith("Bearer ")
                ? header.substring(7).getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (configuredToken.length == 0 || !MessageDigest.isEqual(configuredToken, supplied)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid or missing local sync token.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
