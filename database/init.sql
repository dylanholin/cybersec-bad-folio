CREATE DATABASE IF NOT EXISTS devfolio;
USE devfolio;

-- 🔴 DEV-04 / A05-04 : utilisateur avec tous les privilèges
GRANT ALL PRIVILEGES ON devfolio.* TO 'root'@'%' IDENTIFIED BY 'root';
-- Aucun utilisateur applicatif avec privilèges minimaux

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255),  -- 🔴 : hash MD5 ou pire
    bio TEXT,               -- 🔴 : stocke du HTML brut (XSS stocké)
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

-- 🔴 A07-04 : compte admin par défaut avec mot de passe trivial
-- Password = MD5('admin123') = 0192023a7bbd73250516f069df18b500
INSERT INTO users (email, password, role, bio) VALUES
    ('admin@devfolio.com', '0192023a7bbd73250516f069df18b500', 'ADMIN',
     '<h1>Admin</h1>'),  -- 🔴 HTML brut en base
    ('alice@student.com', '0192023a7bbd73250516f069df18b500', 'USER',
     'Développeuse passionnée'),
    ('bob@student.com', '0192023a7bbd73250516f069df18b500', 'USER',
     'Étudiant en alternance');

-- 🔴 A02-06 : données de test avec mots de passe en commentaire
-- admin123 → 0192023a7bbd73250516f069df18b500 (tous les comptes ont le même mot de passe)

INSERT INTO projects (title, description, github_url, owner_id, is_public) VALUES
    ('Mon Portfolio', 'Mon premier projet Vue.js', 'https://github.com/alice/portfolio', 2, true),
    ('API REST Spring', 'Backend pour mon app', 'https://github.com/bob/api', 3, false),
    ('Projet Secret', 'Données confidentielles du client XYZ — NDA signé',
     'https://github.com/bob/secret', 3, false);
