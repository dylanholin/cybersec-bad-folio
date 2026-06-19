# Phase 4 — Déploiement continu (CD)

> Déploiement automatique sur le VPS via SSH. Le plan initial est dans [`00-depart.md`](./00-depart.md).

---

## Objectif

Après la build, le scan Trivy et le push des images sur GHCR, le job `deploy` se connecte au VPS via SSH pour mettre à jour l'application en production de manière automatique et reproductible.

> **Pour un débutant** : le déploiement continu (CD) est l'étape finale du pipeline. Une fois que le code a passé tous les tests et que les images Docker sont construites et scannées, le pipeline se connecte automatiquement au serveur de production (VPS) pour y installer la nouvelle version. Aucune intervention humaine n'est nécessaire.
>
> **Analogie** : imaginez un restaurant. La CI (tests + build) c'est la préparation en cuisine — on vérifie que les ingrédients sont bons et on cuisine le plat. La CD (déploiement) c'est le service en salle — on apporte le plat au client. Ici, le "client" c'est le serveur VPS qui fait tourner l'application.

---

## Job `deploy`

| Étape | Action |
|---|---|
| **Condition** | `if: github.ref == 'refs/heads/ci-cd-pipeline'` — ne se déclenche que sur push direct sur la branche |
| **Dépendance** | `needs: [build-and-push]` — attend que les images soient poussées sur GHCR |
| **Connexion SSH** | `appleboy/ssh-action@v1.2.0` avec clé privée stockée en GitHub Secret |
| **Mise à jour IMAGE_TAG** | `sed -i` dans `.env` sur le VPS pour pointer vers le SHA du commit |
| **Récupération fichiers** | `git fetch` + `git checkout` du `docker-compose.staging.yml` et `nginx.staging.conf` à jour |
| **Login GHCR** | Authentification Docker avec un PAT (`VPS_GHCR_TOKEN`) pour pull les images privées |
| **Pull + restart** | `docker compose pull` puis `docker compose up -d` |
| **Healthcheck** | Boucle de 12 tentatives × 5s = 60s max sur `/actuator/health` |
| **Rollback logs** | Si échec, affiche les 50 dernières lignes de log du backend |

---

## GitHub Secrets requis

| Secret | Description |
|---|---|
| `VPS_HOST` | Adresse IP du VPS (ou nom de domaine) |
| `VPS_USER` | Utilisateur SSH dédié au déploiement (voir ci-dessous) |
| `VPS_SSH_PRIVATE_KEY` | Clé privée SSH pour la connexion |
| `VPS_SSH_PORT` | Port SSH (défaut : 22) |
| `VPS_PROJECT_DIR` | Chemin du projet sur le VPS (défaut : `/opt/devfolio`) |
| `VPS_GHCR_TOKEN` | Personal Access Token GitHub avec scope `read:packages` |

> **Sécurité** : Le `GITHUB_TOKEN` automatique de GitHub Actions n'est pas utilisé pour le pull depuis le VPS car il est éphémère. Un PAT dédié avec scope minimal `read:packages` est stocké en secret et transmis via `envs` de `appleboy/ssh-action` (non exposé dans les logs).

### Pourquoi un compte `deploy` dédié ?

Le secret `VPS_USER` doit contenir **`deploy`**, le compte créé lors du durcissement du serveur (voir [`docs/securite/07-durcissement-serveur.md`](../securite/07-durcissement-serveur.md)).

**Raisons de sécurité :**

- **Moindre privilège** : `deploy` n'a pas de mot de passe, uniquement une clé SSH. Il ne peut pas se connecter en interactif sans la clé.
- **Isolation des responsabilités** : `deploy` est dédié au déploiement automatique. Les opérations manuelles (debug, maintenance) utilisent le compte `debian` ou un autre utilisateur admin.
- **Auditabilité** : les actions de déploiement sont tracées dans les logs système sous l'identité `deploy`, pas sous un compte partagé.
- **Groupe `docker` contrôlé** : `deploy` appartient au groupe `docker` (nécessaire pour `docker compose`), mais n'a pas de privilèges `sudo` par défaut. L'accès au groupe `docker` est équivalent à un accès root (possibilité de monter `/` dans un conteneur), d'où l'importance de limiter ce groupe au strict nécessaire.
- **SSH restreint** : la configuration SSH (`AllowUsers deploy`) n'autorise que ce compte pour les connexions distantes automatisées, avec `PermitRootLogin no`.

> **Attention** : si `deploy` est créé manuellement **avant** le script de durcissement, vérifier que le groupe `docker` est bien attribué : `id deploy`. Sans ce groupe, `docker compose` échouera avec une erreur de permission.

### Permissions du répertoire `/opt/devfolio`

Le répertoire `/opt/devfolio` et tout son contenu (y compris `.git/` et `.env`) doivent appartenir à `deploy` :

```bash
# Sur le VPS, en root, une seule fois :
chown -R deploy:deploy /opt/devfolio
chmod 600 /opt/devfolio/.env
```

> **Attention** : si les fichiers appartiennent à un autre utilisateur (`root`, `debian`), le job `deploy` peut échouer avec des erreurs de permission (`git fetch`, écriture `.env`). Le job `deploy` se connecte en tant que `deploy`, pas en tant que `root`.

---

## Flux de déploiement

```
push sur ci-cd-pipeline
  → test-backend (JUnit)
  → test-frontend (Vitest)
  → scan-sast (Semgrep)
  → build-and-push (Docker + Trivy + GHCR)
  → deploy (SSH → VPS)
      → sed IMAGE_TAG dans .env
      → git checkout docker-compose.staging.yml nginx.staging.conf
      → docker login ghcr.io
      → docker compose pull
      → docker compose up -d
      → healthcheck /actuator/health (60s max)
      → succès ou rollback logs
```

---

## Configuration manuelle sur le VPS

> **Le job `deploy` ne modifie pas tout automatiquement.** Certaines configurations doivent être définies manuellement une fois sur le VPS.

### `CORS_ALLOWED_ORIGINS`

La variable `CORS_ALLOWED_ORIGINS` dans `.env` doit contenir l'URL publique du VPS. Le job `deploy` ne la met pas à jour automatiquement.

```bash
# Sur le VPS, une seule fois :
cd /opt/devfolio
sed -i 's/^CORS_ALLOWED_ORIGINS=.*/CORS_ALLOWED_ORIGINS=https:\/\/<VPS_IP>/' .env
docker compose -f docker-compose.staging.yml up -d backend
```

> Si cette variable reste à `localhost`, le backend rejette les requêtes du navigateur avec une erreur 403 CORS. Le curl local fonctionne car il n'envoie pas d'en-tête `Origin`.

### Nginx hôte

La configuration Nginx sur l'hôte (`/etc/nginx/sites-enabled/devfolio`) est installée manuellement à la Phase 1. Le job `deploy` ne la modifie pas. Voir [`01-infrastructure-vps.md`](./01-infrastructure-vps.md) pour le détail.

---

## Correctifs appliqués

### Correctif healthcheck — Run #1

| Problème | Cause | Fix |
|---|---|---|
| Backend 401 sur healthcheck | URL `/api/actuator/health` invalide en accès direct (le préfixe `/api` est ajouté par Nginx, pas par le backend) | Correction vers `/actuator/health` dans `ci.yml` et `docker-compose.staging.yml` |

> Le backend Spring Boot n'a pas de `server.servlet.context-path=/api`. Le préfixe `/api` est ajouté par le reverse proxy Nginx sur l'hôte. En accès direct (`127.0.0.1:8080`), l'endpoint actuator est sur `/actuator/health` (sans `/api`).

### Run #2 — Succès ✅

| Résultat | Détail |
|---|---|
| **Status** | Success |
| **SSH** | Connexion réussie avec clé sans passphrase |
| **Pull** | Images `devfolio-backend` et `devfolio-frontend` tirées depuis GHCR |
| **Conteneurs** | MariaDB healthy, backend + frontend recréés et démarrés |
| **Healthcheck** | `Backend OK` après 2 tentatives (~10s) |
| **Déploiement** | Terminé avec succès |

### Correctif frontend nginx — Run #3

| Problème | Cause | Fix |
|---|---|---|
| Frontend 301 Moved Permanently (site inaccessible) | Le conteneur frontend Docker redirige HTTP vers HTTPS (port 80 → 443), mais le port 443 n'est pas mappé sur l'hôte. Le Nginx hôte proxy vers `127.0.0.1:3000` (port 80) qui redirige au lieu de servir le contenu | Création de `nginx.staging.conf` (HTTP simple sans redirection TLS) monté en volume dans `docker-compose.staging.yml` |

> En staging, le Nginx hôte gère déjà le TLS (certificat auto-signé). Le conteneur frontend n'a pas besoin de faire de redirection HTTP → HTTPS. `nginx.staging.conf` sert le contenu statique sur le port 80 sans TLS ni redirection.

---

## Volume de persistance MariaDB

Le volume nommé `db_data` dans `docker-compose.staging.yml` garantit que les données de la base persistent entre les redéploiements, même si le conteneur MariaDB est supprimé et recréé.

```yaml
volumes:
  - db_data:/var/lib/mysql  # volume nommé — persiste entre les déploiements
```
