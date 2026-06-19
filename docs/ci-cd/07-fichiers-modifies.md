# Fichiers créés/modifiés

> Récapitulatif des fichiers créés ou modifiés pour le pipeline CI/CD.

---

| Fichier | Action | Commit |
|---|---|---|
| `backend/pom.xml` | Ajout `spring-boot-starter-test` + upgrade Spring Boot 3.2.0 → 3.5.15 | `9f84d20`, `a881045` |
| `backend/src/test/java/.../JwtServiceTest.java` | Création + correction secret aléatoire | `9f84d20` + correction |
| `backend/src/test/java/.../UrlValidatorTest.java` | Création | `9f84d20` |
| `backend/src/test/java/.../AuthControllerTest.java` | Création | `9f84d20` |
| `frontend/package.json` | Ajout Vitest + @vue/test-utils + jsdom | `9f84d20` |
| `frontend/vitest.config.js` | Création | `9f84d20` |
| `frontend/src/test/basic.test.js` | Création | `9f84d20` |
| `.github/workflows/ci.yml` | Création + corrections successives (Semgrep CLI, Buildx, Trivy v0.36.0, scanners vuln, ignore-unfixed, Node 22, actions:write, job deploy SSH Phase 4) | `ca05741` + corrections |
| `backend/Dockerfile` | Ajout `apk update && apk upgrade` (fix CVE OpenSSL Alpine) | `a881045` |
| `frontend/Dockerfile` | Node 20 → 22 + `apk update && apk upgrade` | `a881045` |
| `docs/ci-cd/01-infrastructure-vps.md` | Création (scission de `01-pipeline-ci.md`) | ce commit |
| `docs/ci-cd/02-pipeline-ci.md` | Création (scission de `01-pipeline-ci.md`) | ce commit |
| `docs/ci-cd/03-tests-automatises.md` | Création (scission de `01-pipeline-ci.md`) | ce commit |
| `docs/ci-cd/04-diagramme-deploiement.md` | Création (scission de `01-pipeline-ci.md`) | ce commit |
| `docs/ci-cd/05-corrections-trivy.md` | Création (scission de `01-pipeline-ci.md`) | ce commit |
| `docs/ci-cd/06-deploiement-continu.md` | Création (scission de `01-pipeline-ci.md`) | ce commit |
| `docs/ci-cd/07-fichiers-modifies.md` | Création (scission de `01-pipeline-ci.md`) | ce commit |
| `docs/ci-cd/diagramme-deploiement.drawio` | Création — diagramme UML de déploiement (diagrams.net) | à commiter |
| `docs/ci-cd/diagramme-deploiement.drawio.png` | Export PNG du diagramme UML (livrable visuel) | à commiter |
| `docker-compose.staging.yml` | Création (Phase 1) + correction healthcheck `/actuator/health` (Phase 4) + volume mount `nginx.staging.conf` | `3f0c687`, `50fd7bd`, `bf3b51c` |
| `nginx.staging.conf` | Création (Phase 4) : config nginx HTTP simple pour staging (sans redirection TLS) | `bf3b51c` |
| `docs/ci-cd/00-depart.md` | Mise à jour références vers nouveaux fichiers segmentés + corrections (Docker Compose v2.x, GHCR only, E2E non implémentés) | ce commit |
| `docs/ci-cd/01-pipeline-ci.md` | **Suppression** (scission en 7 fichiers) | ce commit |
| `README.md` | Mise à jour tableau `docs/ci-cd/` avec les 7 nouveaux fichiers | ce commit |
