package ht.mbds.aime.tp1aime.bean;

import jakarta.enterprise.context.Dependent;
import jakarta.ws.rs.client.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.Serializable;

@Dependent
public class LlmClientPourGemini implements Serializable {
    private final String apiKey;
    private final Client client;

    public LlmClientPourGemini() {
        this.apiKey = System.getenv("GEMINI_KEY");
        if (this.apiKey == null || this.apiKey.isEmpty()) {
            throw new IllegalStateException("La clé GEMINI_KEY n'est pas définie dans les variables d'environnement. Veuillez la définir avant de lancer l'application.");
        }
        this.client = ClientBuilder.newClient();
    }

    /**
     * Envoie une requête à l'API Gemini et gère les erreurs HTTP.
     * Retourne le contenu de la réponse ou un message d'erreur explicite.
     */
    public Response envoyerRequete(Entity<String> entity) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + "gemini-2.5-flash:generateContent?key=" + apiKey;
        return client.target(url)
                .request(MediaType.APPLICATION_JSON)
                .post(entity);
    }

    public void close() {
        if (client != null) {
            client.close();
        }
    }
}
