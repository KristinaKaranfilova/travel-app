package mk.diplomska.travel.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Ја стартува евалуацијата и ги запишува резултатите во папката evaluation/results.
 * Се активира само со профилот "evaluation" (веб серверот не се стартува):
 *
 *   ./mvnw spring-boot:run -Dspring-boot.run.profiles=evaluation
 *
 * Делови (evaluation.parts во application-evaluation.yml):
 *   algorithm     - рути и групирање (без AI, брзо)
 *   rag-retrieval - метрики за пребарувањето (само embeddings, ~1 минута)
 *   rag-answers   - одговори со и без RAG за рачна оценка (60 повици до LLM, ~6 минути)
 */
@Component
@Profile("evaluation")
public class EvaluationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EvaluationRunner.class);

    /** Петте градови од дипломската (id во базата). */
    static final List<Long> CITY_IDS = List.of(1L, 2L, 3L, 4L, 5L);

    private final AlgorithmEvaluation algorithm;
    private final RagEvaluation rag;
    private final ApplicationContext context;
    private final List<String> parts;
    private final Path outputDir;

    public EvaluationRunner(AlgorithmEvaluation algorithm, RagEvaluation rag, ApplicationContext context,
                            @Value("${evaluation.parts}") List<String> parts,
                            @Value("${evaluation.output-dir}") String outputDir) {
        this.algorithm = algorithm;
        this.rag = rag;
        this.context = context;
        this.parts = parts;
        this.outputDir = Path.of(outputDir);
    }

    @Override
    public void run(String... args) throws IOException {
        Files.createDirectories(outputDir);

        if (parts.contains("algorithm")) {
            write("algorithm.md", algorithm.run(CITY_IDS));
        }
        if (parts.contains("rag-retrieval")) {
            write("rag_retrieval.md", rag.evaluateRetrieval());
        }
        if (parts.contains("rag-answers")) {
            write("rag_answers.csv", rag.generateAnswers());
        }

        // Евалуацијата заврши: исклучи ја апликацијата
        System.exit(SpringApplication.exit(context, () -> 0));
    }

    private void write(String fileName, String content) throws IOException {
        Path file = outputDir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        log.info("Wrote {}", file.toAbsolutePath());
    }
}
