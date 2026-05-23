package ht.aime;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;

final class GeminiSupport {

    private static final int MAX_TRANSIENT_RETRIES = 3;
    private static final long DEFAULT_RETRY_DELAY_MS = 5_000L;
    private static final long MAX_RETRY_DELAY_MS = 30_000L;

    private GeminiSupport() {
    }

    static HttpResponse<String> sendWithRetries(HttpClient client, HttpRequest request)
            throws IOException, InterruptedException {

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int retries = 0;

        while (isTransientStatus(response.statusCode()) && retries < MAX_TRANSIENT_RETRIES) {
            long waitMs = computeRetryDelayMs(response.statusCode(), response.body(), retries);
            System.out.println(buildRetryMessage(response.statusCode(), waitMs));
            Thread.sleep(waitMs);

            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            retries++;
        }

        return response;
    }

    static <T> T executeWithRetries(CheckedSupplier<T> action, String operationDescription) {
        RuntimeException lastException = null;

        for (int attempt = 0; attempt <= MAX_TRANSIENT_RETRIES; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                if (!isTransientGeminiException(e)) {
                    throw e;
                }

                lastException = e;
                if (attempt == MAX_TRANSIENT_RETRIES) {
                    throw new IllegalStateException(formatExceptionMessage(operationDescription, e), e);
                }

                int statusCode = extractTransientStatusCode(e);
                long waitMs = computeRetryDelayMs(statusCode, collectMessages(e), attempt);
                System.out.println(buildRetryMessage(statusCode, waitMs));
                sleep(waitMs);
            }
        }

        throw new IllegalStateException(formatExceptionMessage(operationDescription, lastException), lastException);
    }

    static void runWithRetries(CheckedRunnable action, String operationDescription) {
        executeWithRetries(() -> {
            action.run();
            return null;
        }, operationDescription);
    }

    static String formaterReponse(int statusCode, String json) {
        if (statusCode >= 400) {
            return formaterErreur(statusCode, json);
        }

        String texte = extractJsonString(json, "text");
        if (texte != null && !texte.isBlank()) {
            return texte;
        }

        return "Reponse recue, mais texte introuvable dans le JSON.";
    }

    static String formaterErreur(int statusCode, String json) {
        String message = extractJsonString(json, "message");
        String retryDelay = extractJsonString(json, "retryDelay");

        if (statusCode == 429) {
            StringBuilder erreur = new StringBuilder("Quota Gemini depasse");
            if (message != null && !message.isBlank()) {
                erreur.append(" : ").append(message.replace('\n', ' '));
            }
            if (retryDelay != null && !retryDelay.isBlank()) {
                erreur.append(" Retry conseille dans ").append(retryDelay).append('.');
            }
            return erreur.toString();
        }

        if (statusCode == 503) {
            StringBuilder erreur = new StringBuilder("Service Gemini temporairement indisponible");
            if (message != null && !message.isBlank()) {
                erreur.append(" : ").append(message.replace('\n', ' '));
            }
            if (retryDelay != null && !retryDelay.isBlank()) {
                erreur.append(" Retry conseille dans ").append(retryDelay).append('.');
            } else {
                erreur.append(" Reessayez dans quelques instants.");
            }
            return erreur.toString();
        }

        if (message != null && !message.isBlank()) {
            return "Erreur HTTP " + statusCode + " : " + message.replace('\n', ' ');
        }

        return "Erreur HTTP " + statusCode + ".";
    }

    static String extractJsonString(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyPos = json.indexOf(marker);
        if (keyPos == -1) {
            return null;
        }

        int colonPos = json.indexOf(':', keyPos + marker.length());
        if (colonPos == -1) {
            return null;
        }

        int valuePos = colonPos + 1;
        while (valuePos < json.length() && Character.isWhitespace(json.charAt(valuePos))) {
            valuePos++;
        }

        if (valuePos >= json.length() || json.charAt(valuePos) != '"') {
            return null;
        }

        return readJsonString(json, valuePos);
    }

    static boolean isTransientStatus(int statusCode) {
        return statusCode == 429 || statusCode == 503;
    }

    private static String buildRetryMessage(int statusCode, long waitMs) {
        String reason = statusCode == 429
                ? "quota atteinte"
                : "service temporairement indisponible";
        return "Info: " + reason + ", nouvelle tentative dans " + waitMs + " ms...";
    }

    private static long computeRetryDelayMs(int statusCode, String json, int retryIndex) {
        long retryDelayMs = parseRetryDelayMs(json);
        if (retryDelayMs > 0) {
            return retryDelayMs;
        }

        long baseDelayMs = statusCode == 503 ? 3_000L : DEFAULT_RETRY_DELAY_MS;
        long exponentialDelayMs = baseDelayMs * (1L << retryIndex);
        return Math.min(exponentialDelayMs, MAX_RETRY_DELAY_MS);
    }

    private static long parseRetryDelayMs(String json) {
        String retryDelay = extractJsonString(json, "retryDelay");
        if (retryDelay == null || retryDelay.isBlank()) {
            return 0L;
        }

        String cleaned = retryDelay.trim().toLowerCase(Locale.ROOT);
        if (cleaned.endsWith("s")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        try {
            double seconds = Double.parseDouble(cleaned);
            return Math.max(0L, (long) Math.ceil(seconds * 1000.0));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String readJsonString(String json, int openingQuotePos) {
        StringBuilder out = new StringBuilder();
        boolean escaped = false;

        for (int i = openingQuotePos + 1; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                switch (c) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    default -> out.append(c);
                }
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return out.toString();
            } else {
                out.append(c);
            }
        }

        return out.toString();
    }

    static boolean isTransientGeminiException(Throwable throwable) {
        int statusCode = extractTransientStatusCode(throwable);
        return statusCode == 429 || statusCode == 503;
    }

    static String formatOperationFailure(String operationDescription, Throwable throwable) {
        int statusCode = extractTransientStatusCode(throwable);
        if (statusCode == 429 || statusCode == 503) {
            return "Echec pendant " + operationDescription + " : "
                    + formaterErreur(statusCode, collectMessages(throwable));
        }

        String details = collectMessages(throwable).replace('\n', ' ').trim();
        if (details.isEmpty()) {
            details = throwable.getClass().getSimpleName();
        }

        return "Echec pendant " + operationDescription + " : " + details;
    }

    private static boolean isTransientGeminiException(RuntimeException e) {
        int statusCode = extractTransientStatusCode(e);
        return statusCode == 429 || statusCode == 503;
    }

    private static int extractTransientStatusCode(Throwable throwable) {
        String message = collectMessages(throwable).toLowerCase(Locale.ROOT);

        if (message.contains("\"code\": 503")
                || message.contains("\"code\":503")
                || message.contains("http 503")
                || message.contains("status\": \"unavailable\"")
                || message.contains("status\":\"unavailable\"")
                || message.contains("currently experiencing high demand")) {
            return 503;
        }

        if (message.contains("\"code\": 429")
                || message.contains("\"code\":429")
                || message.contains("http 429")
                || message.contains("resource exhausted")
                || message.contains("quota")) {
            return 429;
        }

        return 0;
    }

    private static String formatExceptionMessage(String operationDescription, RuntimeException e) {
        return formatOperationFailure(operationDescription, e);
    }

    private static String collectMessages(Throwable throwable) {
        StringBuilder combined = new StringBuilder();
        Throwable current = throwable;

        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                if (!combined.isEmpty()) {
                    combined.append('\n');
                }
                combined.append(message);
            }
            current = current.getCause();
        }

        return combined.toString();
    }

    private static void sleep(long waitMs) {
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interruption pendant l'attente avant une nouvelle tentative.", e);
        }
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get();
    }

    @FunctionalInterface
    interface CheckedRunnable {
        void run();
    }
}
