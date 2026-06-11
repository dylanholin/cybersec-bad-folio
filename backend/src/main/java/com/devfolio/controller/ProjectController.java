package com.devfolio.controller;

import com.devfolio.model.Project;
import com.devfolio.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URL;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired
    private ProjectRepository projectRepository;

    @GetMapping
    public ResponseEntity<List<Project>> getAllProjects() {
        return ResponseEntity.ok(projectRepository.findByIsPublicTrue());
    }

    @GetMapping("/{id}")
    // 🔴 A01-01 : IDOR — aucune vérification de propriété
    public ResponseEntity<Project> getProject(@PathVariable Long id) {
        return projectRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Project> createProject(@RequestBody Project project) {
        return ResponseEntity.ok(projectRepository.save(project));
    }

    @PutMapping("/{id}")
    // 🔴 A01-04 : n'importe qui peut modifier n'importe quel projet
    public ResponseEntity<Project> updateProject(
            @PathVariable Long id,
            @RequestBody Project updated,
            @RequestHeader(value = "Authorization", required = false) String token) {
        // Aucune vérification que le token correspond au propriétaire du projet
        return projectRepository.findById(id).map(project -> {
            project.setTitle(updated.getTitle());
            project.setDescription(updated.getDescription());
            project.setGithubUrl(updated.getGithubUrl());
            project.setImageUrl(updated.getImageUrl());
            return ResponseEntity.ok(projectRepository.save(project));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    // 🔴 A01-02 : suppression sans contrôle de propriété
    public ResponseEntity<?> deleteProject(@PathVariable Long id) {
        projectRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/import")
    // 🔴 A10-02 : SSRF via URL GitHub non validée
    public ResponseEntity<?> importFromGithub(@RequestParam String githubUrl) throws Exception {
        URL url = new URL(githubUrl); // aucune validation du domaine
        String content = new String(url.openStream().readAllBytes());
        return ResponseEntity.ok(Map.of("content", content));
    }
}
