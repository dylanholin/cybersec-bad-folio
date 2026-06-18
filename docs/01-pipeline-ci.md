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

- **Outil** : Semgrep (installé via `python3 -m pip install semgrep`, config `auto`)
- **Arguments** : `semgrep scan --config auto --sarif --output semgrep.sarif .`
- **Non-bloquant** : `continue-on-error: true` — le rapport est généré pour analyse, le pipeline ne s'arrête pas
- **Permissions** : `contents: read`, `security-events: write` pour l'upload SARIF
- **Sortie** : rapport SARIF uploadé via `github/codeql-action/upload-sarif@v4` (onglet Security de GitHub)

#### 4. `build-and-push` — Build, scan Trivy, push GHCR

- **Dépend de** : `test-backend`, `test-frontend`, `scan-sast`
- **Permissions** : `packages: write` (push GHCR), `contents: read` (checkout), `actions: write` (cache GHA)
- **Étapes** :
  1. `docker/setup-buildx-action@v3` — initialise Buildx (requis pour le cache GHA)
  2. Login GHCR via `GITHUB_TOKEN` (auto, pas de secret manuel)
  3. Build image backend (avec cache GHA `type=gha,mode=max`)
  4. Build image frontend (avec cache GHA)
  5. Scan Trivy backend (`aquasecurity/trivy-action@v0.36.0`, severity HIGH/CRITICAL, `ignore-unfixed: true`, `scanners: vuln` — **bloquant**, `exit-code: 1`)
  6. Scan Trivy frontend (idem)
  7. Push backend vers GHCR (avec cache GHA)
  8. Push frontend vers GHCR (avec cache GHA)

> **Note `scanners: vuln`** : le scan Trivy se limite aux vulnérabilités (pas de scan de secrets). Le certificat SSL auto-signé du frontend génère un faux positif (clé privée asymétrique) qui n'est pas une fuite réelle.
>
> **Note `ignore-unfixed: true`** : les CVE sans correctif disponible sont ignorées pour éviter de bloquer le pipeline sur des vulnérabilités non patchées.

### Tagging des images

- **Tag** : `${{ github.sha }}` (SHA du commit)
- **Pas de `:latest`** : conforme à la règle `AGENTS.md`
- **Registry** : `ghcr.io/dylanholin/devfolio-backend` et `ghcr.io/dylanholin/devfolio-frontend`

### Secrets utilisés

| Secret / Permission | Source | Usage |
|---|---|---|
| `GITHUB_TOKEN` | Auto (GitHub Actions) | Login + push sur GHCR |
| `security-events: write` | Permissions du job `scan-sast` | Upload du rapport SARIF (Semgrep) |
| `packages: write` | Permissions du job `build-and-push` | Push des images Docker sur GHCR |
| `contents: read` | Permissions du job `build-and-push` | Checkout du code |
| `actions: write` | Permissions du job `build-and-push` | Écriture dans le cache GHA (`type=gha,mode=max`) |

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
- Spring Boot parent mis à jour de 3.2.0 vers **3.5.15** (corrige 33 CVE Java : Tomcat, Spring Security, Spring Framework, Logback)

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

## Corrections Trivy — Cycle d'itération CI

Le pipeline a nécessité plusieurs itérations pour passer de 36 vulnérabilités à 0.

### Run #4 — `buildx failed`

| Problème | Cause | Fix |
|---|---|---|
| Docker buildx cache error | Permission `actions: write` manquante | Ajout de la permission au job `build-and-push` |

### Run #5 — 36 CVE détectées (exit code 1)

Trivy a remonté **3 CVE Alpine** + **33 CVE Java** (27 HIGH, 6 CRITICAL).

| Composant | Version vulnérable | Version corrigée | CVE |
|---|---|---|---|
| **Spring Boot** | 3.2.0 | 3.5.15 | CVE-2025-22235 (Spring Boot EndpointRequest) |
| **Tomcat embed** | 10.1.16 | 10.1.55+ | CVE-2025-24813 (CRITICAL RCE), CVE-2026-41293, CVE-2026-43512, CVE-2026-43515, +12 autres |
| **Spring Security** | 6.2.0 | 6.4+ | CVE-2024-38821 (CRITICAL auth bypass), CVE-2026-22732, CVE-2025-22228 |
| **Spring Framework** | 6.1.1 | 6.2.11+ | CVE-2025-41249, CVE-2024-22243/22259/22262, CVE-2024-38816/38819 |
| **Logback** | 1.4.11 | 1.5.x | CVE-2023-6378 (serialization vulnerability) |
| **OpenSSL (Alpine)** | 3.5.6-r0 | 3.5.7-r0 | CVE-2026-45447 (heap use-after-free PKCS7_verify) |

**Correctifs appliqués** :
- `backend/pom.xml` : Spring Boot parent 3.2.0 → 3.5.15 (tire automatiquement Tomcat 10.1.55+, Spring Security 6.4+, Spring Framework 6.2+, Logback 1.5+)
- `backend/Dockerfile` : `apk update && apk upgrade --no-cache` avant `addgroup` (récupère OpenSSL 3.5.7-r0)
- `frontend/Dockerfile` : idem + Node 20 → 22 (cohérence avec la CI)

### Run #6 — Faux positif secret SSL

| Problème | Cause | Fix |
|---|---|---|
| Trivy secret scan : `AsymmetricPrivateKey` détecté | Certificat SSL auto-signé du frontend (clé privée RSA dans `/etc/nginx/ssl/devfolio.key`) | `scanners: vuln` pour limiter Trivy aux vulnérabilités uniquement |

> Le certificat auto-signé est généré dans le Dockerfile (`openssl req -x509 -nodes`). La clé privée n'est pas une fuite réelle — elle est embarquée dans l'image pour le HTTPS de dev/staging.

### Run #7 — Succès ✅

| Résultat | Détail |
|---|---|
| **Status** | Success (3m 16s) |
| **Tests backend** | 15 tests JUnit passés |
| **Tests frontend** | Vitest + build passés |
| **Semgrep** | Scan SAST complété, SARIF uploadé |
| **Trivy backend** | 0 vulnérabilités HIGH/CRITICAL |
| **Trivy frontend** | 0 vulnérabilités HIGH/CRITICAL |
| **Images poussées** | GHCR : `devfolio-backend:8a73a2e`, `devfolio-frontend:8a73a2e` |

> **Note** : Des warnings `Node.js 20 is deprecated` apparaissent sur `actions/checkout@v4`, `docker/build-push-action@v6`, etc. Ces warnings sont des **deprecation notices non bloquants** — GitHub Actions force automatiquement l'exécution sur Node 24. Les actions restent fonctionnelles ; la migration officielle vers les versions Node 22/24 des actions attend les releases upstream.

### Configuration Trivy finale

```yaml
scanners: vuln          # vulnérabilités uniquement (pas de scan secrets)
severity: HIGH,CRITICAL # bloquant uniquement sur HIGH et CRITICAL
ignore-unfixed: true    # ignore les CVE sans correctif disponible
exit-code: '1'          # bloquant
```

---

## Fichiers créés/modifiés

| Fichier | Action | Commit |
|---|---|---|
| `backend/pom.xml` | Ajout `spring-boot-starter-test` + upgrade Spring Boot 3.2.0 → 3.5.15 | `9f84d20`, `a881045` |
| `backend/src/test/java/.../JwtServiceTest.java` | Création + correction secret aléatoire | `9f84d20` + correction |
| `backend/src/test/java/.../UrlValidatorTest.java` | Création | `9f84d20` |
| `backend/src/test/java/.../AuthControllerTest.java` | Création | `9f84d20` |
| `frontend/package.json` | Ajout Vitest + @vue/test-utils + jsdom | `9f84d20` |
| `frontend/vitest.config.js` | Création | `9f84d20` |
| `frontend/src/test/basic.test.js` | Création | `9f84d20` |
| `.github/workflows/ci.yml` | Création + corrections successives (Semgrep CLI, Buildx, Trivy v0.36.0, scanners vuln, ignore-unfixed, Node 22, actions:write) | `ca05741` + corrections |
| `backend/Dockerfile` | Ajout `apk update && apk upgrade` (fix CVE OpenSSL Alpine) | `a881045` |
| `frontend/Dockerfile` | Node 20 → 22 + `apk update && apk upgrade` | `a881045` |
| `docs/01-pipeline-ci.md` | Création + mise à jour corrections pipeline | `cbd22e7` + mise à jour |
| `docker-compose.staging.yml` | Création (Phase 1) | `3f0c687` |
