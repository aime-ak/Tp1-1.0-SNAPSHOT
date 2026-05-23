package ht.aime;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MeteoConversationManager {

    private static final int DEFAULT_FORECAST_DAYS = 3;
    private static final Pattern DAYS_PATTERN = Pattern.compile("(?iu)\\b(\\d{1,2})\\s*jour(?:s)?\\b");
    private static final Pattern LATITUDE_PATTERN = Pattern.compile("(?iu)latitude\\s*(?:est|=|:)?\\s*(-?\\d+(?:[\\.,]\\d+)?)");
    private static final Pattern LONGITUDE_PATTERN = Pattern.compile("(?iu)longitude\\s*(?:est|=|:)?\\s*(-?\\d+(?:[\\.,]\\d+)?)");

    private final AssistantMeteo assistant;
    private final MeteoTool meteoTool;
    private int lastForecastDays = DEFAULT_FORECAST_DAYS;
    private boolean waitingForCity;

    MeteoConversationManager(AssistantMeteo assistant, MeteoTool meteoTool) {
        this.assistant = assistant;
        this.meteoTool = meteoTool;
    }

    String reply(String question) {
        Integer forecastDays = extractForecastDays(question);
        if (forecastDays != null) {
            lastForecastDays = forecastDays;
        }

        boolean weatherQuestion = isWeatherQuestion(question);
        double[] coordinates = extractCoordinates(question);
        if (coordinates != null && (weatherQuestion || waitingForCity)) {
            waitingForCity = false;
            return meteoTool.previsionsPrecipitations(coordinates[0], coordinates[1], lastForecastDays);
        }

        String city = MeteoTool.extractCityCandidate(question);
        if (city != null && (weatherQuestion || waitingForCity)) {
            try {
                double[] coordinatesForCity = meteoTool.coordonneesVille(city);
                waitingForCity = false;
                return "Pour " + city + ", "
                        + meteoTool.previsionsPrecipitations(coordinatesForCity[0], coordinatesForCity[1], lastForecastDays);
            } catch (IllegalArgumentException e) {
                waitingForCity = true;
                return "Je n'ai pas reconnu cette ville. Peux-tu donner une ville plus precise ?";
            }
        }

        if (weatherQuestion) {
            waitingForCity = true;
            return "J'ai besoin d'une ville precise pour verifier la pluie.";
        }

        waitingForCity = false;
        return GeminiSupport.executeWithRetries(
                () -> assistant.chat(question),
                "la generation de la reponse"
        );
    }

    private static boolean isWeatherQuestion(String question) {
        String normalized = normalize(question);
        return normalized.contains("parapluie")
                || normalized.contains("pluie")
                || normalized.contains("meteo")
                || normalized.contains("temperature")
                || normalized.contains("temperatures")
                || normalized.contains("valise")
                || normalized.contains("pleuvoir")
                || normalized.contains("pleut")
                || normalized.contains("precipitation")
                || normalized.contains("precipitations")
                || LATITUDE_PATTERN.matcher(question).find()
                || LONGITUDE_PATTERN.matcher(question).find();
    }

    private static Integer extractForecastDays(String question) {
        Matcher matcher = DAYS_PATTERN.matcher(question);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        String normalized = normalize(question);
        if (normalized.contains("demain")) {
            return 2;
        }
        if (normalized.contains("aujourd hui") || normalized.contains("aujourdhui")) {
            return 1;
        }

        return null;
    }

    private static double[] extractCoordinates(String question) {
        Matcher latitudeMatcher = LATITUDE_PATTERN.matcher(question);
        Matcher longitudeMatcher = LONGITUDE_PATTERN.matcher(question);
        if (!latitudeMatcher.find() || !longitudeMatcher.find()) {
            return null;
        }

        return new double[]{
                parseDecimal(latitudeMatcher.group(1)),
                parseDecimal(longitudeMatcher.group(1))
        };
    }

    private static double parseDecimal(String value) {
        return Double.parseDouble(value.replace(',', '.'));
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('\'', ' ')
                .replace('’', ' ');
    }
}
