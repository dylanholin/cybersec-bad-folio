---
name: owasp-folio-review
description: Auditer un diff git du projet DevFolio pour empêcher les régressions de sécurité. Cette skill est COMPLÉMENTAIRE à AGENTS.md (qui couvre déjà les règles OWASP de base : pas de concaténation SQL, pas de v-html, pas de permitAll(), parseClaimsJws(), pas de secret hardcodé, pas de log4j-core, UrlValidator, Docker isolé). Elle ajoute ce que AGENTS.md ne couvre pas : les régressions silencieuses, les corrections spécifiques de docs/securite/06-corriger-essentiel-demo.md, et une procédure d'audit structurée. Utiliser avant tout commit sur correction, ou quand l'utilisateur demande "revue sécurité", "audit OWASP", "est-ce que c'est safe", ou modifie SecurityConfig, JwtAuthenticationFilter, JwtService, AuthController, AuthService, application.properties, ProjectController, UserController, User.java, stores/auth.js, services/api.js, ProfileView.vue, nginx.staging.conf, ou init.sql.
allowed-tools:
  - read
  - grep
  - glob
  - exec
---

# OWASP Folio Review

AGENTS.md couvre déjà les règles OWASP non négociables de base. Cette skill ne les répète pas.
Elle ajoute trois choses qu'AGENTS.md ne contient pas : une procédure d'audit, les régressions
silencieuses faciles à casser, et les corrections spécifiques appliquées sur la branche `correction`.

## Procédure

1. **Cibler** : lance `git diff` (ou `git diff --staged`). Identifie les fichiers modifiés.
2. **Vérifier** : applique la checklist ci-dessous sur le diff.
3. **Rapporter** : utilise le format en fin de skill.

## Régressions silencieuses

Ces éléments se cassent sans erreur de compilation. Vérifie leur présence dans le diff,
même si l'utilisateur ne les mentionne pas — un refactor innocent peut les supprimer.

- **`FilterRegistrationBean(enabled=false)`** sur `JwtAuthenticationFilter` — sans cela,
  Spring Boot enregistre le filtre deux fois (auto-config servlet + Spring Security) →
  race condition sur `SecurityContext`. Semble inutile, ne l'est pas.
- **`sessionStorage`** dans `stores/auth.js` — remettre `localStorage` est une régression
  A07-03 (le token persiste après fermeture d'onglet). AGENTS.md mentionne sessionStorage
  dans la structure du projet mais pas comme règle non négociable.
- **`TokenBlacklistService`** + endpoint `/api/auth/logout` — invalidation serveur des
  tokens (A07-05). Facile à supprimer en pensant que c'est du code mort.
- **`@JsonIgnore`** sur `User.password` — réexpose les hashes de mot de passe en JSON
  (A02-01b). Facile à retirer en refactorant le model.
- **`BCryptPasswordEncoder`** bean dans `SecurityConfig` — si changé pour
  `NoOpPasswordEncoder` ou `MessageDigestPasswordEncoder` ("MD5"), régression critique
  (A02-01). AGENTS.md ne mentionne pas l'algorithme de hash.

## Corrections spécifiques non couvertes par AGENTS.md

Ces corrections sont documentées dans `docs/securite/06-corriger-essentiel-demo.md` mais
absentes d'AGENTS.md. Vérifie qu'elles ne sont pas régressées par le diff.

### Auth et design (A04)
- `RateLimitService` présent (5 tentatives/min/IP, fenêtre glissante, reset après login OK)
- Message unique "Identifiants incorrects" (pas d'énumération d'utilisateurs)
- Reset token jamais retourné dans l'URL ni dans la réponse
- Complexité MDP : 12 car min + majuscule + chiffre + caractère spécial

### Contrôle d'accès (A01)
- IDOR projets : `project.getOwnerId().equals(currentUserId)` avant PUT/DELETE
- Exclusion du champ `role` dans `UserController` (mass assignment sur le rôle)

### Configuration (A05)
- Actuator : `management.endpoints.web.exposure.include=health` uniquement
  (plus strict que le 403 d'AGENTS.md — désactive à la source, pas juste protégé)
- `include-stacktrace=never` (pas de stacktrace exposée)
- `show-sql=false` (pas de SQL loggé)
- DEBUG off en production (niveau WARN/INFO, pas `DEBUG=true` dans docker-compose)
- Rotation des logs : `max-file-size=10MB`, `max-history=7`
- CORS via `CORS_ALLOWED_ORIGINS` (env var), pas `*` — AGENTS.md interdit `*` mais
  ne documente pas le mécanisme de configuration

### Frontend (A07, A08)
- `services/api.js` : `baseURL` conditionnel (`/api` en prod via `import.meta.env.PROD`,
  `localhost:8080` en dev) — pas de `localhost:8080` hardcodé en production
- CDN Bootstrap avec `integrity` (SRI) + `crossorigin="anonymous"` dans `index.html`
- `nginx.staging.conf` : en-têtes CSP, X-Content-Type-Options, X-Frame-Options, HSTS,
  Referrer-Policy présents

### Infrastructure
- `init.sql` : privilèges BDD limités (pas root), comptes par défaut avec hashes BCrypt
  (pas mots de passe en clair ni en commentaire)
- Spring Boot parent à jour : **3.5.15** (corrige 33 CVE : Tomcat, Spring Security,
  Spring Framework, Logback). Ne pas rétrograder.

## Rapporter

```
## Revue sécurité : [fichier(s) audité(s)]

### Bloquants (modification à refuser)
- [fichier:ligne] [catégorie] [description] [correctif suggéré]

### Avertissements (à corriger mais non bloquants)
- [fichier:ligne] [catégorie] [description]

### Conforme
- [résumer ce qui a été vérifié et validé]
```

Si aucun bloquant : dis-le explicitement. L'absence de rapport = absence de vérification.

## Patterns grep pour le diff

```
FilterRegistrationBean    # si supprimé du diff = régression filtre JWT
localStorage              # dans stores/auth.js = régression A07-03
TokenBlacklistService     # si supprimé = régression invalidation tokens
@JsonIgnore               # si supprimé de User.password = régression A02-01b
NoOpPasswordEncoder       # régression BCrypt → mots de passe en clair
MessageDigestPasswordEncoder  # MD5 = régression A02-01
exposure\.include.*env    # actuator trop permissif
exposure\.include.*heapdump
include-stacktrace.*always  # stacktrace exposée
show-sql.*true            # SQL loggé
DEBUG.*true               # debug en production
import\.meta\.env\.PROD   # si supprimé de api.js = baseURL hardcodé
```