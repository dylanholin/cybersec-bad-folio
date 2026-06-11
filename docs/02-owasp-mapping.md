# Mapping OWASP Top 10 2025

Référence : https://owasp.org/Top10/2025/

---

## A01 : Broken Access Control

| Réf | Vulnérabilité | Fichier | Criticité |
|-----|---------------|---------|-----------|
| A01-01 | Toutes les routes sont `permitAll()` | `SecurityConfig.java:42` | 🔴 CRITIQUE |
| A01-01b | Absence de filtre JWT : SecurityConfig ne peut jamais authentifier | Aucun filtre dans `config/` | 🔴 CRITIQUE |
| A01-02 | Suppression de projet sans contrôle de propriété | `ProjectController.java:56` | 🟠 HAUTE |
| A01-03 | Endpoint admin sans vérification de rôle | `SecurityConfig.java:37`, `UserController.java:19` | 🔴 CRITIQUE |
| A01-04 | Modification de profil/projet sans contrôle d'identité | `UserController.java:33`, `ProjectController.java:39` | 🟠 HAUTE |
| A01-05 | CORS ouvert à tous (`*`) | `SecurityConfig.java:29` | 🟠 HAUTE |
| A01-06 | Protection admin côté client uniquement | `router/index.js:16`, `AdminView.vue:6` | 🟡 MOYENNE |

**Total : 7 vulnérabilités**

---

## A02 : Cryptographic Failures

| Réf | Vulnérabilité | Fichier | Criticité |
|-----|---------------|---------|-----------|
| A02-01 | MD5 sans sel pour les mots de passe | `SecurityConfig.java:53`, `AuthService.java:26` | 🟠 HAUTE |
| A02-01b | Mots de passe exposés dans les réponses JSON (pas de `@JsonIgnore`) | `User.java:33` | 🟠 HAUTE |
| A02-02 | Secrets hardcodés dans `.env` commité | `.env` | 🟠 HAUTE |
| A02-03 | JWT secret hardcodé en fallback | `JwtService.java:19`, `application.properties:15` | 🟠 HAUTE |
| A02-04 | HTTP uniquement, pas de HTTPS | `nginx.conf`, `frontend/Dockerfile` | 🔵 BASSE |
| A02-05 | Mot de passe BDD en clair avec fallback hardcodé | `application.properties:5` | 🟠 HAUTE |
| A02-06 | Mots de passe en commentaire dans init.sql | `init.sql:39` | 🔵 BASSE |

**Total : 7 vulnérabilités**

---

## A03 : Injection

| Réf | Vulnérabilité | Fichier | Criticité |
|-----|---------------|---------|-----------|
| A03-01 | Injection SQL dans la recherche | `SearchController.java:23` | 🔴 CRITIQUE |
| A03-02 | XSS stocké via `v-html` | `ProfileView.vue:35` | 🟠 HAUTE |
| A03-04 | Log injection | `AuthController.java:43` | 🟠 HAUTE |

**Total : 3 vulnérabilités**

---

## A04 : Insecure Design

| Réf | Vulnérabilité | Fichier | Criticité |
|-----|---------------|---------|-----------|
| A04-01 | Pas de rate limiting sur le login | `AuthController.java:37` | 🟡 MOYENNE |
| A04-02 | Énumération d'utilisateurs | `AuthController.java:51` | 🟡 MOYENNE |
| A04-03 | Token de reset dans l'URL | `AuthController.java:83` | 🟡 MOYENNE |
| A04-04 | Pas de validation de complexité des mots de passe | `AuthService.java:24` | 🟡 MOYENNE |

**Total : 4 vulnérabilités**

---

## A05 : Security Misconfiguration

| Réf | Vulnérabilité | Fichier | Criticité |
|-----|---------------|---------|-----------|
| A05-01 | Actuator sans protection | `SecurityConfig.java:39`, `pom.xml:33` | 🟡 MOYENNE |
| A05-02 | Endpoints actuator dangereux activés | `application.properties:20-24` | 🟡 MOYENNE |
| A05-03 | CSRF désactivé | `SecurityConfig.java:24` | 🟠 HAUTE |
| A05-04 | Privilèges BDD excessifs (root) | `init.sql:5` | 🔵 BASSE |
| A05-05 | Pas de Content-Security-Policy | `index.html:6` | 🟡 MOYENNE |
| A05-06 | DEBUG activé en production | `application.properties:34`, `docker-compose.yml:36` | 🟡 MOYENNE |
| A05-07 | `ddl-auto=update` en production | `application.properties:8` | 🔵 BASSE |

**Total : 7 vulnérabilités**

---

## A06 : Vulnerable and Outdated Components

| Réf | Vulnérabilité | Fichier | Criticité |
|-----|---------------|---------|-----------|
| A06-01 | Log4Shell : Log4j 2.14.1 | `pom.xml:49` | 🔴 CRITIQUE |
| A06-02 | Axios 0.21.1 vulnérable (CVE-2021-3749) | `package.json:15` | 🟡 MOYENNE |

**Total : 2 vulnérabilités**

---

## A07 : Identification and Authentication Failures

| Réf | Vulnérabilité | Fichier | Criticité |
|-----|---------------|---------|-----------|
| A07-01 | JWT accepte `alg:none` | `JwtService.java:44-53` | 🔴 CRITIQUE |
| A07-02 | JWT sans expiration | `JwtService.java:29` | 🔴 CRITIQUE |
| A07-03 | JWT dans localStorage | `stores/auth.js:6,14` | 🟡 MOYENNE |
| A07-04 | Compte admin par défaut avec mot de passe trivial | `init.sql:29-31` | 🟠 HAUTE |
| A07-05 | Pas d'invalidation serveur des tokens | `stores/auth.js:17-22` | 🟡 MOYENNE |

**Total : 5 vulnérabilités**

---

## A08 : Software and Data Integrity Failures

| Réf | Vulnérabilité | Fichier | Criticité |
|-----|---------------|---------|-----------|
| A08-01 | Scripts CDN sans SRI | `index.html:8-9` | 🟡 MOYENNE |

**Total : 1 vulnérabilité**

---

## A09 : Security Logging and Monitoring Failures

| Réf | Vulnérabilité | Fichier | Criticité |
|-----|---------------|---------|-----------|
| A09-01 | Mots de passe loggés en clair | `AuthController.java:46`, `AuthService.java:29` | 🟠 HAUTE |
| A09-02 | Échecs de connexion non loggés | `AuthController.java:57` | 🔵 BASSE |
| A09-04 | Pas de rotation des logs | `application.properties:37` | 🔵 BASSE |

**Total : 3 vulnérabilités**

---

## A10 : Server-Side Request Forgery (SSRF)

| Réf | Vulnérabilité | Fichier | Criticité |
|-----|---------------|---------|-----------|
| A10-01 | SSRF via avatar URL | `AvatarController.java:25` | 🔴 CRITIQUE |
| A10-02 | SSRF via import GitHub | `ProjectController.java:63` | 🔴 CRITIQUE |

**Total : 2 vulnérabilités**

---

## Récapitulatif OWASP

| Catégorie OWASP | Nombre | Criticité max |
|-----------------|--------|---------------|
| A01 : Broken Access Control | 7 | 🔴 CRITIQUE |
| A02 : Cryptographic Failures | 7 | 🟠 HAUTE |
| A03 : Injection | 3 | 🔴 CRITIQUE |
| A04 : Insecure Design | 4 | 🟡 MOYENNE |
| A05 : Security Misconfiguration | 7 | 🟠 HAUTE |
| A06 : Vulnerable Components | 2 | 🔴 CRITIQUE |
| A07 : Auth Failures | 5 | 🔴 CRITIQUE |
| A08 : Integrity Failures | 1 | 🟡 MOYENNE |
| A09 : Logging Failures | 3 | 🟠 HAUTE |
| A10 : SSRF | 2 | 🔴 CRITIQUE |
| **Total** | **41** | — |

> Les vulnérabilités restantes (DEV-xx) sont des problèmes d'infrastructure non directement classés dans les catégories OWASP mais liés à A05 (Security Misconfiguration).
