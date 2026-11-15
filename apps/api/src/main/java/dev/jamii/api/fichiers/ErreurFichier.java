package dev.jamii.api.fichiers;

/** Une erreur sur un fichier, avec son statut HTTP et son code (contracts/fichiers.yaml). */
public class ErreurFichier extends RuntimeException {

	final int statut;

	final String code;

	final long attenteSecondes;

	ErreurFichier(int statut, String code, String titre) {
		this(statut, code, titre, 0);
	}

	ErreurFichier(int statut, String code, String titre, long attenteSecondes) {
		super(titre, null, false, false);
		this.statut = statut;
		this.code = code;
		this.attenteSecondes = attenteSecondes;
	}

	public int statut() {
		return statut;
	}

	public String code() {
		return code;
	}

}
