package mk.diplomska.travel.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IngestionServiceTest {

    private static final String FILLER = " Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor.";

    @Test
    void pageIsSplitBySectionsWithMetadata() {
        String text = "Ohrid is a city on Lake Ohrid." + FILLER + "\n\n"
                + "== See ==\nSamuel's Fortress and the Church of St. John at Kaneo." + FILLER + "\n"
                + "=== Churches ===\nMany churches." + FILLER + "\n\n"
                + "== Eat ==\nTry the Ohrid trout." + FILLER + "\n\n"
                + "== Districts ==\nshort\n";
        var page = new WikivoyageClient.Page("Ohrid", text);

        List<Document> docs = IngestionService.splitIntoSections(page, 7L);

        // Overview, See (со поднасловот Churches во себе), Eat; Districts е прекраток
        assertEquals(3, docs.size());
        assertEquals("Overview", docs.get(0).getMetadata().get("section"));
        assertEquals("See", docs.get(1).getMetadata().get("section"));
        assertTrue(docs.get(1).getText().contains("Many churches"));
        assertEquals("https://en.wikivoyage.org/wiki/Ohrid#Eat", docs.get(2).getMetadata().get("source"));
        assertEquals(7L, docs.get(2).getMetadata().get("cityId"));
        assertTrue(docs.get(2).getText().startsWith("Ohrid - Eat"));
    }
}
