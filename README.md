# DevFolio

Application portfolio étudiant : Spring Boot 3.2 + Vue 3 + MariaDB

> Projet pédagogique de sécurisation. Les clés et identifiants présents dans le dépôt sont **fictifs** (exemples AWS, mots de passe de test). En production, tous les secrets doivent être externalisés et les credentials révoqués si compromis.

## Branches

| Branche | Description |
|---------|-------------|
| `main` | Version vulnérable originale, conservée pour la démonstration pédagogique |
| `correction` | Version sécurisée avec les corrections OWASP Top 10 2025 |

Les fichiers `README.md` et `docs/` sont synchronisés sur les deux branches.

## Démarrage

```bash
cp .env.example .env
docker-compose up --build
```

- Frontend : http://localhost:80
- Backend API : http://localhost:8080/api

## Comptes de test

| Email | Mot de passe | Rôle |
|-------|-------------|------|
| admin@devfolio.com | admin123 | ADMIN |
| lilo@student.com | liloPass2024! | USER |
| dylan@student.com | dylanPass2024! | USER |

## Documentation

- [Prise en main](docs/00-prise-en-main.md)
- [Audit des vulnérabilités](docs/01-audit-vulnerabilites.md)
- [Mapping OWASP Top 10 2025](docs/02-owasp-mapping.md)
- [Plan d'action](docs/03-plan-action.md)
- [Infrastructure](docs/04-infrastructure.md)
- [Installation du projet sur Linux](docs/05-installation-linux.md)
