#!/bin/sh
# Non-régression de la PR #355 : derrière les deux Caddy (entree → web), l'API doit voir l'adresse de CHAQUE visiteur.
# Bug d'origine : tous les visiteurs avaient l'adresse d'entree, donc les limites par adresse valaient pour le monde
# entier (5 comptes démo par heure pour toute la planète).
#
# Deux conteneurs d'adresses différentes demandent des liens de connexion par e-mail (limite : 20 par adresse et par
# heure, apps/api LiensEmail.MAX_PAR_IP). A épuise sa limite ; B doit garder la sienne. Puis B ment sur son adresse
# (faux X-Forwarded-For) : l'entrée publique doit l'ignorer, il reste limité.
set -eu
RESEAU=jamii-e2e_default
IMAGE=caddy:2.8.4-alpine   # déjà là pour l'entrée ; son wget (busybox) suffit

demande() { # $1 conteneur, $2 adresse e-mail, $3 en-tête supplémentaire (facultatif) → code HTTP
  docker exec "$1" sh -c '
    entetes=$(wget -S -O /dev/null http://entree:8080/api/me 2>&1 || true)
    jeton=$(echo "$entetes" | sed -n "s/.*Set-Cookie: XSRF-TOKEN=\([^;]*\).*/\1/p" | head -1)
    wget -S -O /dev/null --header "Cookie: XSRF-TOKEN=$jeton" --header "X-XSRF-TOKEN: $jeton" \
      --header "Content-Type: application/json" ${3:+--header "$3"} \
      --post-data "{\"email\":\"$2\"}" http://entree:8080/api/auth/lien-email 2>&1 | sed -n "s/^ *HTTP\/[0-9.]* \([0-9]*\).*/\1/p" | tail -1
  ' _ "$1" "$2" "${3:-}"
}

nettoyer() { docker rm -f adresse-a adresse-b >/dev/null 2>&1 || true; }
trap nettoyer EXIT
nettoyer
docker run -d --name adresse-a --network "$RESEAU" --entrypoint sleep "$IMAGE" 300 >/dev/null
docker run -d --name adresse-b --network "$RESEAU" --entrypoint sleep "$IMAGE" 300 >/dev/null

i=1; while [ $i -le 20 ]; do
  code=$(demande adresse-a "a$i@exemple.invalid")
  [ "$code" = 202 ] || { echo "échec : la demande $i de A devait passer (202), reçu $code"; exit 1; }
  i=$((i + 1))
done
code=$(demande adresse-a "a21@exemple.invalid")
[ "$code" = 429 ] || { echo "échec : la 21e demande de A devait être limitée (429), reçu $code"; exit 1; }
echo "ok : A est limité après 20 demandes"

code=$(demande adresse-b "b1@exemple.invalid")
[ "$code" = 202 ] || { echo "échec : B a sa propre limite, sa 1re demande devait passer (202), reçu $code"; exit 1; }
echo "ok : B, à une autre adresse, n'est pas touché par la limite de A"

i=2; while [ $i -le 20 ]; do demande adresse-b "b$i@exemple.invalid" "X-Forwarded-For: 10.9.9.$i" >/dev/null; i=$((i + 1)); done
code=$(demande adresse-b "b21@exemple.invalid" "X-Forwarded-For: 203.0.113.7")
[ "$code" = 429 ] || { echo "échec : un faux X-Forwarded-For ne doit pas contourner la limite, reçu $code"; exit 1; }
echo "ok : un faux X-Forwarded-For ne contourne pas la limite"
