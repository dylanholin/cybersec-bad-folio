package com.devfolio.controller;

import com.devfolio.model.Project;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    @PersistenceContext
    private EntityManager entityManager;

    @GetMapping("/projects")
    public ResponseEntity<?> searchProjects(@RequestParam String q) {
        // 🔴 A03-01 : INJECTION SQL — concaténation directe du paramètre utilisateur
        // Payload d'exemple : q=' OR '1'='1
        // Payload destructeur : q='; DROP TABLE projects; --
        String sql = "SELECT * FROM projects WHERE title LIKE '%" + q + "%' " +
                     "OR description LIKE '%" + q + "%'";

        @SuppressWarnings("unchecked")
        List<Project> results = entityManager.createNativeQuery(sql, Project.class).getResultList();
        return ResponseEntity.ok(results);
    }
}
