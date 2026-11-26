package mk.diplomska.travel.chat;

import java.util.List;

/**
 * Одговор на чет-ботот.
 *
 * @param sources делови од Wikivoyage врз кои се базира одговорот
 */
public record ChatResponse(String answer, List<Source> sources) {

    /** @param score сличност помеѓу прашањето и текстот (0 до 1) */
    public record Source(String section, String url, double score) {
    }
}
