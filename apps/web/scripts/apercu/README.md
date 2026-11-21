# Image d'aperçu des liens (`public/og-image.png`)

L'image que WhatsApp, LinkedIn, Slack ou X affichent quand on partage `https://jamii.dev` (balises `og:image` de `index.html`).

- **Source** : `apercu.html` (1200 × 630, couleurs et polices du design system).
- **Rendu** : `rendre.mjs`, lancé à la main. L'image est versionnée : le build ne lance pas de navigateur.

```bash
# Depuis la racine du dépôt, avec l'image Playwright déjà utilisée par les tests e2e
docker run --rm -v "$PWD":/repo -v "$PWD/e2e/node_modules":/node_modules:ro -w /repo/apps/web \
  --user "$(id -u):$(id -g)" mcr.microsoft.com/playwright:v1.65.1-noble node scripts/apercu/rendre.mjs
```

Les réseaux gardent l'aperçu en cache : après un changement, forcer la relecture avec l'outil d'inspection de LinkedIn (Post Inspector) ou de Facebook (Sharing Debugger).
