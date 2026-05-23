package ht.aime;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class Test5 {

    private static final String CHAT_MODEL = "gemini-2.5-flash";
    private static final String EMBEDDING_MODEL = "gemini-embedding-001";
    private static final Path INFOS_PATH = Path.of("infos.txt");
    private static final double TEMPERATURE = 0.3;
    private static final Duration EMBEDDING_TIMEOUT = Duration.ofSeconds(10);

    public static void main(String[] args) {

        String geminiKey = System.getenv("GEMINI_KEY");
        if (geminiKey == null || geminiKey.isBlank()) {
            System.out.println("La variable d'environnement GEMINI_KEY n'est pas definie.");
            return;
        }

        if (!Files.isRegularFile(INFOS_PATH)) {
            System.out.println("Le fichier infos.txt est introuvable a la racine du projet.");
            return;
        }

        try {
            Document document = FileSystemDocumentLoader.loadDocument(INFOS_PATH, new TextDocumentParser());

            ChatModel model = GoogleAiGeminiChatModel.builder()
                    .apiKey(geminiKey.trim())
                    .modelName(CHAT_MODEL)
                    .temperature(TEMPERATURE)
                    .build();

            GoogleAiEmbeddingModel embeddingModel = GoogleAiEmbeddingModel.builder()
                    .apiKey(geminiKey.trim())
                    .modelName(EMBEDDING_MODEL)
                    .timeout(EMBEDDING_TIMEOUT)
                    .build();

            EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();
            GeminiSupport.runWithRetries(() -> ingestor.ingest(document), "l'indexation du document RAG");

            EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .maxResults(2)
                    .build();

            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(model)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .contentRetriever(contentRetriever)
                    .build();

            String question = args.length > 0
                    ? String.join(" ", args)
                    : "Pierre appelle son chat. Qu'est-ce qu'il pourrait dire ?";

            List<Content> contenusRecuperes = GeminiSupport.executeWithRetries(
                    () -> contentRetriever.retrieve(Query.from(question)),
                    "la recuperation du contexte RAG"
            );
            String reponse = GeminiSupport.executeWithRetries(
                    () -> assistant.chat(question),
                    "la generation de la reponse"
            );

            System.out.println("Modele chat        : " + CHAT_MODEL);
            System.out.println("Modele embeddings  : " + EMBEDDING_MODEL);
            System.out.println("Temperature        : " + TEMPERATURE);
            System.out.println("Fichier RAG        : " + INFOS_PATH.toAbsolutePath());
            System.out.println("Question           : " + question);
            System.out.println("Contexte RAG       :");
            contenusRecuperes.stream()
                    .map(content -> content.textSegment().text())
                    .forEach(texte -> System.out.println("  - " + texte));
            System.out.println("Reponse            : " + reponse);
        } catch (RuntimeException e) {
            if (GeminiSupport.isTransientGeminiException(e)) {
                System.out.println(GeminiSupport.formatOperationFailure("l'execution de Test5", e));
                return;
            }
            throw e;
        }
    }

    interface Assistant {
        String chat(String userMessage);
    }
}
