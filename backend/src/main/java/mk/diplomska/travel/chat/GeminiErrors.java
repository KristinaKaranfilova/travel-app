package mk.diplomska.travel.chat;

import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/**
 * Грешките од Gemini (бесплатниот план) претворени во јасна порака за корисникот.
 *
 * Најчести се две, и двете привремени:
 *  - 429 / RESOURCE_EXHAUSTED: потрошен е лимитот на барања/токени за оваа минута
 *  - 503 / UNAVAILABLE: моделот е преоптоварен (кај Google, не кај нас)
 */
public final class GeminiErrors {

    /** Порака за корисникот и дали има смисла да се проба повторно. */
    public record Friendly(String message, boolean retryable) {
    }

    private GeminiErrors() {
    }

    public static Friendly describe(Throwable e) {
        String text = fullText(e);
        if (text.contains("429") || text.contains("resource_exhausted") || text.contains("quota")
                || text.contains("rate limit")) {
            return new Friendly("Бесплатниот лимит на Gemini за оваа минута е потрошен. "
                    + "Обиди се повторно за околу една минута.", true);
        }
        if (text.contains("503") || text.contains("unavailable") || text.contains("overloaded")) {
            return new Friendly("Gemini моментално е преоптоварен (кај Google). Обиди се повторно за неколку секунди.", true);
        }
        if (isTimeout(e) || text.contains("timed out") || text.contains("timeout")) {
            return new Friendly("Gemini не одговори навреме. Обиди се повторно.", true);
        }
        if (text.contains("api key") || text.contains("api_key") || text.contains("permission_denied")
                || text.contains("401") || text.contains("403")) {
            return new Friendly("Асистентот не е правилно поставен (Gemini API клучот не е прифатен).", false);
        }
        if (text.contains("404") && text.contains("model")) {
            return new Friendly("Асистентот не е правилно поставен (Gemini моделот не е пронајден).", false);
        }
        return new Friendly("Асистентот моментално не може да одговори. Обиди се повторно.", true);
    }

    /**
     * Дали има смисла веднаш да се проба повторно: привремена грешка што стигнала брзо.
     * По timeout не (корисникот веќе чекал), ниту кај трајни грешки (погрешен клуч/модел).
     */
    public static boolean worthRetrying(Throwable e) {
        Friendly friendly = describe(e);
        return friendly.retryable() && !isTimeout(e) && !fullText(e).contains("404");
    }

    /** Кратко објаснување за логот (без цел stack trace). */
    public static String shortReason(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        return message.length() > 160 ? message.substring(0, 160) + "…" : message;
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof TimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static String fullText(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            sb.append(t.getClass().getSimpleName()).append(' ').append(t.getMessage()).append(' ');
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
