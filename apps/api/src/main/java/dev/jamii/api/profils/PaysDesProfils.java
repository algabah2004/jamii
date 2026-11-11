package dev.jamii.api.profils;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import dev.jamii.api.systalink.ChiffresDesModules;

/**
 * Pour le tableau de bord Systalink (#18, demande #66) : combien d'inscrits par pays déclaré dans le profil.
 * On compte tous les profils qui ont déclaré un pays, publiés ou non : c'est un total, jamais une liste de
 * personnes, et le tableau de bord regroupe sous « autres » les pays de moins de 5 inscrits.
 */
@Service
class PaysDesProfils implements ChiffresDesModules.PaysDesInscrits {

	private final JdbcClient base;

	PaysDesProfils(JdbcClient base) {
		this.base = base;
	}

	@Override
	public Map<String, Long> parPays() {
		Map<String, Long> parPays = new LinkedHashMap<>();
		base.sql("SELECT pays, count(*) AS nombre FROM profil WHERE pays IS NOT NULL GROUP BY pays ORDER BY nombre DESC, pays")
			.query((ligne, i) -> parPays.put(ligne.getString("pays"), ligne.getLong("nombre")))
			.list();
		return parPays;
	}

}
