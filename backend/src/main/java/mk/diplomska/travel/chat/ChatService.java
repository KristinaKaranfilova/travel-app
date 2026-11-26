package mk.diplomska.travel.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
 *
 * Сигурност (бесплатниот Gemini план знае да биде преоптоварен или да го надмине лимитот):
 *  - кратки повторни обиди (application.yml: spring.ai.retry), па јасна порака наместо „висење“
 *  - {@link #stream}: изворите и одговорот стигнуваат постепено (зборови), наместо по 10-30 секунди
 *  - рок од TOKEN_TIMEOUT за секој дел од одговорот (наместо бескрајно чекање)
 *  - брза привремена грешка → уште еден обид (со app.chat.fallback-model ако е зададен)
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    static final int TOP_K = 5;

    /**
     * Минимална сличност (0 до 1) за парчето да се смета за релевантно.
     * Пониско = повеќе резултати, но и повеќе нерелевантни.
     * 0.55 е избрано според евалуацијата (evaluation/results/rag_retrieval.md):
     * туристичките прашања имаа сличност 0.57-0.80, а прашањата надвор од темата 0.46-0.52.
     */
    static final double SIMILARITY_THRESHOLD = 0.55;

    /** Најдолго чекање на следниот дел од одговорот (вклучително и првиот). */
    static final Duration TOKEN_TIMEOUT = Duration.ofSeconds(20);

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

    /** Што се случува со одговорот додека се стримува (го користи ChatController за SSE). */
    public interface StreamListener {
        /** Изворите се познати веднаш по пребарувањето (пред одговорот). */
        void sources(List<ChatResponse.Source> sources);

        /** Нов дел од одговорот. */
        void token(String text);

        void done(String answer, List<ChatResponse.Source> sources);

        void error(GeminiErrors.Friendly error);

        /** true ако корисникот го затворил разговорот (нема смисла да продолжиме). */
        boolean cancelled();
    }

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final String fallbackModel;

    public ChatService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder,
                       @Value("${app.chat.fallback-model:}") String fallbackModel) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
        this.fallbackModel = fallbackModel == null || fallbackModel.isBlank() ? null : fallbackModel;
    }

    /** Цел одговор одеднаш (го користат евалуацијата и /api/chat). */
    public ChatResponse ask(Long cityId, String question) {
        try {
            // 1. Retrieval
            List<Document> documents = retrieve(cityId, question, SIMILARITY_THRESHOLD);

            // 2. Нема релевантен текст → без повик до LLM
            if (documents.isEmpty()) {
                return new ChatResponse(NO_INFO_ANSWER, List.of());
            }

            // 3. Augmentation: контекст + прашање
            String userMessage = userMessage(documents, question);

            // 4. Generation
            String answer = chatClient.prompt()
                    .user(userMessage)
                    .call()
                    .content();

            // 5. Извори
            return new ChatResponse(answer, sources(documents));
        } catch (RuntimeException e) {
            GeminiErrors.Friendly friendly = GeminiErrors.describe(e);
            log.warn("Chat failed for city {}: {}", cityId, GeminiErrors.shortReason(e));
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, friendly.message(), e);
        }
    }

    /**
     * Истите чекори како {@link #ask}, но одговорот се праќа дел по дел (streaming).
     * Се извршува синхроно во повиканата нишка (ChatController ја стартува во позадина).
     */
    public void stream(Long cityId, String question, StreamListener listener) {
        long started = System.currentTimeMillis();
        List<Document> documents;
        try {
            documents = retrieve(cityId, question, SIMILARITY_THRESHOLD); // 1.
        } catch (RuntimeException e) {
            log.warn("Retrieval failed for city {}: {}", cityId, GeminiErrors.shortReason(e));
            listener.error(GeminiErrors.describe(e));
            return;
        }
        long retrieved = System.currentTimeMillis();

        if (documents.isEmpty()) { // 2.
            listener.done(NO_INFO_ANSWER, List.of());
            return;
        }
        List<ChatResponse.Source> sources = sources(documents); // 5. (уште пред одговорот)
        listener.sources(sources);

        String userMessage = userMessage(documents, question); // 3.
        StringBuilder answer = new StringBuilder();
        RuntimeException failure = generate(userMessage, null, answer, listener); // 4.
        // Еден повторен обид, само за брзи привремени грешки (преоптоварен/лимит), пред да стигне текст.
        // По timeout не: корисникот веќе чекал TOKEN_TIMEOUT. Со резервен модел ако е зададен, инаку истиот.
        if (failure != null && answer.isEmpty() && !listener.cancelled() && GeminiErrors.worthRetrying(failure)) {
            log.info("Generation failed ({}), retrying once with {}", GeminiErrors.shortReason(failure),
                    fallbackModel == null ? "the same model" : fallbackModel);
            pause(1000);
            failure = generate(userMessage, fallbackModel, answer, listener);
        }
        if (listener.cancelled()) {
            return;
        }
        if (failure != null) {
            log.warn("Generation failed for city {}: {}", cityId, GeminiErrors.shortReason(failure));
            listener.error(GeminiErrors.describe(failure));
            return;
        }
        log.info("Chat city={} retrieval={} ms, total={} ms", cityId, retrieved - started, System.currentTimeMillis() - started);
        listener.done(answer.toString(), sources);
    }

    /** Го стримува одговорот; враќа null ако успеал, или грешката. model == null → стандардниот модел. */
    private RuntimeException generate(String userMessage, String model, StringBuilder answer, StreamListener listener) {
        ChatClient.ChatClientRequestSpec request = chatClient.prompt().user(userMessage);
        if (model != null) {
            // Резервниот модел „размислува“ пред да одговори (стандардно), па првиот збор доцни
            // и над 20 секунди. За краток одговор од даден контекст доволно е LOW (MINIMAL не е поддржан).
            request = request.options(GoogleGenAiChatOptions.builder()
                    .model(model)
                    .temperature(0.2)
                    .thinkingLevel(GoogleGenAiThinkingLevel.LOW));
        }
        // subscribeOn: Spring AI го прави HTTP повикот кон Gemini синхроно, при претплатата.
        // Без посебна нишка, „заглавена“ врска (Gemini SDK нема свој рок) ја блокира и оваа нишка,
        // па timeout никогаш не би се активирал. Вака чекаме најмногу TOKEN_TIMEOUT.
        Flux<String> parts = request.stream().content()
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(TOKEN_TIMEOUT);
        AtomicBoolean stop = new AtomicBoolean();
        try {
            parts.takeWhile(part -> !stop.get())
                    .doOnNext(part -> {
                        if (listener.cancelled()) {
                            stop.set(true);
                            return;
                        }
                        answer.append(part);
                        listener.token(part);
                    })
                    .blockLast(Duration.ofSeconds(120));
            return null;
        } catch (RuntimeException e) {
            return e;
        }
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

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static String userMessage(List<Document> documents, String question) {
        return "КОНТЕКСТ:\n" + buildContext(documents) + "\n\nПРАШАЊЕ: " + question;
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
