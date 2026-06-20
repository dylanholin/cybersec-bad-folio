# Installation pas à pas sur un VPS

Ce guide détaillé explique comment déployer DevFolio sur un VPS Debian ou Ubuntu, depuis la connexion SSH jusqu'à la vérification finale.

> **Pour qui ?** Débutants n'ayant jamais déployé d'application sur un serveur Linux.

---

## Prérequis

### Côté VPS

| Prérequis | Détail |
|-----------|--------|
| **OS** | Debian 12+ ou Ubuntu 22.04+ |
| **Accès SSH** | Connexion SSH fonctionnelle avec l'utilisateur par défaut (`debian` ou `ubuntu`) |
| **`git`** | Préinstallé sur les images cloud standard. Sur une image minimale : `sudo apt update && sudo apt install -y git` |
| **`sudo`** | Préinstallé sur les images cloud standard. Sur une image minimale : `apt install -y sudo` (depuis root) |
| **Droits sudo** | L'utilisateur par défaut (`debian`/`ubuntu`) doit avoir les droits sudo |

> Sur un VPS Debian/Ubuntu cloud standard (OVH, DigitalOcean, Hetzner, AWS…), `git` et `sudo` sont généralement préinstallés. Sur une image minimale (netinst, container LXC), ils peuvent être absents. Les installer manuellement avant de commencer.

### Côté GitHub (pour le CI/CD automatique)

| Prérequis | Détail |
|-----------|--------|
| **Repo forké** | Le repo doit être forké sur votre compte GitHub si vous voulez que le CI/CD déploie sur **votre** VPS |
| **GitHub Secrets** | `VPS_HOST`, `VPS_USER`, `VPS_SSH_PRIVATE_KEY`, `VPS_SSH_PORT`, `VPS_GHCR_TOKEN`, `VPS_PROJECT_DIR`, voir [docs/ci-cd/06-deploiement-continu.md](ci-cd/06-deploiement-continu.md) |
| **Clé SSH de déploiement** | Paire de clés SSH dédiée au CI/CD (sans passphrase), voir [docs/ci-cd/01-infrastructure-vps.md](ci-cd/01-infrastructure-vps.md) |

> Si vous ne configurez pas le CI/CD, l'application fonctionnera quand même après l'étape 5. Les déploiements suivants se feront manuellement en relançant `deploy.sh`.

### Ce que les scripts installent automatiquement

Vous n'avez **pas** besoin d'installer ces outils manuellement. `hardening.sh` s'en charge :

| Outil | Rôle |
|-------|------|
| Docker + Compose | Containerisation de l'application |
| UFW | Pare-feu (bloque tout sauf 22/80/443) |
| fail2ban | Anti brute-force SSH |
| curl | Requis par les vérifications post-déploiement |

---

## Développement local (sans VPS)

Si vous voulez juste tester l'application sur votre machine, sans déployer sur un serveur.

### Prérequis

| Prérequis | Détail |
|-----------|--------|
| **Docker** | [Docker Desktop](https://www.docker.com/products/docker-desktop/) (Windows/Mac) ou Docker Engine (Linux) |
| **Docker Compose** | Inclus dans Docker Desktop, ou `docker-compose-plugin` sur Linux |
| **Git** | Pour cloner le repo |

### Étapes

```bash
# 1. Cloner le repo
git clone -b ci-cd-pipeline https://github.com/dylanholin/cybersec-bad-folio.git
cd cybersec-bad-folio

# 2. Créer le fichier .env à partir du template
cp .env.example .env

# 3. Éditer .env, remplir au minimum :
#    DB_ROOT_PASSWORD  (mot de passe root MariaDB, min 12 car.)
#    DB_PASSWORD       (mot de passe applicatif, min 12 car.)
#    JWT_SECRET        (openssl rand -base64 48)
#    ADMIN_PASSWORD    (DevfolioAdmin2024!, correspond au hash BCrypt dans init-template.sql)

# 4. Build et démarrer
docker compose up --build
```

### Accès

| Service | URL | Notes |
|---------|-----|-------|
| Frontend | `https://localhost` | Certificat auto-signé, accepter l'avertissement du navigateur |
| Backend API | `https://localhost/api` | Via reverse proxy nginx |
| Backend (debug) | `http://localhost:8080/api` | Accès direct, dev uniquement |
| MariaDB | `localhost:3306` | Bind sur `127.0.0.1` uniquement |

### Ports utilisés

| Port | Service | Si conflit |
|------|---------|------------|
| 80 | nginx (HTTP → redirect HTTPS) | Libérer le port ou modifier `docker-compose.yml` |
| 443 | nginx (HTTPS) | Idem |
| 3306 | MariaDB | Idem |
| 8080 | Backend Spring Boot | Idem |

> **Note** : `DB_ROOT_PASSWORD` et `DB_PASSWORD` peuvent avoir la même valeur en développement local. En production, ils doivent être différents (voir [déploiement VPS](#étape-1--se-connecter-au-vps-et-récupérer-les-scripts)).

---

## Déploiement sur un VPS (production)

### Étape 1 : Se connecter au VPS et récupérer les scripts

```bash
# Depuis votre machine locale, se connecter au VPS
ssh debian@<VPS_IP>
# ou sur Ubuntu : ssh ubuntu@<VPS_IP>
```

```bash
# Sur le VPS : cloner le repo dans un dossier temporaire pour récupérer les scripts
git clone -b ci-cd-pipeline https://github.com/dylanholin/cybersec-bad-folio.git /tmp/devfolio-setup
```

> Ce clone temporaire sert uniquement à récupérer `hardening.sh` et `deploy.sh`. Il sera supprimé à la fin. Le vrai déploiement clonera le repo dans `/opt/devfolio`.

---

### Étape 2 : Durcir le serveur

```bash
# Lancer le script de durcissement (en root via sudo)
# ADMIN_USER=debian (ou ubuntu sur Ubuntu) évite le lockout SSH
# en gardant l'utilisateur admin dans AllowUsers
sudo ADMIN_USER=debian /tmp/devfolio-setup/hardening.sh
```

Le script va :
1. Mettre à jour le système (`apt update && apt upgrade`)
2. Installer Docker, UFW, fail2ban, curl
3. Créer l'utilisateur `deploy` (groupe docker, pas de mot de passe)
4. Durcir SSH (clés uniquement, root interdit, `AllowUsers deploy debian`)
5. Configurer UFW (deny entrant, ouvrir 22/80/443)
6. Configurer fail2ban (jail sshd, 3 tentatives max, ban 1h)
7. Configurer le filet DOCKER-USER (bloquer 3306 et 8080 même si Docker contourne UFW)
8. Capturer une baseline dans `/root/baseline-<date>.txt`

Le script demande confirmation avant de redémarrer SSH. **Ne fermez pas votre session courante avant d'avoir testé une nouvelle connexion.**

---

### Étape 3 : Déposer la clé SSH pour l'utilisateur `deploy`

L'utilisateur `deploy` a été créé sans mot de passe. Il faut lui donner accès à votre clé SSH.

```bash
# Copier votre clé SSH publique vers le compte deploy
sudo mkdir -p /home/deploy/.ssh
sudo cp ~/.ssh/authorized_keys /home/deploy/.ssh/
sudo chown -R deploy:deploy /home/deploy/.ssh
sudo chmod 700 /home/deploy/.ssh
sudo chmod 600 /home/deploy/.ssh/authorized_keys
```

```bash
# Tester la connexion SSH avec deploy (depuis une autre session, sans fermer celle-ci)
# Sur votre machine locale :
ssh deploy@<VPS_IP>
```

> Si la connexion échoue, vérifiez que `authorized_keys` contient bien votre clé publique. Ne fermez **jamais** votre session admin avant d'avoir confirmé que la connexion `deploy` fonctionne.

---

### Étape 4 : Déployer l'application

```bash
# Passer sur l'utilisateur deploy (pas root)
sudo su - deploy

# Lancer le déploiement
# Le script va : cloner le repo dans /opt/devfolio, créer .env avec des secrets
# générés automatiquement, build les images Docker, démarrer les conteneurs
bash /tmp/devfolio-setup/deploy.sh ci-cd-pipeline
```

Le script va :
1. Cloner le repo dans `/opt/devfolio` (branche `ci-cd-pipeline`)
2. Créer `.env` à partir de `.env.example` avec des secrets générés (`openssl rand`)
3. Vous demander de vérifier le `.env` et de confirmer
4. Build et démarrer les conteneurs (`docker compose up --build -d`)
5. Attendre le healthcheck MariaDB
6. Exécuter des tests de sécurité (injection SQL, JWT alg:none, SSRF, actuator)

> **Important** : `ADMIN_PASSWORD` dans le `.env` est défini sur `DevfolioAdmin2024!` (correspond au hash BCrypt dans `init-template.sql`). Ne le modifiez pas sans mettre à jour le hash dans le SQL.

---

### Étape 5 : Vérifier

```bash
# Les conteneurs doivent être Up (MariaDB Healthy, backend Started)
docker compose -f /opt/devfolio/docker-compose.yml ps
```

Sortie attendue :
```
NAME                  STATUS
devfolio-mariadb-1    Up (healthy)
devfolio-backend-1    Up (health: starting → healthy)
devfolio-frontend-1   Up
```

```bash
# Le backend doit répondre (healthcheck public)
curl -s http://127.0.0.1:8080/actuator/health
# → {"status":"UP"}
```

```bash
# Le site doit être accessible depuis un navigateur
# https://<VPS_IP> (certificat auto-signé, accepter l'avertissement)
```

> Si vous obtenez une page "Welcome to nginx!" au lieu du site DevFolio, passez à l'Étape 6. C'est que le nginx hôte n'a pas encore de config DevFolio.

---

### Étape 6 : Configurer nginx hôte (reverse proxy TLS)

> **Pourquoi cette étape ?** Les conteneurs écoutent uniquement sur `127.0.0.1` (frontend sur `3000`, backend sur `8080`). Pour qu'ils soient accessibles depuis l'extérieur en HTTPS, il faut un reverse proxy sur le port 80/443 qui termine le TLS et redirige vers les conteneurs. C'est le rôle du nginx installé sur l'hôte (pas le nginx du conteneur frontend).
>
> Cette config n'est **pas** versionnée dans le repo : elle contient des chemins et certificats spécifiques au VPS. Elle se crée manuellement après le premier déploiement.

```bash
# En root (sudo -i) sur le VPS

# 1. Certificat auto-signé (sans nom de domaine ; sinon utiliser Let's Encrypt)
mkdir -p /etc/nginx/ssl
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -subj "/CN=localhost" \
  -keyout /etc/nginx/ssl/devfolio.key \
  -out /etc/nginx/ssl/devfolio.crt
chmod 600 /etc/nginx/ssl/devfolio.key

# 2. Config reverse proxy
tee /etc/nginx/conf.d/devfolio.conf <<'EOF'
server {
    listen 80 default_server;
    server_name _;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl default_server;
    server_name _;

    ssl_certificate     /etc/nginx/ssl/devfolio.crt;
    ssl_certificate_key /etc/nginx/ssl/devfolio.key;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;

    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /actuator/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
EOF

# 3. Supprimer une éventuelle config par défaut qui capterait les ports 80/443
rm -f /etc/nginx/sites-enabled/default
rm -f /etc/nginx/conf.d/default.conf

# 4. Valider et redémarrer
nginx -t && systemctl restart nginx
```

> **Redéploiement sur un VPS existant** : si une ancienne config DevFolio existe déjà (dans `sites-enabled/devfolio` par exemple), supprimez-la avant de recréer `conf.d/devfolio.conf`, sinon nginx affichera un warning `conflicting server name "_" on 0.0.0.0:80, ignored` :
> ```bash
> rm -f /etc/nginx/sites-enabled/devfolio
> ```

### Vérifier que le site est accessible

```bash
# Test login depuis le VPS (doit retourner un token JWT)
curl -sk -X POST https://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@devfolio.com","password":"DevfolioAdmin2024!"}'
# → {"token":"eyJ...","user":{...}}

# Actuator /env doit retourner 401 (sécurisé par Spring Security)
curl -sk -o /dev/null -w "%{http_code}\n" https://localhost/actuator/env
# → 401
```

Puis depuis un navigateur : `https://<VPS_IP>` (accepter le certificat auto-signé).

> **Si le login retourne 403 dans le navigateur mais 200 en curl** : c'est un problème CORS. Voir la section [Dépannage](#dépannage) ci-dessous.

---

## Après le déploiement initial

### Nettoyer le dossier temporaire

```bash
# Revenir à l'utilisateur debian/ubuntu
exit

# Supprimer le clone temporaire
rm -rf /tmp/devfolio-setup
```

### Déploiements suivants (CI/CD)

Si vous avez configuré les GitHub Secrets (voir [docs/ci-cd/06-deploiement-continu.md](ci-cd/06-deploiement-continu.md)), chaque push sur `ci-cd-pipeline` déclenche automatiquement :

1. Build des images Docker (backend + frontend)
2. Scan Trivy (vulnérabilités HIGH/CRITICAL bloquantes)
3. Push des images sur GHCR (taggées avec le SHA du commit)
4. Déploiement sur le VPS via SSH (`docker compose -f docker-compose.staging.yml pull && up -d`)

Aucune intervention manuelle nécessaire après le premier déploiement.

### Déploiements manuels (sans CI/CD)

Pour relancer un déploiement manuellement :

```bash
# En tant que deploy
cd /opt/devfolio
git fetch origin
git checkout ci-cd-pipeline
git reset --hard origin/ci-cd-pipeline
docker compose up --build -d
```

### Modifier le `.env` (CORS, secrets, etc.)

> **Important** : `docker compose restart` **ne relit pas** le `.env`. Il redémarre le conteneur avec les variables d'environnement qu'il avait au moment de sa création. Pour appliquer un changement de `.env`, il faut **recréer** le conteneur.

```bash
# En tant que deploy, dans /opt/devfolio

# 1. Éditer le .env
nano .env
# (par exemple : CORS_ALLOWED_ORIGINS=https://<VPS_IP>)

# 2. Recréer les conteneurs pour qu'ils relisent le .env
docker compose up -d --force-recreate

# 3. Vérifier que la nouvelle valeur est bien prise en compte
docker exec devfolio-backend-1 printenv CORS_ALLOWED_ORIGINS
# → https://<VPS_IP>
```

> **Cas fréquent : `CORS_ALLOWED_ORIGINS`**. Après un déploiement initial, cette variable contient souvent `http://localhost:5173,http://localhost` (valeur par défaut). Il faut la remplacer par `https://<VPS_IP>` pour que le login fonctionne depuis le navigateur. Sinon le backend renvoie `403 Invalid CORS request` sur les requêtes POST.
>
> **Pourquoi `--force-recreate` et pas `restart` ?** Docker Compose charge le `.env` au moment de créer le conteneur (étape `up`). `restart` ne fait que redémarrer le processus existant avec le même environnement. `--force-recreate` détruit et recrée le conteneur, donc relit le `.env`.

---

## Dépannage

### Erreurs de déploiement

| Problème | Solution |
|----------|----------|
| `git: command not found` | `sudo apt update && sudo apt install -y git` |
| `sudo: command not found` | Depuis root : `apt install -y sudo` puis ajouter votre user au groupe sudo |
| SSH lockout après hardening | Se connecter en console VPS (hors SSH), éditer `/etc/ssh/sshd_config.d/99-devfolio-hardening.conf`, ajouter votre user dans `AllowUsers` |
| `hardening.sh` : "Service SSH non trouvé" | Debian 13 utilise `ssh.socket`. Le script le détecte depuis le commit `51c96b5`. Si vous avez une ancienne version, faire `git pull` puis relancer |
| MariaDB ne démarre pas | Vérifier `.env` : `DB_ROOT_PASSWORD` doit correspondre au mot de passe root du volume existant. Si volume vierge (premier démarrage), n'importe quelle valeur fonctionne |
| `.env` écrasé par `deploy.sh` | Si un `.env` existait avant, `deploy.sh` le détecte et ne l'écrase pas. Si écrasé accidentellement, récupérer les anciens secrets via `docker exec devfolio-mariadb-1 printenv MYSQL_ROOT_PASSWORD` |
| Backend ne démarre pas | `docker compose logs backend`, vérifier que `DB_PASSWORD` dans `.env` correspond à ce que `init-template.sql` a utilisé pour créer l'utilisateur `devfolio_app` |
| `IMAGE_TAG` vide dans `.env` | Ajouter `IMAGE_TAG=manual` dans `/opt/devfolio/.env` (le CI/CD le mettra à jour automatiquement) |
| `deploy.sh` s'arrête au test de login | Bug `pipefail` corrigé depuis le commit `51c96b5`. Si vous avez une ancienne version, faire `git pull` |

### Erreurs d'accès au site (après déploiement)

| Problème | Solution |
|----------|----------|
| Page "Welcome to nginx!" au lieu du site | Le nginx hôte n'a pas de config DevFolio. Suivre l'[Étape 6](#étape-6--configurer-nginx-hôte-reverse-proxy-tls). Ne pas désactiver nginx : c'est lui qui gère le TLS, le frontend Docker écoute en HTTP sur `127.0.0.1:3000` |
| `conflicting server name "_" on 0.0.0.0:80, ignored` | Une ancienne config DevFolio existe dans `sites-enabled/`. La supprimer : `rm -f /etc/nginx/sites-enabled/devfolio` puis `nginx -t && systemctl reload nginx` |
| Login 403 dans le navigateur, mais 200 en curl | **CORS rejeté**. Vérifier `docker exec devfolio-backend-1 printenv CORS_ALLOWED_ORIGINS` : doit contenir `https://<VPS_IP>`. Si la valeur est encore `http://localhost:5173,http://localhost`, voir [Modifier le `.env`](#modifier-le-env-cors-secrets-etc) |
| Login 403 même après correction du `.env` | `docker compose restart` ne relit pas le `.env`. Utiliser `docker compose up -d --force-recreate` (voir [Modifier le `.env`](#modifier-le-env-cors-secrets-etc)) |
| "JWT signature does not match" dans les logs backend | Un ancien token JWT est stocké dans le sessionStorage du navigateur (déploiement précédent avec un autre `JWT_SECRET`). F12 → Application → Session Storage → supprimer `devfolio_token` et `devfolio_user`, recharger la page |
| `NS_ERROR_NET_RESET` ou connexion refusée | Vérifier que le nginx hôte écoute sur 80/443 : `ss -tlnp | grep -E ':(80|443)\b'`. Si rien n'écoute, démarrer nginx : `systemctl start nginx` |

### Diagnostic CORS détaillé

Si le login retourne 403 dans le navigateur mais 200 en curl depuis le VPS :

1. **Identifier la cause** : une réponse 403 sans header `Content-Type` et avec un body court (~20 octets) est typique du rejet CORS par Spring Security (`Invalid CORS request`).

2. **Vérifier la valeur dans le conteneur backend** :
   ```bash
   docker exec devfolio-backend-1 printenv CORS_ALLOWED_ORIGINS
   ```
   - Si la valeur est `http://localhost:5173,http://localhost` : le `.env` n'a pas été mis à jour, ou le conteneur n'a pas été recréé.

3. **Corriger le `.env`** :
   ```bash
   cd /opt/devfolio
   sed -i 's|^CORS_ALLOWED_ORIGINS=.*|CORS_ALLOWED_ORIGINS=https://<VPS_IP>|' .env
   grep CORS_ALLOWED_ORIGINS .env  # vérifier
   ```

4. **Recréer le conteneur backend** :
   ```bash
   docker compose up -d --force-recreate backend
   docker exec devfolio-backend-1 printenv CORS_ALLOWED_ORIGINS  # doit afficher la nouvelle valeur
   ```

5. **Vider le sessionStorage du navigateur** : F12 → Application → Session Storage → supprimer `devfolio_token` et `devfolio_user`, recharger la page.

6. **Tester** : se connecter à `https://<VPS_IP>/login`.

---

## Voir aussi

- [docs/securite/07-durcissement-serveur.md](securite/07-durcissement-serveur.md) : Détails techniques du durcissement
- [docs/securite/08-deploiement-verification.md](securite/08-deploiement-verification.md) : Vérifications post-déploiement
- [docs/ci-cd/01-infrastructure-vps.md](ci-cd/01-infrastructure-vps.md) : Configuration des GitHub Secrets
- [docs/ci-cd/06-deploiement-continu.md](ci-cd/06-deploiement-continu.md) : Détails du job deploy
