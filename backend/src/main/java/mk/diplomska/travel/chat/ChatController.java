package mk.diplomska.travel.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mk.diplomska.travel.common.BackgroundTasks;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
public class ChatController {

    /** Најдолго трае еден разговор (прашање + одговор) преку SSE. */
    private static final long STREAM_TIMEOUT_MS = 150_000;

    private final ChatService chatService;
    private final IngestionService ingestionService;
    private final GuideService guideService;
    private final BackgroundTasks background;
    private final JsonMapper json;

    public ChatController(ChatService chatService, IngestionService ingestionService, GuideService guideService,
                          BackgroundTasks background, JsonMapper json) {
        this.chatService = chatService;
        this.ingestionService = ingestionService;
        this.guideService = guideService;
        this.background = background;
        this.json = json;
    }

    public record ChatRequest(@NotNull Long cityId, @NotBlank @Size(max = 500) String question) {
    }

    /** POST /api/chat  {cityId, question} → {answer, sources[]} (цел одговор одеднаш) */
    @PostMapping("/api/chat")
    public ChatResponse ask(@Valid @RequestBody ChatRequest request) {
        return chatService.ask(request.cityId(), request.question());
    }

    /**
     * POST /api/chat/stream  {cityId, question} → Server-Sent Events, во овој редослед:
     *   sources {sources[]}   - веднаш по пребарувањето (околу 1 секунда)
     *   token   {text}        - делови од одговорот, како што ги пишува моделот
     *   done    {answer, sources[]}
     *   error   {message, retryable}   - наместо done, ако нешто не успее
     */
    @PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        SseListener listener = new SseListener(emitter);
        emitter.onCompletion(listener::cancel);
        emitter.onTimeout(listener::cancel);
        emitter.onError(e -> listener.cancel());
        background.run(() -> chatService.stream(request.cityId(), request.question(), listener));
        return emitter;
    }

    /**
     * GET /api/cities/{cityId}/guide → дали асистентот има водич за градот.
     * Ако нема, внесувањето од Wikivoyage почнува автоматски (во позадина).
     */
    @GetMapping("/api/cities/{cityId}/guide")
    public GuideService.Status guide(@PathVariable Long cityId) {
        return guideService.status(cityId);
    }

    /**
     * Само за развој: внесување на Wikivoyage страница во векторската база.
     * POST /api/admin/ingest/1?title=Ohrid
     */
    @PostMapping("/api/admin/ingest/{cityId}")
    public Map<String, Object> ingest(@PathVariable Long cityId, @RequestParam String title) {
        int chunks = ingestionService.ingest(cityId, title);
        return Map.of("cityId", cityId, "title", title, "chunks", chunks);
    }

    /** Ги праќа настаните од ChatService како SSE (секој податок е JSON). */
    private final class SseListener implements ChatService.StreamListener {

        private final SseEmitter emitter;
        private volatile boolean cancelled;

        SseListener(SseEmitter emitter) {
            this.emitter = emitter;
        }

        void cancel() {
            cancelled = true;
        }

        @Override
        public boolean cancelled() {
            return cancelled;
        }

        @Override
        public void sources(List<ChatResponse.Source> sources) {
            send("sources", Map.of("sources", sources));
        }

        @Override
        public void token(String text) {
            send("token", Map.of("text", text));
        }

        @Override
        public void done(String answer, List<ChatResponse.Source> sources) {
            send("done", Map.of("answer", answer, "sources", sources));
            complete();
        }

        @Override
        public void error(GeminiErrors.Friendly error) {
            send("error", Map.of("message", error.message(), "retryable", error.retryable()));
            complete();
        }

        private void send(String event, Object data) {
            if (cancelled) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name(event).data(json.writeValueAsString(data)));
            } catch (IOException | IllegalStateException e) {
                cancelled = true; // корисникот го затворил прозорецот
            }
        }

        private void complete() {
            if (!cancelled) {
                emitter.complete();
            }
        }
    }
}
