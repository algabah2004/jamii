# Jamii en ligne sur un serveur cloud Systalink

Ce qui tourne en production : **https://87-58-156-18.sslip.io**, sur un serveur cloud Systalink (DataCloud, Madrid,
1 cœur, 2 Go, Ubuntu 24.04). Vérifié le 15/11/2026.

```
navigateur ──https──▶ entree (Caddy, 80/443, certificat Let's Encrypt)
                         └─▶ web (Caddy, pages) ──/api/*──▶ api (Java) ──▶ ia (Python)
                                                              └──── postgres ◀──┘
```

## Mise à jour automatique

1. Une PR est fusionnée sur `main`, la CI passe.
2. `.github/workflows/images.yml` construit les trois images et les publie sur `ghcr.io/algabah2004/jamii-{api,ia,web}`.
3. Sur le serveur, `jamii-maj.timer` lance `mettre-a-jour.sh` toutes les 3 minutes : les nouvelles images sont
   récupérées et seuls les services qui ont changé redémarrent. Les migrations Flyway passent au démarrage de l'API.

Seul du code dont la CI est verte part en ligne. Compter une dizaine de minutes entre la fusion et le site.

**Figer la version en ligne** (avant une démo) : `touch /opt/jamii/PAUSE`. **Reprendre** : `rm /opt/jamii/PAUSE`.

## Installer sur un serveur neuf

```bash
# sur le serveur (root) : Docker, 2 Go de swap, pare-feu (22, 80, 443)
curl -fsSL https://get.docker.com | sh
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw --force enable
mkdir -p /opt/jamii   # y copier compose.yml, Caddyfile, mettre-a-jour.sh et les fichiers d'environnement
cp jamii-maj.service jamii-maj.timer /etc/systemd/system/ && systemctl enable --now jamii-maj.timer
```

Les fichiers d'environnement (`postgres.env`, `ia.env`, `api.env`, et `.env` avec `DOMAINE=<ip-avec-tirets>.sslip.io`)
ne sont **jamais** dans le dépôt : ils contiennent le mot de passe de la base, le jeton interne de l'IA, la clé du
fournisseur d'IA et le secret de l'OAuth App GitHub. Les variables attendues sont décrites dans `.env.example` et dans
`docs/deploiement-datacloud.md`. Aucune ne doit rester vide.

L'adresse de retour de l'OAuth App GitHub doit contenir `https://<domaine>/api/auth/github/retour`.
