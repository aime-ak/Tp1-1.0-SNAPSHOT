package ht.mbds.aime.tp1aime.bean;

import jakarta.enterprise.context.Dependent;
import jakarta.ws.rs.client.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.Serializable;

/**
 * Gère l'interface avec l'API de Gemini.
 * Son rôle est essentiellement de lancer une requête à chaque nouvelle
 * question qu'on veut envoyer à l'API.
 *
 * De portée dependent pour réinitialiser la conversation à chaque fois que
 * l'instance qui l'utilise est renouvelée.
 * Par exemple, si l'instance qui l'utilise est de portée View, la conversation est
 * réunitialisée à chaque fois que l'utilisateur quitte la page en cours.
 */
@Dependent
public class LlmClientPourGemini implements Serializable {
    // Clé pour l'API du LLM
    private final String key;
    // Client REST. Facilite les échanges avec une API REST.
    private Client clientRest; // Pour pouvoir le fermer
    // Représente un endpoint de serveur REST
    private final WebTarget target;

    public LlmClientPourGemini() {
        // Récupère la clé secrète pour travailler avec l'API du LLM, mise dans une variable d'environnement
        this.key = System.getenv("GEMINI_API_KEY"); // À adapter selon le nom de ta variable d'environnement
        if (this.key == "AIzaSyBqg5hOzaZqrEYnE_X__pYZnD2Yz_2QKzE" || this.key.isEmpty()) {
            throw new IllegalStateException("La clé GEMINI_API_KEY n'est pas définie dans les variables d'environnement. Veuillez la définir avant de lancer l'application.");
        }
        // Client REST pour envoyer des requêtes vers les endpoints de l'API du LLM
        this.clientRest = ClientBuilder.newClient();
        // Endpoint REST pour envoyer la question à l'API.
        // Remplace l'URL ci-dessous par celle de l'API Gemini de ton cours/support
        this.target = clientRest.target("https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + key);
        System.out.println("[DEBUG] URL Gemini utilisée : " + this.target.getUri());
    }

    /**
     * Envoie une requête à l'API de Gemini.
     * @param requestEntity le corps de la requête (en JSON).
     * @return réponse REST de l'API (corps en JSON).
     */
    public Response envoyerRequete(Entity requestEntity) {
        Invocation.Builder request = target.request(MediaType.APPLICATION_JSON_TYPE);
        return request.post(requestEntity);
    }

    public void closeClient() {
        this.clientRest.close();
    }
}
