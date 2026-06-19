# Phase 1 — Infrastructure VPS

> Préparation du serveur de production. Le plan initial est dans [`00-depart.md`](./00-depart.md).

---

> **Pour un débutant** : un VPS (Virtual Private Server) est un serveur loué chez un hébergeur (ici Scaleway). Contrairement à votre ordinateur personnel, il est :
> - **Public** : accessible depuis Internet 24/7 (d'où l'importance de la sécurité)
> - **Minimaliste** : seul Docker est installé, pas de Java ni Node.js
> - **Persistant** : il tourne même quand vous éteignez votre PC
>
> **Pourquoi sécuriser le VPS ?** : un serveur public est constamment ciblé par des bots (brute-force SSH, scan de ports). Les mesures ci-dessous (fail2ban, UFW, compte dédié) réduisent la surface d'attaque.

---

## Sécurisation du serveur

| Service | Version | Configuration |
|---|---|---|
| **OS** | Debian 13 (trixie) | Kernel 6.12.90 |
| **fail2ban** | 1.1.0 | Jail sshd : `bantime = 3600`, `maxretry = 3` |
| **UFW** | 0.36.2 | `deny incoming`, `allow outgoing`, ports 22/80/443 |
| **Nginx** | 1.26.3 | Reverse proxy sur l'hôte (Option A) |
| **Docker** | 29.5.3 | Compose v2.x (plugin) |

---

## Compte `deploy` dédié

Un compte utilisateur `deploy` est créé lors du durcissement du serveur (voir [`docs/securite/07-durcissement-serveur.md`](../securite/07-durcissement-serveur.md)). Ce compte est utilisé par le job `deploy` de GitHub Actions pour se connecter au VPS via SSH.

| Propriété | Valeur |
|---|---|
| **Utilisateur** | `deploy` (sans mot de passe, clé SSH uniquement) |
| **Groupe** | `docker` (nécessaire pour `docker compose`) |
| **Répertoire projet** | `/opt/devfolio` (propriété `deploy:deploy`) |
| **SSH** | `AllowUsers deploy debian` dans `/etc/ssh/sshd_config` |
| **sudo** | Non accordé par défaut (moindre privilège) |

> Le secret GitHub `VPS_USER` doit contenir `deploy`. Voir [`06-deploiement-continu.md`](./06-deploiement-continu.md) pour le détail.

---

## Nginx — Reverse proxy hôte

Le conteneur frontend Docker d'origine occupait les ports 80/443 de l'hôte. Il a été reconfiguré pour écouter sur le port 3000 (mappé sur `127.0.0.1:3000`), et Nginx a été installé sur l'hôte pour gérer le TLS et le reverse proxy :

```
Internet (443/80)
    │
    ▼
  Nginx (hôte) ── TLS auto-signé (365 jours, CN=<VPS_IP>)
    │
    ├── /api  ──▶ 127.0.0.1:8080  (backend container)
    └── /     ──▶ 127.0.0.1:3000  (frontend container)
```

- HTTP → redirection 301 vers HTTPS
- En-têtes de sécurité : HSTS, CSP, X-Frame-Options, X-Content-Type-Options, Referrer-Policy
- Certificat auto-signé (pas de nom de domaine → pas de Let's Encrypt)

### Configuration Nginx hôte

Fichier : `/etc/nginx/sites-enabled/devfolio`

```nginx
server {
    listen 80;
    server_name _;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name _;

    ssl_certificate /etc/nginx/ssl/nginx.crt;
    ssl_certificate_key /etc/nginx/ssl/nginx.key;

    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'self';" always;

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        proxy_pass http://127.0.0.1:3000/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## Docker sur le VPS

- **Conteneurs actifs** : backend, frontend et MariaDB sur `127.0.0.1` uniquement
- **Conteneur frontend** : écoute sur `127.0.0.1:3000` (port 80 du conteneur, proxy via Nginx hôte)
- **Réseaux isolés** : `frontend-backend`, `backend-db`
- **Volume persistant** : `db_data` pour MariaDB
- **Ports exposés** : aucun port Docker exposé publiquement (tout via `127.0.0.1`)

---

## Fichier `.env` sur le VPS

Créé manuellement sur le VPS (non versionné, dans `.gitignore`) :

| Variable | Description |
|---|---|
| `DB_NAME` | Nom de la base de données |
| `DB_USER` / `DB_PASSWORD` | Utilisateur applicatif MariaDB |
| `JWT_SECRET` | Secret JWT (48 caractères base64, généré via `openssl rand -base64 48`) |
| `JWT_EXPIRATION` | Durée de validité du token (ms) |
| `CORS_ALLOWED_ORIGINS` | Origines autorisées pour CORS (`https://<VPS_IP>`) |
| `ADMIN_PASSWORD` | Mot de passe du compte admin seed |
| `IMAGE_TAG` | Tag des images Docker (`manual` pour le déploiement initial) |

> **Important** : `CORS_ALLOWED_ORIGINS` doit être défini avec l'URL publique du VPS (`https://<VPS_IP>`). Si laissé à `localhost`, le backend rejette les requêtes du navigateur (403 CORS). Cette configuration est **manuelle** sur le VPS, le job `deploy` ne la modifie pas automatiquement.
