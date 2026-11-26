package mk.diplomska.travel.chat;

import mk.diplomska.travel.city.City;
import mk.diplomska.travel.city.CityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Внесување туристички текст во векторската база (првиот дел од RAG).
 *
 *  1. Се презема Wikivoyage страницата за градот.
 *  2. Текстот се дели по делови (Get in, See, Eat, Stay safe...).
 *  3. Долгите делови се делат на помали парчиња (chunks) од околу 500 токени.
 *  4. За секое парче Gemini пресметува embedding (вектор од 768 броја што го опишува значењето).
 *  5. Парчињата и векторите се зачувуваат во pgvector (табела vector_store),
 *     со metadata: cityId, наслов на делот и линк до изворот.
 *
 * Се користи рачно (admin endpoint) и автоматски од {@link GuideService} за градови без водич.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    /** Наслов на главен дел: точно два знака "=" од секоја страна, на пр. "== See ==". */
    private static final Pattern SECTION_HEADING = Pattern.compile("^==\\s*([^=].*?)\\s*==\\s*$", Pattern.MULTILINE);

    /** Мали делови (на пр. празен „Districts“) не носат корисна информација. */
    private static final int MIN_SECTION_CHARS = 100;

    /** Колку парчиња се праќаат на embedding одеднаш. */
    private static final int BATCH_SIZE = 20;
    /** Кога ќе го надминеме лимитот на бесплатниот план (429), чекаме и пробуваме повторно. */
    private static final long WAIT_AFTER_LIMIT_MS = 20_000;
    private static final int MAX_WAITS_PER_BATCH = 4;

    private final WikivoyageClient wikivoyage;
    private final VectorStore vectorStore;
    private final CityService cityService;

    public IngestionService(WikivoyageClient wikivoyage, VectorStore vectorStore, CityService cityService) {
        this.wikivoyage = wikivoyage;
        this.vectorStore = vectorStore;
        this.cityService = cityService;
    }

    /** Враќа колку парчиња се зачувани. Претходните парчиња за градот се бришат. */
    public int ingest(Long cityId, String wikivoyageTitle) {
        return ingest(cityId, wikivoyageTitle, (done, total) -> { });
    }

    /**
     * Како погоре, со известување за напредокот: progress(зачувани, вкупно парчиња).
     * Ако внесувањето не успее на половина, веќе зачуваните парчиња се бришат,
     * за градот да не изгледа како да има (нецелосен) водич.
     */
    public int ingest(Long cityId, String wikivoyageTitle, BiConsumer<Integer, Integer> progress) {
        City city = cityService.getById(cityId);
        WikivoyageClient.Page page = wikivoyage.fetchPage(wikivoyageTitle);

        List<Document> sections = splitIntoSections(page, cityId);

        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(500)
                .build();
        List<Document> chunks = splitter.apply(sections); // metadata се копира во секое парче

        vectorStore.delete("cityId == " + cityId); // повторно внесување не создава дупликати
        progress.accept(0, chunks.size());
        try {
            addInBatches(chunks, progress);        // тука се повикува Gemini за embeddings
        } catch (RuntimeException e) {
            vectorStore.delete("cityId == " + cityId);
            throw e;
        }

        log.info("Ingested {} chunks for city id={} from Wikivoyage page '{}'", chunks.size(), city.getId(), page.title());
        return chunks.size();
    }

    /**
     * Бесплатниот Gemini план дозволува ограничен број токени за embeddings во минута.
     * Порано се чекаше 25 секунди по секоја група (и кога немаше потреба). Сега групите
     * одат веднаш една по друга, а само кога Gemini ќе одбие (лимит), се чека и се пробува пак.
     */
    private void addInBatches(List<Document> chunks, BiConsumer<Integer, Integer> progress) {
        for (int start = 0; start < chunks.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, chunks.size());
            List<Document> batch = chunks.subList(start, end);
            for (int attempt = 0; ; attempt++) {
                try {
                    vectorStore.add(batch);
                    break;
                } catch (RuntimeException e) {
                    if (attempt >= MAX_WAITS_PER_BATCH) {
                        throw e;
                    }
                    log.info("Embedding batch {}-{} refused ({}), waiting {} s", start, end,
                            GeminiErrors.shortReason(e), WAIT_AFTER_LIMIT_MS / 1000);
                    sleep(WAIT_AFTER_LIMIT_MS);
                }
            }
            progress.accept(end, chunks.size());
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Ја дели страницата на делови според насловите "== ... ==". */
    static List<Document> splitIntoSections(WikivoyageClient.Page page, Long cityId) {
        String text = page.extract();
        List<Document> documents = new ArrayList<>();

        Matcher m = SECTION_HEADING.matcher(text);
        String currentTitle = "Overview"; // текстот пред првиот наслов е вовед
        int currentStart = 0;
        while (m.find()) {
            addSection(documents, page.title(), currentTitle, text.substring(currentStart, m.start()), cityId);
            currentTitle = m.group(1);
            currentStart = m.end();
        }
        addSection(documents, page.title(), currentTitle, text.substring(currentStart), cityId);
        return documents;
    }

    private static void addSection(List<Document> documents, String pageTitle, String sectionTitle,
                                   String body, Long cityId) {
        String trimmed = body.strip();
        if (trimmed.length() < MIN_SECTION_CHARS) {
            return;
        }
        String url = WikivoyageClient.pageUrl(pageTitle)
                + (sectionTitle.equals("Overview") ? "" : "#" + sectionTitle.replace(' ', '_'));

        // Насловот на почетокот на текстот помага при пребарувањето (на пр. "Ohrid - Eat")
        String content = pageTitle + " - " + sectionTitle + "\n\n" + trimmed;
        documents.add(new Document(content, Map.of(
                "cityId", cityId,
                "section", sectionTitle,
                "source", url)));
    }
}
