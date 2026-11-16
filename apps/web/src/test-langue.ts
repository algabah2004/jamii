// Les tests lisent les textes français : on fait comme si la personne avait choisi le français (jsdom dit « en-US »).
localStorage.setItem('jamii.langue', 'fr')
