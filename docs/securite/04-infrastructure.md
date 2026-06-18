> **Suivi des corrections (Itération 2   Jour 2)** : les corrections d'infrastructure réalisées sur la branche `correction` sont détaillées dans [06-corriger-essentiel-demo.md](06-corriger-essentiel-demo.md). Ce document conserve l'état initial (analyse infrastructure Jour 1).
>
> ---
>
# Analyse infrastructure : Docker, réseau, configuration

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

## docker-compose.yml : problèmes identifiés

### 🔴 DEV-03 : MariaDB exposé sur 0.0.0.0

```yaml
ports:
  - "0.0.0.0:3306:3306"
```

La base de données est accessible depuis n'importe quelle interface réseau, y compris Internet.

**Pourquoi c'est une faille :**
- Le binding `0.0.0.0:3306` signifie que le port 3306 de l'hôte écoute sur **toutes les interfaces réseau**, y compris l'interface publique (Internet).
- Un attaquant peut tenter une connexion directe à MariaDB depuis Internet : brute-force du mot de passe, exploitation de vulnérabilités MariaDB, ou extraction de données.
- Même avec un mot de passe fort, l'exposition inutile d'un service augmente la surface d'attaque (principe du moindre privilège).
- Les bases de données ne doivent **jamais** être accessibles directement depuis l'extérieur — seul le backend a besoin de s'y connecter.

**Correction :**
```yaml
ports:
  - "127.0.0.1:3306:3306"  # Accessible uniquement en local
```

Ou mieux : ne pas exposer le port du tout (le backend y accède via le réseau Docker interne).

**Pourquoi la correction fonctionne :**
- `127.0.0.1:3306` bind le port **uniquement sur l'interface de loopback** : seuls les processus locaux de l'hôte peuvent se connecter, aucune connexion externe n'est possible.
- Si le port n'est pas exposé du tout, MariaDB n'est joignable que via le réseau Docker interne (`backend-db`), ce qui est la solution optimale : le frontend et Internet n'ont aucun chemin d'accès vers la base.

---

### 🔴 DEV-04 : mot de passe root trivial + pas de compte applicatif

```yaml
MYSQL_ROOT_PASSWORD: root
```

Et dans `init.sql` :
```sql
GRANT ALL PRIVILEGES ON devfolio.* TO 'root'@'%' IDENTIFIED BY 'root';
```

**Pourquoi c'est une faille :**
- Le compte `root` possède **tous les privilèges** sur toutes les bases : `DROP DATABASE`, `GRANT`, `ALTER`, `CREATE`… Si le backend est compromis (ex: injection SQL), l'attaquant peut exécuter n'importe quelle commande SQL, y compris supprimer la base ou créer des comptes persistants.
- Le mot de passe `root` est trivial et devinable en quelques secondes par brute-force.
- `root@'%'` autorise les connexions depuis **n'importe quel hôte** (`%` = wildcard), ce qui combiné avec DEV-03 permet un accès distant complet.
- Le principe du moindre privilège exige qu'un applicatif n'ait que les droits strictement nécessaires (CRUD sur sa base, pas de DDL ni d'administration).

**Correction :** Créer un utilisateur applicatif avec privilèges limités :
```sql
CREATE USER 'devfolio_app'@'%' IDENTIFIED BY '<mot_de_passe_fort>';
GRANT SELECT, INSERT, UPDATE, DELETE ON devfolio.* TO 'devfolio_app'@'%';
```

**Pourquoi la correction fonctionne :**
- L'utilisateur `devfolio_app` n'a que les droits `SELECT, INSERT, UPDATE, DELETE` sur la base `devfolio` : il ne peut **pas** exécuter `DROP TABLE`, `ALTER`, `GRANT`, ni créer de nouveaux comptes.
- Même en cas d'injection SQL, les dégâts sont limités aux données de la table ciblée (pas de destruction du schéma ni de persistance via de nouveaux comptes).
- Un mot de passe fort résiste au brute-force.

---

### 🔴 DEV-05 : pas de réseau isolé

Aucune section `networks` déclarée. Tous les conteneurs sont sur le réseau bridge par défaut et peuvent communiquer librement.

**Pourquoi c'est une faille :**
- Sur le réseau bridge par défaut, **tous les conteneurs peuvent communiquer entre eux** sur tous les ports. Le frontend (nginx) peut accéder directement à MariaDB sur le port 3306.
- Si le frontend est compromis (ex: RCE via une vulnérabilité nginx), l'attaquant obtient un accès direct à la base de données sans passer par le backend.
- Cela viole le principe de **segmentation réseau** : chaque service ne doit pouvoir joindre que les services dont il a réellement besoin.
- En architecture sécurisée, le frontend ne doit pouvoir parler qu'au backend, et le backend seul doit pouvoir parler à la base.

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

**Pourquoi la correction fonctionne :**
- Deux réseaux séparés sont créés : `frontend-backend` (frontend ↔ backend) et `backend-db` (backend ↔ MariaDB).
- Le frontend n'est **que** sur `frontend-backend` : il ne peut pas résoudre ni atteindre MariaDB (qui est sur `backend-db` uniquement).
- Le backend est sur les deux réseaux et joue le rôle de proxy : il reçoit les requêtes du frontend et les transmet à la base.
- Même si le frontend est compromis, l'attaquant ne peut pas atteindre directement MariaDB.

---

### 🔴 DEV-06 : bind mount sans restriction

```yaml
volumes:
  - ./database:/docker-entrypoint-initdb.d
  - /var/lib/mysql:/var/lib/mysql
```

Le montage de `/var/lib/mysql` expose les données de la BDD sur le système hôte sans restriction.

**Pourquoi c'est une faille :**
- Un bind mount (`/var/lib/mysql:/var/lib/mysql`) crée un mapping direct entre un répertoire de l'hôte et le conteneur. Tout processus sur l'hôte peut lire/écrire les fichiers de la base de données.
- Si un attaquant obtient un accès en lecture sur l'hôte (ex: via un autre service compromis), il peut lire directement les fichiers de données MariaDB (`.ibd`, `.frm`) et extraire les informations sensibles (mots de passe hashés, tokens, données personnelles).
- Un processus hôte compromis en écriture pourrait **corrompre** les fichiers de la base, causant une perte de données ou une injection de données falsifiées.
- Les bind mounts contournent les couches de sécurité de Docker : les données ne sont pas gérées par le moteur Docker et échappent à son contrôle d'accès.

**Correction :** Utiliser un volume nommé Docker :
```yaml
volumes:
  - db_data:/var/lib/mysql

volumes:
  db_data:
```

**Pourquoi la correction fonctionne :**
- Un volume nommé (`db_data`) est géré entièrement par Docker : les fichiers sont stockés dans le répertoire interne de Docker (`/var/lib/docker/volumes/`) et ne sont pas directement accessibles depuis le système de fichiers de l'hôte.
- L'accès aux données ne peut se faire que via le conteneur qui monte le volume, ce qui réduit la surface d'attaque.
- Docker gère le cycle de vie du volume (sauvegarde, nettoyage) de façon sécurisée.
- Les volumes nommés sont aussi plus performants que les bind mounts sur macOS et Windows.

---

### 🔴 DEV-07 : secrets en dur dans docker-compose

```yaml
SPRING_DATASOURCE_PASSWORD: root
JWT_SECRET: secret123
DEVFOLIO_ADMIN_PASSWORD: admin123
```

Ces secrets dupliquent ceux du `.env` et sont visibles dans le fichier `docker-compose.yml`.

**Pourquoi c'est une faille :**
- Les secrets sont écrits **en clair** dans `docker-compose.yml`, un fichier versionné dans Git. Toute personne avec accès au dépôt (y compris public si le repo est public) peut lire les mots de passe et clés secrètes.
- `docker inspect <conteneur>` affiche les variables d'environnement en clair, y compris les secrets. N'importe qui avec accès Docker sur l'hôte peut les récupérer.
- Le mot de passe `root` et le secret JWT `secret123` sont triviaux et devinables par brute-force.
- La duplication des secrets entre `.env` et `docker-compose.yml` crée une incohérence : si l'un est changé mais pas l'autre, des services peuvent échouer ou utiliser un secret obsolète.
- Le fichier `docker-compose.yml` est souvent partagé, copié, ou affiché dans des tickets de support — les secrets fuient facilement.

**Correction :** Utiliser `env_file` ou des secrets Docker :
```yaml
backend:
  env_file:
    - .env
```

**Pourquoi la correction fonctionne :**
- `env_file: .env` charge les variables depuis le fichier `.env` sans les dupliquer dans le YAML. Les secrets n'apparaissent plus dans `docker-compose.yml`.
- `.env` est dans `.gitignore` : il n'est jamais commité dans le dépôt Git.
- Les variables sont injectées au démarrage du conteneur et ne sont pas visibles dans les fichiers versionnés.
- En production, on peut utiliser Docker Secrets (`docker secret`) pour un stockage chiffré et une rotation des secrets.

---

## Backend Dockerfile : problèmes identifiés

### 🔴 DEV-07 : image complète au lieu de JRE alpine

```dockerfile
FROM openjdk:21
```

L'image `openjdk:21` fait ~500 Mo. Une image JRE Alpine ferait ~100 Mo.

**Pourquoi c'est une faille :**
- L'image `openjdk:21` est basée sur Debian et contient le **JDK complet** (outils de compilation, debug, bibliothèques inutiles en runtime). Un conteneur en production n'a besoin que du **JRE** pour exécuter du bytecode.
- Plus l'image est grande, plus elle contient de binaires et de bibliothèques — donc **plus de surface d'attaque** (CVE sur des paquets inutiles comme `gcc`, `make`, `perl` souvent inclus dans les images Debian).
- Les images lourdes rallentissent les pulls et les déploiements, augmentant la fenêtre d'indisponibilité.
- L'image `openjdk` n'est plus officiellement maintenue (dépréciée au profit d'Eclipse Temurin).

**Correction :**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
```

**Pourquoi la correction fonctionne :**
- `eclipse-temurin:21-jre-alpine` ne contient que le **JRE** (pas le JDK) sur **Alpine Linux** (distribution minimale ~5 Mo). L'image finale fait ~100 Mo au lieu de ~500 Mo.
- Alpine n'inclut que les paquets strictement nécessaires, rédudrant drastiquement la surface d'attaque (moins de binaires = moins de vulnérabilités potentielles).
- Eclipse Temurin est la distribution Java officiellement recommandée et maintenue par le projet Adoptium.

---

### 🔴 DEV-08 : conteneur tourne en root

Pas de directive `USER`. Le processus Java tourne en root à l'intérieur du conteneur.

**Pourquoi c'est une faille :**
- Par défaut, Docker exécute les processus en tant que **root** (UID 0). Si l'application est compromise (ex: désérialisation Java, RCE), l'attaquant a les droits root **à l'intérieur du conteneur**.
- Avec les droits root, un attaquant peut :
  - Installer des paquets (si `apk`/`apt` est disponible dans l'image)
  - Lire les fichiers sensibles du conteneur (secrets, config)
  - Tenter une **évasion de conteneur** (container escape) via des vulnérabilités du noyau Linux (ex: CVE-2022-0185, CVE-2024-1086) : un processus root dans le conteneur a plus de chances d'exploiter une vulnérabilité kernel pour obtenir root sur l'hôte.
  - Lancer des processus malveillants (cryptominage, botnet)
- Bien que Docker restreigne les capabilities par défaut (pas de `CAP_SYS_ADMIN`, `CAP_NET_ADMIN`…), un processus root dans le conteneur dispose encore de suffisamment de privilèges pour exploiter des vulnérabilités kernel et tenter une escalade vers l'hôte.

**Correction :**
```dockerfile
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
```

**Pourquoi la correction fonctionne :**
- `USER appuser` fait en sorte que le processus Java tourne avec un utilisateur **non-root** (UID non-0). Même si l'attaquant compromet l'application, il n'a pas les droits root.
- Un utilisateur non-root ne peut pas installer de paquets, écrire dans `/etc`, ni monter de systèmes de fichiers.
- Les tentatives d'évasion de conteneur depuis un utilisateur non-root sont **beaucoup plus difficiles** : la plupart des CVE d'escalade kernel nécessitent les droits root (CAP_SYS_ADMIN) dans le conteneur.
- `adduser -S` crée un utilisateur système sans mot de passe ni home directory, ce qui réduit encore la surface d'attaque (pas de login interactif possible).

---

### 🔴 DEV-08 : port de debug JVM exposé

```dockerfile
EXPOSE 8080 5005
CMD ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "app.jar"]
```

Le port 5005 permet l'attachement distant d'un débogueur. L'argument `address=*:5005` écoute sur toutes les interfaces.

**Pourquoi c'est une faille :**
- JDWP (Java Debug Wire Protocol) permet à un débogueur distant de **contrôler complètement** l'exécution de la JVM : lecture/écriture de variables, exécution de code arbitraire, arrêt/redémarrage.
- `address=*:5005` écoute sur **toutes les interfaces réseau** : n'importe qui pouvant atteindre le port 5005 peut s'attacher au débogueur.
- Un attaquant connecté au débogueur peut **exécuter du code arbitraire** dans la JVM — c'est l'équivalent d'un RCE (Remote Code Execution) sans aucune authentification.
- JDWP est un protocole de développement, **jamais** destiné à la production. Il n'a aucun mécanisme d'authentification ni de chiffrement.
- L'exposition combinée avec DEV-03 (ports sur `0.0.0.0`) rend ce port potentiellement accessible depuis Internet.

**Correction :**
```dockerfile
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
```

**Pourquoi la correction fonctionne :**
- Supprimer `-agentlib:jdwp` désactive complètement le protocole JDWP : le port 5005 n'est plus ouvert et aucun débogueur distant ne peut se connecter.
- Supprimer `5005` de `EXPOSE` indique que le port n'est plus utilisé (bien que `EXPOSE` soit documentaire, il clarifie l'intention).
- La JVM démarre en mode normal, sans interface de debug, ce qui est le seul mode acceptable en production.

---

### 🔴 DEV-09 : pas de .dockerignore

Sans `.dockerignore`, le `COPY . .` (bien que non présent ici, le `COPY src ./src` est utilisé) pourrait copier des fichiers sensibles dans le contexte de build.

**Pourquoi c'est une faille :**
- Sans `.dockerignore`, **tout le contexte du répertoire** est envoyé au démon Docker lors du build. Cela inclut potentiellement :
  - `.env` avec les secrets réels (mot de passe BDD, JWT_SECRET)
  - `target/` avec les artefacts de build précédents (JAR, classes compilées)
  - `.git/` avec l'historique complet du dépôt
  - Fichiers de logs contenant des données sensibles
- Même si ces fichiers ne sont pas dans l'image finale, ils sont envoyés au démon Docker dans le **contexte de build**. Si le Dockerfile utilise `COPY . .`, ces fichiers seront intégrés dans les couches intermédiaires de l'image et pourront être extraits via `docker history` ou en inspectant les couches avec `docker export` / `dive`.
- Un contexte de build volumineux ralentit aussi le build et consomme de l'espace disque inutilement.
- Si le Dockerfile évolue et utilise `COPY . .` (pattern courant), tous les fichiers sensibles seront copiés dans l'image.

**Correction : créer `backend/.dockerignore` :**
```
.env
target/
*.log
.git
.gitignore
```

**Pourquoi la correction fonctionne :**
- `.dockerignore` exclut les fichiers listés du contexte de build Docker : ils ne sont **jamais envoyés** au démon Docker.
- `.env` ne peut plus être accidentellement copié dans l'image, même si le Dockerfile utilise `COPY . .`.
- `.git/` est exclu : l'historique du dépôt ne se retrouve pas dans les couches Docker.
- `target/` est exclu : les artefacts de build précédents n'alourdissent pas le contexte.
- Le contexte de build est plus petit et plus rapide.

---

### 🔴 DEV-12 : `ddl-auto=update` en production

```properties
spring.jpa.hibernate.ddl-auto=update
```

Hibernate est configuré pour modifier automatiquement le schéma de la base de données au démarrage. En production, cela peut :
- Supprimer des colonnes si l'entité Java est modifiée
- Créer des colonnes inattendues
- Causer des pertes de données irréversibles

**Pourquoi c'est une faille :**
- `ddl-auto=update` autorise Hibernate à **altérer le schéma de la base** à chaque démarrage de l'application. En production, c'est un risque critique :
  - Si un développeur supprime un champ d'une entité Java, Hibernate peut **dropper la colonne** correspondante et toutes ses données — sans confirmation ni sauvegarde.
  - Si un champ est renommé, Hibernate peut créer une nouvelle colonne vide et laisser l'ancienne orpheline, causant des incohérences.
  - Les modifications de schéma ne sont **pas traçables** : il n'y a pas de migration versionnée, pas de rollback possible.
- Un attaquant qui peut modifier une entité Java (ex: via un déploiement compromis) peut altérer le schéma de la base pour insérer des colonnes malveillantes ou dropper des données critiques.
- Les changements de schéma en production doivent toujours être **explicites et versionnés** (migrations), jamais automatiques.

**Correction :**
```properties
# Production : valider uniquement (échec si le schéma ne correspond pas)
spring.jpa.hibernate.ddl-auto=validate

# Développement : create-drop ou update
# Utiliser Flyway ou Liquibase pour les migrations de schéma
```

**Pourquoi la correction fonctionne :**
- `ddl-auto=validate` **ne modifie jamais** le schéma : Hibernate vérifie uniquement que les entités Java correspondent aux tables existantes. Si ce n'est pas le cas, l'application refuse de démarrer (fail-fast), ce qui alerte les développeurs avant tout impact en production.
- Les modifications de schéma sont gérées par un outil de migration (Flyway/Liquibase) : chaque changement est un script SQL versionné, traçable, et réversible.
- En cas d'erreur, on peut rollback la migration sans perte de données.

---

## Frontend : problèmes identifiés

### 🔴 HTTP uniquement

`nginx.conf` écoute uniquement sur le port 80. Pas de configuration TLS.

**Pourquoi c'est une faille :**
- HTTP est un protocole **en clair** : toutes les données transitent sans chiffrement sur le réseau. Cela inclut :
  - Les mots de passe envoyés lors du login (formulaire d'authentification)
  - Les tokens JWT transmis dans les en-têtes de chaque requête
  - Les données personnelles des utilisateurs (bio, projets, etc.)
- Un attaquant sur le réseau (man-in-the-middle, Wi-Fi public, FAI) peut **intercepter et lire** tout le trafic, y compris les credentials.
- Le token JWT volé permet l'usurpation d'identité : l'attaquant peut agir au nom de n'importe quel utilisateur jusqu'à l'expiration du token.
- Les navigateurs modernes signalent les sites HTTP comme « non sécurisés », ce qui dégrade la confiance utilisateur.

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

**Pourquoi c'est une faille :**
- Sans en-têtes de sécurité, le navigateur applique ses comportements par défaut, qui sont souvent trop permissifs :
  - **Pas de `X-Content-Type-Options`** : le navigateur peut deviner (sniff) le type MIME d'une réponse, permettant à un attaquant de faire interpréter du HTML comme du JavaScript (MIME sniffing attack).
  - **Pas de `X-Frame-Options`** : la page peut être intégrée dans un `<iframe>` sur un site malveillant, facilitant les attaques de **clickjacking** (l'utilisateur clique sur un élément invisible pensant cliquer ailleurs).
  - **Pas de `X-XSS-Protection`** : le filtre XSS intégré des anciens navigateurs n'est pas activé.
  - **Pas de `Content-Security-Policy`** : le navigateur autorise le chargement de scripts et de ressources depuis n'importe quelle origine, facilitant les attaques **XSS** (injection de scripts externes malveillants).
  - **Pas de `Strict-Transport-Security`** : après une première visite en HTTPS, le navigateur ne force pas les connexions futures en HTTPS — un attaquant peut forcer une rétrogradation vers HTTP (attaque SSL stripping).

**Correction : ajouter dans le bloc `server` :**
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

**Pourquoi c'est une faille :**
- Si `.env` est commité dans Git, tous les secrets (mots de passe, clés API, JWT_SECRET) sont **visibles dans l'historique** du dépôt, même après suppression du fichier (les commits précédents restent accessibles).
- Sur un dépôt public (ou un dépôt privé compromis), les secrets sont accessibles à tout le monde. Des bots scannent automatiquement les dépôts publics pour extraire des clés AWS, tokens GitHub, etc.
- Les clés AWS trouvées sont exploitées en quelques minutes pour créer des instances de cryptominage, générant des coûts considérables.
- Les mots de passe réutilisés (ex: mot de passe Gmail) compromettent aussi les comptes personnels des développeurs.
- Même sur un dépôt privé, un contributeur malveillant ou un accès compromis peut exfiltrer les secrets.

**Correction :**
1. Ajouter `.env` au `.gitignore`
2. Créer `.env.example` avec des valeurs placeholder
3. Révoquer les credentials compromis (clés AWS, mot de passe Gmail)

**Pourquoi la correction fonctionne :**
- `.gitignore` empêche `.env` d'être ajouté au dépôt à l'avenir.
- `.env.example` sert de template : les développeurs savent quelles variables configurer sans exposer de vraies valeurs.
- La révocation des credentials est **indispensable** car les anciens commits contenant les secrets restent dans l'historique Git. Même après suppression du fichier, les secrets sont récupérables via `git log`.
- Pour nettoyer l'historique, il faut utiliser `git filter-repo` ou BFG Repo-Cleaner, puis forcer un push (opération destructive nécessitant un accord explicite).

---

## Configuration pare feu

### Principe

En production, seul le reverse proxy nginx doit être accessible depuis l'extérieur. Tous les autres services sont accessibles uniquement via le réseau Docker interne.

### Règles recommandées (iptables / nftables)

```bash
# Politique par défaut : tout refuser
iptables -P INPUT DROP
iptables -P FORWARD DROP
iptables -P OUTPUT ACCEPT

# Autoriser les connexions déjà établies
iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT

# Autoriser loopback
iptables -A INPUT -i lo -j ACCEPT

# Autoriser SSH (port 22) pour l'administration
iptables -A INPUT -p tcp --dport 22 -j ACCEPT

# Autoriser HTTP (80) et HTTPS (443) uniquement
iptables -A INPUT -p tcp --dport 80 -j ACCEPT
iptables -A INPUT -p tcp --dport 443 -j ACCEPT

# Interdire explicitement les ports internes
iptables -A INPUT -p tcp --dport 3306 -j DROP   # MariaDB
iptables -A INPUT -p tcp --dport 8080 -j DROP   # Backend Spring Boot
iptables -A INPUT -p tcp --dport 5005 -j DROP   # Debug JVM (si présent)
```

### Règles équivalentes nftables

```bash
table inet filter {
    chain input {
        type filter hook input priority 0; policy drop;
        ct state established,related accept
        iif lo accept
        tcp dport { 22, 80, 443 } accept
        tcp dport { 3306, 8080, 5005 } drop
    }
}
```

### Ports exposés après durcissement

| Port | Service | Exposition | Pare feu |
|------|---------|------------|----------|
| 80 | HTTP → redirection HTTPS | Public | ACCEPT |
| 443 | HTTPS | Public | ACCEPT |
| 22 | SSH | Admin uniquement | ACCEPT |
| 3306 | MariaDB | Aucun accès extérieur | DROP |
| 8080 | Backend | Via nginx reverse proxy uniquement | DROP |
| 5005 | Debug JVM | Aucun | DROP |

> **Note** : Docker gère ses propres règles iptables via les chaînes `DOCKER-USER` et `FORWARD`. En production, il est recommandé d'ajouter les restrictions dans la chaîne `DOCKER-USER` pour qu'elles persistent après un redémarrage Docker :
> ```bash
> iptables -I DOCKER-USER -p tcp --dport 3306 -j DROP
> iptables -I DOCKER-USER -p tcp --dport 8080 -j DROP
> ```

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
