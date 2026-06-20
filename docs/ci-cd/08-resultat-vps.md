# Résultat : déploiement et sécurité sur le VPS réel

> Bilan final du Kit 2 (CI/CD). Ce document récapitule l'état du serveur VPS en production :
> pipeline déployé, sécurité vérifiée, et commandes utilisées pour l'audit.

---

## 1. Pipeline CI/CD déployé

### 1.1 Jobs du workflow `.github/workflows/ci.yml`

| Job | Rôle | Statut |
|-----|------|--------|
| `test-backend` | Tests JUnit 5 + Mockito (`mvn clean test -B`) | ✅ |
| `test-frontend` | Tests Vitest + build Vite (`npm ci && npm test && npm run build`) | ✅ |
| `scan-sast` | Semgrep (SAST, non-bloquant, rapport SARIF uploadé) | ✅ |
| `build-and-push` | Build images Docker + scan Trivy (bloquant HIGH/CRITICAL) + push GHCR | ✅ |
| `deploy` | SSH vers le VPS, pull images, `docker compose up -d`, healthcheck | ✅ |

### 1.2 Déclenchement

- **Push** sur `ci-cd-pipeline` → pipeline complet → déploiement automatique sur le VPS
- **Pull request** sur `ci-cd-pipeline` → pipeline sans déploiement (le job `deploy` a `if: github.ref == 'refs/heads/ci-cd-pipeline'`)

### 1.3 Images déployées

| Image | Tag | Registre |
|-------|-----|----------|
| `devfolio-backend` | SHA du commit (pas de `latest`) | GHCR |
| `devfolio-frontend` | SHA du commit (pas de `latest`) | GHCR |

---

## 2. Sécurité du VPS — vérifications réelles

> Toutes les commandes ci-dessous ont été exécutées sur le VPS en production après le
> déploiement. Les résultats sont ceux observés réellement.

### 2.1 Permissions du fichier `.env`

```bash
ls -la /opt/devfolio/.env
```

```
-rw------- 1 deploy deploy 930 Jun 19 15:12 /opt/devfolio/.env
```

| Vérification | Résultat | Statut |
|---|---|---|
| Permissions | `600` (`rw-------`) — seul le propriétaire peut lire/écrire | ✅ |
| Propriétaire | `deploy:deploy` | ✅ |

> Le fichier `.env` contient `JWT_SECRET`, `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS` et
> `IMAGE_TAG`. Des permissions `600` garantissent qu'aucun autre utilisateur du VPS
> ne peut lire ces secrets.

---

### 2.2 fail2ban (bannissement brute-force SSH)

```bash
systemctl status fail2ban
fail2ban-client status sshd
```

```
● fail2ban.service - Fail2Ban Service
     Active: active (running) since Thu 2026-06-18 11:31:50 UTC

Status for the jail: sshd
|- Filter
|  |- Currently failed: 3
|  |- Total failed:     718
|  `- Journal matches:  _SYSTEMD_UNIT=ssh.service + _COMM=sshd
└─ Actions
   |- Currently banned: 2
   |- Total banned:     51
   `- Banned IP list:   <IP_BANNIE_1> <IP_BANNIE_2>
```

| Vérification | Résultat | Statut |
|---|---|---|
| Service | `active (running)` | ✅ |
| Jail `sshd` | Active | ✅ |
| Tentatives échouées totales | 718 (bots scannant internet) | ✅ Normal pour un VPS public |
| IP bannies au total | 51 | ✅ fail2ban fonctionne |
| IP actuellement bannies | 2 (`<IP_BANNIE_1>`, `<IP_BANNIE_2>`) | ✅ |
| Config | `bantime = 3600`, `maxretry = 3` | ✅ |

> **Lecture des chiffres** : 718 tentatives de connexion SSH échouées en 2 jours sont
> des bots automatisés qui scannent internet. fail2ban les bannit après 3 échecs dans
> l'heure. Sans mot de passe sur le compte `deploy` (voir §2.5), ces tentatives ne
> peuvent **jamais** aboutir — mais fail2ban empêche le bruit réseau et les logs.

---

### 2.3 UFW (pare-feu)

```bash
ufw status numbered
```

```
Status: active

     To                         Action      From
     --                         ------      ----
[ 1] 22/tcp                     ALLOW IN    Anywhere
[ 2] 80/tcp                     ALLOW IN    Anywhere
[ 3] 443/tcp                    ALLOW IN    Anywhere
[ 4] 22/tcp (v6)                ALLOW IN    Anywhere (v6)
[ 5] 80/tcp (v6)                ALLOW IN    Anywhere (v6)
[ 6] 443/tcp (v6)               ALLOW IN    Anywhere (v6)
```

| Vérification | Résultat | Statut |
|---|---|---|
| Statut | `active` | ✅ |
| Port 22 (SSH) | Ouvert | ✅ |
| Port 80 (HTTP) | Ouvert (redirigé vers HTTPS par Nginx) | ✅ |
| Port 443 (HTTPS) | Ouvert | ✅ |
| Ports 3306, 8080, 3000 | Non listés = bloqués par défaut | ✅ |

> **Note Docker / UFW** : Docker contourne UFW via `iptables` pour les ports qu'il
> publie. Dans ce projet, les ports internes (3306, 8080, 3000) sont bindés sur
> `127.0.0.1` dans `docker-compose.staging.yml`, donc non exposés publiquement même
> sans règle UFW. Voir `docs/securite/08-deploiement-verification.md` §5.3.

---

### 2.4 Clés SSH

#### Clé personnelle (`id_ed25519`) — connexion en `debian`

```bash
# Sur la machine locale (PowerShell)
ssh-keygen -y -f C:\Users\<USER>\.ssh\id_ed25519
# → demande une passphrase
```

| Vérification | Résultat | Statut |
|---|---|---|
| Passphrase | Présente (demandée à l'utilisation) | ✅ |

> La clé personnelle sert à se connecter manuellement au VPS en `debian` (compte admin).
> La passphrase protège la clé si le fichier est volé sur la machine locale.

#### Clé de déploiement (`devfolio_deploy`) — connexion en `deploy` via GitHub Actions

| Vérification | Résultat | Statut |
|---|---|---|
| Passphrase | Absente (nécessaire pour CI/CD non-interactive) | ✅ Documenté |
| Stockage | GitHub Secret `VPS_SSH_PRIVATE_KEY` (chiffré) | ✅ |
| Compte associé | `deploy` (pas de sudo, groupe `docker` uniquement) | ✅ |

> **Pourquoi pas de passphrase ?** `appleboy/ssh-action` s'exécute dans GitHub Actions
> de façon non-interactive. Si la clé a une passphrase, le job attend une saisie
> clavier qui ne viendra jamais → timeout → déploiement échoué. La clé est dédiée
> au déploiement (pas la clé perso), stockée chiffrée dans GitHub Secrets, et le
> compte `deploy` est limité (pas de sudo). Voir `docs/ci-cd/06-deploiement-continu.md`
> Run #2 : "Connexion réussie avec clé sans passphrase".

---

### 2.5 Compte `deploy` (verrouillage du mot de passe)

```bash
sudo passwd -S deploy
sudo grep "^deploy" /etc/shadow
```

```
deploy L 2026-06-15 0 99999 7 -1

deploy:!:20619:0:99999:7:::
```

| Vérification | Résultat | Statut |
|---|---|---|
| `passwd -S` | `L` (Locked) — mot de passe verrouillé | ✅ |
| `/etc/shadow` | `!` — mot de passe désactivé | ✅ |
| Authentification | Clé SSH uniquement (pas de mot de passe) | ✅ |

> **Pourquoi c'est la protection la plus importante** : un bot peut essayer 10 millions
> de mots de passe, **aucun ne marchera** — il n'y en a pas. La seule façon de se
> connecter en `deploy` est d'avoir la clé privée `devfolio_deploy`, stockée dans
> GitHub Secrets. Le brute-force SSH est **impossible** sur ce compte.

---

## 3. Résumé — toutes les vérifications

| # | Vérification | Résultat | Statut |
|---|---|---|---|
| 1 | `.env` permissions | `600`, propriétaire `deploy:deploy` | ✅ |
| 2 | fail2ban | `active`, 51 bannissements, jail sshd | ✅ |
| 3 | UFW | `active`, ports 22/80/443 uniquement | ✅ |
| 4 | Clé perso `id_ed25519` | Avec passphrase | ✅ |
| 5 | Clé CI/CD `devfolio_deploy` | Sans passphrase (nécessaire, documenté) | ✅ |
| 6 | Compte `deploy` | Mot de passe verrouillé (`L` + `!`) | ✅ |

---

## 4. Limitations connues du projet pédagogique

| Limitation | Impact | Action en production |
|---|---|---|
| Certificat HTTPS auto-signé | Avertissement navigateur, vulnérable au MITM si ignoré | Let's Encrypt avec un nom de domaine |
| `TokenBlacklistService` en mémoire | Blacklist perdue au redémarrage backend (fenêtre 1h max) | Redis avec TTL |
| `RateLimitService` en mémoire | Compteurs reset au redémarrage backend | Redis ou Bucket4j |
| Pas de supervision | Aucune alerte en cas de panne | Netdata ou Prometheus + Grafana |
| Pas de sauvegarde BDD | Perte de données si le volume est corrompu | `mysqldump` périodique + stockage externe |
| Déploiement automatique sans review | Tout push sur `ci-cd-pipeline` déploie en production | Ajouter `environment: production` + required reviewers sur GitHub |

> Ces limitations sont **documentées et acceptées** dans le cadre d'un projet
> pédagogique. Elles sont listées pour montrer la conscience des risques résiduels,
> pas comme des bugs.