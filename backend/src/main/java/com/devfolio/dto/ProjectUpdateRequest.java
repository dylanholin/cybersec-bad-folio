package com.devfolio.dto;

public class ProjectUpdateRequest {

    private String title;
    private String description;
    private String githubUrl;
    private String imageUrl;
    private Boolean isPublic;

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public void setGithubUrl(final String githubUrl) {
        this.githubUrl = githubUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(final String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(final Boolean isPublic) {
        this.isPublic = isPublic;
    }
}
