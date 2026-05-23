package ht.aime;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class Test1 {

    static final String MODEL = "gemini-2.5-flash";
    static final String URL   = "https://generativelanguage.googleapis.com/v1beta/models/"
                                 + MODEL + ":generateContent?key=";
    static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void main(String[] args) {

        String geminiKey = System.getenv("GEMINI_KEY");
        if (geminiKey == null || geminiKey.isBlank()) {
            System.out.println("La variable d'environnement GEMINI_KEY n'est pas definie.");
            return;
        }

        HttpClient client = HttpClient.newHttpClient();

        try {
            chat(client, geminiKey, "Quelle est la capitale de la France ?");
            chat(client, geminiKey, "Quelle heure est-il ?");
            chat(client, geminiKey, "Bonjour, je m'appelle AIME.");
            chat(client, geminiKey, "Comment je m'appelle ?");
        } catch (IOException e) {
            System.out.println(GeminiSupport.formatOperationFailure("l'execution de Test1", e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Interruption pendant l'execution de Test1.");
        }
    }

    static void chat(HttpClient client, String key, String question)
            throws IOException, InterruptedException {

        if (isTimeQuestion(question)) {
            String heure = LocalDateTime.now().format(TIME_FORMAT);
            System.out.println("Question : " + question);
            System.out.println("Reponse  : Il est " + heure + " (heure locale de cette machine).");
            System.out.println();
            return;
        }

        String body = "{\"contents\":[{\"parts\":[{\"text\":\"" + escape(question) + "\"}]}]}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL + key.trim()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = GeminiSupport.sendWithRetries(client, request);
        String reponse = formaterReponse(response.statusCode(), response.body());

        System.out.println("Question : " + question);
        System.out.println("Reponse  : " + reponse);
        System.out.println();
    }

    static String formaterReponse(int statusCode, String json) {
        return GeminiSupport.formaterReponse(statusCode, json);
    }

    static String formaterErreur(int statusCode, String json) {
        return GeminiSupport.formaterErreur(statusCode, json);
    }

    static String extraireValeurJson(String json, String cle) {
        return GeminiSupport.extractJsonString(json, cle);
    }

    static String lireChaineJson(String json, int positionGuillemet) {
        StringBuilder resultat = new StringBuilder();
        boolean echappement = false;

        for (int i = positionGuillemet + 1; i < json.length(); i++) {
            char caractere = json.charAt(i);

            if (echappement) {
                switch (caractere) {
                    case 'n' -> resultat.append('\n');
                    case 'r' -> resultat.append('\r');
                    case 't' -> resultat.append('\t');
                    case '"' -> resultat.append('"');
                    case '\\' -> resultat.append('\\');
                    case '/' -> resultat.append('/');
                    default -> resultat.append(caractere);
                }
                echappement = false;
                continue;
            }

            if (caractere == '\\') {
                echappement = true;
            } else if (caractere == '"') {
                return resultat.toString();
            } else {
                resultat.append(caractere);
            }
        }

        return resultat.toString();
    }

    // Echappe les caracteres speciaux pour le JSON
    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    static boolean isTimeQuestion(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        return normalized.contains("heure");
    }
}
