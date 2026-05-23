package ht.aime;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.CosineSimilarity;

import java.time.Duration;
import java.util.List;

public class Test4 {

    private static final String MODEL = "gemini-embedding-001";
    private static final int OUTPUT_DIMENSIONALITY = 300;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /*
     * Scores a completer apres execution locale avec gemini-embedding-001,
     * TaskType.SEMANTIC_SIMILARITY et une dimension de 300 :
     * - "Le chat dort sur le canape." / "Un chat se repose sur le sofa." -> ...
     * - "J'aime programmer en Java." / "Le developpement Java me plait beaucoup." -> ...
     * - "La terre tourne autour du soleil." / "Les bananes sont jaunes." -> ...
     * - "Je vais a l'ecole en autobus." / "Je me rends en classe en bus." -> ...
     */

    public static void main(String[] args) {

        String geminiKey = System.getenv("GEMINI_KEY");
        if (geminiKey == null || geminiKey.isBlank()) {
            System.out.println("La variable d'environnement GEMINI_KEY n'est pas definie.");
            return;
        }

        try {
            GoogleAiEmbeddingModel embeddingModel = GoogleAiEmbeddingModel.builder()
                    .apiKey(geminiKey.trim())
                    .modelName(MODEL)
                    .taskType(GoogleAiEmbeddingModel.TaskType.SEMANTIC_SIMILARITY)
                    .outputDimensionality(OUTPUT_DIMENSIONALITY)
                    .timeout(TIMEOUT)
                    .build();

            List<PhrasePair> phrasePairs = List.of(
                    new PhrasePair("Le chat dort sur le canape.", "Un chat se repose sur le sofa."),
                    new PhrasePair("J'aime programmer en Java.", "Le developpement Java me plait beaucoup."),
                    new PhrasePair("La terre tourne autour du soleil.", "Les bananes sont jaunes."),
                    new PhrasePair("Je vais a l'ecole en autobus.", "Je me rends en classe en bus.")
            );

            System.out.println("Modele                : " + MODEL);
            System.out.println("Type de tache         : SEMANTIC_SIMILARITY");
            System.out.println("Dimension embeddings  : " + OUTPUT_DIMENSIONALITY);
            System.out.println("Timeout               : " + TIMEOUT);
            System.out.println();

            int index = 1;
            for (PhrasePair phrasePair : phrasePairs) {
                Response<Embedding> firstEmbedding = GeminiSupport.executeWithRetries(
                        () -> embeddingModel.embed(phrasePair.first()),
                        "la creation du premier embedding"
                );
                Response<Embedding> secondEmbedding = GeminiSupport.executeWithRetries(
                        () -> embeddingModel.embed(phrasePair.second()),
                        "la creation du second embedding"
                );
                double similarity = CosineSimilarity.between(firstEmbedding.content(), secondEmbedding.content());

                System.out.println("Couple " + index);
                System.out.println("Phrase 1             : " + phrasePair.first());
                System.out.println("Phrase 2             : " + phrasePair.second());
                System.out.println("Similarite cosinus   : %.4f".formatted(similarity));
                System.out.println();
                index++;
            }
        } catch (RuntimeException e) {
            if (GeminiSupport.isTransientGeminiException(e)) {
                System.out.println(GeminiSupport.formatOperationFailure("l'execution de Test4", e));
                return;
            }
            throw e;
        }
    }

    private record PhrasePair(String first, String second) {
    }
}
