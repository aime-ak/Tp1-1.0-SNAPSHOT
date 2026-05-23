package ht.aime;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MeteoTool {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final String FORECAST_BASE_URL = "https://api.open-meteo.com/v1/forecast";
    private static final String GEOCODING_BASE_URL = "https://geocoding-api.open-meteo.com/v1/search";
    private static final int DEFAULT_FORECAST_DAYS = 3;
    private static final int GEOCODING_RESULT_LIMIT = 5;
    private static final int MAX_FORECAST_DAYS = 16;
    private static final int UMBRELLA_THRESHOLD_PERCENT = 40;
    private static final int MAX_LOCATION_WORDS = 4;
    private static final Set<String> LOCATION_INTRODUCERS = Set.of("a", "au", "aux", "dans", "vers", "pour", "en");
    private static final Set<String> LOCATION_BOUNDARY_WORDS = Set.of(
            "demain", "apres", "après", "aujourd", "hui", "jour", "jours", "semaine", "semaines",
            "mois", "an", "ans", "weekend", "week", "dois", "doit", "faut", "prendre", "apporter",
            "prevoir", "prévoir", "parapluie", "pluie", "meteo", "météo", "temperature", "température",
            "besoin", "sera", "serait", "risque", "pleuvoir", "pleut", "parts", "pars", "partir",
            "vais", "aller", "arrive", "arriver", "quand", "comment", "quel", "quelle", "quels", "quelles"
    );
    private static final Set<String> LOCATION_CONNECTOR_WORDS = Set.of("de", "du", "des", "la", "le", "les", "l", "en", "sur", "sous");

    @Tool("""
            Retourne les previsions de probabilite maximale de pluie pour un lieu donne par sa latitude et sa longitude.
            A utiliser pour repondre aux questions sur la pluie, la meteo, un parapluie ou une valise.
            La reponse indique le risque jour par jour et un conseil explicite sur le parapluie.
            """)
    public String previsionsPrecipitations(
            @P(name = "latitude", description = "Latitude du lieu a analyser.") double latitude,
            @P(name = "longitude", description = "Longitude du lieu a analyser.") double longitude,
            @P(name = "forecastDays", description = "Nombre de jours de prevision a partir d'aujourd'hui, entre 1 et 16.") int forecastDays
    ) {

        int normalizedDays = Math.max(1, Math.min(forecastDays, MAX_FORECAST_DAYS));
        String url = FORECAST_BASE_URL
                + "?latitude=" + formatDecimal(latitude)
                + "&longitude=" + formatDecimal(longitude)
                + "&daily=precipitation_probability_max"
                + "&forecast_days=" + normalizedDays;

        String json = sendGet(url, "la recuperation des previsions Open-Meteo");
        String dailyObject = extractObject(json, "daily");
        List<String> dates = parseStringArray(extractArray(dailyObject, "time"));
        List<Integer> probabilities = parseIntegerArray(extractArray(dailyObject, "precipitation_probability_max"));

        if (dates.isEmpty() || probabilities.isEmpty() || dates.size() != probabilities.size()) {
            throw new IllegalStateException("La reponse Open-Meteo ne contient pas les donnees de precipitation attendues.");
        }

        int maxProbability = probabilities.stream().mapToInt(Integer::intValue).max().orElse(0);
        boolean umbrellaRecommended = maxProbability >= UMBRELLA_THRESHOLD_PERCENT;

        StringBuilder result = new StringBuilder();
        result.append("Previsions de pluie pour ").append(normalizedDays).append(" jour(s) ");
        result.append("aux coordonnees [").append(formatDecimal(latitude)).append(", ").append(formatDecimal(longitude)).append("] : ");

        for (int i = 0; i < dates.size(); i++) {
            if (i > 0) {
                result.append(" | ");
            }
            result.append(dates.get(i)).append(" : ").append(probabilities.get(i)).append('%');
        }

        result.append(". Seuil parapluie : ").append(UMBRELLA_THRESHOLD_PERCENT).append("%.");
        if (umbrellaRecommended) {
            result.append(" Conseil : prends un parapluie.");
        } else {
            result.append(" Conseil : le parapluie n'est pas indispensable.");
        }

        return result.toString();
    }

    @Tool("""
            Retourne la latitude et la longitude d'une ville sous la forme [latitude, longitude].
            A utiliser quand l'utilisateur donne un nom de ville plutot que des coordonnees.
            Si plusieurs villes existent, la premiere proposition retournee par Open-Meteo est utilisee.
            """)
    public double[] coordonneesVille(
            @P(name = "city", description = "Nom de la ville. Pour lever une ambiguite, on peut ajouter le pays en anglais, par exemple 'Rabat,Morocco'.")
            String city
    ) {
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("Le nom de ville ne doit pas etre vide.");
        }

        for (String query : buildGeocodingQueries(city)) {
            GeocodingCandidate candidate = chercherVille(query);
            if (candidate != null) {
                return new double[]{candidate.latitude(), candidate.longitude()};
            }
        }

        throw new IllegalArgumentException("Aucune ville trouvee pour '" + city.trim() + "'.");
    }

    static String extractCityCandidate(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }

        String cleaned = cleanLocationInput(userMessage);
        if (cleaned.isBlank()) {
            return null;
        }

        List<String> extractedLocations = extractLocationCandidates(cleaned);
        if (!extractedLocations.isEmpty()) {
            return extractedLocations.getFirst();
        }

        if (looksLikeStandaloneLocation(cleaned)) {
            return cleaned;
        }

        return null;
    }

    public static void main(String[] args) {
        MeteoTool tool = new MeteoTool();
        String city = args.length > 0 ? String.join(" ", args) : "Paris";
        double[] coordinates = tool.coordonneesVille(city);

        System.out.println("Ville             : " + city);
        System.out.println("Coordonnees       : [" + formatDecimal(coordinates[0]) + ", " + formatDecimal(coordinates[1]) + "]");
        System.out.println(tool.previsionsPrecipitations(coordinates[0], coordinates[1], DEFAULT_FORECAST_DAYS));
    }

    private static String sendGet(String url, String operationDescription) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Erreur HTTP " + response.statusCode() + " pendant " + operationDescription + " : " + response.body());
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("Erreur d'E/S pendant " + operationDescription + ".", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interruption pendant " + operationDescription + ".", e);
        }
    }

    private static String extractObject(String json, String fieldName) {
        return extractEnclosedValue(json, fieldName, '{', '}');
    }

    private static String extractArray(String json, String fieldName) {
        return extractEnclosedValue(json, fieldName, '[', ']');
    }

    private static String extractEnclosedValue(String json, String fieldName, char opening, char closing) {
        String marker = "\"" + fieldName + "\"";
        int fieldIndex = json.indexOf(marker);
        if (fieldIndex < 0) {
            throw new IllegalStateException("Champ JSON introuvable : " + fieldName);
        }

        int openingIndex = json.indexOf(opening, fieldIndex + marker.length());
        if (openingIndex < 0) {
            throw new IllegalStateException("Valeur JSON introuvable pour le champ : " + fieldName);
        }

        int depth = 0;
        boolean inString = false;

        for (int i = openingIndex; i < json.length(); i++) {
            char current = json.charAt(i);
            if (current == '"' && !isEscaped(json, i)) {
                inString = !inString;
            }
            if (inString) {
                continue;
            }

            if (current == opening) {
                depth++;
            } else if (current == closing) {
                depth--;
                if (depth == 0) {
                    return json.substring(openingIndex + 1, i);
                }
            }
        }

        throw new IllegalStateException("Impossible d'extraire le champ JSON : " + fieldName);
    }

    private static boolean isEscaped(String json, int quoteIndex) {
        int backslashCount = 0;
        for (int i = quoteIndex - 1; i >= 0 && json.charAt(i) == '\\'; i--) {
            backslashCount++;
        }
        return backslashCount % 2 != 0;
    }

    private static List<String> parseStringArray(String rawArrayContent) {
        List<String> values = new ArrayList<>();
        boolean inString = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < rawArrayContent.length(); i++) {
            char c = rawArrayContent.charAt(i);
            if (c == '"' && !isEscaped(rawArrayContent, i)) {
                if (inString) {
                    values.add(current.toString());
                    current.setLength(0);
                }
                inString = !inString;
                continue;
            }

            if (inString) {
                current.append(c);
            }
        }

        return values;
    }

    private static List<Integer> parseIntegerArray(String rawArrayContent) {
        List<Integer> values = new ArrayList<>();
        for (String part : rawArrayContent.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !"null".equalsIgnoreCase(trimmed)) {
                values.add((int) Math.round(Double.parseDouble(trimmed)));
            }
        }
        return values;
    }

    private static GeocodingCandidate chercherVille(String query) {
        String url = GEOCODING_BASE_URL
                + "?name=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&count=" + GEOCODING_RESULT_LIMIT
                + "&language=fr&format=json";

        String json = sendGet(url, "la geolocalisation Open-Meteo");
        if (!json.contains("\"results\"")) {
            return null;
        }

        String resultsArray = extractArray(json, "results");
        List<String> resultObjects = extractObjects(resultsArray);
        if (resultObjects.isEmpty()) {
            return null;
        }

        String normalizedQuery = normalizeForComparison(query);
        GeocodingCandidate bestCandidate = null;
        int bestScore = Integer.MIN_VALUE;

        for (int index = 0; index < resultObjects.size(); index++) {
            String resultObject = resultObjects.get(index);
            GeocodingCandidate candidate = GeocodingCandidate.from(resultObject);
            int score = candidate.scoreAgainst(normalizedQuery) - index;
            if (score > bestScore) {
                bestScore = score;
                bestCandidate = candidate;
            }
        }

        return bestCandidate;
    }

    private static List<String> extractObjects(String rawArrayContent) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        int objectStart = -1;

        for (int i = 0; i < rawArrayContent.length(); i++) {
            char current = rawArrayContent.charAt(i);
            if (current == '"' && !isEscaped(rawArrayContent, i)) {
                inString = !inString;
            }
            if (inString) {
                continue;
            }

            if (current == '{') {
                if (depth == 0) {
                    objectStart = i + 1;
                }
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth < 0) {
                    throw new IllegalStateException("Impossible d'extraire les objets JSON du tableau de resultats.");
                }
                if (depth == 0 && objectStart >= 0) {
                    objects.add(rawArrayContent.substring(objectStart, i));
                    objectStart = -1;
                }
            }
        }

        return objects;
    }

    private static double extractDouble(String json, String fieldName) {
        String marker = "\"" + fieldName + "\"";
        int fieldIndex = json.indexOf(marker);
        if (fieldIndex < 0) {
            throw new IllegalStateException("Champ numerique JSON introuvable : " + fieldName);
        }

        int colonIndex = json.indexOf(':', fieldIndex + marker.length());
        if (colonIndex < 0) {
            throw new IllegalStateException("Separateur ':' introuvable pour le champ : " + fieldName);
        }

        int start = colonIndex + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }

        int end = start;
        while (end < json.length()) {
            char current = json.charAt(end);
            if (!(Character.isDigit(current) || current == '-' || current == '+' || current == '.'
                    || current == 'e' || current == 'E')) {
                break;
            }
            end++;
        }

        if (start == end) {
            throw new IllegalStateException("Valeur numerique introuvable pour le champ : " + fieldName);
        }

        return Double.parseDouble(json.substring(start, end));
    }

    private static String formatDecimal(double value) {
        return String.format(Locale.US, "%.5f", value);
    }

    private static List<String> buildGeocodingQueries(String city) {
        Set<String> queries = new LinkedHashSet<>();
        String cleaned = cleanLocationInput(city);
        addQueryFamily(queries, cleaned);

        for (String extractedLocation : extractLocationCandidates(cleaned)) {
            addQueryFamily(queries, extractedLocation);
        }

        return List.copyOf(queries);
    }

    private static String cleanLocationInput(String value) {
        return value.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "");
    }

    private static List<String> extractLocationCandidates(String userMessage) {
        Set<String> candidates = new LinkedHashSet<>();
        List<String> tokens = tokenizeWords(userMessage);

        for (int i = 0; i < tokens.size(); i++) {
            if (!LOCATION_INTRODUCERS.contains(normalizeToken(tokens.get(i)))) {
                continue;
            }

            List<String> locationTokens = new ArrayList<>();
            for (int j = i + 1; j < tokens.size() && locationTokens.size() < MAX_LOCATION_WORDS; j++) {
                String token = tokens.get(j);
                String normalizedToken = normalizeToken(token);
                if (isLocationBoundary(normalizedToken)) {
                    break;
                }
                locationTokens.add(token);
            }

            addLocationPrefixes(candidates, locationTokens);
        }

        return List.copyOf(candidates);
    }

    private static List<String> tokenizeWords(String value) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (Character.isLetter(character) || character == '\'' || character == '’' || character == '-') {
                current.append(character);
                continue;
            }

            if (!current.isEmpty()) {
                tokens.add(current.toString());
                current.setLength(0);
            }
        }

        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    private static boolean isLocationBoundary(String normalizedToken) {
        return normalizedToken.isBlank()
                || LOCATION_INTRODUCERS.contains(normalizedToken)
                || LOCATION_BOUNDARY_WORDS.contains(normalizedToken);
    }

    private static void addLocationPrefixes(Set<String> queries, List<String> locationTokens) {
        for (int length = locationTokens.size(); length >= 1; length--) {
            String lastToken = normalizeToken(locationTokens.get(length - 1));
            if (LOCATION_CONNECTOR_WORDS.contains(lastToken)) {
                continue;
            }
            addQueryVariant(queries, String.join(" ", locationTokens.subList(0, length)));
        }
    }

    private static boolean looksLikeStandaloneLocation(String value) {
        List<String> tokens = tokenizeWords(value);
        if (tokens.isEmpty() || tokens.size() > MAX_LOCATION_WORDS) {
            return false;
        }

        for (int i = 0; i < tokens.size(); i++) {
            String normalizedToken = normalizeToken(tokens.get(i));
            if (normalizedToken.isBlank()) {
                return false;
            }
            if (i == 0 || i == tokens.size() - 1) {
                if (LOCATION_INTRODUCERS.contains(normalizedToken) || LOCATION_BOUNDARY_WORDS.contains(normalizedToken)) {
                    return false;
                }
                continue;
            }
            if (LOCATION_BOUNDARY_WORDS.contains(normalizedToken)) {
                return false;
            }
        }

        return true;
    }

    private static void addQueryFamily(Set<String> queries, String query) {
        addQueryVariant(queries, query);

        String withoutLeadingPreposition = query.replaceFirst("(?iu)^(?:a|\u00E0|au|aux|dans|vers|pour|en)\\s+", "");
        addQueryVariant(queries, withoutLeadingPreposition);

        String normalized = normalizeAscii(withoutLeadingPreposition);
        addQueryVariant(queries, normalized);
    }

    private static void addQueryVariant(Set<String> queries, String query) {
        if (query != null && !query.isBlank()) {
            queries.add(query.trim());
        }
    }

    private static String normalizeToken(String value) {
        return normalizeAscii(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}]+", "")
                .trim();
    }

    private static String normalizeForComparison(String value) {
        return normalizeAscii(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String normalizeAscii(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }

    private record GeocodingCandidate(double latitude, double longitude, String name, String country, String admin1) {

        static GeocodingCandidate from(String json) {
            return new GeocodingCandidate(
                    extractDouble(json, "latitude"),
                    extractDouble(json, "longitude"),
                    GeminiSupport.extractJsonString(json, "name"),
                    GeminiSupport.extractJsonString(json, "country"),
                    GeminiSupport.extractJsonString(json, "admin1")
            );
        }

        int scoreAgainst(String normalizedQuery) {
            String normalizedName = normalizeForComparison(name == null ? "" : name);
            String normalizedCountry = normalizeForComparison(country == null ? "" : country);
            String normalizedAdmin1 = normalizeForComparison(admin1 == null ? "" : admin1);
            String normalizedCombined = normalizeForComparison(
                    String.join(" ", List.of(
                            name == null ? "" : name,
                            admin1 == null ? "" : admin1,
                            country == null ? "" : country
                    ))
            );

            if (normalizedName.equals(normalizedQuery)) {
                return 1_000;
            }
            if (!normalizedCombined.isEmpty() && normalizedCombined.equals(normalizedQuery)) {
                return 900;
            }
            if (!normalizedName.isEmpty() && (normalizedName.startsWith(normalizedQuery) || normalizedQuery.startsWith(normalizedName))) {
                return 700;
            }
            if (!normalizedCombined.isEmpty() && normalizedCombined.contains(normalizedQuery)) {
                return 500;
            }
            if (normalizedCountry.equals(normalizedQuery) || normalizedAdmin1.equals(normalizedQuery)) {
                return 300;
            }
            return 0;
        }
    }
}
