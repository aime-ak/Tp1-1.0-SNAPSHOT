package ht.aime;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class Test2 {

    // Pricing for gemini-2.5-flash (adjust if your course file uses other values)
    private static final double INPUT_USD_PER_1M_TOKENS = 0.30;
    private static final double OUTPUT_USD_PER_1M_TOKENS = 2.50;

    private static final String MODEL = "gemini-2.5-flash";
    private static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/"
            + MODEL + ":generateContent?key=";
    public static void main(String[] args) {

        String geminiKey = System.getenv("GEMINI_KEY");
        if (geminiKey == null || geminiKey.isBlank()) {
            System.out.println("La variable d'environnement GEMINI_KEY n'est pas definie.");
            return;
        }

        HttpClient client = HttpClient.newHttpClient();

        try {
            String question = "Quelle est la capitale d'Haiti ?";
            ApiResult result = ask(client, geminiKey, question);

            System.out.println("Question : " + question);
            System.out.println("Reponse  : " + result.answer());

            if (result.statusCode() >= 400) {
                System.out.println("Erreur HTTP : " + result.statusCode());
                return;
            }

            long inputTokens = result.inputTokens();
            long outputTokens = result.outputTokens();
            double usdCost = computeUsdCost(inputTokens, outputTokens);
            double requestsForOneDollar = usdCost > 0 ? (1.0 / usdCost) : Double.POSITIVE_INFINITY;

            System.out.println("Tokens entree : " + inputTokens);
            System.out.println("Tokens sortie : " + outputTokens);
            System.out.printf("Cout requete (USD) : %.8f%n", usdCost);
            if (Double.isFinite(requestsForOneDollar)) {
                System.out.printf("Requetes similaires pour depenser 1 USD : %.2f%n", requestsForOneDollar);
            } else {
                System.out.println("Requetes similaires pour depenser 1 USD : infini (cout nul)");
            }
        } catch (IOException e) {
            System.out.println(GeminiSupport.formatOperationFailure("l'execution de Test2", e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Interruption pendant l'execution de Test2.");
        }
    }

    private static ApiResult ask(HttpClient client, String key, String question)
            throws IOException, InterruptedException {

        String body = "{\"contents\":[{\"parts\":[{\"text\":\"" + escape(question) + "\"}]}]}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL + key.trim()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = GeminiSupport.sendWithRetries(client, request);
        String json = response.body();

        String answer;
        if (response.statusCode() >= 400) {
            answer = GeminiSupport.formaterErreur(response.statusCode(), json);
        } else {
            String text = GeminiSupport.extractJsonString(json, "text");
            answer = (text == null || text.isBlank())
                    ? "Reponse recue, mais texte introuvable."
                    : text;
        }

        long inputTokens = extractJsonLong(json, "promptTokenCount");
        long outputTokens = extractJsonLong(json, "candidatesTokenCount");

        return new ApiResult(response.statusCode(), answer, inputTokens, outputTokens);
    }

    private static double computeUsdCost(long inputTokens, long outputTokens) {
        double inputCost = (inputTokens / 1_000_000.0) * INPUT_USD_PER_1M_TOKENS;
        double outputCost = (outputTokens / 1_000_000.0) * OUTPUT_USD_PER_1M_TOKENS;
        return inputCost + outputCost;
    }

    private static long extractJsonLong(String json, String key) {
        String marker = "\"" + key + "\":";
        int start = json.indexOf(marker);
        if (start == -1) {
            return 0L;
        }

        int i = start + marker.length();
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }

        int j = i;
        while (j < json.length() && Character.isDigit(json.charAt(j))) {
            j++;
        }

        if (j == i) {
            return 0L;
        }

        try {
            return Long.parseLong(json.substring(i, j));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private record ApiResult(int statusCode, String answer, long inputTokens, long outputTokens) {
    }
}
