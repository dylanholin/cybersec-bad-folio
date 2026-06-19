# DevFolio

Application portfolio étudiant : Spring Boot 3.5 + Vue 3 + MariaDB

> Projet pédagogique de sécurisation et de CI/CD. Les clés et identifiants présents dans le dépôt sont **fictifs** (exemples AWS, mots de passe de test). En production, tous les secrets doivent être externalisés et les credentials révoqués si compromis.

## Branches

| Branche | Description |
|---------|-------------|
| `main` | Version vulnérable originale, conservée pour la démonstration pédagogique |
| `correction` | Version sécurisée avec les corrections OWASP Top 10 2025 |
| `ci-cd-pipeline` | Pipeline de déploiement continu (Kit 2), dérivée de `correction` — contient tout le contenu de `correction` + le pipeline CI/CD |

## Documentation

La documentation est organisée en deux dossiers :

### `docs/securite/` — Sécurisation OWASP (Kit 1)

| Fichier | Contenu |
|---------|---------|
| `00-prise-en-main.md` | Prise en main du projet et de ses vulnérabilités |
| `01-audit-vulnerabilites.md` | Audit complet des vulnérabilités |
| `02-owasp-mapping.md` | Mapping OWASP Top 10 2025 |
| `03-plan-action.md` | Plan d'action correctif |
| `04-infrastructure.md` | Infrastructure et configuration |
| `05-installation-linux.md` | Installation sur Linux |
| `06-corriger-essentiel-demo.md` | Corrections essentielles pour la démo |
| `07-durcissement-serveur.md` | Durcissement du serveur |
| `08-deploiement-verification.md` | Déploiement et vérification |
| `09-resultat.md` | Résultats et bilan |

### `docs/ci-cd/` — Pipeline CI/CD (Kit 2)

| Fichier | Contenu |
|---------|---------|
| `00-depart.md` | Plan et architecture cible du pipeline |
| `01-pipeline-ci.md` | Implémentation détaillée (Phase 1 VPS + Phase 2 CI + Phase 3 tests + Phase 4 déploiement CD) |
| `diagramme-deploiement.drawio` | Diagramme UML de déploiement (diagrams.net) |
| `diagramme-deploiement.drawio.png` | Export PNG du diagramme UML |

## Démarrage

```bash
cp .env.example .env   # Éditer .env avec vos propres valeurs
# Important : en production, définir CORS_ALLOWED_ORIGINS=https://<votre-domaine>
docker-compose up --build
```

### Branche `correction` / `ci-cd-pipeline` (sécurisée)

- Frontend : https://localhost (HTTPS avec certificat auto-signé en dev)
- Backend API : https://localhost/api (via reverse proxy nginx)
- Backend API (debug) : http://localhost:8080/api (accès direct, dev uniquement)

### Branche `main` (vulnérable)

- Frontend : http://localhost
- Backend API : http://localhost:8080/api

## Tests validés (branche `correction`)

Les vérifications suivantes ont été exécutées avec succès sur le backend (accès direct `http://localhost:8080/api`, sans Docker) :

| Test | Résultat |
|------|----------|
| Injection SQL sur `/api/search/projects` | Bloquée (résultat vide) |
| JWT `alg:none` (token falsifié) | Rejeté (401) |
| Route admin sans token | 401 Unauthorized |
| Actuator `/env` sans rôle ADMIN | 401 |
| Actuator `/health` (public) | 200 OK |
| SSRF avatar avec URL interne (`169.254.169.254`) | 400 Bad Request |
| Rate limiting (5 tentatives/min sur login) | 429 Too Many Requests |
| Logout + token blacklisté | Déconnexion réussie |

Un script de vérification automatisée pour un déploiement Docker complet est documenté dans `docs/securite/08-deploiement-verification.md`.

## Tests automatisés (branche `ci-cd-pipeline`)

| Test | Framework | Couverture |
|------|-----------|------------|
| Backend — `JwtServiceTest` | JUnit 5 + Mockito | 4 tests (génération, validation, rejet alg:none, rejet secret différent) |
| Backend — `UrlValidatorTest` | JUnit 5 | 6 tests (HTTPS, whitelist, SSRF, URL malformée) |
| Backend — `AuthControllerTest` | JUnit 5 + Mockito | 7 tests (login, register, logout, rate limiting) |
| Frontend — `basic.test.js` | Vitest | 2 tests (sanity check) |

Pipeline CI : `.github/workflows/ci.yml` — voir `docs/ci-cd/01-pipeline-ci.md` pour le détail.

## Comptes de test

| Email | Mot de passe | Rôle |
|-------|-------------|------|
| admin@devfolio.com | DevfolioAdmin2024! | ADMIN |
| lilo@student.com | liloPass2024! | USER |
| dylan@student.com | dylanPass2024! | USER |
