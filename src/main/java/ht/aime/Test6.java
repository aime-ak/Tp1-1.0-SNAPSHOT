package ht.aime;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.stream.Stream;

public class Test6 {

    private static final String CHAT_MODEL = "gemini-2.5-flash";
    private static final String EMBEDDING_MODEL = "gemini-embedding-001";
    private static final double TEMPERATURE = 0.3;
    private static final Duration EMBEDDING_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RESULTS = 3;
    private static final int MAX_SEGMENT_SIZE = 1_000;
    private static final int MAX_OVERLAP_SIZE = 100;

    public static void main(String[] args) {

        String geminiKey = System.getenv("GEMINI_KEY");
        if (geminiKey == null || geminiKey.isBlank()) {
            System.out.println("La variable d'environnement GEMINI_KEY n'est pas definie.");
            return;
        }

        List<Path> pdfFiles = listPdfFilesAtProjectRoot();
        if (pdfFiles.isEmpty()) {
            System.out.println("Aucun fichier PDF n'a ete trouve a la racine du projet.");
            System.out.println("Copiez le support de cours PDF a la racine du projet puis relancez Test6.");
            return;
        }

        Path pdfPath = pdfFiles.getFirst();

        try {
            if (pdfFiles.size() > 1) {
                System.out.println("Plusieurs PDF detectes a la racine, utilisation de : " + pdfPath.getFileName());
            }

            Document document = FileSystemDocumentLoader.loadDocument(
                    pdfPath,
                    new ApachePdfBoxDocumentParser()
            );

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
                    .documentSplitter(DocumentSplitters.recursive(MAX_SEGMENT_SIZE, MAX_OVERLAP_SIZE))
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();
            GeminiSupport.runWithRetries(() -> ingestor.ingest(document), "l'indexation du document PDF");

            EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .maxResults(MAX_RESULTS)
                    .build();

            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(model)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                    .contentRetriever(contentRetriever)
                    .build();

            System.out.println("Modele chat        : " + CHAT_MODEL);
            System.out.println("Modele embeddings  : " + EMBEDDING_MODEL);
            System.out.println("Temperature        : " + TEMPERATURE);
            System.out.println("Fichier RAG        : " + pdfPath.toAbsolutePath());
            System.out.println("Segments RAG       : max " + MAX_SEGMENT_SIZE + " caracteres, overlap " + MAX_OVERLAP_SIZE);
            System.out.println("Conversation ouverte. Tapez votre question, puis Entrer.");
            System.out.println("Tapez fin pour terminer.");
            conversationAvec(assistant);
        } catch (RuntimeException e) {
            if (GeminiSupport.isTransientGeminiException(e)) {
                System.out.println(GeminiSupport.formatOperationFailure("l'execution de Test6", e));
                return;
            }
            throw e;
        }
    }

    private static void conversationAvec(Assistant assistant) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("==================================================");
                System.out.println("Posez votre question : ");
                if (!scanner.hasNextLine()) {
                    break;
                }

                String question = scanner.nextLine();
                if (question.isBlank()) {
                    continue;
                }

                System.out.println("==================================================");
                if ("fin".equalsIgnoreCase(question)) {
                    break;
                }

                String reponse = GeminiSupport.executeWithRetries(
                        () -> assistant.chat(question),
                        "la generation de la reponse"
                );
                System.out.println("Assistant : " + reponse);
                System.out.println("==================================================");
            }
        }
    }

    private static List<Path> listPdfFilesAtProjectRoot() {
        try (Stream<Path> files = Files.list(Path.of("."))) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de lister les fichiers PDF a la racine du projet.", e);
        }
    }

    interface Assistant {
        String chat(String userMessage);
    }
}
