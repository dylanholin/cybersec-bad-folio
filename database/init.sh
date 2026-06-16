#!/bin/bash
set -e

# Ce script genere le SQL d'initialisation a partir du template
# en substituant les variables d'environnement, puis l'execute.
# Il est concu pour etre place dans /docker-entrypoint-initdb.d/
# et execute par MariaDB au premier demarrage (volume vide).

TEMPLATE="/templates/init-template.sql"
TMP_SQL="/tmp/init_generated.sql"

# Substitution des placeholders par les variables d'environnement
# Utilisation d'awk pour eviter les problemes de caracteres speciaux
# avec sed (/, &, \ dans le mot de passe)
# MYSQL_DATABASE est la variable standard MariaDB (pas besoin de DB_NAME separe)
awk -v db_name="${MYSQL_DATABASE}" \
    -v db_user="${DB_USER}" \
    -v db_password="${DB_PASSWORD}" '{
    gsub(/__DB_NAME__/, db_name);
    gsub(/__DB_USER__/, db_user);
    gsub(/__DB_PASSWORD__/, db_password);
    print
}' "${TEMPLATE}" > "${TMP_SQL}"

# Execution du SQL genere
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" < "${TMP_SQL}"

# Suppression immediate du fichier temporaire pour eviter
# la persistence du secret sur disque
rm -f "${TMP_SQL}"
