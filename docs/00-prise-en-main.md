> **Suivi des corrections (Itération 2   Jour 2)** : les corrections réalisées sur la branche `correction` sont détaillées dans [06-corriger-essentiel-demo.md](06-corriger-essentiel-demo.md). Ce document conserve l'état initial (prise en main Jour 1).
>
> ---
>
# Prise en main du projet DevFolio

## Présentation

**DevFolio** est une plateforme de portfolio étudiant composée de :

- **Backend** : Spring Boot 3.2 (Java 21) + MariaDB 10.11
- **Frontend** : Vue 3 + Vite + Bootstrap, servi par Nginx
- **Base de données** : MariaDB 10.11

L'application permet aux étudiants de créer un profil, publier des projets et importer des dépôts GitHub.

> ⚠️ Ce projet contient **volontairement** de nombreuses vulnérabilités et mauvaises pratiques dans un but pédagogique.

---

## Architecture

```
cybersec-bad-folio-main/
├── .env                        # Secrets hardcodés (commité !)
├── .gitignore                  # Incomplet (.env non exclu)
├── docker-compose.yml          # Orchestration des 3 services
├── database/
│   └── init.sql                # Script d'initialisation BDD
├── backend/
│   ├── Dockerfile
│   ├── pom.xml                 # Dépendances Maven
│   └── src/main/java/com/devfolio/
│       ├── config/SecurityConfig.java
│       ├── config/JwtAuthenticationFilter.java (à créer)
│       ├── controller/
│       │   ├── AuthController.java
│       │   ├── UserController.java
│       │   ├── ProjectController.java
│       │   ├── SearchController.java
│       │   └── AvatarController.java
│       ├── service/
│       │   ├── AuthService.java
│       │   ├── JwtService.java
│       │   └── ProjectService.java
│       ├── model/
│       │   ├── User.java
│       │   └── Project.java
│       ├── repository/
│       │   ├── UserRepository.java
│       │   └── ProjectRepository.java
│       └── resources/application.properties
├── frontend/
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── vite.config.js
│   ├── package.json
│   ├── index.html
│   └── src/
│       ├── services/api.js
│       ├── stores/auth.js
│       ├── router/index.js
│       └── views/
│           ├── HomeView.vue
│           ├── LoginView.vue
│           ├── RegisterView.vue
│           ├── ProfileView.vue
│           ├── ProjectView.vue
│           └── AdminView.vue
└── docs/                      # Documentation de l'audit
```

---

## Services et ports

| Service   | Port exposé | Rôle                        |
|-----------|-------------|-----------------------------|
| frontend  | 80          | Nginx : sert le SPA Vue.js  |
| backend   | 8080        | Spring Boot API REST        |
| mariadb   | 3306        | Base de données             |

> 🔴 Le port MariaDB (3306) est exposé sur `0.0.0.0`, accessible depuis l'extérieur.
> 🔴 Le port de debug JVM (5005) est exposé dans le Dockerfile backend.

---

## Démarrage

```bash
docker-compose up --build
```

- Frontend : http://localhost:80
- Backend API : http://localhost:8080/api
- Actuator : http://localhost:8080/actuator

---

## Données manipulées

- **Utilisateurs** : email, mot de passe (hash MD5), rôle, bio (HTML brut), avatar
- **Projets** : titre, description, URL GitHub, visibilité (public/privé)
- **Secrets** : JWT, identifiants BDD, credentials AWS, mot de passe Gmail, identifiants admin

---

## Dépendances clés

| Composant        | Version      | Problème connu                          |
|------------------|--------------|-----------------------------------------|
| Spring Boot      | 3.2.0        |                                         |
| Log4j Core       | 2.14.1       | 🔴 CVE-2021-44228 (Log4Shell)           |
| jjwt             | 0.11.5       |                                         |
| MariaDB JDBC     | 3.3.0        |                                         |
| axios (frontend) | 0.21.1       | 🔴 CVE-2021-3749 (SSRF)                 |
| Vue              | ^3.4.0       |                                         |
| Bootstrap (CDN)  | 5.3.0        | 🔴 Pas de SRI (Subresource Integrity)   |
