# Plan d'action priorisé — Sécurisation DevFolio

## Contexte

L'application doit être sécurisée avant déploiement. Tous les problèmes ne peuvent pas être corrigés simultanément. Ce plan priorise les corrections qui **réduisent le plus de risque en priorité**.

> **Dépendance clé :** Les points 5 (SecurityConfig), 10 (UserController) et 11 (ProjectController) dépendent tous de la création du filtre JWT (point 1). Il faut traiter la chaîne JWT en premier.

---

## 🔴 Phase 1 — Bloquants critiques

Ces vulnérabilités permettent une compromission totale sans effort.

| # | Vulnérabilité | Action | Fichier(s) |
|---|---------------|--------|------------|
| 1 | **VULN-06b** — Absence de filtre JWT | Créer `JwtAuthenticationFilter` + l'enregistrer dans `SecurityConfig` | Nouveau `config/JwtAuthenticationFilter.java`, `SecurityConfig.java` |
| 2 | **VULN-04** — JWT alg:none | Supprimer le fallback de parsing non signé, ne garder que `parseClaimsJws()` | `JwtService.java:44-53` |
| 3 | **VULN-01** — Injection SQL | Remplacer par requête paramétrée JPA (`@Query` avec `:q` ou méthode dérivée) | `SearchController.java`, `ProjectRepository.java` |
| 4 | **VULN-07** — Log4Shell | Supprimer la dépendance `log4j-core` (Spring Boot utilise déjà Logback) | `pom.xml:45-50` |
| 5 | **VULN-05** — JWT sans expiration | Ajouter `.setExpiration()` + propriété `jwt.expiration` | `JwtService.java:29` |
| 6 | **VULN-06** — Contrôle d'accès inopérant | Remplacer `permitAll()` par des restrictions de rôle + routes publiques | `SecurityConfig.java:35-43` |

**Corrections détaillées :**

### 1 — Filtre JWT (prérequis pour 5, 10, 11)

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

### 2 + 5 — JwtService corrigé (fallback + expiration)

```java
@Service
public class JwtService {
    @Value("${jwt.secret}")           // plus de fallback hardcodé
    private String secret;

    @Value("${jwt.expiration:3600000}") // 1h par défaut
    private long expirationMs;

    public String generateToken(User user) {
        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("role", user.getRole())
                .claim("userId", user.getId())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
```

Supprimer `parseUnsignedClaims`, l'import `DefaultClaims` et l'`ObjectMapper`. Le secret doit faire ≥ 32 octets pour HS256 (`secret123` du `.env` fera planter `hmacShaKeyFor` — générer avec `openssl rand -base64 48`).

### 3 — Injection SQL corrigée

```java
// ProjectRepository.java
@Query("SELECT p FROM Project p WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
       "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%'))")
List<Project> search(@Param("q") String q);
```

Ou via méthode dérivée :
```java
List<Project> findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String title, String description);
```

### 6 — SecurityConfig corrigé

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
    http
        .csrf(csrf -> csrf.disable()) // justifié : API stateless + JWT en header Authorization (pas de cookie de session)
        .cors(cors -> cors.configurationSource(request -> {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost"));
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
            config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
            return config;
        }))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/projects", "/api/projects/*",
                             "/api/users/*", "/api/search/**").permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .requestMatchers("/actuator/health").permitAll()
            .requestMatchers("/actuator/**").hasRole("ADMIN")
            .anyRequest().authenticated()
        )
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
}
```

---

## 🟠 Phase 2 — Haute priorité

| # | Vulnérabilité | Action | Fichier(s) |
|---|---------------|--------|------------|
| 7 | **VULN-02** — SSRF import GitHub | Whitelist `github.com` / `raw.githubusercontent.com`, bloquer IP privées | `ProjectController.java` |
| 8 | **VULN-03** — SSRF avatar | Whitelist domaines images, bloquer IP privées, protocole HTTPS uniquement | `AvatarController.java` |
| 9 | **VULN-10** — Secrets hardcodés | Ajouter `.env` au `.gitignore`, créer `.env.example`, utiliser `env_file` dans docker-compose | `.gitignore`, `docker-compose.yml` |
| 10 | **VULN-08** — MD5 sans sel | Remplacer par `BCryptPasswordEncoder`, supprimer `md5Hash()`, ré-initialiser les comptes seed | `SecurityConfig.java`, `AuthService.java` |
| 11 | **VULN-07b** — Mots de passe dans les réponses JSON | Ajouter `@JsonProperty(access = WRITE_ONLY)` sur `getPassword()` | `User.java:33` |
| 12 | **VULN-11** — Élévation de privilèges | Exclure `role` de la mise à jour utilisateur, vérifier l'identité via `Authentication` | `UserController.java` |
| 13 | **VULN-12** — IDOR sur les projets | Vérifier `project.getOwnerId() == currentUserId` avant PUT/DELETE | `ProjectController.java` |
| 14 | **VULN-09** — XSS stocké | Remplacer `v-html` par `{{ user.bio }}` | `ProfileView.vue:35` |
| 15 | **VULN-13** — CORS ouvert | Limiter au domaine du frontend (déjà corrigé dans Phase 1 point 6) | `SecurityConfig.java` |

**Corrections détaillées :**

### 7 + 8 — SSRF corrigé (UrlValidator partagé)

```java
public final class UrlValidator {
    private static final Set<String> ALLOWED_HOSTS = Set.of(
        "raw.githubusercontent.com", "api.github.com", "github.com",
        "i.imgur.com", "avatars.githubusercontent.com");

    public static URL validate(String rawUrl) {
        URI uri = new URI(rawUrl);
        if (!"https".equals(uri.getScheme()))
            throw new IllegalArgumentException("Seul HTTPS est autorisé");
        if (!ALLOWED_HOSTS.contains(uri.getHost()))
            throw new IllegalArgumentException("Domaine non autorisé : " + uri.getHost());
        InetAddress addr = InetAddress.getByName(uri.getHost());
        if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress() || addr.isAnyLocalAddress())
            throw new IllegalArgumentException("Adresse IP interdite");
        return uri.toURL();
    }
}
```

### 10 — BCrypt

```java
// SecurityConfig.java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

```java
// AuthService.java — supprimer md5Hash(), ajouter validation
public User register(String email, String password) {
    if (password == null || password.length() < 12)
        throw new IllegalArgumentException("Mot de passe trop court (12 caractères min.)");
    log.info("Registering user: {}", email);
    User user = new User();
    user.setEmail(email);
    user.setPassword(passwordEncoder.encode(password));
    user.setRole("USER");
    return userRepository.save(user);
}
```

### 12 — UserController corrigé

```java
@PutMapping("/users/{id}")
public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody User updated,
                                    Authentication authentication) {
    Long currentUserId = (Long) authentication.getDetails();
    if (!currentUserId.equals(id))
        return ResponseEntity.status(403).body(Map.of("error", "Accès refusé"));
    return userRepository.findById(id).map(user -> {
        user.setEmail(updated.getEmail());
        user.setBio(updated.getBio());
        // role : volontairement NON modifiable par cet endpoint
        return ResponseEntity.ok(userRepository.save(user));
    }).orElse(ResponseEntity.notFound().build());
}
```

Idéalement, remplacer `@RequestBody User` par un DTO `UpdateUserRequest(email, bio)` pour éliminer structurellement le mass assignment.

### 14 — XSS corrigé

```html
<!-- Avant -->
<div class="bio" v-html="user.bio"></div>
<!-- Après -->
<div class="bio" style="white-space: pre-line">{{ user.bio }}</div>
```

---

## 🟡 Phase 3 — Moyenne priorité

| # | Vulnérabilité | Action | Fichier(s) |
|---|---------------|--------|------------|
| 16 | **VULN-17** — Actuator entièrement exposé | Limiter à `health`, désactiver `env/heapdump/shutdown` | `application.properties` |
| 17 | **VULN-18** — Stacktraces exposées | `include-stacktrace=never` | `application.properties` |
| 18 | **VULN-19** — DEBUG en production | Passer à `WARN` | `application.properties` |
| 19 | **VULN-20** — Port debug JVM | Supprimer `-agentlib:jdwp` et `EXPOSE 5005` | `backend/Dockerfile` |
| 20 | **VULN-15** — Log injection | Utiliser des paramètres de log structurés (`log.info("Login: {}", email)`) | `AuthController.java` |
| 21 | **VULN-16** — Mots de passe loggés | Supprimer les logs de mots de passe | `AuthController.java`, `AuthService.java` |
| 22 | **VULN-22** — Énumération utilisateurs | Message générique "Identifiants incorrects" | `AuthController.java` |
| 23 | **VULN-26** — Pas de CSP | Ajouter en-tête CSP dans Nginx | `nginx.conf` |
| 24 | **VULN-27** — Scripts CDN sans SRI | Ajouter attributs `integrity` | `index.html` |
| 25 | **VULN-30** — Axios vulnérable | Mettre à jour axios ≥ 0.22.0 | `package.json` |
| 26 | **VULN-14** — CSRF désactivé | Documenter le choix stateless (JWT en header Authorization = pas de vecteur CSRF) | `SecurityConfig.java` |

---

## 🔵 Phase 4 — Durcissement infrastructure

| # | Vulnérabilité | Action | Fichier(s) |
|---|---------------|--------|------------|
| 27 | **VULN-35** — MariaDB sur 0.0.0.0 | Exposer sur `127.0.0.1` uniquement ou ne pas exposer | `docker-compose.yml` |
| 28 | **VULN-31** — Conteneur en root | Ajouter `USER nobody` | `backend/Dockerfile` |
| 29 | **VULN-33** — Pas de .dockerignore | Créer `.dockerignore` | `backend/.dockerignore` |
| 30 | **VULN-34** — Pas de réseau isolé | Déclarer des réseaux Docker | `docker-compose.yml` |
| 31 | **VULN-36** — Pas de compte BDD applicatif | Créer un utilisateur restreint | `init.sql` |
| 32 | **VULN-37** — Pas de HTTPS | Configurer TLS Nginx (auto-signé pour demo) | `nginx.conf` |
| 33 | **VULN-24** — JWT dans localStorage | Migrer vers cookie HttpOnly | `auth.js`, backend |
| 34 | **VULN-44** — `ddl-auto=update` en prod | Passer à `validate` + Flyway/Liquibase | `application.properties` |

---

## ⏳ Risques acceptés temporairement pour la démonstration

Les vulnérabilités suivantes sont **connues mais acceptées** pour la démo, à corriger ensuite :

| # | Vulnérabilité | Justification | Correction prévue |
|---|---------------|---------------|-------------------|
| VULN-37 | Pas de HTTPS (HTTP uniquement) | Certificat auto-signé peu utile pour une démo locale | Let's Encrypt en prod + redirection HTTP→HTTPS dans Nginx |
| VULN-40 | Pas de rate limiting sur le login | Pas de vrais utilisateurs en prod | Bucket4j ou Nginx `limit_req` |
| VULN-25 | Pas d'invalidation serveur des tokens JWT | JWT avec expiration courte (1h) suffit pour la démo | Blacklist en BDD ou refresh tokens |
| VULN-21 | Validation complexité des mots de passe | Partiellement corrigé (12c + majuscule + chiffre + spécial) | Renforcer avec un validateur complet |
| VULN-23 | Token de reset dans l'URL | Fonctionnalité non critique pour la démo | Envoyer par email, pas dans l'URL |
| VULN-28 | Protection admin côté client | Sera couvert par VULN-06 (protection serveur) | Déjà corrigé côté serveur |
| VULN-29 | Hashes affichés dans l'UI admin | Corrigé par @JsonIgnore + auth serveur | Déjà corrigé |
| VULN-32 | Image Docker complète | Pas bloquant pour la démo | JRE Alpine déjà en place |
| VULN-38 | Pas de rotation des logs | Rotation configurée (10 Mo, 7 jours) | Déjà corrigé |
| VULN-39 | Échecs de connexion non loggés | Échecs désormais loggés | Déjà corrigé |
| VULN-41 | Fallback mot de passe en clair | BCrypt lève une exception si échec | Déjà corrigé |
| VULN-42 | Tous les comptes même mot de passe | Mots de passe désormais différents | Déjà corrigé |
| VULN-43 | Données NDA dans les projets de test | Données fictives supprimées | Déjà corrigé |
| VULN-45 | Pas de healthcheck Docker | Non bloquant pour la démo | Healthcheck MariaDB ajouté |

---

## 📊 Synthèse du plan

| Phase | Criticité | Nombre de corrections |
|-------|-----------|----------------------|
| Phase 1 | 🔴 CRITIQUE | 6 |
| Phase 2 | 🟠 HAUTE | 9 |
| Phase 3 | 🟡 MOYENNE | 11 |
| Phase 4 | 🔵 BASSE | 8 |
| **Total** | — | **34** |

> Les phases 1 et 2 sont prioritaires et doivent être terminées avant la démo. La Phase 1 est un prérequis bloquant pour la Phase 2 (points 12, 13 nécessitent le filtre JWT de la Phase 1).

---

## ✅ Tests de vérification par correction

| # | Vulnérabilité | Test de vérification |
|---|---------------|----------------------|
| 1 | Filtre JWT | Requête GET `/api/projects` sans token → 401 ; avec token valide → 200 ; avec token expiré → 401 |
| 2 | JWT alg:none | Forger un token avec `alg:none` (header `eyJhbGciOiJub25lIn0`) → doit retourner 401 |
| 3 | Injection SQL | `GET /api/search/projects?q=' OR '1'='1` → ne doit pas retourner tous les projets ; payload normal fonctionne |
| 4 | Log4Shell | Vérifier que `log4j-core` n'apparaît plus dans `mvn dependency:tree` |
| 5 | JWT expiration | Vérifier qu'un token généré n'est plus valide après `jwt.expiration` ms |
| 6 | SecurityConfig | `GET /api/admin/users` sans rôle ADMIN → 403 ; `GET /actuator/env` → 403 ; `GET /api/projects` (public) → 200 |
| 7-8 | SSRF | `POST /api/users/avatar?url=http://169.254.169.254/` → 400 ; `url=file:///etc/passwd` → 400 ; URL whitelistée → 200 |
| 9 | Secrets | Vérifier que `.env` est dans `.gitignore` ; `docker-compose` utilise `env_file` |
| 10 | BCrypt | Vérifier en BDD que les hashes commencent par `$2a$` ; login avec un compte seed fonctionne |
| 11 | @JsonIgnore | `GET /api/users/1` → la réponse JSON ne contient pas de champ `password` |
| 12 | Escalade privilèges | `PUT /api/users/2` en tant qu'utilisateur 1 → 403 ; `PUT /api/users/1` avec `role=ADMIN` dans le body → le rôle n'est pas modifié |
| 13 | IDOR projets | `DELETE /api/projects/1` en tant qu'utilisateur non-propriétaire → 403 |
| 14 | XSS | Saisir `<img src=x onerror="alert(1)">` dans bio → le texte est affiché tel quel, pas exécuté |
| 16 | Actuator | `GET /actuator/env` → 403 ; `GET /actuator/heapdump` → 403 ; `GET /actuator/health` → 200 |
| 17 | Stacktraces | Provoquer une erreur 500 → la réponse ne contient pas de stacktrace |
| 20 | Log injection | Envoyer `username="admin\nERROR Fake message"` → le log ne contient pas de ligne falsifiée |
| 22 | Énumération | Login avec email inexistant → même message que mot de passe incorrect |
