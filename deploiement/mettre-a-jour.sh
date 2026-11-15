#!/bin/sh
# Lancé toutes les 3 minutes par jamii-maj.timer : récupère les images publiées après une CI verte (images.yml) et ne
# redémarre que les services dont l'image a changé. `touch /opt/jamii/PAUSE` fige la version en ligne (avant une démo).
set -eu
cd /opt/jamii
[ -f PAUSE ] && exit 0
docker compose pull -q api ia web
docker compose up -d --remove-orphans
docker image prune -f >/dev/null
