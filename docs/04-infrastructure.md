# Analyse infrastructure — Docker, réseau, configuration

## Vue d'ensemble de l'infrastructure

```
                    ┌─────────────────────────────────────────┐
                    │           Réseau bridge par défaut       │
                    │        (aucune isolation réseau)         │
                    │                                         │
  :80  ┌──────────┐ │  :8080  ┌──────────┐  :3306  ┌───────┐ │
 ◄─────┤ frontend │◄├────────►│ backend  ├────────►│mariadb│ │
       │ (nginx)  │ │         │(spring)  │         │       │ │
       └──────────┘ │         └──────────┘         └───────┘ │
                    │            :5005 (debug JVM)             │
                    └─────────────────────────────────────────┘
                          ↑
                    :3306 exposé sur 0.0.0.0
```

---

## docker-compose.yml — Problèmes identifiés

### 🔴 DEV-03 — MariaDB exposé sur 0.0.0.0

```yaml
ports:
  - "0.0.0.0:3306:3306"
```

La base de données est accessible depuis n'importe quelle interface réseau, y compris Internet.

**Correction :**
```yaml
ports:
  - "127.0.0.1:3306:3306"  # Accessible uniquement en local
```

Ou mieux : ne pas exposer le port du tout (le backend y accède via le réseau Docker interne).

---

### 🔴 DEV-04 — Mot de passe root trivial + pas de compte applicatif

```yaml
MYSQL_ROOT_PASSWORD: root
```

Et dans `init.sql` :
```sql
GRANT ALL PRIVILEGES ON devfolio.* TO 'root'@'%' IDENTIFIED BY 'root';
```

**Correction :** Créer un utilisateur applicatif avec privilèges limités :
```sql
CREATE USER 'devfolio_app'@'%' IDENTIFIED BY '<mot_de_passe_fort>';
GRANT SELECT, INSERT, UPDATE, DELETE ON devfolio.* TO 'devfolio_app'@'%';
```

---

### 🔴 DEV-05 — Pas de réseau isolé

Aucune section `networks` déclarée. Tous les conteneurs sont sur le réseau bridge par défaut et peuvent communiquer librement.

**Correction :**
```yaml
networks:
  frontend-backend:
  backend-db:

services:
  frontend:
    networks:
      - frontend-backend
  backend:
    networks:
      - frontend-backend
      - backend-db
  mariadb:
    networks:
      - backend-db
```

Ainsi le frontend ne peut pas accéder directement à la base de données.

---

### 🔴 DEV-06 — Bind mount sans restriction

```yaml
volumes:
  - ./database:/docker-entrypoint-initdb.d
  - /var/lib/mysql:/var/lib/mysql
```

Le montage de `/var/lib/mysql` expose les données de la BDD sur le système hôte sans restriction.

**Correction :** Utiliser un volume nommé Docker :
```yaml
volumes:
  - db_data:/var/lib/mysql

volumes:
  db_data:
```

---

### 🔴 DEV-07 — Secrets en dur dans docker-compose

```yaml
SPRING_DATASOURCE_PASSWORD: root
JWT_SECRET: secret123
DEVFOLIO_ADMIN_PASSWORD: admin123
```

Ces secrets dupliquent ceux du `.env` et sont visibles dans le fichier `docker-compose.yml`.

**Correction :** Utiliser `env_file` ou des secrets Docker :
```yaml
backend:
  env_file:
    - .env
```

---

## Backend Dockerfile — Problèmes identifiés

### 🔴 DEV-07 — Image complète au lieu de JRE alpine

```dockerfile
FROM openjdk:21
```

L'image `openjdk:21` fait ~500 Mo. Une image JRE Alpine ferait ~100 Mo.

**Correction :**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
```

---

### 🔴 DEV-08 — Conteneur tourne en root

Pas de directive `USER`. Le processus Java tourne en root à l'intérieur du conteneur.

**Correction :**
```dockerfile
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
```

---

### 🔴 DEV-08 — Port de debug JVM exposé

```dockerfile
EXPOSE 8080 5005
CMD ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "app.jar"]
```

Le port 5005 permet l'attachement distant d'un débogueur. L'argument `address=*:5005` écoute sur toutes les interfaces.

**Correction :**
```dockerfile
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
```

---

### 🔴 DEV-09 — Pas de .dockerignore

Sans `.dockerignore`, le `COPY . .` (bien que non présent ici, le `COPY src ./src` est utilisé) pourrait copier des fichiers sensibles dans le contexte de build.

**Correction — créer `backend/.dockerignore` :**
```
.env
target/
*.log
.git
.gitignore
```

---

### 🔴 DEV-12 — `ddl-auto=update` en production

```properties
spring.jpa.hibernate.ddl-auto=update
```

Hibernate est configuré pour modifier automatiquement le schéma de la base de données au démarrage. En production, cela peut :
- Supprimer des colonnes si l'entité Java est modifiée
- Créer des colonnes inattendues
- Causer des pertes de données irréversibles

**Correction :**
```properties
# Production : valider uniquement (échec si le schéma ne correspond pas)
spring.jpa.hibernate.ddl-auto=validate

# Développement : create-drop ou update
# Utiliser Flyway ou Liquibase pour les migrations de schéma
```

---

## Frontend — Problèmes identifiés

### 🔴 HTTP uniquement

`nginx.conf` écoute uniquement sur le port 80. Pas de configuration TLS.

**Correction minimale pour la démo (certificat auto-signé) :**
```nginx
server {
    listen 443 ssl;
    ssl_certificate /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;
    # ...
}

server {
    listen 80;
    return 301 https://$host$request_uri;
}
```

---

### 🔴 Pas d'en-têtes de sécurité dans Nginx

Aucun en-tête de sécurité n'est configuré.

**Correction — ajouter dans le bloc `server` :**
```nginx
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header X-XSS-Protection "1; mode=block" always;
add_header Content-Security-Policy "default-src 'self'; script-src 'self' https://cdn.jsdelivr.net; style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net;" always;
add_header Strict-Transport-Security "max-age=31536000" always;
```

---

## .env et .gitignore

### 🔴 .env commité dans le dépôt

Le fichier `.env` contient des secrets réels (mot de passe Gmail, clés AWS) mais n'est pas exclu du `.gitignore`.

**Correction :**
1. Ajouter `.env` au `.gitignore`
2. Créer `.env.example` avec des valeurs placeholder
3. Révoquer les credentials compromis (clés AWS, mot de passe Gmail)

---

## Récapitulatif infrastructure

| Réf | Problème | Impact | Correction |
|-----|----------|--------|------------|
| DEV-03 | MariaDB sur 0.0.0.0 | BDD accessible depuis Internet | `127.0.0.1:3306` |
| DEV-04 | Root BDD sans compte applicatif | Privilèges excessifs | Créer un utilisateur restreint |
| DEV-05 | Pas de réseau Docker isolé | Conteneurs accessibles entre eux | Déclarer des réseaux |
| DEV-06 | Bind mount BDD | Données exposées sur l'hôte | Volume nommé |
| DEV-07 | Secrets en dur + image complète | Fuite de secrets, surface d'attaque | env_file + image alpine |
| DEV-08 | Conteneur en root + debug JVM | RCE si compromis | USER + supprimer jdwp |
| DEV-09 | Pas de .dockerignore | Fichiers sensibles dans le build | Créer .dockerignore |
| DEV-10 | SQL loggé en clair | Fuite de données | `show-sql=false` |
| DEV-11 | Stacktraces exposées | Divulgation d'architecture | `include-stacktrace=never` |
| DEV-12 | `ddl-auto=update` en prod | Altération automatique du schéma BDD | `ddl-auto=validate` + Flyway/Liquibase |
