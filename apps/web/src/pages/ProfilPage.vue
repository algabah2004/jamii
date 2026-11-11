<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useSession } from '../session'

// Page vide de l'ossature : le profil prouvé (#19) viendra dans features/profils.
const { t } = useI18n()
const { moi } = useSession()
</script>

<template>
  <section v-if="moi">
    <h1>{{ t('profil.titre') }}</h1>
    <img v-if="moi.avatarUrl" :src="moi.avatarUrl" alt="" width="64" height="64" />
    <p>
      <strong>{{ moi.nom }}</strong>
      <span v-if="moi.loginGithub"> · @{{ moi.loginGithub }}</span>
    </p>
    <p>{{ t('profil.vide') }}</p>
  </section>
</template>
