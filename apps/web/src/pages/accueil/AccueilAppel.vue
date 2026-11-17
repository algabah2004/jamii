<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { useSession } from '../../session'
import { vApparait } from './apparait'

const { t } = useI18n()
const { etat } = useSession()
const cle = 'accueil.appel'
</script>

<template>
  <!-- La fin de la page : deux portes, selon qui lit. Un visiteur qui a tout lu a toujours quelque chose à faire. -->
  <section class="appel" aria-labelledby="appel-titre" data-test="appel">
    <div class="appel__lumiere" aria-hidden="true" />
    <h2 id="appel-titre" v-apparait>{{ t(`${cle}.titre`) }}</h2>
    <ul class="portes">
      <li v-apparait>
        <RouterLink
          :to="etat === 'connecte' ? '/profil' : '/connexion'"
          class="porte porte--dev"
          data-test="appel-dev"
        >
          <span class="porte__qui">{{ t(`${cle}.dev.qui`) }}</span>
          <strong class="porte__titre">{{ t(`${cle}.dev.titre`) }}</strong>
          <span class="porte__texte">{{ t(`${cle}.dev.texte`) }}</span>
          <span class="porte__action">{{ etat === 'connecte' ? t(`${cle}.dev.actionConnecte`) : t(`${cle}.dev.action`) }} →</span>
        </RouterLink>
      </li>
      <li v-apparait="140">
        <RouterLink to="/besoin" class="porte porte--besoin" data-test="appel-besoin">
          <span class="porte__qui">{{ t(`${cle}.besoin.qui`) }}</span>
          <strong class="porte__titre">{{ t(`${cle}.besoin.titre`) }}</strong>
          <span class="porte__texte">{{ t(`${cle}.besoin.texte`) }}</span>
          <span class="porte__action">{{ t(`${cle}.besoin.action`) }} →</span>
        </RouterLink>
      </li>
    </ul>
  </section>
</template>

<style scoped>
/* Le même dégradé que la scène, en plus calme : la page finit comme elle commence. */
.appel {
  position: relative; isolation: isolate; overflow: hidden;
  display: grid; gap: var(--space-8); justify-items: center; text-align: center;
  padding: clamp(var(--space-8), 6vw, var(--space-16, 4rem)) clamp(var(--space-4), 4vw, var(--space-12));
  border-radius: calc(var(--radius-lg) * 1.5);
  background: var(--midnight);
}
.appel__lumiere {
  position: absolute; inset: 0; z-index: -1; pointer-events: none;
  background:
    radial-gradient(40% 70% at 50% 110%, color-mix(in srgb, var(--cantaloupe) 75%, transparent), transparent 75%),
    radial-gradient(60% 90% at 50% 0%, color-mix(in srgb, var(--electric-blue) 60%, transparent), transparent 75%);
}
.appel h2 {
  margin: 0; max-width: 22ch; color: #fff; text-wrap: balance;
  font-family: var(--font-display); font-size: clamp(1.75rem, 4vw, 2.75rem); line-height: 1.1; letter-spacing: -0.03em;
}

.portes { list-style: none; margin: 0; padding: 0; width: 100%; max-width: 56rem; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-4); }
.portes > li { display: flex; }
.porte {
  flex: 1; display: grid; gap: var(--space-2); align-content: start; text-align: start;
  padding: var(--space-6); border-radius: var(--radius-lg);
  background: #fff; color: var(--midnight); text-decoration: none;
  box-shadow: 0 16px 40px color-mix(in srgb, var(--midnight) 35%, transparent);
  border-top: 4px solid var(--porte);
  transition: transform var(--motion-base) var(--ease-out), box-shadow var(--motion-base) var(--ease-out);
}
.porte--dev { --porte: var(--electric-blue); --porte-ink: #0270A6; }
.porte--besoin { --porte: var(--cantaloupe); --porte-ink: #B4431F; }
.porte:hover { transform: translateY(-4px); box-shadow: 0 22px 48px color-mix(in srgb, var(--midnight) 45%, transparent); }
.porte:focus-visible { outline: 3px solid #fff; outline-offset: 4px; }
.porte__qui { color: var(--porte-ink); font-weight: 700; font-size: var(--type-body-sm-size); letter-spacing: 0.04em; text-transform: uppercase; }
.porte__titre { font-family: var(--font-display); font-size: 1.5rem; letter-spacing: -0.02em; }
/* Toujours sur fond blanc : le texte reste sombre, même en thème sombre. */
.porte__texte { color: #3d4a52; line-height: 1.55; }
.porte__action { margin-top: var(--space-2); color: var(--porte-ink); font-weight: 700; }

@media (max-width: 700px) { .portes { grid-template-columns: minmax(0, 1fr); } }
@media (prefers-reduced-motion: reduce) { .porte { transition: none; } .porte:hover { transform: none; } }
</style>
