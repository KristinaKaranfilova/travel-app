package mk.diplomska.travel.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class ChatController {

    private final ChatService chatService;
    private final IngestionService ingestionService;

    public ChatController(ChatService chatService, IngestionService ingestionService) {
        this.chatService = chatService;
        this.ingestionService = ingestionService;
    }

    public record ChatRequest(@NotNull Long cityId, @NotBlank @Size(max = 500) String question) {
    }

    /** POST /api/chat  {cityId, question} → {answer, sources[]} */
    @PostMapping("/api/chat")
    public ChatResponse ask(@Valid @RequestBody ChatRequest request) {
        return chatService.ask(request.cityId(), request.question());
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
}
