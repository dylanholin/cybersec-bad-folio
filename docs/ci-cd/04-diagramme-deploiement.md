# Diagramme de déploiement

> Architecture visuelle du pipeline CI/CD et de l'infrastructure VPS.

---

## Livrables UML

- **Format modifiable** : [`diagramme-deploiement.drawio`](./diagramme-deploiement.drawio) : ouvrir avec https://app.diagrams.net
- **Image exportée** : [`diagramme-deploiement.drawio.png`](./diagramme-deploiement.drawio.png) : livrable visuel du diagramme UML

---

## Version Mermaid (rendu GitHub)

```mermaid
graph TB
    subgraph GHA["GitHub Actions (CI Runner)"]
        REPO["Dépôt Git<br/>(branche ci-cd-pipeline)"]
        TEST_BE["test-backend<br/>(JUnit 5 + Mockito)"]
        TEST_FE["test-frontend<br/>(Vitest + Vite build)"]
        SAST["scan-sast<br/>(Semgrep → SARIF)"]
        BUILD["build-and-push<br/>(Docker Buildx + Trivy)"]
    end

    subgraph GHCR["GHCR (Container Registry)"]
        IMG_BE["devfolio-backend:SHA"]
        IMG_FE["devfolio-frontend:SHA"]
    end

    subgraph VPS["VPS Debian (&lt;VPS_IP&gt;)"]
        NGINX["Nginx (Reverse Proxy hôte)<br/>:443 / :80, TLS auto-signé"]

        subgraph DOCKER["Docker Engine"]
            BE["backend<br/>(Spring Boot 3.5)<br/>:8080 → 127.0.0.1"]
            FE["frontend<br/>(Nginx + Vue 3)<br/>:3000 → 127.0.0.1"]
            DB[("MariaDB 10.11<br/>:3306 → 127.0.0.1<br/>Volume: db_data")]
            ENV[".env<br/>(secrets non versionnés)"]
            COMPOSE["docker-compose.staging.yml<br/>(images GHCR pré-construites)"]
        end
    end

    CLIENT["Client (Navigateur web)"]

    REPO --> TEST_BE
    REPO --> TEST_FE
    REPO --> SAST
    TEST_BE --> BUILD
    TEST_FE --> BUILD
    SAST --> BUILD
    BUILD -->|push images| IMG_BE
    BUILD -->|push images| IMG_FE
    BUILD -.->|SSH deploy Phase 4| VPS
    IMG_BE -->|docker pull| BE
    IMG_FE -->|docker pull| FE
    CLIENT -->|HTTPS| NGINX
    NGINX -->|proxy /api| BE
    NGINX -->|proxy /| FE
    BE -->|JPA / JDBC| DB
    FE -->|API REST| BE
    ENV -.->|env_file| BE
    COMPOSE -.->|orchestre| DOCKER
```

---

## Version ASCII (référence rapide)

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
│  fail2ban (sshd)  │  UFW (22/80/443)  │  Compte `deploy` (SSH)     │
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
