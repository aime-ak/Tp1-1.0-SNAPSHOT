package ht.aime;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;

import java.util.Scanner;

public class Test7 {

    private static final String CHAT_MODEL = "gemini-2.5-flash";
    private static final double TEMPERATURE = 0.3;

    public static void main(String[] args) {

        String geminiKey = System.getenv("GEMINI_KEY");
        if (geminiKey == null || geminiKey.isBlank()) {
            System.out.println("La variable d'environnement GEMINI_KEY n'est pas definie.");
            return;
        }

        try {
            ChatModel model = GoogleAiGeminiChatModel.builder()
                    .apiKey(geminiKey.trim())
                    .modelName(CHAT_MODEL)
                    .temperature(TEMPERATURE)
                    .build();

            MeteoTool meteoTool = new MeteoTool();
            AssistantMeteo assistant = AiServices.builder(AssistantMeteo.class)
                    .chatModel(model)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                    .tools(meteoTool)
                    .build();
            MeteoConversationManager conversationManager = new MeteoConversationManager(assistant, meteoTool);

            System.out.println("Modele chat        : " + CHAT_MODEL);
            System.out.println("Temperature        : " + TEMPERATURE);
            System.out.println("Outil disponible   : MeteoTool (Open-Meteo)");

            if (args.length > 0) {
                String question = String.join(" ", args);
                String reponse = conversationManager.reply(question);
                System.out.println("Question           : " + question);
                System.out.println("Reponse            : " + reponse);
                return;
            }

            System.out.println("Conversation ouverte. Tapez votre question, puis Entrer.");
            System.out.println("Tapez fin pour terminer.");
            conversationAvec(conversationManager);
        } catch (RuntimeException e) {
            if (GeminiSupport.isTransientGeminiException(e)) {
                System.out.println(GeminiSupport.formatOperationFailure("l'execution de Test7", e));
                return;
            }
            throw e;
        }
    }

    private static void conversationAvec(MeteoConversationManager conversationManager) {
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

                String reponse = conversationManager.reply(question);
                System.out.println("Assistant : " + reponse);
                System.out.println("==================================================");
            }
        }
    }
}
