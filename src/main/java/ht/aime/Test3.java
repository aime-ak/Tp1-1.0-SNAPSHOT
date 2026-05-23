package ht.aime;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class Test3 {

    private static final String TRANSLATION_PROMPT_TEMPLATE = "Traduis le texte suivant en anglais : {texte}";

    public static void main(String[] args) {

        String geminiKey = System.getenv("GEMINI_KEY");
        if (geminiKey == null || geminiKey.isBlank()) {
            System.out.println("La variable d'environnement GEMINI_KEY n'est pas definie.");
            return;
        }

        HttpClient client = HttpClient.newHttpClient();

        String texteATraduire = args.length > 0
                ? String.join(" ", args)
                : "Bonjour, comment allez-vous aujourd'hui ?";

        String question = renderPrompt(texteATraduire);

        String body = "{\"contents\":[{\"parts\":[{\"text\":\"" + Test1.escape(question) + "\"}]}]}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(Test1.URL + geminiKey.trim()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = GeminiSupport.sendWithRetries(client, request);
            String traduction = Test1.formaterReponse(response.statusCode(), response.body());

            System.out.println("Texte source : " + texteATraduire);
            System.out.println("Prompt rendu : " + question);
            System.out.println("Traduction   : " + traduction);
        } catch (IOException e) {
            System.out.println(GeminiSupport.formatOperationFailure("l'execution de Test3", e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Interruption pendant l'execution de Test3.");
        }
    }

    private static String renderPrompt(String texteATraduire) {
        return TRANSLATION_PROMPT_TEMPLATE.replace("{texte}", texteATraduire);
    }
}
