package mk.diplomska.travel.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Одговарање на прашања со RAG (Retrieval-Augmented Generation).
 *
 * Чекорите се напишани рачно (без готов Advisor), за секој да е видлив:
 *  1. Retrieval: прашањето се претвора во вектор и се бараат 5-те најслични
 *     парчиња текст за тој град во pgvector (cosine сличност).
 *  2. Ако нема доволно слично парче, се враќа „немам информација“,
 *     без да се повика LLM-от (така моделот нема шанса да измислува).
 *  3. Augmentation: пронајдените парчиња се ставаат во prompt-от како контекст.
 *  4. Generation: Gemini одговара САМО врз основа на контекстот.
 *  5. Се враќаат и изворите (линкови до Wikivoyage), за корисникот да провери.
 */
@Service
public class ChatService {

    static final int TOP_K = 5;

    /**
     * Минимална сличност (0 до 1) за парчето да се смета за релевантно.
     * Пониско = повеќе резултати, но и повеќе нерелевантни.
     * 0.55 е избрано според евалуацијата (evaluation/results/rag_retrieval.md):
     * туристичките прашања имаа сличност 0.57-0.80, а прашањата надвор од темата 0.46-0.52.
     */
    static final double SIMILARITY_THRESHOLD = 0.55;

    static final String NO_INFO_ANSWER =
            "Немам проверена информација за ова прашање во туристичките извори за овој град.";

    private static final String SYSTEM_PROMPT = """
            Ти си туристички асистент. Одговараш на прашања за градот за кој патува корисникот.

            Правила:
            - Одговарај САМО врз основа на текстот во делот КОНТЕКСТ.
            - Ако контекстот не го содржи одговорот, кажи дека немаш проверена информација за тоа.
              Не измислувај имиња, цени, адреси или работно време.
            - Одговарај на истиот јазик на кој е поставено прашањето.
            - Одговорот нека биде краток и практичен (најмногу 5-6 реченици или кратка листа).
            - Пиши обичен текст, без Markdown (без ѕвездички и #). За листа користи "• " на почеток на редот.
            """;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public ChatService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
    }

    public ChatResponse ask(Long cityId, String question) {
        // 1. Retrieval
        List<Document> documents = retrieve(cityId, question, SIMILARITY_THRESHOLD);

        // 2. Нема релевантен текст → без повик до LLM
        if (documents.isEmpty()) {
            return new ChatResponse(NO_INFO_ANSWER, List.of());
        }

        // 3. Augmentation: контекст + прашање
        String userMessage = "КОНТЕКСТ:\n" + buildContext(documents) + "\n\nПРАШАЊЕ: " + question;

        // 4. Generation
        String answer = chatClient.prompt()
                .user(userMessage)
                .call()
                .content();

        // 5. Извори
        return new ChatResponse(answer, sources(documents));
    }

    /**
     * Векторско пребарување: TOP_K најслични парчиња за градот, со сличност над прагот.
     * Јавно е за да може посебно да се евалуира (threshold = 0 враќа секогаш TOP_K парчиња).
     */
    public List<Document> retrieve(Long cityId, String question, double threshold) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(TOP_K)
                .similarityThreshold(threshold)
                .filterExpression("cityId == " + cityId) // само текстови за овој град
                .build());
    }

    /** Парчињата нумерирани: [1] ..., [2] ... */
    static String buildContext(List<Document> documents) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            sb.append('[').append(i + 1).append("] ").append(documents.get(i).getText()).append("\n\n");
        }
        return sb.toString();
    }

    /** Еден извор по линк (ако две парчиња се од ист дел, линкот се покажува еднаш). */
    static List<ChatResponse.Source> sources(List<Document> documents) {
        Map<String, ChatResponse.Source> byUrl = new LinkedHashMap<>();
        for (Document d : documents) {
            String url = String.valueOf(d.getMetadata().get("source"));
            String section = String.valueOf(d.getMetadata().get("section"));
            double score = d.getScore() == null ? 0 : d.getScore();
            byUrl.putIfAbsent(url, new ChatResponse.Source(section, url, Math.round(score * 100) / 100.0));
        }
        return List.copyOf(byUrl.values());
    }
}
