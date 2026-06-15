# Itération 3 (partie 2) : déployer l'application et vérifier l'exposition réseau

## Contexte

Le serveur est durci et l'environnement est prêt (cf. [07-durcissement-serveur.md](07-durcissement-serveur.md)). On peut maintenant **déployer l'application** puis **vérifier ce qui est réellement exposé** sur le réseau.

Ce document est le livrable du **déploiement** et de la **vérification réseau**. Il correspond aux étapes 3 à 5 décrites dans [CONTEXT.md](CONTEXT.md). Il suppose la stratégie **Docker Compose** retenue en doc 07.

> **Rappel de la règle d'or :** ne jamais fermer la session SSH active tant qu'une nouvelle connexion n'a pas été validée, et vérifier l'exposition réseau **après** chaque changement de configuration de ports.

---

## 3. Préparer le déploiement de l'application

### 3.1 Récupérer le projet

Le code est récupéré dans le répertoire dédié créé en doc 07 (`/opt/devfolio`), avec le compte `deploy` (pas root).

```bash
# En tant que 'deploy'
git clone -b correction https://github.com/dylanholin/cybersec-bad-folio.git /opt/devfolio
cd /opt/devfolio
```

> On déploie la branche **`correction`** (version sécurisée), jamais `main` (version volontairement vulnérable).

### 3.2 Préparer les fichiers de configuration et les secrets

Le `.env` n'est **pas** dans le dépôt (`.gitignore`). Le script `deploy.sh` le crée automatiquement à partir du template et génère les secrets (`JWT_SECRET`, `DB_PASSWORD`, `ADMIN_PASSWORD`) avec `openssl rand`.

Si vous préparez le `.env` manuellement :

```bash
cp .env.example .env
chmod 600 .env          # lecture par le seul propriétaire

# Générer les secrets (JWT ≥ 48 car., mots de passe ≥ 24 car.)
openssl rand -base64 48   # → JWT_SECRET
openssl rand -base64 24   # → DB_PASSWORD, ADMIN_PASSWORD
```

> **Cohérence Docker :** en déploiement conteneurisé, `DB_HOST` vaut le **nom du service** Docker (`mariadb`), pas `localhost`. La résolution se fait via le réseau Docker interne `backend-db`.

### 3.3 Vérifications avant déploiement

| À vérifier | Comment | Attendu |
|------------|---------|---------|
| Secrets présents et forts | `grep -E 'JWT_SECRET|PASSWORD' .env` | Aucune valeur d'exemple ; `JWT_SECRET` ≥ 48 car. |
| Aucun secret en dur dans le YAML | `grep -iE 'password|secret' docker-compose.yml` | Uniquement `env_file: .env`, pas de valeurs |
| `.env` non versionné | `git check-ignore .env` | Retourne `.env` |
| Permissions `.env` | `stat -c '%a' .env` | `600` |
| Ports publiés | `grep -A2 'ports:' docker-compose.yml` | 80/443 publics ; 3306/8080 non publiés ou bindés `127.0.0.1` |
| Utilisateurs conteneurs | `grep -i user backend/Dockerfile` | `USER appuser` (non root) |

### 3.4 Anticiper les erreurs fréquentes

| Erreur | Symptôme | Prévention |
|--------|----------|------------|
| `JWT_SECRET` trop court | `InvalidKeyException` au démarrage backend | `openssl rand -base64 48` |
| `.env` non chargé | `Could not resolve placeholder 'JWT_SECRET'` | `env_file: .env` présent pour le service `backend` |
| `DB_HOST=localhost` en conteneur | Backend ne joint pas la BDD | Utiliser `DB_HOST=mariadb` |
| Conflit de port 80/443 | `bind: address already in use` | `ss -tulpn` pour repérer un service occupant le port |
| `nginx:alpine` sans `openssl` | Build échoue : `openssl: not found` | Ajouter `RUN apk add --no-cache openssl` dans le Dockerfile frontend |
| `USER nginx` sans permissions | Frontend crash : `mkdir() /var/cache/nginx/client_temp failed` | Préparer les répertoires `RUN mkdir -p /var/cache/nginx/client_temp /var/log/nginx /run && chown -R nginx:nginx ...` avant `USER nginx` |
| init.sql rejoué | Doublons en base | Volume nommé persistant ; ne pas réinitialiser à chaque `up` |
| Permissions volume | MariaDB ne démarre pas | Laisser Docker gérer le volume nommé `db_data` |

---

## 4. Déployer l'application

### 4.1 Lancer la stack

```bash
cd /opt/devfolio
docker compose up --build -d
```

### 4.2 Vérifications post-déploiement

```bash
# État des conteneurs (tous 'healthy'/'running')
docker compose ps

# Logs (repérer 'Started DevfolioApplication' côté backend)
docker compose logs -f backend
docker compose logs mariadb

# Le backend tourne-t-il en non-root ?
docker exec backend whoami            # attendu : appuser

# Quels ports sont réellement publiés par Docker ?
docker compose ps --format 'table {{.Service}}\t{{.Ports}}'
ss -tulpn
```

| Contrôle | Commande | Attendu |
|----------|----------|---------|
| Conteneurs actifs | `docker compose ps` | frontend, backend, mariadb up ; MariaDB `healthy` |
| Backend non root | `docker exec backend whoami` | `appuser` |
| Réseaux isolés | `docker network ls` ; `docker inspect backend` | `frontend-backend` + `backend-db` |
| Volume nommé | `docker volume ls` | `db_data` (pas de bind mount `/var/lib/mysql`) |
| Pas de privilège | `docker inspect --format '{{.HostConfig.Privileged}}' backend` | `false` |

### 4.3 Questions de durcissement Docker à se poser

- Le conteneur tourne-t-il en root ? → backend `appuser` ; envisager `USER nginx` côté frontend (risque restant connu, cf. doc 06).
- Tous les ports exposés sont-ils nécessaires ? → seuls 80/443 doivent être publics. 8080 ne doit pas être publié sur `0.0.0.0` (risque restant doc 06 : à binder sur `127.0.0.1` ou supprimer).
- Les volumes montés sont-ils raisonnables ? → uniquement `db_data` (nommé) et le montage en lecture seule de `init.sql`.
- Des secrets apparaissent-ils dans les variables d'environnement ? → injectés via `env_file`, jamais en dur dans le YAML.
- Les images sont-elles récentes et minimales ? → `eclipse-temurin:21-jre-alpine`, `nginx:alpine`, `mariadb:10.11`.

### 4.4 Tester l'application

Depuis le serveur lui-même :

```bash
# Frontend (HTTPS, certificat auto-signé en démo -> -k)
curl -ks -o /dev/null -w "%{http_code}\n" https://localhost/        # 200
# Redirection HTTP -> HTTPS
curl -s -o /dev/null -w "%{http_code}\n" http://localhost/          # 301
# API via reverse proxy
curl -ks https://localhost/api/projects                             # JSON projets publics
# Login
curl -ks -X POST https://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@devfolio.com","password":"<ADMIN_PASSWORD>"}'  # token JWT
```

Depuis une autre machine du réseau : remplacer `localhost` par l'IP/nom du serveur et vérifier que **seuls** 80/443 répondent.

Avec des entrées inhabituelles (régressions de sécurité) — réutiliser les tests de la [doc 06 §5](06-corriger-essentiel-demo.md#5-checklist-de-préparation-au-déploiement) :

| Test | Requête | Attendu |
|------|---------|---------|
| Injection SQL | `GET /api/search/projects?q=' OR '1'='1` | Résultat vide |
| JWT alg:none | token forgé `eyJhbGciOiJub25lIn0...` sur `/api/admin/users` | 401 |
| Admin sans token | `GET /api/admin/users` | 401 |
| Actuator env | `GET /actuator/env` | 401/403 |
| SSRF avatar | `POST /api/users/avatar?url=http://169.254.169.254/` | 400 |

> Le script `verify_security.sh` ([doc 06 §7](06-corriger-essentiel-demo.md#7-automatisation-des-vérifications-de-sécurité)) automatise ces vérifications. Sur le serveur : `./verify_security.sh https://localhost`.

---

## 5. Vérifier l'exposition réseau

Une application qui « répond » n'est pas forcément correctement exposée. On vérifie ici **ce qui est réellement accessible**, en particulier depuis l'extérieur.

### 5.1 Scan local des ports en écoute

```bash
ss -tulpn
```

On compare avec la baseline de la doc 07 et avec la liste cible : seuls 22 (SSH), 80, 443 doivent écouter sur une interface publique. Tout port `0.0.0.0:3306` ou `0.0.0.0:8080` est une **anomalie**.

### 5.2 Scan depuis une autre machine

```bash
# Depuis un poste distinct, sur le réseau
nmap -Pn -p- <serveur>            # balayage complet des ports TCP
nmap -sV -p 22,80,443,3306,8080 <serveur>   # détection de version sur les ports clés
```

| Port | Attendu depuis l'extérieur |
|------|----------------------------|
| 22 | open (administration) |
| 80 | open → redirige vers 443 |
| 443 | open |
| 3306 | **closed/filtered** (MariaDB jamais exposée) |
| 8080 | **closed/filtered** (backend via reverse proxy uniquement) |
| 5005 | **closed/filtered** (debug JVM supprimé) |

### 5.3 Le piège Docker / UFW

Docker écrit ses propres règles `iptables` et **court-circuite** UFW pour les ports qu'il publie. Conséquence : un port déclaré dans `ports:` peut rester joignable de l'extérieur **malgré** `ufw deny`.

**Vérification :** si `nmap` montre 3306 ou 8080 ouverts alors qu'UFW les refuse, c'est Docker qui les publie.

**Corrections possibles :**

```yaml
# Option A (préférée) : ne pas publier les ports internes du tout
#   -> supprimer la section 'ports:' pour mariadb et backend
#   La communication passe par les réseaux Docker internes.

# Option B : binder explicitement sur la loopback
services:
  backend:
    ports:
      - "127.0.0.1:8080:8080"   # accessible seulement depuis l'hôte
```

```bash
# Option C : restreindre dans la chaîne DOCKER-USER (persiste après redémarrage Docker)
sudo iptables -I DOCKER-USER -p tcp --dport 3306 -j DROP
sudo iptables -I DOCKER-USER -p tcp --dport 8080 -j DROP
```

> Référence détaillée : [04-infrastructure.md](04-infrastructure.md) (chaîne `DOCKER-USER`, tableau des ports après durcissement).

### 5.4 Informations exposées à inspecter

```bash
# En-têtes de réponse : pas de divulgation de version, en-têtes de sécurité présents
curl -ksI https://localhost/

# Pas de stacktrace dans les erreurs
curl -ks https://localhost/api/inexistant
```

Chercher à identifier :

- des **ports oubliés** (services lancés automatiquement par un paquet installé) ;
- des **services de debug** (port 5005, actuator non protégé) ;
- des **interfaces d'administration** exposées (`/actuator/**`, console BDD) ;
- des **messages d'erreur trop détaillés** (stacktraces — doit être `include-stacktrace=never`) ;
- des **informations sensibles** (versions logicielles dans les en-têtes, hashes dans le JSON — neutralisés par `@JsonIgnore`) ;
- des **services qui ne devraient écouter qu'en local** (MariaDB).

### 5.5 Revue croisée

Si possible, faire vérifier l'exposition par une autre équipe depuis une machine distincte : un regard externe repère souvent un port ou un endpoint oublié.

---

## 6. Récapitulatif du déploiement

| Domaine | Décision | Justification |
|---------|----------|---------------|
| Mode de déploiement | Docker Compose, branche `correction` | Cohérence dev/prod, infra déjà durcie |
| Emplacement | `/opt/devfolio`, compte `deploy` | Pas d'opération en root |
| Secrets | `.env` (600), injectés via `env_file` | Aucun secret en dur, JWT ≥ 48 car. |
| Ports publics | 80, 443 uniquement | Surface réseau minimale ; backend via reverse proxy |
| Ports internes | 3306, 8080 non publiés (ou `127.0.0.1`) | Évite le contournement UFW par Docker |
| Conteneurs | non root, non privilégiés, images alpine | Réduction de l'impact en cas de compromission |
| Vérification | `nmap` externe + `verify_security.sh` | Preuve de l'exposition réelle |

---

## 7. Risques restants après déploiement

| Risque | Criticité | Détail | Action recommandée |
|--------|-----------|--------|--------------------|
| Backend 8080 publié sur `0.0.0.0` | BASSE | Accessible sans passer par nginx | Binder `127.0.0.1:8080` ou supprimer le mapping |
| Frontend nginx en root | BASSE | Image `nginx:alpine` root par défaut | Ajouter `USER nginx` au Dockerfile |
| Certificat auto-signé | INFO | Avertissement navigateur | Let's Encrypt en production |
| Rate limiting / blacklist en mémoire | INFO | Ne fonctionne pas en cluster | Redis / Bucket4j en production |
| Pas de supervision ni sauvegarde | INFO | Aucune alerte ni restauration | Voir bonus (supervision, backups, fail2ban) |

> Ces risques sont de criticité **basse** ou **informationnelle** et ne sont pas bloquants pour une démonstration temporaire. Ils sont à traiter avant un déploiement en production. Voir aussi les risques restants documentés en [doc 06 §3](06-corriger-essentiel-demo.md#3-vulnérabilités-restantes).

---

## 8. Checklist de déploiement et de vérification réseau

**Déploiement**

- [ ] Projet cloné depuis la branche `correction` dans `/opt/devfolio`
- [ ] `.env` créé, rempli avec des secrets forts, permissions `600`
- [ ] Aucun secret en dur dans `docker-compose.yml` (`env_file: .env`)
- [ ] `docker compose up -d` : tous les conteneurs `up`, MariaDB `healthy`
- [ ] Backend `appuser` (non root), conteneurs non privilégiés
- [ ] Réseaux Docker isolés + volume nommé `db_data`

**Vérification réseau**

- [ ] `ss -tulpn` : seuls 22/80/443 en écoute publique
- [ ] `nmap` externe : 3306, 8080, 5005 fermés/filtrés
- [ ] Vérifié que Docker ne contourne pas UFW (ports internes non publiés)
- [ ] En-têtes de sécurité présents, pas de divulgation de version
- [ ] Pas de stacktrace ni d'endpoint d'admin exposé
- [ ] `verify_security.sh` exécuté : tests de régression OK
- [ ] (si possible) revue croisée par une autre équipe

---

## 9. Bonus — aller plus loin (facultatif)

- **Supervision minimale** : `docker stats`, healthchecks, ou un agent léger (Netdata/Prometheus).
- **Bannissement brute force** : `fail2ban` sur SSH (et éventuellement nginx).
- **Sauvegardes** : dump périodique MariaDB (`mysqldump`) + sauvegarde du volume `db_data`.
- **Automatisation** : script de déploiement (`deploy.sh`) enchaînant `git pull` + `docker compose up -d --build`.
- **Pare-feu plus restrictif** : limiter SSH à une IP/plage d'administration.
- **Isolation accrue** : profils seccomp/AppArmor, `--cap-drop ALL` + capabilities minimales sur les conteneurs.
