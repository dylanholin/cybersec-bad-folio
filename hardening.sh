#!/bin/bash
# hardening.sh : durcissement d'un serveur Linux avant déploiement DevFolio
# Correspond aux étapes 0 à 2 de docs/CONTEXT.md
# Usage : sudo ./hardening.sh
# ATTENTION : ce script modifie SSH, le pare-feu et les permissions.
# LIRE ET ADAPTER AVANT D'EXÉCUTER. Ne jamais lancer aveuglément.

set -euo pipefail

# ── Configuration (adapter avant exécution) ──────────────────────────
DEPLOY_USER="${DEPLOY_USER:-deploy}"
# Utilisateur admin existant (ex: debian, ubuntu) à conserver dans AllowUsers
# pour éviter le lockout SSH. Détecté automatiquement si le script est lancé via sudo.
ADMIN_USER="${ADMIN_USER:-${SUDO_USER:-}}"
SSH_PORT="${SSH_PORT:-22}"
APP_DIR="${APP_DIR:-/opt/devfolio}"
LOG_DIR="${LOG_DIR:-/var/log/devfolio}"
UFW_APP_PORTS="${UFW_APP_PORTS:-80,443}"  # ports applicatifs publics (HTTP/HTTPS)

# ── Couleurs ──────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ── Vérifications préalables ──────────────────────────────────────────
if [ "$(id -u)" -ne 0 ]; then
    error "Ce script doit être exécuté en root (sudo)."
fi

info "=== Durcissement du serveur DevFolio ==="
info "Utilisateur déploiement : $DEPLOY_USER | App dir : $APP_DIR | SSH port : $SSH_PORT"
if [ -n "$ADMIN_USER" ]; then
    info "Utilisateur admin conservé : $ADMIN_USER"
else
    warn "ADMIN_USER non détecté (script lancé en root direct ?)."
    warn "AllowUsers ne contiendra que '$DEPLOY_USER'. RISQUE DE LOCKOUT SSH."
    warn "Définir ADMIN_USER manuellement : sudo ADMIN_USER=debian ./hardening.sh"
fi
echo ""

# ── Étape 0 : Baseline ───────────────────────────────────────────────
BASELINE="/root/baseline-$(date +%F).txt"
info "Capture de la baseline initiale dans $BASELINE"
{
    echo "=== OS ==="; cat /etc/os-release 2>/dev/null || uname -a
    echo "=== Kernel ==="; uname -a
    echo "=== Users ==="; cut -d: -f1,3,7 /etc/passwd
    echo "=== Services running ==="; systemctl list-units --type=service --state=running --no-pager 2>/dev/null || true
    echo "=== Listening ports ==="; ss -tulpn
    echo "=== Sudo/wheel members ==="; getent group sudo wheel 2>/dev/null || true
    echo "=== Docker members ==="; getent group docker 2>/dev/null || true
} | tee "$BASELINE"
echo ""

# ── Étape 1 : Mise à jour système ─────────────────────────────────────
info "Mise à jour du système..."
if command -v apt &>/dev/null; then
    apt update && apt upgrade -y
elif command -v dnf &>/dev/null; then
    dnf upgrade --refresh -y
else
    warn "Gestionnaire de paquets non reconnu (ni apt ni dnf). Mise à jour manuelle requise."
fi

# ── Étape 1 : Installation du strict nécessaire ───────────────────────
info "Vérification des outils nécessaires..."
MISSING=()
for cmd in docker git curl ufw; do
    if ! command -v "$cmd" &>/dev/null; then
        MISSING+=("$cmd")
    fi
done

if [ ${#MISSING[@]} -gt 0 ]; then
    warn "Outils manquants : ${MISSING[*]}"
    info "Installation des outils manquants..."

    if command -v apt &>/dev/null; then
        apt install -y ca-certificates curl git ufw

        # Docker Engine (procédure officielle Debian/Ubuntu)
        if ! command -v docker &>/dev/null; then
            info "Installation de Docker..."
            install -m 0755 -d /etc/apt/keyrings
            curl -fsSL https://download.docker.com/linux/$(
                . /etc/os-release && echo "$ID"
            )/gpg -o /etc/apt/keyrings/docker.asc
            chmod a+r /etc/apt/keyrings/docker.asc
            echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/$(
                . /etc/os-release && echo "$ID"
            ) $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
                | tee /etc/apt/sources.list.d/docker.list > /dev/null
            apt update
            apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
        fi
    elif command -v dnf &>/dev/null; then
        dnf install -y git curl ufw
        if ! command -v docker &>/dev/null; then
            info "Installation de Docker (dnf)..."
            dnf config-manager --add-repo https://download.docker.com/linux/fedora/docker-ce.repo 2>/dev/null || \
                dnf config-manager --add-repo https://download.docker.com/linux/rhel/docker-ce.repo 2>/dev/null || \
                warn "Impossible d'ajouter le dépôt Docker. Installation manuelle requise."
            dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
        fi
    fi
else
    info "Tous les outils nécessaires sont présents."
fi

# Démarrer Docker si pas actif
if command -v systemctl &>/dev/null && ! systemctl is-active --quiet docker; then
    systemctl enable --now docker
fi

# ── Étape 1 : Utilisateur dédié ───────────────────────────────────────
if id "$DEPLOY_USER" &>/dev/null; then
    info "L'utilisateur '$DEPLOY_USER' existe déjà."
    if ! id -nG "$DEPLOY_USER" 2>/dev/null | grep -qw "docker"; then
        info "Ajout de '$DEPLOY_USER' au groupe docker..."
        usermod -aG docker "$DEPLOY_USER"
    fi
else
    info "Création de l'utilisateur '$DEPLOY_USER'..."
    adduser --disabled-password --gecos "" "$DEPLOY_USER" 2>/dev/null || useradd -m -s /bin/bash "$DEPLOY_USER"
    info "Ajout au groupe docker..."
    usermod -aG docker "$DEPLOY_USER"
fi
warn "Pensez à déposer une clé SSH pour '$DEPLOY_USER' dans /home/$DEPLOY_USER/.ssh/authorized_keys"

# ── Étape 1 : Répertoires ─────────────────────────────────────────────
info "Création des répertoires $APP_DIR et $LOG_DIR..."
mkdir -p "$APP_DIR" "$LOG_DIR"
chown "$DEPLOY_USER:$DEPLOY_USER" "$APP_DIR" "$LOG_DIR"
chmod 750 "$APP_DIR" "$LOG_DIR"

# ── Étape 2 : Durcissement SSH ────────────────────────────────────────
info "Durcissement SSH..."
SSHD_CONFIG="/etc/ssh/sshd_config"
SSHD_DROPIN="/etc/ssh/sshd_config.d/99-devfolio-hardening.conf"

# Construire la liste AllowUsers : deploy + admin (si défini)
ALLOW_USERS="$DEPLOY_USER"
if [ -n "$ADMIN_USER" ] && [ "$ADMIN_USER" != "$DEPLOY_USER" ]; then
    ALLOW_USERS="$DEPLOY_USER $ADMIN_USER"
fi

# Déterminer le gestionnaire de service SSH (ssh sur Debian/Ubuntu, sshd sur RHEL/Fedora)
if systemctl list-unit-files 2>/dev/null | grep -q '^ssh\.service'; then
    SSH_SERVICE="ssh"
elif systemctl list-unit-files 2>/dev/null | grep -q '^sshd\.service'; then
    SSH_SERVICE="sshd"
else
    warn "Service SSH non trouvé. Durcissement manuel requis."
    SSH_SERVICE=""
fi

# Écrire la configuration dans un fichier drop-in (plus sûr que de modifier le fichier principal)
if [ -d /etc/ssh/sshd_config.d ]; then
    info "Écriture de la configuration dans $SSHD_DROPIN"
    cat > "$SSHD_DROPIN" <<EOF
# DevFolio : durcissement SSH
PasswordAuthentication no
PermitRootLogin no
PubkeyAuthentication yes
AllowUsers $ALLOW_USERS
MaxAuthTries 3
LoginGraceTime 30
EOF
    chmod 644 "$SSHD_DROPIN"
else
    warn "Répertoire sshd_config.d non disponible. Modification directe de $SSHD_CONFIG (backup automatique)."
    cp "$SSHD_CONFIG" "${SSHD_CONFIG}.bak.$(date +%F)"
    sed -i "s/^#\?PasswordAuthentication.*/PasswordAuthentication no/" "$SSHD_CONFIG"
    sed -i "s/^#\?PermitRootLogin.*/PermitRootLogin no/" "$SSHD_CONFIG"
    sed -i "s/^#\?PubkeyAuthentication.*/PubkeyAuthentication yes/" "$SSHD_CONFIG"
    grep -q "^AllowUsers" "$SSHD_CONFIG" || echo "AllowUsers $ALLOW_USERS" >> "$SSHD_CONFIG"
    grep -q "^MaxAuthTries" "$SSHD_CONFIG" || echo "MaxAuthTries 3" >> "$SSHD_CONFIG"
    grep -q "^LoginGraceTime" "$SSHD_CONFIG" || echo "LoginGraceTime 30" >> "$SSHD_CONFIG"
fi

# Valider la syntaxe AVANT de redémarrer
if command -v sshd &>/dev/null; then
    info "Validation de la syntaxe sshd_config..."
    sshd -t || error "Erreur de syntaxe dans sshd_config. NE PAS REDÉMARRER SSH."
fi

if [ -n "$SSH_SERVICE" ]; then
    warn "SSH va être redémarré. VÉRIFIEZ QU'UNE CLÉ EST DÉPOSÉE AVANT DE CONTINUER."
    read -p "Continuer le redémarrage SSH ? [y/N] " -r
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        systemctl restart "$SSH_SERVICE"
        info "SSH redémarré. OUVREZ UNE NOUVELLE SESSION pour vérifier avant de fermer celle-ci."
    else
        warn "SSH non redémarré. Configuration en attente."
    fi
fi

# ── Étape 2 : Pare-feu UFW ────────────────────────────────────────────
info "Configuration du pare-feu UFW..."
if command -v ufw &>/dev/null; then
    # Politique par défaut
    ufw default deny incoming
    ufw default allow outgoing

    # SSH AVANT d'activer (sinon on se coupe l'accès)
    ufw allow "$SSH_PORT"/tcp comment "SSH"

    # Ports applicatifs
    IFS=',' read -ra PORTS <<< "$UFW_APP_PORTS"
    for PORT in "${PORTS[@]}"; do
        ufw allow "${PORT}/tcp" comment "DevFolio app"
    done

    # Activer (peut demander confirmation)
    echo "y" | ufw enable
    ufw status numbered
else
    warn "UFW non disponible. Configuration manuelle du pare-feu requise (iptables/nftables)."
fi

# ── Étape 2 : fail2ban (anti brute-force SSH) ─────────────────────────
info "Installation et configuration de fail2ban..."
if command -v apt &>/dev/null; then
    apt install -y fail2ban
elif command -v dnf &>/dev/null; then
    dnf install -y fail2ban
else
    warn "Gestionnaire de paquets non reconnu. fail2ban non installé."
fi

if command -v fail2ban-client &>/dev/null; then
    # Configuration jail SSH (drop-in pour ne pas modifier jail.local directement)
    JAIL_DROPIN="/etc/fail2ban/jail.d/99-devfolio-sshd.conf"
    cat > "$JAIL_DROPIN" <<EOF
# DevFolio : jail SSH
[sshd]
enabled = true
port = $SSH_PORT
filter = sshd
logpath = %(sshd_log)s
backend = systemd
maxretry = 3
bantime = 3600
findtime = 600
EOF
    systemctl enable fail2ban
    systemctl restart fail2ban
    fail2ban-client status sshd 2>/dev/null || warn "Jail sshd non actif immédiatement. Vérifier : fail2ban-client status"
else
    warn "fail2ban non installé. Bannissement brute force SSH non actif."
fi

# ── Étape 2 : Filet de sécurité DOCKER-USER (iptables) ────────────────
# Docker contourne UFW via iptables. Ces règles empêchent l'exposition
# accidentelle des ports 3306 et 8080 même si docker-compose.yml est modifié.
# NB : ces règles ne persistent pas après un redémarrage de Docker.
# Un service systemd les réapplique automatiquement (voir ci-dessous).
info "Configuration du filet DOCKER-USER (iptables)..."
if command -v iptables &>/dev/null; then
    # Autoriser les connexions déjà établies (requis par Docker)
    iptables -C DOCKER-USER -m conntrack --ctstate ESTABLISHED,RELATED -j RETURN 2>/dev/null || \
        iptables -I DOCKER-USER 1 -j RETURN -m conntrack --ctstate ESTABLISHED,RELATED
    # Bloquer les ports internes
    iptables -C DOCKER-USER -p tcp --dport 3306 -j DROP 2>/dev/null || \
        iptables -I DOCKER-USER -p tcp --dport 3306 -j DROP
    iptables -C DOCKER-USER -p tcp --dport 8080 -j DROP 2>/dev/null || \
        iptables -I DOCKER-USER -p tcp --dport 8080 -j DROP
    info "Règles DOCKER-USER appliquées (3306 et 8080 bloqués)"

    # Service systemd pour réappliquer les règles après un redémarrage de Docker
    # (les règles DOCKER-USER sont perdues quand Docker recrée la chaîne)
    DOCKER_USER_SERVICE="/etc/systemd/system/docker-user-rules.service"
    cat > "$DOCKER_USER_SERVICE" <<EOF
[Unit]
Description=DevFolio : règles iptables DOCKER-USER
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
ExecStart=/bin/bash -c 'iptables -C DOCKER-USER -m conntrack --ctstate ESTABLISHED,RELATED -j RETURN 2>/dev/null || iptables -I DOCKER-USER 1 -j RETURN -m conntrack --ctstate ESTABLISHED,RELATED; iptables -C DOCKER-USER -p tcp --dport 3306 -j DROP 2>/dev/null || iptables -I DOCKER-USER -p tcp --dport 3306 -j DROP; iptables -C DOCKER-USER -p tcp --dport 8080 -j DROP 2>/dev/null || iptables -I DOCKER-USER -p tcp --dport 8080 -j DROP'
RemainAfterExit=yes

[Install]
WantedBy=multi-user.target
EOF
    systemctl daemon-reload
    systemctl enable docker-user-rules.service
    info "Service systemd docker-user-rules activé (persistant après redémarrage Docker)"
else
    warn "iptables non disponible. Filet DOCKER-USER non configuré."
fi

# ── Étape 2 : Audit utilisateurs et permissions ───────────────────────
info "Audit des utilisateurs et permissions..."

# Comptes avec shell interactif
info "Comptes avec shell interactif :"
grep -E '/(bash|sh|zsh)$' /etc/passwd

# Comptes sans mot de passe
info "Vérification des comptes sans mot de passe..."
EMPTY_PW=$(awk -F: '($2==""){print $1}' /etc/shadow 2>/dev/null || true)
if [ -n "$EMPTY_PW" ]; then
    warn "Comptes sans mot de passe détectés : $EMPTY_PW"
else
    info "Aucun compte sans mot de passe."
fi

# SUID/SGID suspects
info "Recherche de binaires SUID/SGID (vérifier les résultats)..."
find / -perm -4000 -o -perm -2000 2>/dev/null | head -30 || true

# ── Résumé ─────────────────────────────────────────────────────────────
echo ""
info "=== Durcissement terminé ==="
info "Baseline initiale : $BASELINE"
info "Utilisateur déploiement : $DEPLOY_USER (groupe docker)"
info "Utilisateur admin conservé : ${ADMIN_USER:-<non défini>}"
info "Répertoire application : $APP_DIR"
info "Pare-feu : UFW actif (deny entrant par défaut, SSH + $UFW_APP_PORTS ouverts)"
info "Anti brute-force : fail2ban actif (jail sshd, maxretry=3, bantime=3600)"
info "Filet Docker : DOCKER-USER bloque 3306 et 8080 (persistant via systemd)"
info ""
warn "ACTIONS MANUELLES RESTANTES :"
warn "  1. Déposer la clé SSH de '$DEPLOY_USER' dans /home/$DEPLOY_USER/.ssh/authorized_keys"
warn "  2. Tester une connexion SSH par clé AVANT de fermer la session courante"
warn "  3. Exécuter deploy.sh en tant que '$DEPLOY_USER' pour déployer l'application"
