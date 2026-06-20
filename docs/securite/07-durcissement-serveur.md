# Itération 3 (partie 1) : durcir et préparer le serveur de démonstration

## Contexte

Les corrections applicatives sont en place sur la branche `correction` (cf. [06-corriger-essentiel-demo.md](06-corriger-essentiel-demo.md)). L'application ne sera pas exécutée sur la machine de développement mais sur un **serveur Linux distant** accessible en SSH, volontairement minimal.

Ce document est le livrable de la **préparation du serveur** : prise en main, préparation de l'environnement, durcissement. Il correspond aux étapes 0 à 2 décrites dans [CONTEXT.md](CONTEXT.md). Le **déploiement** de l'application et la **vérification de l'exposition réseau** sont traités séparément dans `08-deploiement-verification.md`.

> **Stratégie de déploiement retenue : Docker Compose.** L'application est déjà conçue pour Docker (réseaux isolés, utilisateur non-root, volume nommé, healthcheck   cf. [04-infrastructure.md](04-infrastructure.md)). Réutiliser cette base limite l'écart entre l'environnement de développement et le serveur, et réduit le risque d'erreur de configuration manuelle. L'alternative (installation directe Java/Maven/Node/MariaDB) est possible mais multiplierait les services à durcir sur l'hôte.

> **Note importante :** ce document est une **méthodologie pédagogique** et un **modèle de livrable**. Les commandes doivent être adaptées au système réellement fourni (distribution, version, utilisateurs existants). Toute commande modifiant SSH, le pare-feu ou les permissions est **potentiellement bloquante** : la règle d'or est de **garder une session SSH ouverte** et de **vérifier qu'une nouvelle connexion fonctionne** avant de fermer la session courante.

---

## 0. Prise en main du serveur

### 0.1 Accès SSH par clé

L'authentification par clé est préférée au mot de passe (pas de secret transmissible, résistance au brute force).

```bash
# Sur le poste client, générer une paire de clés si nécessaire (ed25519 recommandé)
ssh-keygen -t ed25519 -C "prenom-demo-devfolio"

# Déposer la clé publique sur le serveur
ssh-copy-id -i ~/.ssh/id_ed25519.pub <user>@<serveur>

# Se connecter
ssh <user>@<serveur>
```

> Sur Windows, ces commandes sont disponibles via OpenSSH (PowerShell) ou WSL. `ssh-copy-id` peut manquer sous PowerShell ; dans ce cas, ajouter manuellement la clé publique dans `~/.ssh/authorized_keys` du serveur.

### 0.2 Reconnaissance de l'état initial (avant toute modification)

L'objectif est de **constater** l'état réel de la machine avant de la modifier. Aucune action destructive à ce stade.

| But | Commande | Ce qu'on cherche |
|-----|----------|------------------|
| Identifier le système | `cat /etc/os-release` ; `uname -a` | Distribution, version, noyau (impacte le gestionnaire de paquets : `apt`, `dnf`...) |
| Lister les utilisateurs | `cut -d: -f1,3,7 /etc/passwd` ; `getent group sudo` | Comptes humains vs comptes système, qui a `sudo` |
| Voir les services actifs | `systemctl list-units --type=service --state=running` | Services inutiles à désactiver |
| Voir les ports en écoute | `ss -tulpn` | Ports exposés, services réseau lancés automatiquement |
| Voir les processus | `ps aux --sort=-%mem` | Processus root, agents inattendus |
| Lister les paquets | `dpkg -l` (Debian) / `rpm -qa` (RHEL) | Surface logicielle existante |

> **Livrable attendu :** un instantané (« baseline ») de cet état initial. Il sert de point de comparaison après durcissement et de preuve de la réduction de surface.

Exemple de capture de baseline :

```bash
{
  echo "=== OS ==="; cat /etc/os-release
  echo "=== Kernel ==="; uname -a
  echo "=== Users ==="; cut -d: -f1,3,7 /etc/passwd
  echo "=== Services running ==="; systemctl list-units --type=service --state=running --no-pager
  echo "=== Listening ports ==="; ss -tulpn
} | tee ~/baseline-$(date +%F).txt
```

---

## 1. Préparer l'environnement de déploiement

### 1.1 Mettre le système à jour

Un système non à jour expose des CVE connues. C'est la première réduction de risque.

```bash
# Debian / Ubuntu
sudo apt update && sudo apt upgrade -y

# Fedora / RHEL
sudo dnf upgrade --refresh -y
```

### 1.2 Vérifier ce qui est déjà présent

Avant d'installer, vérifier l'existant pour **ne pas ajouter de surface inutile**.

```bash
command -v docker        # Docker installé ?
command -v docker-compose; docker compose version 2>/dev/null   # Compose (plugin v2) ?
command -v git
command -v curl
command -v ufw           # pare-feu disponible ?
```

### 1.3 Installer le strict nécessaire (stratégie Docker)

Pour un déploiement Docker Compose, seuls **Docker**, **Compose** et **Git** (récupération du projet) sont réellement requis.

```bash
# Debian / Ubuntu : Docker Engine + plugin Compose (dépôt officiel Docker)
sudo apt install -y ca-certificates curl git
# (suivre la procédure officielle docs.docker.com pour ajouter le dépôt Docker,
#  puis :)
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
```

> **Principe de moindre surface :** chaque paquet installé est une surface d'attaque supplémentaire et peut démarrer un service réseau. On n'installe **pas** « tout ce qui manque ». Par exemple, inutile d'installer un serveur web, une base de données ou un runtime Java sur l'hôte si tout tourne en conteneurs.

### 1.4 Créer un utilisateur dédié au déploiement

Ne pas déployer ni opérer en root. Un utilisateur dédié (membre du groupe `docker`) suffit.

```bash
sudo adduser deploy
sudo usermod -aG docker deploy        # peut piloter Docker sans root
# (optionnel) sudo, en exigeant le mot de passe :
sudo usermod -aG sudo deploy          # Debian/Ubuntu (ou 'wheel' sur RHEL)
```

> **Avertissement de sécurité :** appartenir au groupe `docker` équivaut quasiment à un accès root (on peut monter `/` dans un conteneur). C'est un compromis accepté pour l'exploitation Docker, mais il faut limiter le nombre de membres de ce groupe et protéger l'accès SSH de ce compte.
>
> **Piège rencontré en production :** si `deploy` est créé manuellement **avant** le script de durcissement, le groupe `docker` peut être oublié. Vérifier avec `id deploy` et compléter avec `usermod -aG docker deploy` si besoin. De même, `AllowUsers` doit inclure à la fois `deploy` **et** l'utilisateur admin existant (ex: `debian`), sinon l'accès SSH admin est perdu. Le script `hardening.sh` détecte automatiquement l'utilisateur admin via `$SUDO_USER`.

### 1.5 Préparer répertoires et logs

```bash
# Emplacement du projet et des données
sudo mkdir -p /opt/devfolio
sudo chown deploy:deploy /opt/devfolio
chmod 750 /opt/devfolio

# Répertoire de logs applicatifs (si déploiement hors conteneur ou pour collecter les logs Docker)
sudo mkdir -p /var/log/devfolio
sudo chown deploy:deploy /var/log/devfolio
chmod 750 /var/log/devfolio
```

---

## 2. Durcir le serveur

Objectif : réduire l'exposition **avant** d'installer l'application.

### 2.1 Durcissement SSH

SSH est le point d'entrée critique du serveur. Configuration recommandée dans `/etc/ssh/sshd_config` (ou un fichier dédié dans `/etc/ssh/sshd_config.d/`) :

```text
PasswordAuthentication no
PermitRootLogin no
PubkeyAuthentication yes
# Limiter explicitement les comptes autorisés (deploy + admin existant)
AllowUsers deploy debian
# Réduire la fenêtre d'attaque
MaxAuthTries 3
LoginGraceTime 30
```

Appliquer **après avoir vérifié qu'une clé fonctionne** :

```bash
# 1) Valider la syntaxe avant de redémarrer
sudo sshd -t

# 2) Recharger le service (selon la distrib : ssh ou sshd)
sudo systemctl restart ssh 2>/dev/null || sudo systemctl restart sshd

# 3) DANS UNE NOUVELLE session, vérifier que la connexion par clé fonctionne
#    AVANT de fermer la session courante.
```

> **Règle d'or :** ne jamais fermer la session SSH active tant qu'une **nouvelle** connexion n'a pas été testée avec succès. Une erreur dans `sshd_config` peut verrouiller l'accès au serveur.
>
> **Changement de port SSH :** possible (réduit les scans automatisés) mais ce n'est **pas** une protection forte (sécurité par l'obscurité). Si changé, penser à ouvrir le nouveau port dans le pare-feu **avant** de redémarrer SSH.

Ressources : [Manuel OpenSSH](https://www.openssh.com/manual.html) · [Durcissement SSH (S. Robert)](https://blog.stephane-robert.info/docs/securiser/durcissement/ssh/)

### 2.2 Pare-feu (UFW)

Politique par défaut : **tout refuser en entrée**, n'ouvrir que le strict nécessaire.

```bash
sudo apt install -y ufw

sudo ufw default deny incoming
sudo ufw default allow outgoing

# Autoriser SSH AVANT d'activer (sinon on se coupe l'accès)
sudo ufw allow OpenSSH        # ou : sudo ufw allow 22/tcp

# Ports publics de l'application
sudo ufw allow 80/tcp         # HTTP -> redirigé vers HTTPS
sudo ufw allow 443/tcp        # HTTPS (seul port applicatif réellement public)

sudo ufw enable
sudo ufw status numbered
```

**Ports à NE PAS exposer publiquement :**

| Port | Service | Décision |
|------|---------|----------|
| 80 | HTTP → redirection HTTPS | Ouvert (public) |
| 443 | HTTPS (reverse proxy nginx) | Ouvert (public) |
| 22 | SSH | Ouvert (administration uniquement) |
| 3306 | MariaDB | **Fermé**, accès via réseau Docker interne uniquement |
| 8080 | Backend Spring Boot | **Fermé**, accès via nginx reverse proxy uniquement |
| 5005 | Debug JVM | **Fermé**, supprimé du Dockerfile (cf. doc 06) |

> **Piège Docker / UFW :** Docker manipule directement `iptables` et **contourne** les règles UFW pour les ports qu'il publie (`ports:` dans `docker-compose.yml`). Conséquence : un port publié par Docker peut rester accessible **malgré** une règle `ufw deny`. La parade est de **ne pas publier** les ports internes (3306, 8080) sur l'hôte, ou de les binder sur `127.0.0.1` (cf. [04-infrastructure.md](04-infrastructure.md), chaîne `DOCKER-USER`). Ce point est repris au moment du déploiement (doc 08).
>
> **Filet de sécurité `DOCKER-USER`** : même avec le bind `127.0.0.1`, ajouter des règles DROP dans la chaîne `DOCKER-USER` empêche toute exposition accidentelle si quelqu'un modifie `docker-compose.yml` :
>
> ```bash
> # Autoriser les connexions déjà établies (requis par Docker)
> sudo iptables -I DOCKER-USER 1 -j RETURN -m conntrack --ctstate ESTABLISHED,RELATED
> # Bloquer les ports internes même si Docker tente de les publier
> sudo iptables -I DOCKER-USER -p tcp --dport 3306 -j DROP
> sudo iptables -I DOCKER-USER -p tcp --dport 8080 -j DROP
> ```
>
> ⚠️ Ces règles ne persistent pas après un redémarrage de Docker. Pour les rendre persistantes, les ajouter dans un script au démarrage ou dans `hardening.sh`.

Ressources : [Documentation UFW](https://wiki.ubuntu.com/UncomplicatedFirewall) · [Guide DigitalOcean UFW](https://www.digitalocean.com/community/tutorials/how-to-set-up-a-firewall-with-ufw-on-ubuntu)

### 2.3 Réduire les services et la surface réseau

```bash
# Repérer les services inutiles qui écoutent sur le réseau
ss -tulpn

# Désactiver puis arrêter un service inutile (exemple générique)
sudo systemctl disable <service>
sudo systemctl stop <service>
```

Cibles fréquentes sur une machine minimale : serveurs mail locaux, services d'impression, partages réseau, bases de données héritées. **Ne désactiver qu'après avoir vérifié** qu'aucun élément nécessaire n'en dépend.

### 2.4 Utilisateurs et privilèges

- Vérifier qu'aucun compte inattendu ne possède un shell de connexion ou des droits `sudo`.
- Vérifier qu'aucun compte n'a de mot de passe vide.

```bash
# Comptes avec un shell interactif
grep -E '/(bash|sh|zsh)$' /etc/passwd

# Membres des groupes à privilèges
getent group sudo wheel docker

# Comptes sans mot de passe (champ vide entre les deux premiers ':')
sudo awk -F: '($2==""){print $1}' /etc/shadow
```

### 2.5 Permissions des fichiers sensibles

```bash
# Secrets applicatifs : lecture par le seul propriétaire
chmod 600 /opt/devfolio/.env
chown deploy:deploy /opt/devfolio/.env

# Clés SSH du compte de déploiement
chmod 700 /home/deploy/.ssh
chmod 600 /home/deploy/.ssh/authorized_keys

# Rechercher d'éventuels binaires SUID/SGID suspects
sudo find / -perm -4000 -o -perm -2000 2>/dev/null
```

> **Lien avec les règles du projet :** le fichier `.env` ne doit jamais être commité (`.gitignore`) et reste en `600`. Le secret `JWT_SECRET` doit faire ≥ 48 caractères base64 (`openssl rand -base64 48`). Aucun secret en clair dans `docker-compose.yml` : injection via `env_file`.

### 2.6 Durcissement Docker (préparation)

Bonnes pratiques à vérifier/maintenir au moment du déploiement :

- conteneurs **non privilégiés** (pas de `--privileged`) ;
- conteneurs **non root** (`USER appuser` côté backend ; `USER nginx` côté frontend) ;
- **ports internes non publiés** sur l'hôte (3306, 8080) ;
- **volumes nommés** plutôt que bind mount de répertoires hôtes sensibles ;
- **images minimales** (`eclipse-temurin:21-jre-alpine`, `nginx:alpine`) ;
- **secrets** via `env_file`, jamais en dur dans le YAML ;
- nettoyage régulier (`docker image prune`, `docker container prune`).

```bash
docker ps
docker images
docker network ls
docker inspect <conteneur>   # vérifier Privileged, User, Mounts, Ports
```

Ressources : [Docker security](https://docs.docker.com/engine/security/) · [OWASP Docker Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Docker_Security_Cheat_Sheet.html)

---

## 3. Récapitulatif des modifications serveur

| Domaine | Modification | Effet sécurité |
|---------|--------------|----------------|
| Mises à jour | `apt/dnf upgrade` | Correction des CVE connues |
| SSH | Clés uniquement, root interdit, `AllowUsers deploy debian`, `MaxAuthTries 3` | Réduction du brute force et de l'usurpation |
| Pare-feu | UFW : deny entrant par défaut, ouverture 22/80/443 uniquement | Surface réseau minimale |
| Services | Désactivation des services réseau inutiles | Moins de processus exposés |
| Utilisateurs | Compte `deploy` dédié, pas d'opération en root, audit des privilèges | Moindre privilège |
| Permissions | `.env` en 600, clés SSH en 600/700, audit SUID/SGID | Protection des secrets et fichiers sensibles |
| Docker | Non privilégié, non root, ports internes non publiés, images minimales | Réduction de l'impact en cas de compromission |

---

## 4. Risques restants avant déploiement

| Risque | Criticité | Détail | Action recommandée |
|--------|-----------|--------|--------------------|
| Contournement UFW par Docker | MOYENNE | Docker publie ses ports via `iptables` et ignore UFW | Ne pas publier 3306/8080, ou binder sur `127.0.0.1` + règles `DOCKER-USER` (traité en doc 08) |
| Membres du groupe `docker` | MOYENNE | Accès quasi-root via Docker | Limiter le nombre de membres, surveiller l'accès SSH |
| Certificat HTTPS auto-signé | INFO | Avertissement navigateur en démo | Let's Encrypt en production |
| Pas de supervision / sauvegarde | INFO | Aucune alerte ni restauration en cas d'incident | Voir bonus (supervision, backups) |
| Pas de bannissement automatique | ~~BASSE~~ | ~~Brute force SSH non bloqué dynamiquement~~ | **Corrigé** : `fail2ban` installé (jail sshd, `bantime = 3600`, `maxretry = 3`, cf. `01-infrastructure-vps.md`). |
| Mot de passe `devfolio_app` en dur dans `init.sql` | ~~BASSE~~ | ~~`'DevfolioApp2024!'` hardcodé dans le SQL commité~~ | **Corrigé** : `init.sql` remplacé par `init-template.sql` + `init.sh` avec injection `${DB_PASSWORD}`. Fichier genere supprime immediatement apres execution. |
| `MYSQL_ROOT_PASSWORD` = `DB_PASSWORD` | ~~INFO~~ | ~~Le mot de passe root MariaDB est identique au compte applicatif dans `docker-compose.yml`~~ | **Corrigé** : `DB_ROOT_PASSWORD` séparé de `DB_PASSWORD` dans `.env.example`, `docker-compose.yml`, `docker-compose.staging.yml` et `deploy.sh`. |

---

## 5. Checklist de préparation serveur

- [ ] Accès SSH par clé fonctionnel (mot de passe désactivé)
- [ ] `PermitRootLogin no` et `AllowUsers deploy debian` appliqués + nouvelle connexion testée
- [ ] **Session de secours ouverte** avant tout redémarrage SSH
- [ ] Système à jour
- [ ] Docker + Compose installés, version vérifiée
- [ ] Utilisateur `deploy` créé (**vérifier groupe `docker`** avec `id deploy`)
- [ ] **Clé SSH déposée** pour `deploy` (`/home/deploy/.ssh/authorized_keys`) avant durcissement SSH
- [ ] Répertoires `/opt/devfolio` et `/var/log/devfolio` créés avec bonnes permissions
- [ ] UFW actif : entrant refusé par défaut, 22/80/443 ouverts uniquement
- [ ] Services réseau inutiles désactivés
- [ ] Aucun compte à privilèges ou mot de passe vide inattendu
- [ ] `.env` et clés SSH avec permissions restrictives (600/700)
- [ ] Baseline initiale conservée pour comparaison

> **Suite :** une fois le serveur durci et l'environnement prêt, passer au déploiement de l'application et à la vérification de l'exposition réseau   document `08-deploiement-verification.md`.
