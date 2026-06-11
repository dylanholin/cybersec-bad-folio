package com.devfolio.service;

import com.devfolio.model.Project;
import com.devfolio.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProjectService {

    @Autowired
    private ProjectRepository projectRepository;

    public List<Project> getAllPublicProjects() {
        return projectRepository.findByIsPublicTrue();
    }

    public List<Project> getProjectsByOwner(Long ownerId) {
        return projectRepository.findByOwnerId(ownerId);
    }

    public Optional<Project> getProjectById(Long id) {
        return projectRepository.findById(id);
    }

    public Project createProject(Project project) {
        return projectRepository.save(project);
    }

    public void deleteProject(Long id) {
        projectRepository.deleteById(id);
    }
}
