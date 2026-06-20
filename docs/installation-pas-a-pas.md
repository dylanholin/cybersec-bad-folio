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

---

## Dépannage

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
| Port 80/443 déjà utilisé | Si nginx est installé sur l'hôte : `sudo systemctl stop nginx && sudo systemctl disable nginx` (le frontend Docker gère le TLS) |
| `deploy.sh` s'arrête au test de login | Bug `pipefail` corrigé depuis le commit `51c96b5`. Si vous avez une ancienne version, faire `git pull` |

---

## Voir aussi

- [docs/securite/07-durcissement-serveur.md](securite/07-durcissement-serveur.md) : Détails techniques du durcissement
- [docs/securite/08-deploiement-verification.md](securite/08-deploiement-verification.md) : Vérifications post-déploiement
- [docs/ci-cd/01-infrastructure-vps.md](ci-cd/01-infrastructure-vps.md) : Configuration des GitHub Secrets
- [docs/ci-cd/06-deploiement-continu.md](ci-cd/06-deploiement-continu.md) : Détails du job deploy
