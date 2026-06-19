# Pipeline de déploiement continu — DevFolio

> Kit 2 : CI/CD, tests automatisés, scan de sécurité, déploiement sur VPS.

---

## Objectif

Construire un pipeline complet de **Intégration Continue (CI)** et **Déploiement Continu (CD)** pour l'application DevFolio (Spring Boot + Vue.js + MariaDB).

Le pipeline s'exécute sur **GitHub Actions** (runner `ubuntu-latest`) et déploie sur un **VPS Debian** chez un hébergeur cloud.

---

## Concepts CI/CD pour débutants

> Cette section explique les termes fondamentaux utilisés tout au long de cette documentation. Si vous débutez en CI/CD, lisez-la avant de continuer.

### Qu'est-ce que la CI/CD ?

| Terme | Définition |
|---|---|
| **CI (Intégration Continue)** | Pratique qui consiste à vérifier automatiquement le code à chaque changement (push, pull request). Chaque modification est testée et scannée avant d'être intégrée. |
| **CD (Déploiement Continu)** | Suite logique de la CI : une fois le code validé, il est déployé automatiquement sur le serveur cible, sans intervention manuelle. |

### Vocabulaire technique

| Terme | Explication simple |
|---|---|
| **Runner** | Machine virtuelle éphémère fournie par GitHub (ici `ubuntu-latest`). Elle exécute les tâches définies dans le workflow, puis est détruite. On n'a rien à installer ni maintenir. |
| **Workflow** | Fichier YAML (`.github/workflows/ci.yml`) qui décrit la suite d'actions à exécuter automatiquement. C'est la "recette" du pipeline. |
| **Job** | Étape du workflow exécutée sur un runner. Chaque job est indépendant. Dans ce projet : `test-backend`, `test-frontend`, `scan-sast`, `build-and-push`, `deploy`. |
| **Step** | Sous-étape d'un job. Par exemple, dans le job `test-backend` : checkout du code → installation Java 21 → `mvn clean test`. |
| **Trigger** | Événement qui déclenche le workflow. Ici : `push` (envoi de code) et `pull_request` (ouverture d'une PR) sur la branche `ci-cd-pipeline`. |
| **Build (construction)** | Transformation du code source en artefact exécutable. Ici : compilation Java (`mvn`), build frontend (`vite build`), et construction des images Docker. |
| **Test** | Vérification automatique que le code fonctionne comme prévu. Ici : tests unitaires JUnit (backend) et Vitest (frontend). |
| **Scan SAST** | Static Application Security Testing : analyse du code source à la recherche de vulnérabilités sans l'exécuter. Outil utilisé : Semgrep. |
| **Scan d'image** | Analyse d'une image Docker à la recherche de vulnérabilités connues (CVE). Outil utilisé : Trivy. |
| **GHCR** | GitHub Container Registry : registre d'images Docker hébergé par GitHub. Équivalent de Docker Hub mais privé, accessible avec le `GITHUB_TOKEN`. |
| **Image Docker** | Snapshot reproductible d'une application + son environnement. Construite une fois, exécutée partout. Ici : `devfolio-backend:SHA` et `devfolio-frontend:SHA`. |
| **Déploiement** | Mise en production du code sur le serveur cible (VPS). Ici : pull des images depuis GHCR + `docker compose up -d`. |
| **Healthcheck** | Vérification automatique que l'application démarre correctement après un déploiement. Ici : requête HTTP sur `/actuator/health` (max 60s). |
| **Secret** | Valeur sensible (clé SSH, token, mot de passe) stockée chiffrée dans GitHub (Settings → Secrets) ou sur le VPS (`.env`). Jamais dans le code. |
| **Artifact** | Fichier produit par le pipeline (image Docker, rapport SARIF, build frontend). |

### Comment les jobs s'enchaînent

```
push sur la branche
    │
    ▼
┌─────────────┐  ┌──────────────┐  ┌───────────┐
│ test-backend │  │ test-frontend│  │ scan-sast │
│ (JUnit)      │  │ (Vitest)     │  │ (Semgrep) │
└──────┬───────┘  └──────┬───────┘  └─────┬─────┘
       │                 │                │
       └────────┬────────┘────────────────┘
                ▼
       ┌─────────────────┐
       │ build-and-push   │
       │ (Docker + Trivy  │
       │  + push GHCR)    │
       └────────┬─────────┘
                ▼
       ┌─────────────────┐
       │ deploy           │
       │ (SSH → VPS)      │
       └─────────────────┘
```

> Les 3 premiers jobs s'exécutent **en parallèle**. Le job `build-and-push` attend que les 3 soient terminés (`needs`). Le job `deploy` attend que `build-and-push` soit terminé.

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
│  └─ Push images GHCR                │     │                                     │
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
| Backend | Spring Boot 3.5, Java 21, Maven |
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
   - Docker 29.5.3 et Docker Compose v2.x (plugin) déjà installés ✅
   - Réseaux Docker isolés : `frontend-backend` et `backend-db`
   - Volume persistant `db_data` pour MariaDB
   - Tous les ports mappés sur `127.0.0.1` uniquement (pas d'exposition publique)

### Phase 2 — Pipeline CI (GitHub Actions)

4. **Créer `.github/workflows/ci.yml`**
   - Checkout du code
   - Tests backend : `mvn clean test` (Java 21 sur runner)
   - Build frontend : `npm ci && npm run build` (Node 22 sur runner)
   - Tests unitaires JUnit + Mockito
   - Scan SAST avec Semgrep
   - Scan d'image Docker avec Trivy
   - Push des images sur GHCR (GitHub Container Registry)

5. **`docker-compose.staging.yml`**
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

> **Tests E2E (non implémentés)** : Playwright ou Cypress — scénarios login, création de projet, modification de profil. Voir section "Prochaine étape".

### Phase 4 — Déploiement continu

7. **Déploiement sur le VPS**
   - GitHub Actions se connecte au VPS via SSH (clé déployée en secret)
   - `docker compose pull && docker compose up -d` sur le VPS
   - Affichage des logs + exit 1 si healthcheck échoue

8. **Monitoring et logs**
   - `docker logs` centralisés
   - Healthcheck HTTP sur `/actuator/health`

---

## Différences avec les branches `main` et `correction`

| Aspect | `main` | `correction` / `ci-cd-pipeline` |
|---|---|---|
| Objectif | Sécurité applicative (OWASP) | Sécurité + pipeline de déploiement |
| Tests | Aucun (YAGNI) | JUnit + Mockito (unitaires) |
| Doc | `docs/securite/` pédagogique sécurité | `docs/securite/` + `docs/ci-cd/` |
| Infra | Local (Docker Desktop) | VPS cloud (Docker Engine) |
| Déploiement | Manuel (`docker compose up`) | Automatisé (GitHub Actions → VPS) |

---

## Règles de sécurité spécifiques CI/CD

- **Jamais de secret dans `.github/workflows/`** : utiliser `${{ secrets.XXX }}`
- **Jamais de `.env` commité** : `.env` est dans `.gitignore`, généré par GitHub Secrets ou copié sur le VPS
- **Pas de `latest` tag en production** : images versionnées (`:1.2.3`) ou SHA du commit
- **Build avant test** : `mvn clean compile` doit passer avant les tests
- **Scan bloquant** : un échec Trivy (vulnérabilités HIGH/CRITICAL) bloque le pipeline. Semgrep (SAST) est non-bloquant : le rapport SARIF est uploadé pour analyse dans l'onglet Security de GitHub
- **Pas de déploiement automatique sur production** : staging automatique, prod manuelle (review)

---

## Avancement

| Phase | Statut | Détail |
|---|---|---|
| **Phase 1 — Infrastructure VPS** | ✅ Terminée | fail2ban, UFW, Nginx hôte, certificat auto-signé, `.env` créé — voir `01-infrastructure-vps.md` |
| **Phase 2 — Pipeline CI** | ✅ Terminée | `.github/workflows/ci.yml`, tests, Semgrep, Trivy, push GHCR — voir `02-pipeline-ci.md` |
| **Phase 3 — Tests automatisés** | ✅ Terminée | JUnit + Mockito (15 tests backend), Vitest (2 tests frontend) — voir `03-tests-automatises.md` |
| **Phase 4 — Déploiement continu** | ✅ Terminée | Job `deploy` SSH → VPS, `docker compose pull/up`, healthcheck `/actuator/health` 60s — voir `06-deploiement-continu.md` |

---

## Prochaine étape

- **Let's Encrypt (2.8)** : sécuriser la connexion HTTPS avec un certificat valide (nécessite un nom de domaine).
- **Tests E2E** : étendre la couverture avec Playwright ou Cypress (navigation, authentification, CRUD projets).

> L'implémentation détaillée des phases terminées est documentée dans les fichiers `01-infrastructure-vps.md` à `07-fichiers-modifies.md`.
