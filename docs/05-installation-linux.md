> **Suivi des corrections (Itération 2   Jour 2)** : les corrections réalisées sur la branche `correction` sont détaillées dans [06-corriger-essentiel-demo.md](06-corriger-essentiel-demo.md). Ce document conserve l'état initial (guide installation Jour 1).
>
> ---
>
# Installation et lancement sur Linux (Fedora / RHEL)

Ce guide explique comment installer les dépendances et lancer DevFolio sur une workstation Linux sans Docker.

---

## Prérequis

| Outil | Version minimale | Installation Fedora |
|-------|-----------------|---------------------|
| Java (OpenJDK) | 21 | `sudo dnf install -y java-21-openjdk` |
| Maven | 3.9 | `sudo dnf install -y maven` |
| Node.js + npm | 18+ | `sudo dnf install -y nodejs npm` |
| MariaDB | 10.11 | `sudo dnf install -y mariadb-server` |

> Si Java 25 est installé par défaut, Spring Boot 3.2 nécessite Java 21. Vérifier avec `java --version`. Si besoin, forcer Java 21 : `sudo alternatives --config java`.

---

## 1. Base de données MariaDB

### Démarrer le service

```bash
sudo systemctl enable --now mariadb
```

### Sécuriser l'installation

```bash
sudo mysql_secure_installation
```

Réponses recommandées :
- Enter current password for root : **Entrée** (pas de mot de passe initial)
- Switch to unix_socket authentication : **Y**
- Change the root password : **n**
- Remove anonymous users : **Y**
- Disallow root login remotely : **Y**
- Remove test database : **n** (pas critique en dev)
- Reload privilege tables : **Y**

### Créer la base et l'utilisateur applicatif

```bash
sudo mariadb -e "CREATE DATABASE IF NOT EXISTS devfolio;"
sudo mariadb -e "CREATE USER 'devfolio_app'@'localhost' IDENTIFIED BY 'DevfolioApp2024!';"
sudo mariadb -e "GRANT SELECT, INSERT, UPDATE, DELETE ON devfolio.* TO 'devfolio_app'@'localhost';"
sudo mariadb -e "FLUSH PRIVILEGES;"
```

> **Attention** : le caractère `!` dans le mot de passe peut être interprété par bash (history expansion). Pour éviter cela, utiliser des guillemets simples ou échapper le `!` :
> ```bash
> # Méthode 1 : guillemets simples
> sudo mariadb -e "CREATE USER 'devfolio_app'@'localhost' IDENTIFIED BY 'DevfolioApp2024!';"
>
> # Méthode 2 : désactiver l'history expansion dans la session
> set +H
> ```

### Charger les données initiales

```bash
sudo mariadb devfolio < database/init.sql
```

---

## 2. Configuration des variables d'environnement

```bash
cp .env.example .env
```

Éditer `.env` avec les valeurs pour le lancement local :

```env
DB_HOST=localhost
DB_PORT=3306
DB_NAME=devfolio
DB_USER=devfolio_app
DB_PASSWORD=DevfolioApp2024!

JWT_SECRET=<générer avec : openssl rand -base64 48>
JWT_EXPIRATION=3600000

ADMIN_EMAIL=admin@devfolio.com
ADMIN_PASSWORD=DevfolioAdmin2024!
```

Charger les variables dans le shell (obligatoire avant chaque lancement du backend) :

```bash
set +H  # désactiver l'history expansion de bash (nécessaire si un mot de passe contient !)
export $(grep -v '^#' .env | grep -v '^$' | xargs)
```

Ou exporter manuellement :

```bash
set +H
export DB_HOST=localhost DB_PORT=3306 DB_NAME=devfolio
export DB_USER=devfolio_app DB_PASSWORD='DevfolioApp2024!'
export JWT_SECRET='votre-secret-généré-avec-openssl'
export JWT_EXPIRATION=3600000
export ADMIN_EMAIL=admin@devfolio.com ADMIN_PASSWORD='DevfolioAdmin2024!'
```

> **Important** : les variables d'environnement ne persistent pas entre les sessions. Il faut les recharger à chaque ouverture de terminal.

---

## 3. Lancer le backend et le frontend

Le backend et le frontend tournent en continu. Il faut **deux terminaux séparés**.

### Terminal 1 : Backend

```bash
cd /home/user/Downloads/cybersec-bad-folio-main
set +H
export $(grep -v '^#' .env | grep -v '^$' | xargs)
cd backend
mvn spring-boot:run
```

Attendre le message `Started DevfolioApplication` dans les logs.

Vérifier : http://localhost:8080/api/projects doit retourner du JSON.

### Terminal 2 : Frontend

```bash
cd /home/user/Downloads/cybersec-bad-folio-main/frontend
npm install  # uniquement la première fois
npm run dev
```

Ouvrir le navigateur sur : **http://localhost:5173**

---

## 4. Vérification

| Test | Commande / URL | Résultat attendu |
|------|---------------|-------------------|
| Backend actif | `curl http://localhost:8080/api/projects` | Liste JSON des projets publics |
| Login | `curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"email":"admin@devfolio.com","password":"DevfolioAdmin2024!"}'` | Token JWT dans la réponse |
| Rate limiting | Envoyer 6+ requêtes de login en moins d'une minute | 429 Too Many Requests |
| Logout | `curl -X POST http://localhost:8080/api/auth/logout -H "Authorization: Bearer <token>"` | `{"message":"Déconnexion réussie"}`   token blacklisté |
| Route protégée | `curl http://localhost:8080/api/admin/users` | 401 Unauthorized |
| Route admin | `curl -H "Authorization: Bearer <token>" http://localhost:8080/api/admin/users` | Liste des utilisateurs (rôle ADMIN) |
| Frontend | http://localhost:5173 | Page d'accueil DevFolio |

---

## Dépannage

### `hmacShaKeyFor` : InvalidKeyException

Le secret JWT doit faire au moins 32 octets pour HS256. Régénérer :

```bash
openssl rand -base64 48
```

Puis mettre à jour `JWT_SECRET` dans `.env`.

### Port 8080 déjà utilisé

```bash
lsof -i :8080    # identifier le processus
kill <PID>       # le libérer
```

### MariaDB refuse la connexion

Vérifier que le service tourne et que l'utilisateur existe :

```bash
sudo systemctl status mariadb
sudo mariadb -e "SELECT User, Host FROM mysql.user WHERE User='devfolio_app';"
```

### `Could not resolve placeholder 'JWT_SECRET'`

Les variables d'environnement ne sont pas chargées. Exécuter avant `mvn spring-boot:run` :

```bash
set +H  # obligatoire si un mot de passe contient !
export $(grep -v '^#' .env | grep -v '^$' | xargs)
```

Ou exporter manuellement :

```bash
set +H
export DB_HOST=localhost DB_PORT=3306 DB_NAME=devfolio
export DB_USER=devfolio_app DB_PASSWORD='DevfolioApp2024!'
export JWT_SECRET='votre-secret-généré'
export JWT_EXPIRATION=3600000
export ADMIN_EMAIL=admin@devfolio.com ADMIN_PASSWORD='DevfolioAdmin2024!'
```

### 403 sur POST `/api/auth/login`

Si Spring Security bloque les POST anonymes avec un 403 vide :

1. Vérifier que `FilterRegistrationBean` est présent dans `SecurityConfig` (empêche le double-enregistrement du filtre JWT)
2. Vérifier que `/error` est en `permitAll()` dans `authorizeHttpRequests`
3. Vérifier que `exceptionHandling` retourne du JSON (401/403) au lieu d'une réponse vide

### `Query did not return a unique result`

Le script `init.sql` a été chargé plusieurs fois, créant des doublons. Nettoyer :

```bash
set +H
mariadb -u devfolio_app -p'DevfolioApp2024!' -e "DELETE FROM devfolio.users WHERE id > 3;"
mariadb -u devfolio_app -p'DevfolioApp2024!' -e "SELECT id, email, role FROM devfolio.users;"
```

### `npm install` échoue

Vérifier la version de Node :

```bash
node --version   # doit être >= 18
```

Si trop ancien :

```bash
sudo dnf install -y nodejs npm
```

### 429 Too Many Requests sur le login

Le rate limiting bloque après 5 tentatives échouées par minute et par IP. Attendre 60 secondes ou :

```bash
# Vérifier le compteur dans les logs backend
# Le compteur se réinitialise aussi après un login réussi
```

### Token toujours valide après logout

Le logout côté serveur blacklist le token. Vérifier que le frontend appelle bien `POST /api/auth/logout` (le store Pinia le fait automatiquement). Si le token est toujours accepté après logout, vérifier que `TokenBlacklistService` est bien injecté dans `JwtAuthenticationFilter`.
