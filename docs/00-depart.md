# Pipeline de déploiement continu — DevFolio

> Kit 2 : CI/CD, tests automatisés, scan de sécurité, déploiement sur VPS.

---

## Objectif

Construire un pipeline complet de **Intégration Continue (CI)** et **Déploiement Continu (CD)** pour l'application DevFolio (Spring Boot + Vue.js + MariaDB).

Le pipeline s'exécute sur **GitHub Actions** (runner `ubuntu-latest`) et déploie sur un **VPS Debian** chez un hébergeur cloud.

---

## Architecture cible

```
┌─────────────────────────────────────┐     ┌─────────────────────────────────────┐
│  GitHub Actions (CI)                │     │  VPS Debian (<VPS_IP>)            │
│  ├─ Build Maven (Docker)            │     │  ├─ Docker + Docker Compose       │
│  ├─ Build Vite (Docker)             │────▶│  ├─ Nginx (reverse proxy)         │
│  ├─ Tests JUnit / Mockito           │     │  ├─ fail2ban (SSH)                │
│  ├─ Scan SAST (Semgrep)             │     │  ├─ MariaDB (volume persistant)   │
│  ├─ Scan image (Trivy)              │     │  └─ Application (containers)      │
│  └─ Push images GHCR / Docker Hub   │     │                                     │
└─────────────────────────────────────┘     └─────────────────────────────────────┘
```

---

## Contraintes et choix

| Contrainte | Décision |
|---|---|
| **Pas de nom de domaine** | HTTPS avec certificat auto-signé en staging (Nginx gère le TLS) |
| **VPS minimaliste** | Docker-only : pas de Java 21 ni Node.js installés sur le VPS |
| **Build isolés** | Maven et Node exécutés dans des conteneurs éphémères (GitHub Actions ou Docker) |
| **Secrets** | Variables GitHub (`Settings > Secrets`) + `.env` sur le VPS (jamais dans le repo) |
| **Branches protégées** | `main` (vulnérable), `correction` (sécurisé), `ci-cd-pipeline` (ce cours) |

---

## Stack technique

| Couche | Technologie |
|---|---|
| Backend | Spring Boot 3.2, Java 21, Maven |
| Frontend | Vue 3, Vite, Bootstrap 5 |
| Base de données | MariaDB 10.11 |
| CI | GitHub Actions (runner `ubuntu-latest`) |
| Containerisation | Docker, Docker Compose |
| Reverse proxy | Nginx |
| Sécurité VPS | fail2ban, firewall UFW |
| Scan SAST | Semgrep |
| Scan image | Trivy |

---

## Plan de travail

### Phase 1 — Infrastructure VPS (ce repo, branche `ci-cd-pipeline`)

1. **Sécuriser le VPS**
   - Mettre à jour le système (`apt update && apt upgrade`)
   - Configurer `fail2ban` pour SSH
   - Activer le firewall UFW (ports 22, 80, 443)

2. **Installer Nginx sur l'hôte** (Option A — Nginx hôte)
   - Arrêter le conteneur frontend existant (conflit ports 80/443)
   - Reverse proxy : `127.0.0.1:8080` (backend) et `127.0.0.1:3000` (frontend)
   - HTTP → redirection 301 vers HTTPS
   - HTTPS avec certificat auto-signé (pas de nom de domaine → pas de Let's Encrypt)
   - En-têtes de sécurité 2026 : HSTS, CSP, X-Frame-Options, X-Content-Type-Options, Referrer-Policy

3. **Préparer Docker sur le VPS**
   - Docker 29.5.3 et Docker Compose v5.1.4 déjà installés ✅
   - Réseaux Docker isolés : `frontend-backend` et `backend-db`
   - Volume persistant `db_data` pour MariaDB
   - Tous les ports mappés sur `127.0.0.1` uniquement (pas d'exposition publique)

### Phase 2 — Pipeline CI (GitHub Actions)

4. **Créer `.github/workflows/ci.yml`**
   - Checkout du code
   - Build Maven (`mvn clean compile`) via Docker
   - Build frontend (`npm install && npm run build`) via Docker
   - Tests unitaires JUnit + Mockito
   - Tests d'intégration (testcontainers MariaDB)
   - Scan SAST avec Semgrep
   - Scan d'image Docker avec Trivy
   - Push des images sur GHCR (GitHub Container Registry)

5. **`docker-compose.staging.yml`** ✅ (créé)
   - Images pré-construites depuis GHCR (pas de build local sur le VPS)
   - Tag d'image explicite via `${IMAGE_TAG}` (SHA du commit, pas de `:latest`)
   - Variables d'environnement via `.env` sur le VPS
   - Healthchecks pour MariaDB et le backend (`/actuator/health`)
   - Tous les ports mappés sur `127.0.0.1` (frontend `3000:80`, backend `8080:8080`, MariaDB `3306:3306`)
   - `restart: unless-stopped` sur tous les services

### Phase 3 — Tests automatisés

6. **Tests unitaires backend**
   - JUnit 5 + Mockito
   - Couvrir les controllers, services, validators
   - Ne pas tester la couche JPA (utiliser `@WebMvcTest` et `@MockBean`)

7. **Tests E2E frontend**
   - Playwright ou Cypress
   - Scénarios : login, création de projet, modification de profil
   - Exécuter contre le backend démarré en local (testcontainers)

### Phase 4 — Déploiement continu

8. **Déploiement sur le VPS**
   - GitHub Actions se connecte au VPS via SSH (clé déployée en secret)
   - `docker-compose pull && docker-compose up -d` sur le VPS
   - Rollback automatique si healthcheck échoue

9. **Monitoring et logs**
   - `docker logs` centralisés
   - Healthcheck HTTP sur `/actuator/health`

---

## Différences avec les branches `main` et `correction`

| Aspect | `main` / `correction` | `ci-cd-pipeline` |
|---|---|---|
| Objectif | Sécurité applicative (OWASP) | Pipeline de déploiement |
| Tests | Aucun (YAGNI) | JUnit + Mockito + E2E obligatoires |
| Doc | `docs/` pédagogique sécurité | `docs/` pédagogique CI/CD |
| Infra | Local (Docker Desktop) | VPS cloud (Docker Engine) |
| Déploiement | Manuel (`docker-compose up`) | Automatisé (GitHub Actions → VPS) |

---

## Règles de sécurité spécifiques CI/CD

- **Jamais de secret dans `.github/workflows/`** : utiliser `${{ secrets.XXX }}`
- **Jamais de `.env` commité** : `.env` est dans `.gitignore`, généré par GitHub Secrets ou copié sur le VPS
- **Pas de `latest` tag en production** : images versionnées (`:1.2.3`) ou SHA du commit
- **Build avant test** : `mvn clean compile` doit passer avant les tests
- **Scan bloquant** : un échec SAST (Semgrep) ou Trivy bloque le merge
- **Pas de déploiement automatique sur production** : staging automatique, prod manuelle (review)

---

## Avancement

| Phase | Statut | Détail |
|---|---|---|
| **Phase 1 — Infrastructure VPS** | ✅ Terminée | fail2ban, UFW, Nginx hôte, certificat auto-signé, `.env` créé |
| **Phase 2 — Pipeline CI** | À faire | `.github/workflows/ci.yml`, push images GHCR |
| **Phase 3 — Tests automatisés** | À faire | JUnit + Mockito, E2E |
| **Phase 4 — Déploiement continu** | À faire | SSH deploy, healthcheck, rollback |

### Phase 1 — Récapitulatif VPS

- **OS** : Debian 13 (trixie), kernel 6.12.90
- **Docker** : 29.5.3 + Compose v5.1.4
- **fail2ban** : 1.1.0, jail sshd (bantime 1h, maxretry 3)
- **UFW** : actif, ports 22/80/443 uniquement
- **Nginx hôte** : 1.26.3, reverse proxy `127.0.0.1:8080` (backend) + `127.0.0.1:3000` (frontend)
- **Certificat** : auto-signé (pas de domaine), validité 365 jours
- **En-têtes sécurité** : HSTS, CSP, X-Frame-Options, X-Content-Type-Options, Referrer-Policy
- **Conteneurs Docker** : backend + MariaDB sur `127.0.0.1` uniquement, frontend arrêté (remplacé par Nginx hôte)
- **`.env`** : créé sur le VPS (non versionné), contient `CORS_ALLOWED_ORIGINS`, `JWT_SECRET`, `IMAGE_TAG=manual`

---

## Prochaine étape

→ **Phase 2 — Pipeline CI** : créer `.github/workflows/ci.yml` (build, tests, scan, push images GHCR).
