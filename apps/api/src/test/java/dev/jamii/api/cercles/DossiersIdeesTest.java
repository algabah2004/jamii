package dev.jamii.api.cercles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;
import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DossiersIdeesTest {
 @Autowired MockMvcTester mvc;
 @Autowired JdbcClient base;

 private UtilisateurConnecte utilisateur() {
  UUID id = UUID.randomUUID();
  base.sql("INSERT INTO utilisateur (id, nom, login_github, cree_le, mis_a_jour_le) VALUES (:id, 'Membre test', :login, :t, :t)")
   .param("id", id).param("login", "t-" + id).param("t", Timestamp.from(Instant.now())).update();
  return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
 }
 private MvcTestResult ecrire(String methode, String url, UtilisateurConnecte u, String json) {
  var req = methode.equals("PUT") ? mvc.put().uri(url) : mvc.post().uri(url);
  return req.with(csrf()).with(oauth2Login().oauth2User(u)).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
 }
 private MvcTestResult lire(String url, UtilisateurConnecte u) { return mvc.get().uri(url).with(oauth2Login().oauth2User(u)).exchange(); }
 private UUID creer(UtilisateurConnecte u) throws Exception {
  var r = ecrire("POST", "/api/cercles", u, "{\"nom\":\"Projet du cercle\",\"taille\":4}");
  assertThat(r).hasStatus(HttpStatus.CREATED);
  return UUID.fromString(JsonPath.read(r.getResponse().getContentAsString(), "$.id"));
 }
 private String idee(String extra) {
  return "{\"titre\":\"Une idée préparée\",\"description\":\"Un projet expliqué aux membres.\"" + extra + "}";
 }
 private String url(UUID cercle) { return "/api/cercles/" + cercle + "/membres/moi/idee"; }
 private String uploader(UtilisateurConnecte u, UUID cercle) throws Exception {
  var r = mvc.post().uri("/api/fichiers").multipart().file(new MockMultipartFile("fichier", "cahier.txt", "text/plain", "Les besoins du projet".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
   .param("cercleId", cercle.toString()).with(csrf()).with(oauth2Login().oauth2User(u)).exchange();
  assertThat(r).hasStatus(HttpStatus.CREATED);
  return JsonPath.read(r.getResponse().getContentAsString(), "$.id");
 }
 @Test void le_document_est_visible_aux_membres_apres_enregistrement_et_cache_aux_autres() throws Exception {
  var auteur = utilisateur(); var membre = utilisateur(); var tiers = utilisateur();
  UUID c = creer(auteur);
  assertThat(ecrire("POST", "/api/cercles/" + c + "/membres", membre, "{}")).hasStatusOk();
  String id = uploader(auteur, c), fichier = "/api/fichiers/" + id;
  assertThat(lire(fichier, auteur)).hasStatusOk();
  assertThat(lire(fichier, membre)).hasStatus(HttpStatus.NOT_FOUND);
  assertThat(ecrire("PUT", url(c), auteur, idee(",\"cahierChargesId\":\"" + id + "\""))).hasStatusOk()
   .bodyJson().extractingPath("$.idee.cahierCharges.nom").isEqualTo("cahier.txt");
  var document = lire(fichier, membre);
  assertThat(document).hasStatusOk();
  assertThat(document.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
  assertThat(lire(fichier, tiers)).hasStatus(HttpStatus.NOT_FOUND);
  assertThat(lire("/api/cercles/" + c, tiers)).hasStatusOk().bodyJson().extractingPath("$.membres[0].idee").isNull();
  assertThat(ecrire("PUT", url(c), membre, idee(",\"cahierChargesId\":\"" + id + "\""))).hasStatus(HttpStatus.BAD_REQUEST);
 }
 @Test void le_backlog_est_remplace_puis_cree_une_seule_fois_pour_le_bon_tour() throws Exception {
  var membres = List.of(utilisateur(), utilisateur(), utilisateur(), utilisateur());
  UUID c = creer(membres.getFirst());
  String backlog = ",\"backlog\":[{\"titre\":\"Concevoir les écrans\",\"description\":\"Parcours du projet\",\"priorite\":\"haute\",\"echeance\":\"2027-01-10\"},{\"titre\":\"Créer la connexion\"}]";
  for (var u : membres) {
   if (!u.equals(membres.getFirst())) assertThat(ecrire("POST", "/api/cercles/" + c + "/membres", u, "{}")).hasStatusOk();
   assertThat(ecrire("PUT", url(c), u, idee(backlog))).hasStatusOk();
   assertThat(ecrire("PUT", url(c), u, idee(backlog))).hasStatusOk();
  }
  assertThat(base.sql("SELECT count(*) FROM cercle_backlog WHERE cercle_id = :c").param("c", c).query(Integer.class).single()).isEqualTo(8);
  assertThat(ecrire("POST", "/api/cercles/" + c + "/tirage", membres.getFirst(), "{}")).hasStatusOk();
  UUID projet = base.sql("SELECT projet_id FROM cercle_membre WHERE cercle_id = :c AND rang = 1").param("c", c).query(UUID.class).single();
  assertThat(base.sql("SELECT count(*) FROM tache WHERE projet_id = :p").param("p", projet).query(Integer.class).single()).isEqualTo(2);
  assertThat(base.sql("SELECT titre FROM tache WHERE projet_id = :p ORDER BY cree_le, id").param("p", projet).query(String.class).list()).containsExactly("Concevoir les écrans", "Créer la connexion");
  assertThat(base.sql("SELECT priorite FROM tache WHERE projet_id = :p AND titre = 'Concevoir les écrans'").param("p", projet).query(String.class).single()).isEqualTo("haute");
  assertThat(base.sql("SELECT count(*) FROM tache WHERE projet_id = :p AND assignee_id IS NULL AND statut = 'a_faire'").param("p", projet).query(Integer.class).single()).isEqualTo(2);
  assertThat(ecrire("POST", "/api/cercles/" + c + "/tirage", membres.getFirst(), "{}")).hasStatus(HttpStatus.CONFLICT);
  assertThat(ecrire("PUT", url(c), membres.getFirst(), idee(backlog))).hasStatus(HttpStatus.CONFLICT);
  assertThat(base.sql("SELECT count(*) FROM tache WHERE projet_id = :p").param("p", projet).query(Integer.class).single()).isEqualTo(2);
 }
 @Test void un_backlog_invalide_n_efface_pas_la_preparation_valide() throws Exception {
  var u = utilisateur(); UUID c = creer(u);
  assertThat(ecrire("PUT", url(c), u, idee(",\"backlog\":[{\"titre\":\"Une tâche valide\"}]"))).hasStatusOk();
  assertThat(ecrire("PUT", url(c), u, idee(",\"backlog\":[{\"titre\":\"Identique\"},{\"titre\":\"identique\"}]"))).hasStatus(HttpStatus.BAD_REQUEST);
  assertThat(base.sql("SELECT titre FROM cercle_backlog WHERE cercle_id = :c").param("c", c).query(String.class).single()).isEqualTo("Une tâche valide");
 }
}
