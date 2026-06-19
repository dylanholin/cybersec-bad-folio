# Phase 2 — Pipeline CI (GitHub Actions)

> Workflow d'intégration continue. Le plan initial est dans [`00-depart.md`](./00-depart.md).

---

## Workflow

Fichier : `.github/workflows/ci.yml`

**Triggers** : `push` et `pull_request` sur la branche `ci-cd-pipeline`.

---

## Jobs

### 1. `test-backend` — Tests backend

- **Runner** : `ubuntu-latest`
- **Java** : Temurin 21 (cache Maven activé)
- **Commande** : `mvn clean test -B`
- **Frameworks** : JUnit 5 + Mockito (inclus via `spring-boot-starter-test`)

### 2. `test-frontend` — Tests + build frontend

- **Runner** : `ubuntu-latest`
- **Node** : 22 (cache npm activé)
- **Commandes** : `npm ci` → `npm test` → `npm run build`
- **Frameworks** : Vitest 1.x + @vue/test-utils 2.x

### 3. `scan-sast` — Scan statique (Semgrep)

- **Outil** : Semgrep (installé via `python3 -m pip install semgrep`, config `auto`)
- **Arguments** : `semgrep scan --config auto --sarif --output semgrep.sarif .`
- **Non-bloquant** : `continue-on-error: true` — le rapport est généré pour analyse, le pipeline ne s'arrête pas
- **Permissions** : `contents: read`, `security-events: write` pour l'upload SARIF
- **Sortie** : rapport SARIF uploadé via `github/codeql-action/upload-sarif@v4` (onglet Security de GitHub).

### 4. `build-and-push` — Build, scan Trivy, push GHCR

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

---

## Tagging des images

- **Tag** : `${{ github.sha }}` (SHA du commit)
- **Pas de `:latest`** : conforme à la règle `AGENTS.md`
- **Registry** : `ghcr.io/dylanholin/devfolio-backend` et `ghcr.io/dylanholin/devfolio-frontend`

---

## Secrets et permissions

| Secret / Permission | Source | Usage |
|---|---|---|
| `GITHUB_TOKEN` | Auto (GitHub Actions) | Login + push sur GHCR |
| `security-events: write` | Permissions du job `scan-sast` | Upload du rapport SARIF (Semgrep) |
| `packages: write` | Permissions du job `build-and-push` | Push des images Docker sur GHCR |
| `contents: read` | Permissions du job `build-and-push` | Checkout du code |
| `actions: write` | Permissions du job `build-and-push` | Écriture dans le cache GHA (`type=gha,mode=max`) |

Aucun secret manuel requis pour la phase CI. Les secrets SSH pour le déploiement (Phase 4) sont documentés dans [`06-deploiement-continu.md`](./06-deploiement-continu.md).
