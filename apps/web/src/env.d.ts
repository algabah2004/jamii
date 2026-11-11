declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const composant: DefineComponent
  export default composant
}

interface ImportMetaEnv {
  /** Charte du déploiement (src/design-system/marques.css) : `jamii` par défaut, ou `partenaire`. */
  readonly VITE_MARQUE?: string
}
