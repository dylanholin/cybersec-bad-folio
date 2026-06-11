package com.devfolio.service;

import com.devfolio.model.User;
import com.devfolio.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public User register(String email, String password) {
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("Mot de passe trop court (12 caractères minimum)");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("Le mot de passe doit contenir au moins une majuscule");
        }
        if (!password.matches(".*[0-9].*")) {
            throw new IllegalArgumentException("Le mot de passe doit contenir au moins un chiffre");
        }
        if (!password.matches(".*[^a-zA-Z0-9].*")) {
            throw new IllegalArgumentException("Le mot de passe doit contenir au moins un caractère spécial");
        }
        log.info("Registering user: {}", email);

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");
        return userRepository.save(user);
    }
}
