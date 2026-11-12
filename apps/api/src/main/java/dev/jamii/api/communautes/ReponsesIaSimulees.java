package dev.jamii.api.communautes;

import java.time.Duration;
import java.util.Optional;

/**
 * Une IA simulée, sans réseau : elle « réfléchit » un instant puis propose une démarche générale. Annoncée comme telle
 * (plan-final.md : « fournisseur branchable, simulé pour la démo »). Le texte reste prudent : il sera relu par un pair.
 */
class ReponsesIaSimulees implements ReponsesIa {

	private final Duration reflexion;

	ReponsesIaSimulees(Duration reflexion) {
		this.reflexion = reflexion;
	}

	@Override
	public Optional<String> proposer(Question question) {
		try {
			Thread.sleep(reflexion);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		}
		return Optional.of("""
				Une piste pour « %s » : reproduis d'abord le problème sur un petit exemple isolé, puis compare avec la \
				documentation officielle de l'outil concerné (%s). Note ce qui change entre les deux : c'est souvent là \
				que se trouve la cause. Un membre de la communauté doit vérifier cette réponse avant qu'elle soit acceptée.\
				""".formatted(question.titre(), question.theme()));
	}

}
