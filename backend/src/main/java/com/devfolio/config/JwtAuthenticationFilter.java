package com.devfolio.config;

import com.devfolio.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        log.debug("[JWT Filter] {} {} — Auth header: {}", method, uri,
                request.getHeader("Authorization") != null ? "present" : "absent");

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.validateToken(header.substring(7));
                String role = claims.get("role", String.class);
                Long userId = claims.get("userId", Integer.class).longValue();

                var auth = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                auth.setDetails(userId);
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("[JWT Filter] Authenticated user: {} role: {}", claims.getSubject(), role);
            } catch (JwtException | IllegalArgumentException e) {
                log.warn("[JWT Filter] Invalid token for {} {}: {}", method, uri, e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
