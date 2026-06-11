package com.devfolio.controller;

import com.devfolio.model.User;
import com.devfolio.repository.UserRepository;
import com.devfolio.service.AuthService;
import com.devfolio.service.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/login")
    // 🔴 A04-01 : aucun rate limiting — brute force possible
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String username = request.get("email");
        String password = request.get("password");

        // 🔴 A03-04 : injection dans les logs (log injection)
        log.info("Login attempt for user: " + username);

        // 🔴 A09-01 : mot de passe loggé en clair
        log.debug("Password received: " + password);

        Optional<User> userOpt = userRepository.findByEmail(username);

        // 🔴 A04-02 : messages distincts → user enumeration
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Utilisateur inconnu"));
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            // 🔴 A09-02 : echec non loggé
            return ResponseEntity.status(401).body(Map.of("error", "Mot de passe incorrect"));
        }

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
        String token = UUID.randomUUID().toString();
        // 🔴 A04-03 : token dans l'URL (logs serveur, historique navigateur)
        String resetUrl = "http://localhost:5173/reset-password?token=" + token + "&email=" + email;
        return ResponseEntity.ok(Map.of("resetUrl", resetUrl));
    }
}
