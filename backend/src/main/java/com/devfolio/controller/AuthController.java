package com.devfolio.controller;

import com.devfolio.model.User;
import com.devfolio.repository.UserRepository;
import com.devfolio.service.AuthService;
import com.devfolio.service.JwtService;
import com.devfolio.service.RateLimitService;
import com.devfolio.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthService authService;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getRemoteAddr();

        // A04-01 : rate limiting (5 tentatives/minute par IP)
        if (rateLimitService.isRateLimited(clientIp)) {
            return ResponseEntity.status(429).body(Map.of("error", "Trop de tentatives, réessayez dans une minute"));
        }

        String username = request.get("email");
        String password = request.get("password");

        log.info("Login attempt for user: {}", username);

        Optional<User> userOpt = userRepository.findByEmail(username);

        if (userOpt.isEmpty() || !passwordEncoder.matches(password, userOpt.get().getPassword())) {
            log.warn("Failed login attempt for: {}", username);
            return ResponseEntity.status(401).body(Map.of("error", "Identifiants incorrects"));
        }

        // Login réussi : réinitialiser le compteur de rate limit pour cette IP
        rateLimitService.reset(clientIp);

        User user = userOpt.get();
        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(Map.of("token", token, "user", user));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email déjà utilisé"));
        }

        User user = authService.register(email, password);
        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(Map.of("token", token, "user", user));
    }

    @PostMapping("/reset-password/request")
    public ResponseEntity<?> requestReset(@RequestParam String email) {
        // Le token doit être envoyé par email, pas retourné dans la réponse
        log.info("Password reset requested for: {}", email);
        return ResponseEntity.ok(Map.of("message", "Si le compte existe, un email de réinitialisation a été envoyé"));
    }

    /**
     * A07-05 : Logout côté serveur — invalide le token JWT en l'ajoutant à la blacklist.
     * Le token reste blacklisté jusqu'à son expiration naturelle.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.validateToken(token);
                tokenBlacklistService.blacklist(token, claims);
                log.info("Logout réussi pour {}", claims.getSubject());
            } catch (Exception e) {
                // Token déjà invalide ou expiré — rien à blacklister
                log.debug("Logout avec token invalide : {}", e.getMessage());
            }
        }
        return ResponseEntity.ok(Map.of("message", "Déconnexion réussie"));
    }
}
