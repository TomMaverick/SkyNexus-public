package skynexus.model;

import skynexus.util.ValidationUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Modellklasse für einen Log-Eintrag im SkyNexus-System.
 */
public record LogEntry(LocalDateTime timestamp, String thread, String level, String logger, String message) {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Erstellt einen neuen LogEntry mit den angegebenen Eigenschaften.
     *
     * @throws IllegalArgumentException wenn einer der Parameter ungültig ist
     */
    public LogEntry {
        ValidationUtils.validateNotNull(timestamp, "Zeitstempel");
        ValidationUtils.validateNotEmpty(level, "Log-Level");
        ValidationUtils.validateNotEmpty(logger, "Logger-Name");
        ValidationUtils.validateNotNull(message, "Log-Nachricht");
    }

    /**
     * Gibt den formatierten Zeitstempel des Log-Eintrags zurück.
     */
    public String getFormattedTimestamp() {
        return this.timestamp.format(FORMATTER);
    }

    /**
     * Gibt eine formatierte String-Repräsentation des Log-Eintrags zurück.
     */
    @Override
    public String toString() {
        return String.format("[%s] %s %s - %s",
                getFormattedTimestamp(), this.level, this.logger, this.message);
    }
}
