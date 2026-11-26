package mk.diplomska.travel.chat;

import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class GeminiErrorsTest {

    @Test
    void rateLimitIsRetryableWithQuotaMessage() {
        var e = new RuntimeException("wrapped", new RuntimeException("429 RESOURCE_EXHAUSTED: quota exceeded"));
        GeminiErrors.Friendly f = GeminiErrors.describe(e);
        assertTrue(f.retryable());
        assertTrue(f.message().contains("лимит"));
    }

    @Test
    void overloadedModelIsRetryable() {
        GeminiErrors.Friendly f = GeminiErrors.describe(new RuntimeException("503 UNAVAILABLE: The model is overloaded"));
        assertTrue(f.retryable());
        assertTrue(f.message().contains("преоптоварен"));
    }

    @Test
    void timeoutIsDetectedFromCause() {
        GeminiErrors.Friendly f = GeminiErrors.describe(new RuntimeException("io", new SocketTimeoutException("Read")));
        assertTrue(f.retryable());
        assertTrue(f.message().contains("навреме"));
    }

    @Test
    void invalidApiKeyIsNotRetryable() {
        GeminiErrors.Friendly f = GeminiErrors.describe(new RuntimeException("400 INVALID_ARGUMENT: API key not valid"));
        assertFalse(f.retryable());
    }

    @Test
    void retriesOnlyFastTemporaryErrors() {
        assertTrue(GeminiErrors.worthRetrying(new RuntimeException("503 UNAVAILABLE: overloaded")));
        assertFalse(GeminiErrors.worthRetrying(new RuntimeException("io", new SocketTimeoutException("Read"))));
        assertFalse(GeminiErrors.worthRetrying(new RuntimeException("404 models/x is not found")));
        assertFalse(GeminiErrors.worthRetrying(new RuntimeException("API key not valid")));
    }

    @Test
    void shortReasonUsesRootCause() {
        var e = new RuntimeException("outer", new IllegalStateException("root problem"));
        assertEquals("root problem", GeminiErrors.shortReason(e));
    }
}
