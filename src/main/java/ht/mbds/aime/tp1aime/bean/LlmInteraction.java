package ht.mbds.aime.tp1aime.bean;

/**
 * Conteneur immuable pour une interaction avec le LLM.
 * - questionJson : JSON envoyé
 * - reponseJson : JSON reçu
 * - reponseExtraite : texte de réponse à afficher
 */
public record LlmInteraction(String questionJson, String reponseJson, String reponseExtraite) {
}
