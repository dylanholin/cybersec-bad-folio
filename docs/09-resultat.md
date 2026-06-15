# Résultat final du projet DevFolio

> Ce document récapitule l'ensemble des actions réalisées et l'état final du projet après sécurisation et déploiement.

---

## 1. Sécurisation du code (branche `correction`)

### 1.1 Vulnérabilités corrigées (OWASP Top 10 2025)

| # | Vulnérabilité | Réf OWASP | Correction |
|---|--------------|-----------|------------|
| 1 | JWT `alg:none` | A07-01 | `parseClaimsJws()` exclusivement, suppression du fallback `parseUnsignedClaims()` |
| 2 | Injection SQL | A03-01 | Requêtes JPA paramétrées (`@Query` + `:param`) |
| 3 | XSS stockée (bio profil) | A04-01 | Interpolation Vue `{{ }}` au lieu de `v-html` |
| 4 | SSRF (avatar) | A10-01 | `UrlValidator` : whitelist domaines + HTTPS + blocage IP privées |
| 5 | IDOR (projets) | A01-01 | Vérification propriétaire + `hasRole("ADMIN")` |
| 6 | Élévation de privilèges | A01-02 | Vérification rôle côté serveur, pas seulement client |
| 7 | Hash MD5 mots de passe | A02-01 | BCrypt avec sel automatique |
| 8 | Mots de passe dans réponses JSON | A07-02 | `@JsonProperty(access = WRITE_ONLY)` sur `password` |
| 9 | Fallback secret JWT hardcodé | A07-03 | Suppression du fallback, `@Value("${jwt.secret}")` sans défaut |
| 10 | Logout côté client uniquement | A07-05 | `TokenBlacklistService` (SHA-256) + endpoint `POST /api/auth/logout` |
| 11 | CDN Bootstrap sans SRI | A08-01 | Attributs `integrity` + `crossorigin="anonymous"` |
| 12 | Pas de CSP / en-têtes sécurité | A05-05 | En-têtes nginx : CSP, X-Content-Type-Options, X-Frame-Options, HSTS, Referrer-Policy |
| 13 | HTTP uniquement | A02-04 | HTTPS (certificat auto-signé en dev) + redirection HTTP→HTTPS |
| 14 | MDP en commentaire SQL | A02-06 | Suppression des commentaires contenant des mots de passe |
| 15 | Actuator sans protection | A05-01/02 | `include=health`, `hasRole("ADMIN")` sur `/actuator/**` |
| 16 | Stacktraces exposées | DEV-11 | `include-stacktrace=never` |
| 17 | SQL loggé | DEV-10 | `show-sql=false` |
| 18 | DEBUG en production | A05-06 | Niveau `WARN`/`INFO` |
| 19 | Admin MDP trivial | A07-04 | Mot de passe fort (≥ 12 car.) + hash BCrypt |
| 20 | Axios vulnérable (CVE) | A06-02 | Mise à jour vers `axios ^1.7.0` |
| 21 | `TokenBlacklistService` hashCode() | A07-05b | Remplacement par SHA-256 (`MessageDigest`) |

### 1.2 Corrections d'infrastructure

| # | Problème | Correction |
|---|----------|------------|
| 22 | MariaDB sur `0.0.0.0` | Bind `127.0.0.1:3306` |
| 23 | Root BDD sans compte applicatif | Utilisateur `devfolio_app` (SELECT/INSERT/UPDATE/DELETE) |
| 24 | Pas de réseau Docker isolé | Réseaux `frontend-backend` + `backend-db` |
| 25 | Bind mount BDD | Volume nommé `db_data` |
| 26 | Secrets en dur docker-compose | `env_file: .env` |
| 27 | Image complète + root + debug JVM | `eclipse-temurin:21-jre-alpine`, `USER appuser`, port 5005 supprimé |
| 28 | Pas de .dockerignore | Création `backend/.dockerignore` |
| 29 | Pas de healthcheck MariaDB | Healthcheck + `condition: service_healthy` |
| 30 | Pas d'en-têtes sécurité nginx | X-Content-Type-Options, X-Frame-Options, CSP, HSTS, Referrer-Policy |
| 31 | Port backend 8080 sur `0.0.0.0` | Bind `127.0.0.1:8080` |
| 32 | Frontend nginx en root | `USER nginx` + `apk add openssl` + permissions cache/log |
| 33 | Pas de .gitattributes | Création (LF forcé sur scripts/Dockerfile) |
| 34 | Pas de .env.example | Création (template sans valeurs) |

---

## 2. Durcissement du serveur

### 2.1 Actions réalisées

- **Mises à jour système** complètes
- **Installation Docker** + Compose
- **Utilisateur dédié** `deploy` (groupe `docker`, sudo)
- **Durcissement SSH** :
  - `PasswordAuthentication no`
  - `PermitRootLogin no`
  - `PubkeyAuthentication yes`
  - `AllowUsers deploy debian`
  - Clé SSH déposée pour `deploy` avant redémarrage SSH
- **Pare-feu UFW** :
  - `deny incoming` par défaut
  - Ouverts : 22/tcp (SSH), 80/tcp, 443/tcp
  - Fermés : 3306, 8080, 5005
- **Filet de sécurité `DOCKER-USER`** (iptables) :
  - Bloque les ports 3306 et 8080 même si Docker tente de les publier
  - `conntrack ESTABLISHED,RELATED` autorisé pour le fonctionnement de Docker
- **Permissions** : `.env` en 600, clés SSH en 600/700
- **Baseline** conservée pour comparaison

### 2.2 Bugs rencontrés et corrigés en production

| Bug | Cause | Correction |
|-----|-------|------------|
| SSH `Permission denied` après durcissement | `AllowUsers deploy` sans clé SSH pour ce compte | Dépôt de `authorized_keys` + `AllowUsers deploy debian` |
| `deploy` pas dans le groupe `docker` | Utilisateur créé manuellement avant le script | Vérification `id -nG` + `usermod -aG docker` si besoin |
| `openssl: not found` dans nginx:alpine | Image Alpine minimale sans openssl | `RUN apk add --no-cache openssl` dans le Dockerfile frontend |
| `mkdir() /var/cache/nginx/client_temp failed` | `USER nginx` sans permissions sur les répertoires | `RUN mkdir -p ... && chown -R nginx:nginx ...` avant `USER nginx` |

---

## 3. Déploiement

### 3.1 Architecture déployée

```
Internet → [UFW: 80/443] → nginx (frontend, USER nginx)
                                  ├── / → Vue 3 static
                                  └── /api → backend (127.0.0.1:8080, USER appuser)
                                                    └── mariadb (127.0.0.1:3306, healthy)
```

### 3.2 Vérifications post-déploiement

| Test | Résultat |
|------|----------|
| `docker compose ps` (3 conteneurs Up, MariaDB Healthy) | ✅ |
| `curl -sk https://localhost/` → 200 | ✅ |
| `curl -s http://localhost/` → 301 (redirect HTTPS) | ✅ |
| `curl -sk https://localhost/api/search/projects?q=test` → `[]` | ✅ |
| Port 3306 non exposé (bind `127.0.0.1`) | ✅ |
| Port 8080 non exposé (bind `127.0.0.1`) | ✅ |
| UFW actif, seuls 22/80/443 ouverts | ✅ |
| En-têtes de sécurité nginx (HSTS, CSP, X-Frame-Options…) | ✅ |

### 3.3 Tests de sécurité (régressions)

| Test | Attendu | Résultat |
|------|---------|----------|
| Injection SQL : `?q=' OR '1'='1` | Résultat vide | ✅ |
| JWT `alg:none` | 401 | ✅ |
| Admin sans token | 401 | ✅ |
| Actuator `/env` | 401/403 | ✅ |
| SSRF avatar `169.254.169.254` | 400 | ✅ |
| XSS dans la bio | Affiché comme texte brut | ✅ |

---

## 4. Risques résiduels

| Risque | Criticité | Action recommandée |
|--------|-----------|--------------------|
| Certificat HTTPS auto-signé | INFO | Let's Encrypt en production |
| Rate limiting en mémoire | INFO | Redis ou Bucket4j en production |
| Token blacklist en mémoire | INFO | Redis avec TTL en production |
| Pas de supervision ni sauvegarde | INFO | Netdata/Prometheus + `mysqldump` périodique |
| Pas de bannissement brute force | BASSE | `fail2ban` sur SSH |

> Ces risques ne sont pas bloquants pour une démonstration pédagogique temporaire.

---

## 5. Livrables

| Livrable | Emplacement |
|----------|-------------|
| Code sécurisé | Branche `correction` du dépôt |
| Code vulnérable (démonstration) | Branche `main` du dépôt |
| Scripts de durcissement et déploiement | `hardening.sh`, `deploy.sh` |
| Documentation technique | `docs/00` à `docs/08` |
| Présent résultat | `docs/09-resultat.md` |
| Serveur déployé et durci | Serveur distant (accès SSH + HTTPS) |

---

## 6. Checklist finale

- [x] Toutes les vulnérabilités OWASP Top 10 corrigées sur la branche `correction`
- [x] Infrastructure Docker durcie (réseaux isolés, utilisateurs non-root, ports restreints)
- [x] Serveur durci (SSH, UFW, utilisateur dédié, permissions)
- [x] Application déployée et fonctionnelle en HTTPS
- [x] Ports 3306/8080 non exposés publiquement
- [x] Aucun secret en dur dans le code ou la documentation versionnée
- [x] Documentation synchronisée entre les branches `main` et `correction`
- [x] Tests de sécurité passés (injection SQL, XSS, SSRF, JWT, actuator)
