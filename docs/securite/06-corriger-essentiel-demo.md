# Itération 2, Jour 2 : Corriger l'essentiel avant la démo

## Contexte

Le CTO maintient la démonstration. Il faut corriger le plus dangereux avant la fin de la journée. L'objectif n'est pas la perfection, mais la réduction maximale des risques critiques avant déploiement.

Ce document constitue le livrable de la deuxième journée. Il s'appuie sur l'audit initial (Jour 1) et documente les corrections réalisées sur la branche `correction`.

---

## 0. Reprise du plan d'action et priorisation mise à jour

### Analyse de la veille

L'audit initial (Jour 1) a identifié **41 vulnérabilités** réparties sur les 10 catégories OWASP Top 10 2025, plus **10 problèmes d'infrastructure** (DEV-xx). Le code source sur la branche `main` contenait des marqueurs 🔴 signalant chaque problème.

### Priorisation mise à jour

**Principe : réduire le risque maximal avec le temps disponible.**

| Priorité | Catégorie | Exemples | Logique |
|----------|-----------|----------|---------|
| **P0   Bloquant** | Compromission totale sans effort | Injection SQL, JWT alg:none, permitAll, Log4Shell | Corriger en premier   risque de compromission immédiate |
| **P1   Haute** | Exploitation grave mais nécessite un contexte | SSRF, XSS, IDOR, élévation de privilèges, secrets exposés | Corriger si le temps le permet |
| **P2   Acceptable pour démo** | Risque limité ou exploitation difficile | Rate limiting, SRI, CSP, ddl-auto | Reporter après la démo |

### Dépendance clé identifiée

La chaîne JWT (filtre + service + SecurityConfig) est le **prérequis** de toutes les corrections d'accès. Sans `JwtAuthenticationFilter`, remplacer `permitAll()` par `authenticated()` bloquerait tout, y compris les utilisateurs légitimes. Il faut traiter cette chaîne en premier.

### Répartition des tâches dans l'équipe

| Membre | Tâches | Fichiers principaux |
|--------|--------|---------------------|
| **A** | Chaîne JWT complète (filtre + JwtService + SecurityConfig + BCrypt) | `JwtAuthenticationFilter.java`, `JwtService.java`, `SecurityConfig.java` |
| **B** | Injection SQL + SSRF + XSS + IDOR + élévation de privilèges | `SearchController.java`, `ProjectRepository.java`, `AvatarController.java`, `ProjectController.java`, `UserController.java`, `ProfileView.vue`, `UrlValidator.java` |
| **C** | Infrastructure (Docker, secrets, .env, nginx, actuator, init.sql) + documentation | `docker-compose.yml`, `Dockerfile`, `.gitignore`, `.env.example`, `application.properties`, `nginx.conf`, `init.sql`, `AuthController.java`, `AuthService.java` |

> **Coordination** : les tâches du membre C (AuthService, init.sql) dépendent du membre A (BCryptPasswordEncoder dans SecurityConfig). Livrer SecurityConfig en premier.

---

## 1. Vulnérabilités corrigées

### 1.1 Phase P0 Bloquants critiques (tous corrigés)

| # | Vulnérabilité | Réf OWASP | Correction appliquée | Fichier(s) |
|---|---------------|-----------|----------------------|------------|
| 1 | Absence de filtre JWT | A01-01b | Création de `JwtAuthenticationFilter` + `FilterRegistrationBean(enabled=false)` + enregistrement dans SecurityConfig | `config/JwtAuthenticationFilter.java`, `SecurityConfig.java` |
| 2 | JWT alg:none (fallback non signé) | A07-01 | Suppression du bloc `catch` fallback, de `parseUnsignedClaims`, des imports `ObjectMapper`/`DefaultClaims`/`Base64`. Uniquement `parseClaimsJws()` | `JwtService.java` |
| 3 | Injection SQL recherche | A03-01 | Remplacement par `@Query` paramétrée dans `ProjectRepository` + injection de `ProjectRepository` dans `SearchController` (suppression d'`EntityManager`) | `SearchController.java`, `ProjectRepository.java` |
| 4 | Log4Shell (log4j-core 2.14.1) | A06-01 | Suppression de la dépendance `log4j-core` du `pom.xml` (Spring Boot utilise Logback par défaut) | `pom.xml` |
| 5 | JWT sans expiration | A07-02 | Ajout de `.setExpiration()` + propriété `jwt.expiration` (1h par défaut) | `JwtService.java` |
| 6 | Contrôle d'accès inopérant | A01-01/03/05 | CORS restreint (localhost uniquement), `hasRole("ADMIN")` sur `/api/admin/**` et `/actuator/**`, `authenticated()` sur les autres routes, `exceptionHandling` JSON | `SecurityConfig.java` |

### 1.2 Phase P1 Haute priorité (tous corrigés)

| # | Vulnérabilité | Réf OWASP | Correction appliquée | Fichier(s) |
|---|---------------|-----------|----------------------|------------|
| 7 | SSRF import GitHub | A10-02 | Validation via `UrlValidator` (whitelist github.com, HTTPS uniquement, IP privées bloquées, limite 2 Mo) | `ProjectController.java`, `UrlValidator.java` |
| 8 | SSRF avatar URL | A10-01 | Validation via `UrlValidator` (mêmes règles + domaines images) | `AvatarController.java`, `UrlValidator.java` |
| 9 | Secrets hardcodés | A02-02/03/05 | `.env` ajouté au `.gitignore`, création de `.env.example`, `env_file: .env` dans docker-compose, suppression des fallbacks hardcodés dans `application.properties` | `.gitignore`, `.env.example`, `docker-compose.yml`, `application.properties` |
| 10 | MD5 sans sel → BCrypt | A02-01 | `BCryptPasswordEncoder` dans SecurityConfig, `passwordEncoder.encode()` dans AuthService, hashes BCrypt dans init.sql | `SecurityConfig.java`, `AuthService.java`, `init.sql` |
| 11 | Mots de passe dans JSON | A02-01b | `@JsonIgnore` sur le champ `password` de `User.java` | `User.java` |
| 12 | Élévation de privilèges (role dans PUT) | A01-04 | Exclusion de `role` de la mise à jour utilisateur, vérification `currentUserId == id` via `Authentication` | `UserController.java` |
| 13 | IDOR projets | A01-02 | Vérification `project.getOwnerId().equals(currentUserId)` avant PUT/DELETE | `ProjectController.java` |
| 14 | XSS stocké (v-html) | A03-02 | Remplacement de `v-html="user.bio"` par `{{ user.bio }}` avec `white-space: pre-line` | `ProfileView.vue` |
| 15 | CORS ouvert (*) | A01-05 | CORS configurable via `CORS_ALLOWED_ORIGINS` (env), fallback localhost, trim espaces | `SecurityConfig.java`, `application.properties`, `.env.example` |

### 1.3 Phase P2 Améliorations supplémentaires (corrigées)

| # | Vulnérabilité | Réf OWASP | Correction appliquée | Fichier(s) |
|---|---------------|-----------|----------------------|------------|
| 16 | Pas de rate limiting login | A04-01 | `RateLimitService` (5 tentatives/min/IP, fenêtre glissante) + réinitialisation après login réussi | `RateLimitService.java`, `AuthController.java` |
| 17 | Énumération utilisateurs | A04-02 | Message unique "Identifiants incorrects" au lieu de messages distincts | `AuthController.java` |
| 18 | Token reset dans URL | A04-03 | Ne retourne plus le token dans la réponse, message générique | `AuthController.java` |
| 19 | Validation complexité MDP | A04-04 | 12 car. min + majuscule + chiffre + caractère spécial | `AuthService.java` |
| 20 | Mots de passe loggés en clair | A09-01 | Paramètres de log `{}` au lieu de concaténation, plus de log du MDP | `AuthController.java`, `AuthService.java` |
| 21 | Échecs connexion non loggés | A09-02 | `log.warn("Failed login attempt for: {}", username)` | `AuthController.java` |
| 22 | Log injection | A03-04 | `log.info("Login attempt for user: {}", username)` au lieu de concaténation | `AuthController.java` |
| 23 | JWT dans localStorage | A07-03 | Passage à `sessionStorage` (détruit à la fermeture de l'onglet) | `stores/auth.js` |
| 23b | `api.js` hardcodé `localhost:8080` | A07-03b | `baseURL` conditionné par `import.meta.env.PROD` (`/api` en prod, `localhost:8080` en dev) | `services/api.js` |
| 24 | Invalidation serveur tokens | A07-05 | `TokenBlacklistService` (blacklist en mémoire avec nettoyage auto) + endpoint `POST /api/auth/logout` | `TokenBlacklistService.java`, `AuthController.java`, `JwtAuthenticationFilter.java` |
| 25 | Protection admin côté client uniquement | A01-06 | Décode le JWT au lieu de lire localStorage + protection serveur `hasRole("ADMIN")` | `router/index.js`, `SecurityConfig.java` |
| 26 | CDN sans SRI | A08-01 | Attributs `integrity` + `crossorigin="anonymous"` sur les balises Bootstrap | `index.html` |
| 27 | Pas de CSP | A05-05 | En-têtes de sécurité dans nginx : CSP, X-Content-Type-Options, X-Frame-Options, HSTS, Referrer-Policy | `nginx.conf` |
| 28 | HTTP uniquement | A02-04 | HTTPS avec certificat auto-signé en dev + HSTS + redirection HTTP→HTTPS | `nginx.conf`, `frontend/Dockerfile` |
| 29 | MDP en commentaire SQL | A02-06 | Suppression des commentaires contenant des mots de passe en clair | `init.sql` |
| 30 | Actuator sans protection | A05-01/02 | `include=health` uniquement, env/heapdump/shutdown désactivés, `hasRole("ADMIN")` sur `/actuator/**` | `application.properties`, `SecurityConfig.java` |
| 31 | Stacktraces exposées | DEV-11 | `include-stacktrace=never` | `application.properties` |
| 32 | SQL loggé | DEV-10 | `show-sql=false` | `application.properties` |
| 33 | DEBUG en production | A05-06 | Niveau `WARN`/`INFO`, suppression de `DEBUG=true` dans docker-compose | `application.properties`, `docker-compose.yml` |
| 34 | Pas de rotation des logs | A09-04 | `rollingpolicy.max-file-size=10MB`, `max-history=7` | `application.properties` |
| 35 | ddl-auto=update | DEV-12 | `ddl-auto=validate` | `application.properties` |
| 36 | Admin MDP trivial | A07-04 | Mot de passe fort (≥ 12 car.) + hash BCrypt | `init.sql` |
| 37 | Axios vulnérable (CVE-2021-3749) | A06-02 | Mise à jour vers `axios: ^1.7.0` | `frontend/package.json` |

### 1.4 Corrections d'infrastructure (toutes corrigées)

| # | Problème | Réf | Correction | Fichier(s) |
|---|----------|-----|------------|------------|
| 38 | MariaDB sur 0.0.0.0 | DEV-03 | `127.0.0.1:3306` | `docker-compose.yml` |
| 39 | Root BDD sans compte applicatif | DEV-04 | Utilisateur `devfolio_app` avec SELECT/INSERT/UPDATE/DELETE uniquement | `init.sql` |
| 40 | Pas de réseau Docker isolé | DEV-05 | Réseaux `frontend-backend` + `backend-db` | `docker-compose.yml` |
| 41 | Bind mount BDD | DEV-06 | Volume nommé `db_data` | `docker-compose.yml` |
| 42 | Secrets en dur docker-compose | DEV-07 | `env_file: .env` | `docker-compose.yml` |
| 43 | Image complète + root + debug JVM | DEV-07/08 | `eclipse-temurin:21-jre-alpine`, `USER appuser`, suppression `-agentlib` et `EXPOSE 5005` | `backend/Dockerfile` |
| 44 | Pas de .dockerignore | DEV-09 | Création de `backend/.dockerignore` | `backend/.dockerignore` |
| 45 | Pas de healthcheck MariaDB |   | Healthcheck + `condition: service_healthy` | `docker-compose.yml` |
| 46 | Pas d'en-têtes sécurité nginx |   | X-Content-Type-Options, X-Frame-Options, CSP, HSTS, Referrer-Policy | `nginx.conf` |

### 1.5 Justification acceptée

| Réf | Problème | Décision | Justification |
|-----|----------|----------|---------------|
| A05-03 | CSRF désactivé | **Maintenu** | API stateless + JWT en header Authorization (pas de cookie de session). CSRF n'est pas applicable dans ce contexte. |

---

## 2. Changements importants réalisés

### Chaîne JWT (prérequis de toutes les corrections d'accès)

**Avant** : Le projet générait des tokens JWT mais n'avait aucun filtre Spring Security pour les valider. Toutes les routes étaient `permitAll()`, y compris `/api/admin/**` et `/actuator/**`. Le `JwtService` acceptait les tokens non signés (`alg:none`) via un fallback et n'avait pas d'expiration.

**Après** :
- `JwtAuthenticationFilter` extrait le token du header `Authorization: Bearer`, le valide via `JwtService.validateToken()` (uniquement `parseClaimsJws()`), vérifie la blacklist, et peuple le `SecurityContext`
- `FilterRegistrationBean(enabled=false)` empêche le double-enregistrement servlet
- `JwtService` : fallback supprimé, expiration ajoutée (1h par défaut), secret sans fallback hardcodé
- `SecurityConfig` : CORS restreint, routes protégées par rôle, `exceptionHandling` retourne du JSON

### Hachage des mots de passe

**Avant** : MD5 sans sel (via `MessageDigest` + `HexFormat`). Les hashes MD5 sont dans init.sql. Le fallback en cas d'erreur retourne le mot de passe en clair.

**Après** : `BCryptPasswordEncoder` (cost factor 12 par défaut). Les comptes seed dans init.sql utilisent des hashes BCrypt. `AuthService.register()` utilise `passwordEncoder.encode()`.

### Infrastructure Docker

**Avant** : Réseau bridge par défaut (pas d'isolation), MariaDB sur `0.0.0.0:3306`, bind mount `/var/lib/mysql`, conteneur backend en root avec debug JVM, secrets en dur dans docker-compose.

**Après** : Réseaux isolés `frontend-backend` + `backend-db`, MariaDB sur `127.0.0.1:3306`, volume nommé `db_data`, `eclipse-temurin:21-jre-alpine` avec `USER appuser`, `env_file: .env`, healthcheck MariaDB.

---

## 3. Vulnérabilités restantes

> **Mise à jour itération 3** : les trois risques de criticité BASSE ont été corrigés (cf. [07-durcissement-serveur.md](07-durcissement-serveur.md) et [08-deploiement-verification.md](08-deploiement-verification.md)). Seuls restent les risques informationnels, non bloquants pour une démo temporaire.

| Réf | Problème | Criticité | Détail | Statut |
|-----|----------|-----------|--------|--------|
| A07-05b | `TokenBlacklistService` utilise `hashCode()` au lieu de SHA-256 | ~~BASSE~~ | ~~Collisions possibles entre tokens différents~~ | **Corrigé** : `hashToken()` utilise désormais `MessageDigest.getInstance("SHA-256")` |
|   | Port backend 8080 exposé sur `0.0.0.0` | ~~BASSE~~ | ~~Backend accessible sans passer par nginx~~ | **Corrigé** : bindé sur `127.0.0.1:8080` dans `docker-compose.yml` |
|   | Frontend nginx tourne en root | ~~BASSE~~ | ~~Image `nginx:alpine` root par défaut~~ | **Corrigé** : `USER nginx` + installation `openssl` (`apk add --no-cache openssl`) + préparation des répertoires cache/log (`mkdir` + `chown -R nginx:nginx /var/cache/nginx /var/log/nginx /run`) avant le switch d'utilisateur |
|   | Certificat HTTPS auto-signé | INFO | Généré dans le Dockerfile frontend. Le navigateur affiche un avertissement. | Utiliser Let's Encrypt en production |
|   | Rate limiting en mémoire | INFO | `RateLimitService` ne fonctionne pas en cluster (non distribué). | Utiliser Redis ou Bucket4j en production |
|   | Token blacklist en mémoire | INFO | `TokenBlacklistService` ne fonctionne pas en cluster. | Utiliser Redis avec TTL en production |

### Nouvelles vulnérabilités identifiées lors de la revue de code post-itération 3

Ces éléments ont été découverts lors d'une revue de code complémentaire et n'étaient pas présents dans l'audit initial (Jour 1). Ils seront corrigés individuellement (un commit par vulnérabilité) sur la branche `correction`.

| Réf | Problème | Criticité | Détail | Statut |
|-----|----------|-----------|--------|--------|
| NEW-01 | Mot de passe `devfolio_app` en dur dans `init.sql` | ~~BASSE~~ | ~~`'DevfolioApp2024!'` est hardcodé dans le script SQL commité~~ | **Corrigé** : `init.sql` remplacé par `init-template.sql` + `init.sh`. Le mot de passe applicatif est injecté via `${DB_PASSWORD}` au premier demarrage MariaDB. Le fichier genere est supprime immediatement apres execution. |
| NEW-02 | DNS rebinding possible sur `UrlValidator` | ~~BASSE~~ | ~~La résolution DNS (`InetAddress.getByName`) et la requête HTTP (`openStream`) ne sont pas atomiques. Un attaquant peut faire pointer un domaine autorisé vers une IP privée entre les deux opérations.~~ | **Corrigé** : méthode `UrlValidator.fetchContent()` qui résout le DNS une seule fois et se connecte vers l'IP résolue. SNI + header Host préservés pour TLS, IPv6 gérée (`[...]`). L'ancienne méthode `validate()` (vulnérable) a été **supprimée** pour empêcher toute régression. Validation factorisée dans `parseAndCheckScheme()` + `resolveAndCheckIp()` (DRY). `AvatarController` et `ProjectController` utilisent `fetchContent()`. |
| NEW-03 | Mass assignment partiel sur `ProjectController.updateProject()` | ~~BASSE~~ | ~~`@RequestBody Project` permet de modifier `isPublic` (visibilité) sans validation métier. Le DTO `UserUpdateRequest` existe côté `UserController` mais pas pour les projets.~~ | **Corrigé** : `ProjectCreateRequest` et `ProjectUpdateRequest` créés (sans `id`, sans `ownerId`). Le serveur construit manuellement l'entity `Project` à partir des DTOs. `ownerId` est contrôlé exclusivement par `authentication.getDetails()`. `isPublic` modifiable uniquement via le DTO autorisé. |
| NEW-04 | ~~Pas de validation du format email côté serveur~~ | ~~INFO~~ | ~~`AuthService.register()` ne vérifie pas que l'email est un format RFC 5322 valide.~~ | **Corrigé** : validation regex dans `AuthService.register()` + try-catch 400 dans `AuthController.register()`. |
| NEW-05 | ~~Fallback `${DB_PASSWORD:}` (chaîne vide)~~ | ~~INFO~~ | ~~`spring.datasource.password=${DB_PASSWORD:}` possède un fallback vide. Bien que cela provoque un échec de connexion bruyant, un fallback sur un secret est une mauvaise pratique.~~ | **Corrigé** : fallback supprimé, `spring.datasource.password=${DB_PASSWORD}` (sans valeur par défaut). |
| NEW-06 | `MYSQL_ROOT_PASSWORD` = `DB_PASSWORD` | ~~INFO~~ | ~~Dans `docker-compose.yml`, le mot de passe root MariaDB est identique au mot de passe du compte applicatif.~~ | **Corrigé** : `DB_ROOT_PASSWORD` séparé de `DB_PASSWORD` dans `.env.example`, `docker-compose.yml`, `docker-compose.staging.yml` et `deploy.sh`. Le mot de passe root est généré indépendamment du mot de passe applicatif. |

> **Évaluation** : en plus des risques informationnels précédents (non distribuabilité en cluster, certificat auto-signé), ces 6 nouvelles vulnérabilités ont été identifiées. Aucune n'est de criticité critique ou haute. Elles ne représentent pas un risque bloquant pour une démonstration temporaire mais devront être traitées avant un déploiement en production.

---

## 4. Justification des priorités choisies

### Pourquoi la chaîne JWT en premier ?

Sans `JwtAuthenticationFilter`, remplacer `permitAll()` par `authenticated()` dans `SecurityConfig` bloquerait **toutes** les requêtes authentifiées   le filtre Spring Security ne saurait jamais qui est connecté. C'est le prérequis technique de toutes les corrections d'accès (VULN-06, VULN-12, VULN-13).

### Pourquoi l'injection SQL avant les SSRF ?

L'injection SQL permet la **destruction de données** en un seul GET request (`DROP TABLE`). Les SSRF nécessitent un attaquant actif et ne détruisent pas directement les données. L'effort de correction est similaire, mais l'impact de l'injection SQL est supérieur.

### Pourquoi Log4Shell est-il classé P0 alors que Spring Boot utilise Logback ?

Même si Spring Boot utilise Logback par défaut, la présence de `log4j-core 2.14.1` dans le classpath crée un vecteur d'attaque RCE si une bibliothèque tierce ou une configuration route des logs vers Log4j. La correction est triviale (supprimer 5 lignes de pom.xml)   le rapport effort/impact est maximal.

### Pourquoi les XSS/IDOR/élévation de privilèges sont-ils en P1 ?

Ces vulnérabilités nécessitent un utilisateur authentifié et une action délibérée. Si le contrôle d'accès (P0-6) est en place, la surface d'attaque est déjà réduite. Leur correction est importante mais pas bloquante pour la démo.

### Pourquoi le rate limiting, la CSP et le SRI sont-ils en P2 ?

Ce sont des défenses en profondeur. Le rate limiting ne protège que contre le brute force (les MDP sont désormais forts avec BCrypt). La CSP et le SRI ajoutent des couches de protection supplémentaires mais le XSS est déjà corrigé à la source (`{{ }}` au lieu de `v-html`).

---

## 5. Checklist de préparation au déploiement

### Services nécessaires

- [x] Frontend (nginx:alpine)   ports 80/443
- [x] Backend (eclipse-temurin:21-jre-alpine)   port 8080
- [x] MariaDB 10.11   pas de port exposé hors Docker

### Ports à ouvrir

| Port | Service | Exposition | Statut |
|------|---------|------------|--------|
| 80 | HTTP → redirection HTTPS | Public | [x] Configuré |
| 443 | HTTPS | Public (seul port public) | [x] Configuré |
| 3306 | MariaDB | **Aucun** accès extérieur | [x] 127.0.0.1 uniquement |
| 5005 | Debug JVM | **Aucun** | [x] Supprimé |
| 8080 | Backend | Via nginx reverse proxy | [x] Restreint à `127.0.0.1` |

### Dépendances requises

- [x] Docker + Docker Compose sur le serveur
- [x] Certificat TLS (auto-signé pour démo, Let's Encrypt en production)
- [x] `.env` avec secrets forts (JWT_SECRET ≥ 48 chars base64)

### Utilisateurs et permissions

- [x] Compte `devfolio_app` MariaDB (SELECT, INSERT, UPDATE, DELETE uniquement)
- [x] `USER appuser` dans le conteneur backend (pas root)
- [x] Mot de passe admin changé (pas `admin123`)

### Secrets et variables de configuration

- [x] `.env` présent avec valeurs fortes
- [x] `.env` exclu du dépôt git (`.gitignore`)
- [x] `JWT_SECRET` : ≥ 48 caractères, généré avec `openssl rand -base64 48`
- [x] `JWT_EXPIRATION` : 3600000 (1h)
- [x] Pas de fallback hardcodé dans `application.properties`
- [x] `docker-compose.yml` utilise `env_file: .env` (pas de secrets en dur)

### Éléments à NE PAS exposer

- [x] Port MariaDB (3306)   restreint à 127.0.0.1
- [x] Port debug JVM (5005)   supprimé
- [x] Endpoints actuator dangereux   désactivés + protégés par rôle ADMIN
- [x] Fichier `.env`   dans `.gitignore`
- [x] Stacktraces dans les réponses d'erreur   `include-stacktrace=never`
- [x] Hashes de mots de passe dans les réponses JSON   `@JsonIgnore`

### Vérifications de sécurité post-installation

| Test | Commande | Résultat attendu |
|------|----------|-----------------|
| Injection SQL | `GET /api/search/projects?q=' OR '1'='1` | Résultat vide (recherche littérale) |
| Admin sans token | `GET /api/admin/users` | 401 Unauthorized |
| Admin avec token USER | `GET /api/admin/users` (token USER) | 403 Forbidden |
| Actuator env | `GET /actuator/env` | 401 ou 403 |
| SSRF avatar | `POST /api/users/avatar?url=http://169.254.169.254/` | 400 "Domaine non autorisé" |
| XSS bio | Saisir `<img src=x onerror=alert(1)>` dans la bio | Affiché comme texte brut |
| Login | `POST /api/auth/login` avec identifiants valides | Token JWT + user |
| Token expiré | Requête avec token expiré | 401 Unauthorized |
| Rate limiting | 6+ requêtes login en < 1 min | 429 Too Many Requests |
| Logout | `POST /api/auth/logout` avec token | Token blacklisté, requêtes suivantes → 401 |
| Conteneur backend | `docker exec backend whoami` | `appuser` |
| HTTPS | Accès HTTP | Redirection 301 vers HTTPS |

### Anticipation des erreurs de configuration

| Erreur possible | Symptôme | Solution |
|----------------|----------|----------|
| `JWT_SECRET` < 32 octets | `InvalidKeyException` au démarrage | Régénérer avec `openssl rand -base64 48` |
| `.env` non chargé | `Could not resolve placeholder 'JWT_SECRET'` | Charger les variables : `export $(grep -v '^#' .env \| xargs)` |
| Filtre JWT enregistré deux fois | 403 sur POST anonymes (login) | Vérifier `FilterRegistrationBean(enabled=false)` |
| init.sql chargé deux fois | Doublons BDD | Nettoyer : `DELETE FROM users WHERE id > 3` |
| CORS trop restrictif / non configuré pour l'IP publique | Frontend retourne 403 sur login (OK en curl local) | Définir `CORS_ALLOWED_ORIGINS=https://<IP>` dans `.env` |
| Certificat non trusted | Avertissement navigateur | Normal en dev (auto-signé) ; Let's Encrypt en prod |
| `api.js` avec `baseURL` hardcodé | Requêtes vers `localhost:8080` bloquées par CSP/CORS | Vérifier `import.meta.env.PROD ? '/api' : 'http://localhost:8080/api'` |
| `ddl-auto=validate` + schéma modifié | Erreur au démarrage | Utiliser Flyway/Liquibase pour les migrations |

---

## 6. Synthèse de l'état actuel de sécurité

### Avant correction (branche `main`)

L'application est dans un état **critique** (risque 10/10). Un attaquant non authentifié peut :
- Lire, modifier ou supprimer toute la base de données via injection SQL
- Forger un token admin via `alg:none` et accéder à toutes les fonctions d'administration
- Scanner le réseau interne et accéder aux métadonnées cloud via SSRF
- Exécuter du code arbitraire à distance via Log4Shell
- Voir tous les secrets (JWT, BDD, Gmail, AWS) dans le dépôt git
- Lire les hashes de mots de passe de tous les utilisateurs via l'API admin non protégée
- Injecter du JavaScript dans les profils utilisateurs (XSS stocké)

### Après correction (branche `correction`)

L'application est dans un état **acceptable pour une démonstration temporaire** (risque 2/10) :
- Injection SQL éliminée (requêtes paramétrées JPA)
- Authentification JWT fonctionnelle avec vérification de signature stricte et expiration
- Contrôle d'accès opérationnel (admin protégé par rôle, actuator restreint)
- Log4Shell éliminé (log4j-core supprimé)
- SSRF bloqué (whitelist de domaines + HTTPS + IP privées bloquées)
- XSS éliminé (interpolation Vue au lieu de v-html)
- IDOR et élévation de privilèges corrigés (vérification d'identité côté serveur)
- Mots de passe hachés avec BCrypt (plus de MD5)
- Infrastructure Docker durcie (réseaux isolés, utilisateur non-root, pas de debug)
- Secrets externalisés (.env + .gitignore)
- HTTPS + en-têtes de sécurité nginx
- Rate limiting + invalidation serveur des tokens

Les risques résiduels (basse criticité) sont liés à des limitations d'architecture en mémoire (rate limiting, blacklist) et au certificat auto-signé. Ils ne sont pas bloquants pour une démo et seront traités avant la production.

---

## 7. Automatisation des vérifications de sécurité

### Script de vérification post déploiement

Le script suivant automatise les principaux tests de sécurité définis dans la checklist (§5). Il peut être exécuté sur le serveur de démonstration après le déploiement pour valider rapidement l'état de sécurité.

```bash
#!/bin/bash
# verify_security.sh : vérifications automatisées post déploiement
# Usage : ./verify_security.sh [BASE_URL]
# Exemple : ./verify_security.sh https://localhost

BASE="${1:-https://localhost}"
PASS=0
FAIL=0

check() {
    local name="$1"
    local actual="$2"
    local expected="$3"
    if echo "$actual" | grep -q "$expected"; then
        echo "  ✅ $name"
        PASS=$((PASS + 1))
    else
        echo "  ❌ $name (obtenu : $actual, attendu : $expected)"
        FAIL=$((FAIL + 1))
    fi
}

echo "=== Vérifications de sécurité DevFolio ==="
echo "Cible : $BASE"
echo ""

# 0. Obtenir un token JWT pour les tests authentifiés
echo "  Connexion admin..."
LOGIN_RESP=$(curl -sk -X POST "$BASE/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"email":"admin@devfolio.com","password":"<ADMIN_PASSWORD>"}')
TOKEN=$(echo "$LOGIN_RESP" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
if [ -z "$TOKEN" ]; then
    echo "  ❌ Impossible de se connecter (vérifier les credentials)"
    echo "  Réponse : $LOGIN_RESP"
    exit 1
fi
echo "  ✅ Token obtenu"
echo ""

# 1. Injection SQL (la recherche littérale de "' OR '1'='1" doit retourner un résultat vide)
RESP=$(curl -sk "$BASE/api/search/projects?q=' OR '1'='1")
if echo "$RESP" | grep -q '"id"'; then
    echo "  ❌ Injection SQL (des projets retournés, injection possible)"
    FAIL=$((FAIL + 1))
else
    echo "  ✅ Injection SQL (résultat vide, injection bloquée)"
    PASS=$((PASS + 1))
fi

# 2. JWT alg:none (token falsifié avec algorithme "none" doit être rejeté)
FAKE_TOKEN="eyJhbGciOiJub25lIn0.eyJzdWIiOiJhZG1pbkBkZXZmb2xpby5jb20iLCJyb2xlIjoiQURNSU4iLCJ1c2VySWQiOjF9."
RESP=$(curl -sk -o /dev/null -w "%{http_code}" "$BASE/api/admin/users" \
    -H "Authorization: Bearer $FAKE_TOKEN")
check "JWT alg:none (401)" "$RESP" "401"

# 3. Admin sans token
RESP=$(curl -sk -o /dev/null -w "%{http_code}" "$BASE/api/admin/users")
check "Admin sans token (401)" "$RESP" "401"

# 4. Actuator env
RESP=$(curl -sk -o /dev/null -w "%{http_code}" "$BASE/actuator/env")
check "Actuator env (401/403)" "$RESP" "40[13]"

# 5. Actuator health (public)
RESP=$(curl -sk -o /dev/null -w "%{http_code}" "$BASE/actuator/health")
check "Actuator health (200)" "$RESP" "200"

# 6. SSRF avatar (route authentifiée, nécessite le token)
RESP=$(curl -sk -o /dev/null -w "%{http_code}" -X POST "$BASE/api/users/avatar" \
    -H "Authorization: Bearer $TOKEN" \
    -d "url=http://169.254.169.254/")
check "SSRF avatar (400)" "$RESP" "400"

# 7. HTTPS actif
RESP=$(curl -sk -o /dev/null -w "%{http_code}" "$BASE/")
check "HTTPS actif (200)" "$RESP" "200"

# 8. Redirection HTTP vers HTTPS
RESP=$(curl -s -o /dev/null -w "%{http_code}" http://localhost/)
check "HTTP vers HTTPS (301)" "$RESP" "301"

# 9. Conteneur backend non root
USER=$(docker exec backend whoami 2>/dev/null || echo "N/A")
check "Conteneur backend (appuser)" "$USER" "appuser"

# 10. MariaDB non exposé (le port 3306 ne doit pas répondre depuis l'extérieur)
if nc -z localhost 3306 2>/dev/null; then
    echo "  ❌ MariaDB exposé (port 3306 accessible)"
    FAIL=$((FAIL + 1))
else
    echo "  ✅ MariaDB non exposé (port 3306 inaccessible)"
    PASS=$((PASS + 1))
fi

echo ""
echo "=== Résultat : $PASS passé(s), $FAIL échoué(s) ==="
exit $FAIL
```

### Utilisation

1. Copier le script ci dessus dans un fichier `verify_security.sh` à la racine du projet
2. Rendre le script exécutable et le lancer :

```bash
chmod +x verify_security.sh
./verify_security.sh https://localhost
```

> **Prérequis** : `curl`, `nc` (netcat), `docker` (pour les tests 9 et 10). Ce script est conçu pour Linux/macOS (bash). Sur Windows, utiliser WSL ou Git Bash, ou adapter les commandes en PowerShell.
>
> **Note** : ce script utilise `curl -k` (insecure) car le certificat HTTPS est auto signé en développement. En production avec un certificat valide, retirer l'option `-k`.

### Intégration CI/CD

Pour une intégration dans un pipeline CI/CD (GitHub Actions, GitLab CI), le script peut être appelé après le déploiement :

```yaml
# Exemple GitHub Actions
security_verify:
  stage: verify
  script:
    - chmod +x verify_security.sh
    - ./verify_security.sh https://staging.devfolio.local
  allow_failure: false
```

---

## 8. Notes détaillées sur deux vulnérabilités corrigées

### Note A : VULN-04 JWT alg:none (A07-01)

**Description du problème :**

Le `JwtService.validateToken()` tentait d'abord un parsing signé avec `parseClaimsJws()`. En cas d'échec, le bloc `catch` vérifiait si le token avait 2 parties ou 3 parties avec signature vide, puis décodait le payload en Base64 et le parseait en Claims **sans aucune vérification de signature** via la méthode `parseUnsignedClaims()`. Cela permettait l'attaque `alg:none` : un attaquant crée un token avec le header `{"alg":"none"}` et un payload contenant `"role":"ADMIN"`, et le serveur l'accepte comme valide.

**Code vulnérable :**
```java
} catch (Exception e) {
    // 🔴 : fallback qui parse sans vérifier la signature
    String[] parts = token.split("\\.");
    if (parts.length == 2 || (parts.length == 3 && parts[2].isEmpty())) {
        try {
            String payload = new String(Base64.getDecoder().decode(parts[1]));
            return parseUnsignedClaims(payload);
        } catch (Exception ex) {
            throw e;
        }
    }
    throw e;
}
```

**Justification de la criticité :** 🔴 CRITIQUE
- Exploitation immédiate : un simple encodage Base64 suffit, aucun outil spécialisé requis
- Permet l'usurpation d'identité de n'importe quel utilisateur, y compris admin
- Permet l'élévation de privilèges (n'importe qui peut devenir ADMIN)
- Aucune trace dans les logs (le token est "valide" du point de vue du serveur)

**Méthode de correction :**
1. Suppression intégrale du bloc `catch` contenant le fallback `parseUnsignedClaims`
2. Suppression de la méthode `parseUnsignedClaims` et des imports associés (`ObjectMapper`, `DefaultClaims`, `Base64`)
3. Conservation exclusive de `parseClaimsJws()` qui vérifie obligatoirement la signature
4. L'exception se propage et est interceptée par le `JwtAuthenticationFilter` qui nettoie le `SecurityContext`

**Code corrigé :**
```java
public Claims validateToken(String token) {
    return Jwts.parserBuilder()
            .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
            .build()
            .parseClaimsJws(token)
            .getBody();
}
```

**Démonstration du fonctionnement après correction :**
- **Avant** : `curl -H "Authorization: Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiJhZG1pbkBkZXZmb2xpby5jb20iLCJyb2xlIjoiQURNSU4iLCJ1c2VySWQiOjF9." http://localhost:8080/api/admin/users` → **200 OK** (liste des utilisateurs avec hashes)
- **Après** : même requête → **401 Unauthorized** (token rejeté car signature invalide)
- **Après** : login normal avec `admin@devfolio.com` → token signé généré → `curl -H "Authorization: Bearer <token_signé>" http://localhost:8080/api/admin/users` → **200 OK** (accès légitime)

---

### Note B : VULN-01 Injection SQL dans la recherche (A03-01)

**Description du problème :**

Le `SearchController.searchProjects()` construisait une requête SQL par concaténation directe du paramètre utilisateur `q` dans une chaîne SQL native exécutée via `EntityManager.createNativeQuery()`. Il n'y avait aucun échappement, aucune requête paramétrée, aucune validation.

**Code vulnérable :**
```java
String sql = "SELECT * FROM projects WHERE title LIKE '%" + q + "%' " +
             "OR description LIKE '%" + q + "%'";
List<Project> results = entityManager.createNativeQuery(sql, Project.class).getResultList();
```

**Justification de la criticité :** 🔴 CRITIQUE
- Exploitation triviale : `?q=' OR '1'='1` retourne toutes les données
- Impact maximal : lecture de toutes les tables (users avec hashes MDP), modification de données (`UPDATE`), suppression de tables (`DROP TABLE`), voire exécution de commandes système selon les droits MariaDB
- Aucune authentification requise (endpoint GET public)
- L'attaque peut être invisible dans les logs (URL encodée)

**Méthode de correction :**
1. Suppression de l'injection de `EntityManager` dans `SearchController`
2. Injection de `ProjectRepository` à la place
3. Ajout d'une méthode `@Query` paramétrée dans `ProjectRepository` :

```java
@Query("SELECT p FROM Project p WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
       "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%'))")
List<Project> search(@Param("q") String q);
```

4. Le paramètre `:q` est automatiquement échappé par JPA/Hibernate   impossible d'injecter du SQL

**Code corrigé (SearchController) :**
```java
@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Autowired
    private ProjectRepository projectRepository;

    @GetMapping("/projects")
    public ResponseEntity<?> searchProjects(@RequestParam String q) {
        List<Project> results = projectRepository.search(q);
        return ResponseEntity.ok(results);
    }
}
```

**Démonstration du fonctionnement après correction :**
- **Avant** : `GET /api/search/projects?q=' OR '1'='1` → retourne **TOUS** les projets (y compris privés et données sensibles)
- **Avant** : `GET /api/search/projects?q='; DROP TABLE projects; --` → **supprime la table projects**
- **Après** : `GET /api/search/projects?q=' OR '1'='1` → recherche littérale de la chaîne `' OR '1'='1` → **résultat vide** (aucun projet ne contient ce texte)
- **Après** : `GET /api/search/projects?q=portfolio` → retourne les projets contenant "portfolio" → **fonctionnement normal préservé**
