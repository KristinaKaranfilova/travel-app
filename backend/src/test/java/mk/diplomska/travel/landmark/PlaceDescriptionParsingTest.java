package mk.diplomska.travel.landmark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlaceDescriptionParsingTest {

    @Test
    void wktPointIsConvertedToLatLon() {
        assertArrayEquals(new double[]{41.11, 20.8}, WikidataClient.parsePoint("Point(20.8 41.11)"), 1e-9);
        assertNull(WikidataClient.parsePoint("invalid"));
        assertNull(WikidataClient.parsePoint(null));
    }

    @Test
    void commonsFileTitleIsDecoded() {
        assertEquals("File:Св Софија+1.JPG", WikidataClient.fileTitle(
                "https://commons.wikimedia.org/wiki/Special:FilePath/%D0%A1%D0%B2%20%D0%A1%D0%BE%D1%84%D0%B8%D1%98%D0%B0+1.JPG?width=640"));
        assertNull(WikidataClient.fileTitle("https://upload.wikimedia.org/direct.jpg"));
    }

    @Test
    void shortTextStaysUnchanged() {
        assertEquals("Кратък опис.", PlaceDetailsClient.shorten("  Кратък   опис. "));
    }

    @Test
    void longTextIsCutAtSentenceEnd() {
        String first = "а".repeat(200) + ".";
        String result = PlaceDetailsClient.shorten(first + " " + "б".repeat(400));
        assertEquals(first, result);
    }
}
