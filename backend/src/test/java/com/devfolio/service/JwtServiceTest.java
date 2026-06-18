package com.devfolio.service;

import com.devfolio.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private final String secret = Base64.getEncoder().encodeToString(new byte[48]);

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", secret);
        ReflectionTestUtils.setField(jwtService, "expirationMs", 3600000L);
    }

    private User createUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setRole("USER");
        return user;
    }

    @Test
    void generateToken_shouldContainSubjectAndClaims() {
        User user = createUser();
        String token = jwtService.generateToken(user);

        assertNotNull(token);
        Claims claims = jwtService.validateToken(token);
        assertEquals("test@example.com", claims.getSubject());
        assertEquals("USER", claims.get("role"));
        assertEquals(1, claims.get("userId"));
    }

    @Test
    void validateToken_shouldRejectTamperedToken() {
        String token = jwtService.generateToken(createUser());
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThrows(Exception.class, () -> jwtService.validateToken(tampered));
    }

    @Test
    void validateToken_shouldRejectUnsignedToken() {
        String unsignedToken = Jwts.builder()
                .setSubject("test@example.com")
                .claim("role", "ADMIN")
                .compact();

        assertThrows(Exception.class, () -> jwtService.validateToken(unsignedToken));
    }

    @Test
    void validateToken_shouldRejectTokenWithDifferentSecret() {
        String token = jwtService.generateToken(createUser());
        String otherSecret = Base64.getEncoder().encodeToString(new byte[48]);

        assertThrows(Exception.class, () -> {
            Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(otherSecret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseClaimsJws(token);
        });
    }
}
