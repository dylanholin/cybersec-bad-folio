# Implémentation du pipeline CI — DevFolio

> Détail de l'implémentation des phases terminées. Le plan initial est dans [`00-depart.md`](./00-depart.md).

---

## Phase 1 — Infrastructure VPS

### Sécurisation du serveur

| Service | Version | Configuration |
|---|---|---|
| **OS** | Debian 13 (trixie) | Kernel 6.12.90 |
| **fail2ban** | 1.1.0 | Jail sshd : `bantime = 3600`, `maxretry = 3` |
| **UFW** | 0.36.2 | `deny incoming`, `allow outgoing`, ports 22/80/443 |
| **Nginx** | 1.26.3 | Reverse proxy sur l'hôte (Option A) |
| **Docker** | 29.5.3 | Compose v5.1.4 |

### Nginx — Reverse proxy hôte

Le conteneur frontend Docker d'origine occupait les ports 80/443. Il a été arrêté et remplacé par Nginx installé sur l'hôte :

```
Internet (443/80)
    │
    ▼
  Nginx (hôte) ── TLS auto-signé (365 jours, CN=<VPS_IP>)
    │
    ├── /api  ──▶ 127.0.0.1:8080  (backend container)
    └── /     ──▶ 127.0.0.1:3000  (frontend container)
```

- HTTP → redirection 301 vers HTTPS
- En-têtes de sécurité : HSTS, CSP, X-Frame-Options, X-Content-Type-Options, Referrer-Policy
- Certificat auto-signé (pas de nom de domaine → pas de Let's Encrypt)

### Docker sur le VPS

- **Conteneurs actifs** : backend + MariaDB sur `127.0.0.1` uniquement
- **Conteneur frontend** : arrêté (remplacé par Nginx hôte)
- **Réseaux isolés** : `frontend-backend`, `backend-db`
- **Volume persistant** : `db_data` pour MariaDB
- **Ports exposés** : aucun port Docker exposé publiquement (tout via `127.0.0.1`)

### Fichier `.env` sur le VPS

Créé manuellement sur le VPS (non versionné, dans `.gitignore`) :

| Variable | Description |
|---|---|
| `DB_ROOT_PASSWORD` | Mot de passe root MariaDB |
| `DB_NAME` | Nom de la base de données |
| `DB_USER` / `DB_PASSWORD` | Utilisateur applicatif MariaDB |
| `JWT_SECRET` | Secret JWT (48 caractères base64, généré via `openssl rand -base64 48`) |
| `JWT_EXPIRATION` | Durée de validité du token (ms) |
| `CORS_ALLOWED_ORIGINS` | Origines autorisées pour CORS (`https://<VPS_IP>`) |
| `ADMIN_PASSWORD` | Mot de passe du compte admin seed |
| `IMAGE_TAG` | Tag des images Docker (`manual` pour le déploiement initial) |

---

## Phase 2 — Pipeline CI (GitHub Actions)

### Workflow

Fichier : `.github/workflows/ci.yml`

**Triggers** : `push` et `pull_request` sur la branche `ci-cd-pipeline`.

### Jobs

#### 1. `test-backend` — Tests backend

- **Runner** : `ubuntu-latest`
- **Java** : Temurin 21 (cache Maven activé)
- **Commande** : `mvn clean test -B`
- **Frameworks** : JUnit 5 + Mockito (inclus via `spring-boot-starter-test`)

#### 2. `test-frontend` — Tests + build frontend

- **Runner** : `ubuntu-latest`
- **Node** : 22 (cache npm activé)
- **Commandes** : `npm ci` → `npm test` → `npm run build`
- **Frameworks** : Vitest 1.x + @vue/test-utils 2.x

#### 3. `scan-sast` — Scan statique (Semgrep)

- **Outil** : Semgrep (action `semgrep/semgrep-action@v1`, config `auto`)
- **Arguments** : `--sarif --output=semgrep.sarif`
- **Permissions** : `security-events: write` pour l'upload SARIF
- **Sortie** : rapport SARIF uploadé via `github/codeql-action/upload-sarif@v4`

#### 4. `build-and-push` — Build, scan Trivy, push GHCR

- **Dépend de** : `test-backend`, `test-frontend`, `scan-sast`
- **Permissions** : `packages: write` (pour push sur GHCR)
- **Étapes** :
  1. Login GHCR via `GITHUB_TOKEN` (auto, pas de secret manuel)
  2. Build image backend (avec cache GHA)
  3. Build image frontend (avec cache GHA)
  4. Scan Trivy backend (severity HIGH/CRITICAL — **bloquant**, `exit-code: 1`)
  5. Scan Trivy frontend (severity HIGH/CRITICAL — **bloquant**)
  6. Push backend vers GHCR
  7. Push frontend vers GHCR

### Tagging des images

- **Tag** : `${{ github.sha }}` (SHA du commit)
- **Pas de `:latest`** : conforme à la règle `AGENTS.md`
- **Registry** : `ghcr.io/dylanholin/devfolio-backend` et `ghcr.io/dylanholin/devfolio-frontend`

### Secrets utilisés

| Secret / Permission | Source | Usage |
|---|---|---|
| `GITHUB_TOKEN` | Auto (GitHub Actions) | Login + push sur GHCR |
| `security-events: write` | Permissions du job | Upload du rapport SARIF (Semgrep) |
| `packages: write` | Permissions du job | Push des images Docker sur GHCR |
| `contents: read` | Permissions du job | Checkout du code |

Aucun secret manuel requis pour la phase CI. Les secrets SSH pour le déploiement (Phase 4) seront ajoutés ultérieurement.

---

## Tests créés

### Backend — 3 classes, 15 tests

| Fichier | Tests | Couverture |
|---|---|---|
| `JwtServiceTest` | 4 | Génération de token, validation, rejet token falsifié, rejet `alg:none`, rejet secret différent |
| `UrlValidatorTest` | 6 | HTTPS only, whitelist, rejet IP metadata (169.254.169.254), URL malformée, taille max fetch |
| `AuthControllerTest` | 7 | Login 200/401/429, register 200/400, logout, rate limiting, password mismatch |

> **Note** : le test `validateToken_shouldRejectTokenWithDifferentSecret` a nécessité un secret aléatoire (`SecureRandom`) car deux appels à `Base64.getEncoder().encodeToString(new byte[48])` produisent le même encodage (tableau de zéros), ce qui invalidait le test.

### Frontend — 1 fichier, 2 tests

| Fichier | Tests | Couverture |
|---|---|---|
| `basic.test.js` | 2 | Sanity check (assertions de base, opérations string) |

### Dépendances ajoutées

**Backend** (`pom.xml`) :
- `spring-boot-starter-test` (scope `test`) — inclut JUnit 5, Mockito, AssertJ

**Frontend** (`package.json`) :
- `vitest` ^1.0.0 — runner de tests
- `@vue/test-utils` ^2.4.0 — utilitaires de test Vue
- `jsdom` ^24.0.0 — environnement DOM pour les tests
- Script `test` : `vitest run`
- Config : `vitest.config.js` (environnement jsdom, globals activés)

---

## Diagramme de déploiement

```
┌─────────────────────────────────────────────────────────────┐
│  VPS Debian (<VPS_IP>)                                    │
│                                                             │
│  ┌──────────────┐    ┌──────────────────────────────────┐  │
│  │  Nginx (hôte)│    │  Docker Engine                    │  │
│  │  :443 / :80  │    │                                   │  │
│  │  TLS auto-   │    │  ┌─────────────┐  ┌────────────┐ │  │
│  │  signé       │───▶│  │  backend    │  │  mariadb   │ │  │
│  │              │    │  │  :8080      │◀▶│  :3306     │ │  │
│  │  /api ───────│───▶│  │  127.0.0.1  │  │  127.0.0.1 │ │  │
│  │  /    ───────│───▶│  └─────────────┘  └────────────┘ │  │
│  │              │    │       │                          │  │
│  │              │    │       ▼                          │  │
│  │              │    │  ┌─────────────┐                 │  │
│  │              │    │  │  frontend   │                 │  │
│  │              │    │  │  :3000      │                 │  │
│  │              │    │  │  127.0.0.1  │                 │  │
│  │              │    │  └─────────────┘                 │  │
│  └──────────────┘    └──────────────────────────────────┘  │
│                                                             │
│  fail2ban (sshd)  │  UFW (22/80/443)                       │
└─────────────────────────────────────────────────────────────┘
         ▲
         │ SSH (déploiement Phase 4)
         │
┌─────────────────────────────────────────────────────────────┐
│  GitHub Actions (CI)                                        │
│  ├─ test-backend  (mvn clean test)                         │
│  ├─ test-frontend (vitest + vite build)                    │
│  ├─ scan-sast     (Semgrep)                                │
│  └─ build-and-push (Trivy scan + push GHCR)                │
│                                                             │
│  GHCR : ghcr.io/dylanholin/devfolio-{backend,frontend}     │
└─────────────────────────────────────────────────────────────┘
```

---

## Fichiers créés/modifiés

| Fichier | Action | Commit |
|---|---|---|
| `backend/pom.xml` | Ajout `spring-boot-starter-test` | `9f84d20` |
| `backend/src/test/java/.../JwtServiceTest.java` | Création + correction secret aléatoire | `9f84d20` + correction |
| `backend/src/test/java/.../UrlValidatorTest.java` | Création | `9f84d20` |
| `backend/src/test/java/.../AuthControllerTest.java` | Création | `9f84d20` |
| `frontend/package.json` | Ajout Vitest + @vue/test-utils + jsdom | `9f84d20` |
| `frontend/vitest.config.js` | Création | `9f84d20` |
| `frontend/src/test/basic.test.js` | Création | `9f84d20` |
| `.github/workflows/ci.yml` | Création + correction Semgrep/SARIF | `ca05741` + correction |
| `docs/01-pipeline-ci.md` | Création | `cbd22e7` |
| `docker-compose.staging.yml` | Création (Phase 1) | `3f0c687` |
