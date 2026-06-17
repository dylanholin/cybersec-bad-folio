package com.devfolio.controller;

import com.devfolio.dto.ProjectUpdateRequest;
import com.devfolio.model.Project;
import com.devfolio.repository.ProjectRepository;
import com.devfolio.util.UrlValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
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
    public ResponseEntity<Project> getProject(@PathVariable Long id) {
        return projectRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Project> createProject(@RequestBody Project project,
                                                  Authentication authentication) {
        Long currentUserId = (Long) authentication.getDetails();
        project.setOwnerId(currentUserId);
        return ResponseEntity.ok(projectRepository.save(project));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProject(@PathVariable Long id,
                                            @RequestBody ProjectUpdateRequest updated,
                                            Authentication authentication) {
        Long currentUserId = (Long) authentication.getDetails();
        return projectRepository.findById(id).map(project -> {
            if (!project.getOwnerId().equals(currentUserId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Accès refusé"));
            }
            project.setTitle(updated.getTitle());
            project.setDescription(updated.getDescription());
            project.setGithubUrl(updated.getGithubUrl());
            project.setImageUrl(updated.getImageUrl());
            return ResponseEntity.ok(projectRepository.save(project));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProject(@PathVariable Long id,
                                            Authentication authentication) {
        Long currentUserId = (Long) authentication.getDetails();
        return projectRepository.findById(id).map(project -> {
            if (!project.getOwnerId().equals(currentUserId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Accès refusé"));
            }
            projectRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/import")
    public ResponseEntity<?> importFromGithub(@RequestParam String githubUrl) {
        try {
            URL url = UrlValidator.validate(githubUrl);
            try (InputStream in = url.openStream()) {
                String content = new String(in.readNBytes((int) UrlValidator.getMaxFetchSize()));
                return ResponseEntity.ok(Map.of("content", content));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Impossible de récupérer le contenu"));
        }
    }
}
