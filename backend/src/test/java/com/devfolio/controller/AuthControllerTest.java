package com.devfolio.controller;

import com.devfolio.model.User;
import com.devfolio.repository.UserRepository;
import com.devfolio.service.AuthService;
import com.devfolio.service.JwtService;
import com.devfolio.service.RateLimitService;
import com.devfolio.service.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthService authService;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private AuthController authController;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setPassword("hashedPassword");
        testUser.setRole("USER");
    }

    @Test
    void login_shouldReturn401WhenUserNotFound() {
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(rateLimitService.isRateLimited("127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        ResponseEntity<?> response = authController.login(
                Map.of("email", "unknown@example.com", "password", "pass"),
                httpRequest);

        assertEquals(401, response.getStatusCode().value());
        assertTrue(response.getBody().toString().contains("Identifiants incorrects"));
    }

    @Test
    void login_shouldReturn429WhenRateLimited() {
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(rateLimitService.isRateLimited("127.0.0.1")).thenReturn(true);

        ResponseEntity<?> response = authController.login(
                Map.of("email", "test@example.com", "password", "pass"),
                httpRequest);

        assertEquals(429, response.getStatusCode().value());
    }

    @Test
    void login_shouldReturnTokenWhenCredentialsValid() {
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(rateLimitService.isRateLimited("127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password", "hashedPassword")).thenReturn(true);
        when(jwtService.generateToken(testUser)).thenReturn("jwt-token");

        ResponseEntity<?> response = authController.login(
                Map.of("email", "test@example.com", "password", "password"),
                httpRequest);

        assertEquals(200, response.getStatusCode().value());
        verify(rateLimitService).reset("127.0.0.1");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("jwt-token", body.get("token"));
    }

    @Test
    void login_shouldReturn401WhenPasswordMismatch() {
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(rateLimitService.isRateLimited("127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong", "hashedPassword")).thenReturn(false);

        ResponseEntity<?> response = authController.login(
                Map.of("email", "test@example.com", "password", "wrong"),
                httpRequest);

        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    void register_shouldReturn400WhenEmailAlreadyUsed() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        ResponseEntity<?> response = authController.register(
                Map.of("email", "test@example.com", "password", "pass"));

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().toString().contains("Email"));
    }

    @Test
    void register_shouldReturnTokenWhenNewUser() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(authService.register("new@example.com", "pass")).thenReturn(testUser);
        when(jwtService.generateToken(testUser)).thenReturn("jwt-token");

        ResponseEntity<?> response = authController.register(
                Map.of("email", "new@example.com", "password", "pass"));

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("jwt-token", body.get("token"));
    }

    @Test
    void register_shouldReturn400WhenEmailFormatInvalid() {
        when(userRepository.findByEmail("invalid-email")).thenReturn(Optional.empty());
        when(authService.register("invalid-email", "pass"))
                .thenThrow(new IllegalArgumentException("Format d'email invalide"));

        ResponseEntity<?> response = authController.register(
                Map.of("email", "invalid-email", "password", "pass"));

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().toString().contains("Format d'email invalide"));
    }

    @Test
    void logout_shouldReturnOkEvenWithoutToken() {
        when(httpRequest.getHeader("Authorization")).thenReturn(null);

        ResponseEntity<?> response = authController.logout(httpRequest);

        assertEquals(200, response.getStatusCode().value());
        verifyNoInteractions(tokenBlacklistService);
    }
}
