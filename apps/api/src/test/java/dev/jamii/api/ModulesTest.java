package dev.jamii.api;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Chaque sous-paquet de dev.jamii.api (auth, projets, communautes…) est un module.
 * Ce test échoue si un module utilise les classes internes d'un autre, ou s'il y a un cycle.
 */
class ModulesTest {

	@Test
	void les_frontieres_entre_modules_sont_respectees() {
		ApplicationModules.of(ApiApplication.class).verify();
	}

}
