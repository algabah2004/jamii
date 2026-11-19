// Les tests lisent les textes français : on fait comme si la personne avait choisi le français (jsdom dit « en-US »).
// Les tests du pré-rendu tournent sous Node, sans stockage : la langue y est fixée par le rendu lui-même.
if (typeof localStorage !== 'undefined') localStorage.setItem('jamii.langue', 'fr')
