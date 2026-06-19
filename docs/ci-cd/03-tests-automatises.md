# Phase 3 — Tests automatisés

> Tests unitaires backend et frontend. Le plan initial est dans [`00-depart.md`](./00-depart.md).

---

> **Pour un débutant** : un test automatisé est un morceau de code qui vérifie qu'une autre partie du code fonctionne correctement. Au lieu de tester manuellement (cliquer sur l'interface, saisir des données), on écrit des tests qui s'exécutent automatiquement à chaque push. Si un test échoue, le pipeline s'arrête et le code n'est pas déployé.
>
> **Types de tests dans ce projet** :
> - **Tests unitaires** : vérifient une fonction ou une classe isolément (ex : "ce token JWT est-il valide ?"). Rapides, pas de base de données.
> - **Tests E2E (non implémentés)** : simulent un utilisateur réel qui navigue sur le site (clic, formulaire, navigation). Plus lents, nécessitent l'application complète.

---

## Backend — 3 classes, 16 tests

| Fichier | Tests | Couverture |
|---|---|---|
| `JwtServiceTest` | 4 | Génération de token, validation, rejet token falsifié, rejet `alg:none`, rejet secret différent |
| `UrlValidatorTest` | 6 | HTTPS only, whitelist, rejet IP metadata (169.254.169.254), URL malformée, taille max fetch |
| `AuthControllerTest` | 8 | Login 200/401/429, register 200/400, logout, rate limiting, password mismatch, email invalide |

> **Note** : le test `validateToken_shouldRejectTokenWithDifferentSecret` a nécessité un secret aléatoire (`SecureRandom`) car deux appels à `Base64.getEncoder().encodeToString(new byte[48])` produisent le même encodage (tableau de zéros), ce qui invalidait le test.

---

## Frontend — 1 fichier, 2 tests

| Fichier | Tests | Couverture |
|---|---|---|
| `basic.test.js` | 2 | Sanity check (assertions de base, opérations string) |

---

## Dépendances ajoutées

### Backend (`pom.xml`)

- `spring-boot-starter-test` (scope `test`) — inclut JUnit 5, Mockito, AssertJ
- Spring Boot parent mis à jour de 3.2.0 vers **3.5.15** (corrige 33 CVE Java : Tomcat, Spring Security, Spring Framework, Logback)

### Frontend (`package.json`)

- `vitest` ^1.0.0 — runner de tests
- `@vue/test-utils` ^2.4.0 — utilitaires de test Vue
- `jsdom` ^24.0.0 — environnement DOM pour les tests
- Script `test` : `vitest run`
- Config : `vitest.config.js` (environnement jsdom, globals activés)
