package dev.brex.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventTest {

    @Test
    void attributesAreDefensivelyCopied() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("points", "2");
        Event event = Event.of(1.5, "deposit.complete", attributes);

        attributes.put("points", "99");

        assertEquals("2", event.attribute("points"));
        assertThrows(UnsupportedOperationException.class, () -> event.attributes().put("x", "y"));
    }

    @Test
    void readsPoints() {
        assertEquals(2.0, Event.of(0, "score", Map.of("points", "2")).points());
        assertEquals(0.0, Event.of(0, "intake.start").points());
        assertThrows(IllegalStateException.class, () -> Event.of(0, "score", Map.of("points", "two")).points());
    }

    @Test
    void rejectsInvalidNames() {
        assertThrows(IllegalArgumentException.class, () -> Event.of(0, "intake start"));
        assertThrows(IllegalArgumentException.class, () -> Event.of(0, ""));
        assertThrows(IllegalArgumentException.class, () -> MechanismStateChange.of(0, "lift", ""));
    }

    @Test
    void valueEquality() {
        assertEquals(MechanismStateChange.of(1, "lift", "RAISING"), MechanismStateChange.of(1, "lift", "RAISING"));
        assertEquals(LogEntry.of(1, LogLevel.WARNING, "stall"), LogEntry.of(1, LogLevel.WARNING, "stall"));
    }
}
