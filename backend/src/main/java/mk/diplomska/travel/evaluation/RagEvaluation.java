package mk.diplomska.travel.evaluation;

import mk.diplomska.travel.chat.ChatResponse;
import mk.diplomska.travel.chat.ChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Евалуација на RAG чет-ботот.
 *
 * Множество: 25 туристички прашања (5 по град) со очекуван дел од Wikivoyage
 * (на пр. „Каде да јадам?“ → Eat) и 5 прашања надвор од темата (очекувано: одбиени).
 *
 * 1. Retrieval (автоматски):
 *    - Hit@5: дали барем едно од 5-те пронајдени парчиња е од очекуваниот дел
 *    - MRR: 1 / позиција на првото точно парче (1 = првото е точно, 0.5 = второто...)
 *    - сличност на најдоброто парче, за туристички и за прашања надвор од темата
 *    - влијание на прагот на сличност: колку туристички прашања добиваат одговор
 *      и колку прашања надвор од темата се одбиени, за различни прагови
 *
 * 2. Одговори (за рачна оценка):
 *    за секое прашање се зачувуваат одговорот СО RAG и одговорот БЕЗ RAG
 *    (истиот модел, без контекст) во CSV, со празни колони за оценка.
 */
@Component
public class RagEvaluation {

    static final double[] THRESHOLDS = {0.3, 0.4, 0.5, 0.55, 0.6, 0.7};

    /** Пауза помеѓу повици до LLM-от, поради лимитот на бесплатниот план. */
    static final long PAUSE_BETWEEN_LLM_CALLS_MS = 5_000;

    private final ChatService chatService;
    private final ChatClient plainClient;

    public RagEvaluation(ChatService chatService, ChatClient.Builder builder) {
        this.chatService = chatService;
        this.plainClient = builder
                .defaultSystem("Ти си туристички асистент. Одговори кратко (најмногу 5-6 реченици), "
                        + "на македонски, обичен текст без Markdown.")
                .build();
    }

    record Question(long cityId, String text, Set<String> expectedSections) {
        boolean offTopic() {
            return expectedSections.isEmpty();
        }
    }

    record RetrievalResult(Question question, List<String> sections, double topScore, int firstHitRank) {
    }

    public static List<Question> loadQuestions() {
        try {
            String csv = new ClassPathResource("evaluation/rag_questions.csv")
                    .getContentAsString(StandardCharsets.UTF_8);
            List<Question> questions = new ArrayList<>();
            for (String line : csv.lines().skip(1).toList()) { // прв ред е заглавие
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(";");
                Set<String> expected = parts[2].equals("-") ? Set.of() : Set.of(parts[2].split("\\|"));
                questions.add(new Question(Long.parseLong(parts[0]), parts[1], expected));
            }
            return questions;
        } catch (IOException e) {
            throw new IllegalStateException("Не може да се прочита rag_questions.csv", e);
        }
    }

    /** Retrieval метрики. Враќа содржина за results/rag_retrieval.md */
    public String evaluateRetrieval() {
        List<RetrievalResult> results = new ArrayList<>();
        for (Question q : loadQuestions()) {
            // праг 0 = секогаш 5 резултати, за да ја видиме вистинската сличност
            List<Document> docs = chatService.retrieve(q.cityId(), q.text(), 0.0);
            List<String> sections = docs.stream().map(d -> String.valueOf(d.getMetadata().get("section"))).toList();
            double topScore = docs.isEmpty() || docs.get(0).getScore() == null ? 0 : docs.get(0).getScore();
            int firstHit = 0;
            for (int i = 0; i < sections.size(); i++) {
                if (q.expectedSections().contains(sections.get(i))) {
                    firstHit = i + 1;
                    break;
                }
            }
            results.add(new RetrievalResult(q, sections, topScore, firstHit));
        }

        List<RetrievalResult> tourism = results.stream().filter(r -> !r.question().offTopic()).toList();
        List<RetrievalResult> offTopic = results.stream().filter(r -> r.question().offTopic()).toList();

        double hitAt1 = tourism.stream().filter(r -> r.firstHitRank() == 1).count() / (double) tourism.size();
        double hitAt5 = tourism.stream().filter(r -> r.firstHitRank() > 0).count() / (double) tourism.size();
        double mrr = tourism.stream().mapToDouble(r -> r.firstHitRank() == 0 ? 0 : 1.0 / r.firstHitRank())
                .average().orElse(0);

        StringBuilder md = new StringBuilder();
        md.append("# Евалуација на RAG: пребарување (retrieval)\n\n");
        md.append(String.format(Locale.ROOT,
                "Прашања: %d туристички (5 по град) и %d надвор од темата. Top-K = 5.%n%n",
                tourism.size(), offTopic.size()));

        md.append("## Точност на пребарувањето (туристички прашања)\n\n");
        md.append("| Метрика | Вредност |\n|---|---|\n");
        md.append(String.format(Locale.ROOT, "| Hit@1 (првото парче е од точниот дел) | %.0f%% |%n", hitAt1 * 100));
        md.append(String.format(Locale.ROOT, "| Hit@5 (точниот дел е меѓу првите 5) | %.0f%% |%n", hitAt5 * 100));
        md.append(String.format(Locale.ROOT, "| MRR (Mean Reciprocal Rank) | %.2f |%n%n", mrr));

        md.append("## Сличност на најдоброто парче\n\n");
        md.append("| Вид прашања | Просек | Минимум | Максимум |\n|---|---|---|---|\n");
        md.append(scoreRow("Туристички", tourism));
        md.append(scoreRow("Надвор од темата", offTopic));

        md.append("\n## Избор на прагот на сличност\n\n");
        md.append("Ако најдоброто парче има сличност под прагот, ботот не одговара („немам информација“).\n\n");
        md.append("| Праг | Туристички прашања со одговор | Одбиени прашања надвор од темата |\n|---|---|---|\n");
        for (double t : THRESHOLDS) {
            long answered = tourism.stream().filter(r -> r.topScore() >= t).count();
            long rejected = offTopic.stream().filter(r -> r.topScore() < t).count();
            md.append(String.format(Locale.ROOT, "| %.2f | %d / %d | %d / %d |%n",
                    t, answered, tourism.size(), rejected, offTopic.size()));
        }

        md.append("\n## Детали по прашање\n\n");
        md.append("| Град | Прашање | Очекуван дел | Пронајдени делови (по ред) | Позиција | Сличност |\n");
        md.append("|---|---|---|---|---|---|\n");
        for (RetrievalResult r : results) {
            md.append(String.format(Locale.ROOT, "| %d | %s | %s | %s | %s | %.2f |%n",
                    r.question().cityId(), r.question().text(),
                    r.question().offTopic() ? "надвор од темата" : String.join(" / ", r.question().expectedSections()),
                    String.join(", ", r.sections()),
                    r.question().offTopic() ? "-" : (r.firstHitRank() == 0 ? "нема" : String.valueOf(r.firstHitRank())),
                    r.topScore()));
        }
        return md.toString();
    }

    private static String scoreRow(String label, List<RetrievalResult> results) {
        DoubleSummaryStatistics s = results.stream().mapToDouble(RetrievalResult::topScore).summaryStatistics();
        return String.format(Locale.ROOT, "| %s | %.2f | %.2f | %.2f |%n", label, s.getAverage(), s.getMin(), s.getMax());
    }

    /**
     * Одговори со RAG и без RAG, во CSV (раздвојувач ;) за рачна оценка во Excel.
     * Колоните „оценка“ се празни: 2 = точно, 1 = делумно, 0 = погрешно/измислено.
     */
    public String generateAnswers() {
        StringBuilder csv = new StringBuilder("﻿"); // BOM, за Excel да ја препознае кирилицата
        csv.append("cityId;прашање;одговор СО RAG;извори;оценка СО RAG;одговор БЕЗ RAG;оценка БЕЗ RAG\n");
        for (Question q : loadQuestions()) {
            ChatResponse withRag = chatService.ask(q.cityId(), q.text());
            sleep();
            String withoutRag = plainClient.prompt()
                    .user("Град: " + cityName(q.cityId()) + ". Прашање: " + q.text())
                    .call()
                    .content();
            sleep();
            String sources = String.join(", ", withRag.sources().stream().map(ChatResponse.Source::section).toList());
            csv.append(q.cityId()).append(';')
                    .append(cell(q.text())).append(';')
                    .append(cell(withRag.answer())).append(';')
                    .append(cell(sources)).append(";;")
                    .append(cell(withoutRag)).append(";\n");
        }
        return csv.toString();
    }

    private static String cityName(long cityId) {
        return switch ((int) cityId) {
            case 1 -> "Охрид";
            case 2 -> "Скопје";
            case 3 -> "Рим";
            case 4 -> "Париз";
            case 5 -> "Барселона";
            default -> "непознат";
        };
    }

    /** CSV ќелија во наводници; наводниците во текстот се дуплираат. */
    private static String cell(String text) {
        return "\"" + (text == null ? "" : text.replace("\"", "\"\"")) + "\"";
    }

    private static void sleep() {
        try {
            Thread.sleep(PAUSE_BETWEEN_LLM_CALLS_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
