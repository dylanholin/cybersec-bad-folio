#!/bin/bash
# deploy.sh — déploiement de DevFolio sur le serveur durci
# Correspond aux étapes 3 à 5 de docs/CONTEXT.md
# Usage : ./deploy.sh [BRANCH]
# Exécuter en tant que l'utilisateur de déploiement (pas root)

set -euo pipefail

BRANCH="${1:-correction}"
REPO_URL="https://github.com/dylanholin/cybersec-bad-folio.git"
APP_DIR="/opt/devfolio"

# ── Couleurs ──────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ── Vérifications préalables ──────────────────────────────────────────
if [ "$(id -u)" -eq 0 ]; then
    error "Ne pas exécuter ce script en root. Utiliser le compte de déploiement."
fi

if ! command -v docker &>/dev/null; then
    error "Docker n'est pas installé. Exécuter hardening.sh d'abord."
fi

if ! docker compose version &>/dev/null && ! docker-compose version &>/dev/null; then
    error "Docker Compose n'est pas installé. Exécuter hardening.sh d'abord."
fi

info "=== Déploiement DevFolio (branche $BRANCH) ==="
echo ""

# ── Étape 3 : Récupérer le projet ─────────────────────────────────────
if [ -d "$APP_DIR/.git" ]; then
    info "Mise à jour du dépôt existant dans $APP_DIR..."
    cd "$APP_DIR"
    git fetch origin
    git checkout "$BRANCH"
    git reset --hard "origin/$BRANCH"
else
    info "Clonage du dépôt (branche $BRANCH) dans $APP_DIR..."
    git clone -b "$BRANCH" "$REPO_URL" "$APP_DIR"
    cd "$APP_DIR"
fi

# ── Étape 3 : Préparer les secrets ────────────────────────────────────
if [ ! -f .env ]; then
    info "Création du fichier .env à partir du template..."
    cp .env.example .env
    chmod 600 .env

    # Générer un JWT_SECRET fort
    JWT_SECRET=$(openssl rand -base64 48 2>/dev/null || head -c 48 /dev/urandom | base64)
    DB_ROOT_PASSWORD=$(openssl rand -base64 24 2>/dev/null || head -c 24 /dev/urandom | base64)
    DB_PASSWORD=$(openssl rand -base64 24 2>/dev/null || head -c 24 /dev/urandom | base64)
    ADMIN_PASSWORD=$(openssl rand -base64 24 2>/dev/null || head -c 24 /dev/urandom | base64)

    # Remplir les valeurs (adaptation simple par sed)
    sed -i "s|^JWT_SECRET=.*|JWT_SECRET=${JWT_SECRET}|" .env
    sed -i "s|^DB_ROOT_PASSWORD=.*|DB_ROOT_PASSWORD=${DB_ROOT_PASSWORD}|" .env
    sed -i "s|^DB_PASSWORD=.*|DB_PASSWORD=${DB_PASSWORD}|" .env
    sed -i "s|^ADMIN_PASSWORD=.*|ADMIN_PASSWORD=${ADMIN_PASSWORD}|" .env
    sed -i "s|^DB_HOST=.*|DB_HOST=mariadb|" .env

    warn "Fichier .env créé avec des secrets générés automatiquement."
    warn "VÉRIFIEZ ET COMPLÉTEZ LES VALEURS AVANT DE POURSUIVRE :"
    warn "  $APP_DIR/.env"
    warn ""
    warn "En particulier : DB_USER, ADMIN_EMAIL, et les mots de passe des comptes seed dans init.sql"
    warn "doivent être cohérents avec les valeurs du .env."
    read -p "Continuer le déploiement ? [y/N] " -r
    [[ $REPLY =~ ^[Yy]$ ]] || error "Déploiement annulé. Modifier .env puis relancer."
else
    info "Fichier .env existant détecté. Vérification des secrets..."
    # Vérifier que JWT_SECRET est suffisamment long
    JWT_LEN=$(grep '^JWT_SECRET=' .env | cut -d'=' -f2- | wc -c)
    if [ "$JWT_LEN" -lt 32 ]; then
        warn "JWT_SECRET trop court ($((JWT_LEN - 1)) car.). Risque d'InvalidKeyException."
        warn "Régénérer avec : openssl rand -base64 48"
    fi
    # Vérifier que DB_HOST est correct pour Docker
    DB_HOST_VAL=$(grep '^DB_HOST=' .env | cut -d'=' -f2)
    if [ "$DB_HOST_VAL" = "localhost" ]; then
        warn "DB_HOST=localhost dans .env. En conteneurisé, utiliser DB_HOST=mariadb."
        warn "Correction automatique..."
        sed -i 's|^DB_HOST=localhost|DB_HOST=mariadb|' .env
    fi
fi

# Vérifier qu'aucun secret n'est en dur dans docker-compose.yml.
# On ne flague que les valeurs littérales : les références ${VAR} sont légitimes.
if grep -iE '^[[:space:]]+(MYSQL_ROOT_PASSWORD|JWT_SECRET|DB_PASSWORD|ADMIN_PASSWORD)[[:space:]]*:' docker-compose.yml 2>/dev/null \
        | grep -qvE '\$\{'; then
    error "Un secret littéral est présent dans docker-compose.yml. Utiliser env_file: .env et des références \${...}."
fi

# ── Étape 4 : Déployer ────────────────────────────────────────────────
info "Construction et démarrage des conteneurs..."
cd "$APP_DIR"

# Déterminer la commande compose
if docker compose version &>/dev/null; then
    COMPOSE_CMD="docker compose"
else
    COMPOSE_CMD="docker-compose"
fi

$COMPOSE_CMD up --build -d

info "Attente du healthcheck MariaDB..."
for i in $(seq 1 30); do
    if $COMPOSE_CMD ps mariadb 2>/dev/null | grep -q "healthy"; then
        break
    fi
    sleep 2
done

# ── Étape 4 : Vérifications post-déploiement ──────────────────────────
info "Vérifications post-déploiement..."
echo ""

# Conteneurs actifs
info "État des conteneurs :"
$COMPOSE_CMD ps
echo ""

# Backend non root
BACKEND_USER=$(docker exec backend whoami 2>/dev/null || echo "N/A")
if [ "$BACKEND_USER" = "appuser" ]; then
    info "Backend : utilisateur '$BACKEND_USER' (non root)"
else
    warn "Backend : utilisateur '$BACKEND_USER' — attendu 'appuser'"
fi

# Frontend non root
FRONTEND_USER=$(docker exec frontend whoami 2>/dev/null || echo "N/A")
if [ "$FRONTEND_USER" = "nginx" ]; then
    info "Frontend : utilisateur '$FRONTEND_USER' (non root)"
else
    warn "Frontend : utilisateur '$FRONTEND_USER' — attendu 'nginx'"
fi

# Conteneurs non privilégiés
PRIV=$(docker inspect --format '{{.HostConfig.Privileged}}' backend 2>/dev/null || echo "N/A")
if [ "$PRIV" = "false" ]; then
    info "Backend : non privilégié"
else
    warn "Backend : privilégié=$PRIV — attendu false"
fi

# Réseaux isolés
info "Réseaux Docker :"
docker network ls | grep -E "frontend-backend|backend-db" || warn "Réseaux isolés non trouvés"
echo ""

# Ports en écoute
info "Ports en écoute sur l'hôte :"
ss -tulpn | grep -E ':(80|443|3306|8080|5005)\s' || true
echo ""

# ── Étape 5 : Vérification de l'exposition réseau ─────────────────────
info "=== Vérification de l'exposition réseau ==="
echo ""

BASE_URL="https://localhost"

# HTTPS actif
HTTP_CODE=$(curl -sk -o /dev/null -w "%{http_code}" "$BASE_URL/" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    info "HTTPS actif (200)"
else
    warn "HTTPS : code $HTTP_CODE (attendu 200)"
fi

# Redirection HTTP -> HTTPS
HTTP_REDIRECT=$(curl -s -o /dev/null -w "%{http_code}" http://localhost/ 2>/dev/null || echo "000")
if [ "$HTTP_REDIRECT" = "301" ]; then
    info "Redirection HTTP -> HTTPS (301)"
else
    warn "Redirection HTTP : code $HTTP_REDIRECT (attendu 301)"
fi

# Login
info "Test de login..."
ADMIN_PASSWORD_VAL=$(grep '^ADMIN_PASSWORD=' .env | cut -d'=' -f2-)
LOGIN_RESP=$(curl -sk -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"admin@devfolio.com\",\"password\":\"${ADMIN_PASSWORD_VAL}\"}" 2>/dev/null || echo "")
TOKEN=$(echo "$LOGIN_RESP" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
if [ -n "$TOKEN" ]; then
    info "Login réussi, token obtenu"
else
    warn "Login échoué. Vérifier ADMIN_PASSWORD dans .env et init.sql."
fi

# Tests de sécurité (régressions)
info "Tests de sécurité..."
echo ""

# Injection SQL
RESP=$(curl -sk "$BASE_URL/api/search/projects?q=' OR '1'='1" 2>/dev/null || echo "")
if echo "$RESP" | grep -q '"id"'; then
    warn "Injection SQL : des projets retournés (VULNÉRABLE)"
else
    info "Injection SQL : bloquée (résultat vide)"
fi

# Admin sans token
RESP=$(curl -sk -o /dev/null -w "%{http_code}" "$BASE_URL/api/admin/users" 2>/dev/null || echo "000")
if echo "$RESP" | grep -q "401"; then
    info "Admin sans token : 401 (OK)"
else
    warn "Admin sans token : $RESP (attendu 401)"
fi

# Actuator env
RESP=$(curl -sk -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/env" 2>/dev/null || echo "000")
if echo "$RESP" | grep -q "40[13]"; then
    info "Actuator /env : $RESP (protégé)"
else
    warn "Actuator /env : $RESP (attendu 401/403)"
fi

# SSRF (nécessite token)
if [ -n "$TOKEN" ]; then
    RESP=$(curl -sk -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/users/avatar" \
        -H "Authorization: Bearer $TOKEN" \
        -d "url=http://169.254.169.254/" 2>/dev/null || echo "000")
    if [ "$RESP" = "400" ]; then
        info "SSRF avatar : 400 (bloqué)"
    else
        warn "SSRF avatar : $RESP (attendu 400)"
    fi
fi

# JWT alg:none
FAKE_TOKEN="eyJhbGciOiJub25lIn0.eyJzdWIiOiJhZG1pbkBkZXZmb2xpby5jb20iLCJyb2xlIjoiQURNSU4iLCJ1c2VySWQiOjF9."
RESP=$(curl -sk -o /dev/null -w "%{http_code}" "$BASE_URL/api/admin/users" \
    -H "Authorization: Bearer $FAKE_TOKEN" 2>/dev/null || echo "000")
if [ "$RESP" = "401" ]; then
    info "JWT alg:none : 401 (rejeté)"
else
    warn "JWT alg:none : $RESP (attendu 401)"
fi

# MariaDB non exposé : le bind doit rester local (127.0.0.1) ou absent.
# NB : tester 'localhost' ne suffit pas (le port y répond par conception).
# On inspecte donc l'adresse de bind réelle via ss.
if ss -tulpn 2>/dev/null | grep -E '[[:space:]]:3306[[:space:]]' | grep -qvE '127\.0\.0\.1:3306|\[::1\]:3306'; then
    warn "MariaDB : port 3306 bindé sur une interface non locale (exposition possible)"
else
    info "MariaDB : port 3306 non exposé publiquement (OK)"
fi

# Backend 8080 : doit être bindé uniquement sur 127.0.0.1 (accès via nginx).
if ss -tulpn 2>/dev/null | grep -E '[[:space:]]:8080[[:space:]]' | grep -qvE '127\.0\.0\.1:8080|\[::1\]:8080'; then
    warn "Backend : port 8080 bindé sur une interface non locale (exposition possible)"
else
    info "Backend : port 8080 non exposé publiquement (OK, bindé 127.0.0.1)"
fi

echo ""
info "=== Déploiement terminé ==="
info "Application : $BASE_URL"
info "API : $BASE_URL/api"
info ""
info "Pour vérifier manuellement :"
info "  $COMPOSE_CMD ps"
info "  $COMPOSE_CMD logs -f backend"
info "  ss -tulpn"
info ""
warn "N'oubliez pas :"
warn "  - Vérifier depuis une autre machine que seuls 80/443 répondent"
warn "  - Exécuter nmap -Pn -p- <serveur> depuis un poste distant"
warn "  - Mettre à jour init.sql si ADMIN_PASSWORD a été changé dans .env"
