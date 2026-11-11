package dev.jamii.api.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Active les tâches planifiées (ici : supprimer les vieux jetons de connexion). */
@Configuration
@EnableScheduling
class PlanificationConfig {

}
