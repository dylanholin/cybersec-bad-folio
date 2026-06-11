CREATE DATABASE IF NOT EXISTS devfolio;
USE devfolio;

CREATE USER IF NOT EXISTS 'devfolio_app'@'%' IDENTIFIED BY 'DevfolioApp2024!';
GRANT SELECT, INSERT, UPDATE, DELETE ON devfolio.* TO 'devfolio_app'@'%';

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255),
    bio TEXT,
    role VARCHAR(50) DEFAULT 'USER',
    avatar_url VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255),
    description TEXT,
    github_url VARCHAR(500),
    image_url VARCHAR(500),
    owner_id BIGINT,
    is_public BOOLEAN DEFAULT true,
    FOREIGN KEY (owner_id) REFERENCES users(id)
);

-- Comptes avec hashes BCrypt (cost factor 12)
-- admin123
INSERT INTO users (email, password, role, bio) VALUES
    ('admin@devfolio.com', '$2a$12$80ekbUlL4gAAHdSfRpurLutjGNmPot.2EgSgmJUJ0FJsX1W95XiqO', 'ADMIN',
     'Administrateur DevFolio'),
    ('lilo@student.com', '$2a$12$LpVOy7RNc5Tj76lygYnHYOWYyq2qyOtgkVNxXL7kuquWWgmuQ/PnG', 'USER',
     'Développeuse passionnée'),
    ('dylan@student.com', '$2a$12$uSkJcrD/B9IbN9SZvMEmxO2rybfL73bqwrrQpQoFIzZm3.0BwF6u6', 'USER',
     'Étudiant en alternance');

INSERT INTO projects (title, description, github_url, owner_id, is_public) VALUES
    ('Mon Portfolio', 'Mon premier projet Vue.js', 'https://github.com/lilo/portfolio', 2, true),
    ('API REST Spring', 'Backend pour mon app', 'https://github.com/dylan/api', 3, false);
