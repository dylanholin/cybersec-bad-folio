# DevFolio

Application portfolio étudiant — Spring Boot 3.2 + Vue 3 + MariaDB

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
