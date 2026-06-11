package com.devfolio.service;

import com.devfolio.model.User;
import com.devfolio.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private UserRepository userRepository;

    public User register(String email, String password) {
        // 🔴 A04-04 : aucune validation de complexité du mot de passe
        // 🔴 A02-01 : MD5 sans sel
        String hashedPassword = md5Hash(password);

        // 🔴 A09-01 : log du mot de passe en clair
        log.debug("Registering user: " + email + " with password: " + password);

        User user = new User();
        user.setEmail(email);
        user.setPassword(hashedPassword);
        user.setRole("USER");
        return userRepository.save(user);
    }

    public String md5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // 🔴 : retourne le mot de passe en clair si MD5 échoue
            return input;
        }
    }
}
