package com.devfolio.model;

import jakarta.persistence.*;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "github_url", length = 500)
    private String githubUrl;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "is_public")
    private Boolean isPublic = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }

    public Boolean getIsPublic() { return isPublic; }
    public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }
}
