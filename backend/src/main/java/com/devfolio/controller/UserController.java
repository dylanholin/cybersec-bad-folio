package com.devfolio.controller;

import com.devfolio.model.User;
import com.devfolio.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/admin/users")
    // 🔴 A01-03 : endpoint admin sans vérification de rôle ADMIN
    // Retourne les hashes MD5 des mots de passe
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/users/{id}")
    // 🔴 A01-04 : modification de n'importe quel profil sans contrôle d'identité
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User updated) {
        return userRepository.findById(id).map(user -> {
            user.setEmail(updated.getEmail());
            user.setRole(updated.getRole()); // 🔴 : l'appelant peut s'auto-promouvoir ADMIN
            user.setBio(updated.getBio());
            return ResponseEntity.ok(userRepository.save(user));
        }).orElse(ResponseEntity.notFound().build());
    }
}
