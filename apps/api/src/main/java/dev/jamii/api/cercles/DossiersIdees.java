package dev.jamii.api.cercles;

import java.sql.Date;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import dev.jamii.api.fichiers.Fichiers;
import dev.jamii.api.projets.Projets;

/** Les pièces de préparation d'une idée. Le verrou du cercle et la transaction sont portés par GestionCercles. */
@Service
class DossiersIdees {
 private final JdbcClient base;
 private final Fichiers fichiers;
 private final Projets projets;

 DossiersIdees(JdbcClient base, Fichiers fichiers, Projets projets) {
  this.base = base; this.fichiers = fichiers; this.projets = projets;
 }

 void enregistrer(UUID cercle, UUID auteur, UUID document, List<Projets.TachePrevue> lignes) {
  List<Projets.TachePrevue> backlog = lignes == null ? List.of() : lignes;
  if (backlog.size() > 100) throw invalide("Le backlog contient plus de 100 tâches");
  var titres = new HashSet<String>();
  for (var t : backlog) {
   if (t == null || t.titre() == null || t.titre().strip().length() < 3 || t.titre().strip().length() > 160
     || (t.description() != null && t.description().length() > 2000)
     || (t.priorite() != null && !List.of("haute", "normale", "basse").contains(t.priorite()))) {
    throw invalide("Une tâche du backlog est invalide");
   }
   if (!titres.add(t.titre().strip().toLowerCase(Locale.ROOT))) throw invalide("Deux tâches ont le même titre");
  }
  if (document != null) {
   fichiers.verifierPiecesCercle(List.of(document), auteur, cercle);
   if (!"document".equals(fichiers.decrire(List.of(document)).getFirst().sorte())) throw invalide("Un document est attendu");
  }
  base.sql("UPDATE cercle_membre SET cahier_charges_id = :d WHERE cercle_id = :c AND utilisateur_id = :u")
   .param("d", document, java.sql.Types.OTHER).param("c", cercle).param("u", auteur).update();
  base.sql("DELETE FROM cercle_backlog WHERE cercle_id = :c AND utilisateur_id = :u").param("c", cercle).param("u", auteur).update();
  for (int i = 0; i < backlog.size(); i++) {
   var t = backlog.get(i);
   base.sql("INSERT INTO cercle_backlog (cercle_id, utilisateur_id, position, titre, description, priorite, echeance) VALUES (:c, :u, :i, :t, :d, :p, :e)")
    .param("c", cercle).param("u", auteur).param("i", i).param("t", t.titre().strip())
    .param("d", t.description()).param("p", t.priorite() == null ? "normale" : t.priorite())
    .param("e", t.echeance() == null ? null : Date.valueOf(t.echeance())).update();
  }
 }

 Fichiers.Piece document(UUID cercle, UUID auteur) {
  return base.sql("SELECT cahier_charges_id FROM cercle_membre WHERE cercle_id = :c AND utilisateur_id = :u AND cahier_charges_id IS NOT NULL")
   .param("c", cercle).param("u", auteur).query(UUID.class).optional()
   .flatMap(id -> fichiers.decrire(List.of(id)).stream().findFirst()).orElse(null);
 }

 List<Projets.TachePrevue> backlog(UUID cercle, UUID auteur) {
  return base.sql("SELECT titre, description, priorite, echeance FROM cercle_backlog WHERE cercle_id = :c AND utilisateur_id = :u ORDER BY position")
   .param("c", cercle).param("u", auteur).query((rs, i) -> new Projets.TachePrevue(rs.getString("titre"), rs.getString("description"), rs.getString("priorite"), rs.getObject("echeance", java.time.LocalDate.class))).list();
 }

 void creerTaches(UUID cercle, UUID auteur, UUID projet, Instant maintenant) {
  projets.initialiserBacklog(projet, auteur, backlog(cercle, auteur), maintenant);
 }

 private static ErreurCercle invalide(String message) {
  return new ErreurCercle(HttpStatus.BAD_REQUEST, "backlog_invalide", message);
 }
}
