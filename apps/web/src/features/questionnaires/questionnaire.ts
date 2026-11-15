/*
 * Les questionnaires personnalisés (contracts/socle.yaml, `Question` et `Reponse` ; #257) : l'organisateur d'un
 * hackathon ou le client d'une mission pose ses propres questions, à l'inscription ou dans une proposition.
 * Ici : les limites du contrat, et les transformations pures entre ce que la personne saisit et ce que l'API attend.
 * L'API revérifie tout ; le front ne fait qu'aider à bien remplir.
 */
import type { components } from '../../api/generated/socle'

export type Question = components['schemas']['Question']
export type Reponse = components['schemas']['Reponse']
export type TypeQuestion = Question['type']
/** Ce que la personne a saisi, par identifiant de question. */
export type Valeurs = Record<string, string | string[]>

export const TYPES: readonly TypeQuestion[] = ['texte_court', 'texte_long', 'choix_unique', 'choix_multiples', 'oui_non']

export const LIMITES = {
  questionsMax: 10,
  intituleMin: 3,
  intituleMax: 200,
  aideMax: 300,
  choixMin: 2,
  choixMax: 8,
  choixLongueurMax: 80,
  texteCourtMax: 200,
  texteLongMax: 2000,
} as const

export const aDesChoix = (type: TypeQuestion) => type === 'choix_unique' || type === 'choix_multiples'

/** Un identifiant libre (`q1`, `q2`…) : stable, les réponses s'y rattachent. */
export function identifiantLibre(questions: readonly Question[]): string {
  const pris = new Set(questions.map((q) => q.id))
  let n = questions.length + 1
  while (pris.has(`q${n}`)) n++
  return `q${n}`
}

export function nouvelleQuestion(questions: readonly Question[], modele: Partial<Question> = {}): Question {
  const type = modele.type ?? 'texte_court'
  return {
    id: identifiantLibre(questions),
    intitule: modele.intitule ?? '',
    type,
    obligatoire: modele.obligatoire ?? false,
    ...(modele.aide ? { aide: modele.aide } : {}),
    ...(aDesChoix(type) ? { choix: modele.choix ? [...modele.choix] : ['', ''] } : {}),
  }
}

/** Le questionnaire tel que l'API l'attend : textes nettoyés, choix vides retirés, `choix` seulement là où il sert. */
export function nettoyer(questions: readonly Question[]): Question[] {
  return questions.map((q) => {
    const aide = q.aide?.trim()
    const base: Question = { id: q.id, intitule: q.intitule.trim(), type: q.type, obligatoire: q.obligatoire, ...(aide ? { aide } : {}) }
    return aDesChoix(q.type) ? { ...base, choix: (q.choix ?? []).map((c) => c.trim()).filter(Boolean) } : base
  })
}

/** Ce qui empêche d'enregistrer, question par question (clé i18n `questionnaire.erreurs.*`). Vide : tout va bien. */
export function problemes(questions: readonly Question[]): Record<string, string> {
  const trouves: Record<string, string> = {}
  for (const q of nettoyer(questions)) {
    if (q.intitule.length < LIMITES.intituleMin) trouves[q.id] = 'intitule'
    else if (aDesChoix(q.type) && (q.choix?.length ?? 0) < LIMITES.choixMin) trouves[q.id] = 'choix'
    else if (aDesChoix(q.type) && new Set(q.choix).size !== q.choix?.length) trouves[q.id] = 'doublon'
  }
  return trouves
}

/** Les réponses à envoyer, dans l'ordre des questions ; une réponse vide n'est pas envoyée. */
export function versReponses(questions: readonly Question[], valeurs: Valeurs): Reponse[] {
  return questions.flatMap((q): Reponse[] => {
    const v = valeurs[q.id]
    if (Array.isArray(v)) return v.length ? [{ questionId: q.id, valeur: [...v] }] : []
    const propre = (v ?? '').trim()
    return propre ? [{ questionId: q.id, valeur: propre }] : []
  })
}

/** Les questions obligatoires sans réponse : le formulaire les signale avant même d'appeler l'API. */
export function manquantes(questions: readonly Question[], valeurs: Valeurs): string[] {
  const repondues = new Set(versReponses(questions, valeurs).map((r) => r.questionId))
  return questions.filter((q) => q.obligatoire && !repondues.has(q.id)).map((q) => q.id)
}

/**
 * Les erreurs encore valables : une question obligatoire qui a maintenant une réponse perd son erreur tout de suite.
 * Les autres erreurs de l'API (`trop_long`, `valeur_inconnue`…) restent jusqu'au prochain envoi.
 */
export function erreursRestantes(questions: readonly Question[], valeurs: Valeurs, erreurs: Record<string, string>): Record<string, string> {
  const repondues = new Set(versReponses(questions, valeurs).map((r) => r.questionId))
  return Object.fromEntries(Object.entries(erreurs).filter(([id, code]) => !(code === 'obligatoire' && repondues.has(id))))
}

/** Le message sous le formulaire après un refus : `null` s'il n'y a plus rien à signaler, sinon le nombre de réponses obligatoires qui manquent encore. */
export function resumeErreurs(restantes: Record<string, string>): { manquantes: number } | null {
  const codes = Object.values(restantes)
  return codes.length ? { manquantes: codes.filter((c) => c === 'obligatoire').length } : null
}

/** Met le focus sur le premier champ d'une question, pour qu'on sache tout de suite quoi remplir (WCAG 3.3.1). */
export function focaliserQuestion(id: string): void {
  document.querySelector<HTMLElement>(`[data-test="question-${id}"] :is(input, textarea)`)?.focus()
}

/** Les erreurs de l'API (`reponses.<id>` → code), ramenées à l'identifiant de la question. */
export function erreursParQuestion(champs: Record<string, string>): Record<string, string> {
  const parQuestion: Record<string, string> = {}
  for (const [champ, code] of Object.entries(champs)) {
    const trouve = champ.match(/^reponses\.([a-z0-9]+)$/)
    if (trouve) parQuestion[trouve[1]!] = code
  }
  return parQuestion
}
