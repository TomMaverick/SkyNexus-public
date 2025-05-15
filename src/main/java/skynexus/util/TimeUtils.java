package skynexus.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.model.Airport;

import java.time.*;
import java.time.format.DateTimeFormatter;

/**
 * Utility-Klasse für allgemeine Datums-, Zeit- und ortsbezogene Zeitberechnungen.
 */
public final class TimeUtils {
    private static final Logger logger = LoggerFactory.getLogger(TimeUtils.class);

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * Privater Konstruktor verhindert Instanziierung.
     */
    private TimeUtils() {
        // Utility-Klasse
    }

    /**
     * Formatiert eine Ladezeit intelligent:
     * - Unter 1000 ms: "XXX ms"
     * - Ab 1000 ms: "X.X Sekunden"
     *
     * @param milliseconds Ladezeit in Millisekunden
     * @return Formatierter String
     */
    public static String formatLoadingTime(long milliseconds) {
        if (milliseconds < 1000) {
            return String.format("%d ms", milliseconds);
        } else {
            return String.format(java.util.Locale.US, "%.1f Sekunden", milliseconds / 1000.0);
        }
    }


    /**
     * Formatiert eine Zeitdauer in Minuten als "HH:MM" String.
     *
     * @param minutes Die zu formatierende Zeitdauer in Minuten
     * @return Die formatierte Zeitdauer im Format "HH:MM"
     */
    public static String formatMinutesAsHHMM(int minutes) {
        if (minutes < 0) {
            logger.warn("Negative Minutenanzahl für Formatierung: {}", minutes);
            minutes = Math.abs(minutes); // Umwandlung zu positivem Wert für sinnvolle Ausgabe
        }
        int hours = minutes / 60;
        int mins = minutes % 60;
        return String.format("%02d:%02d", hours, mins);
    }

    /**
     * Formatiert ein LocalDateTime mit dem Standarddatumsformat (dd.MM.yyyy HH:mm).
     *
     * @param dateTime Das zu formatierende Datum/Zeit-Objekt
     * @param defaultValue Rückgabewert, wenn dateTime null ist
     * @return Der formatierte String oder defaultValue bei null
     */
    public static String formatStandardDateTime(LocalDateTime dateTime, String defaultValue) {
        return (dateTime != null) ? dateTime.format(DATE_TIME_FORMATTER) : defaultValue;
    }


    // --- Beibehaltene Flughafen-Zeit-Methoden ---


    /**
     * Ermittelt umfassende Zeitinformationen für einen Flughafen (Lokalzeit, Zeitzonen-ID).
     *
     * @param airport Der Flughafen.
     * @return AirportTimeInfo mit Lokalzeit und Zeitzonenangabe.
     * @throws IllegalArgumentException wenn Flughafen null ist.
     * @throws DateTimeException wenn Zeitzone nicht ermittelt werden kann.
     */
    public static AirportTimeInfo getAirportTimeInfo(Airport airport) {
        ValidationUtils.validateNotNull(airport, "Flughafen für Zeitzoneninfo");

        try {
            ZoneId zoneId = determineTimeZone(airport.getLongitude(), airport.getLatitude());
            ZonedDateTime zonedDateTime = ZonedDateTime.now(zoneId);
            LocalDateTime localTime = zonedDateTime.toLocalDateTime();

            // Zeitzonen-Offset formatieren
            ZoneOffset currentOffset = zonedDateTime.getOffset();
            String timezoneString = formatZoneOffset(currentOffset);


            logger.debug("Berechnete Lokalzeit für Flughafen {}: {} ({})",
                    airport.getIcaoCode(), localTime, timezoneString);

            return new AirportTimeInfo(localTime, timezoneString, zoneId);

        } catch (DateTimeException e) {
            logger.warn("Fehler bei Zeitzonenermittlung für Flughafen {}: {}", airport.getIcaoCode(), e.getMessage());
            // Fallback zur Systemzeit
            ZoneId systemZone = ZoneId.systemDefault();
            return new AirportTimeInfo(
                    LocalDateTime.now(),
                    formatZoneOffset(ZonedDateTime.now(systemZone).getOffset()), // Offset der Systemzone formatieren
                    systemZone
            );
        } catch (Exception e) {
            // Catch other potential exceptions like from ValidationUtils inside determineTimeZone if added
            logger.error("Unerwarteter Fehler bei getAirportTimeInfo für {}: {}", airport.getIcaoCode(), e.getMessage(), e);
            // Fallback zur Systemzeit
            ZoneId systemZone = ZoneId.systemDefault();
            return new AirportTimeInfo(
                    LocalDateTime.now(),
                    formatZoneOffset(ZonedDateTime.now(systemZone).getOffset()),
                    systemZone
            );
        }
    }

    /**
     * Formatiert einen ZoneOffset als String (z.B. "UTC+02:00").
     * @param offset Der zu formatierende ZoneOffset.
     * @return Formatierter String.
     */
    private static String formatZoneOffset(ZoneOffset offset) {
        if (offset == null) return "UTC";
        return "UTC" + offset.getId().replace("Z", "+00:00"); // Stellt sicher, dass Z als +00:00 dargestellt wird
    }


    /**
     * Bestimmt die passende Zeitzone basierend auf geografischen Koordinaten (vereinfacht).
     * Für eine genaue Lösung wäre eine externe Bibliothek oder Datenbank nötig.
     *
     * @param longitude Längengrad.
     * @param latitude  Breitengrad.
     * @return ZoneId der geschätzten Zeitzone.
     */
    private static ZoneId determineTimeZone(double longitude, double latitude) {
        // Basic validation
        try {
            ValidationUtils.validateLongitude(longitude);
            ValidationUtils.validateLatitude(latitude);
        } catch (IllegalArgumentException e) {
            logger.warn("Ungültige Koordinaten für Zeitzonenbestimmung: Long={}, Lat={}. Fehler: {}", longitude, latitude, e.getMessage());
            return ZoneId.systemDefault(); // Fallback
        }


        // --- Vereinfachte Zuweisung (Beispiele) ---
        // Dies ist nur eine grobe Schätzung und deckt nicht alle Fälle/Grenzen korrekt ab!
        // Eine Bibliothek wie 'java-time-tzdb' oder ein Geo-Lookup-Service wäre präziser.

        // Mitteleuropa
        if (longitude >= 6 && longitude <= 15 && latitude >= 45 && latitude <= 55) return ZoneId.of("Europe/Berlin");
        // Westeuropa
        if (longitude >= -10 && longitude < 6 && latitude >= 35 && latitude <= 60) return ZoneId.of("Europe/London");
        // Osteuropa
        if (longitude > 15 && longitude <= 30 && latitude >= 45 && latitude <= 60) return ZoneId.of("Europe/Kiev");
        // Nordamerika (Ostküste)
        if (longitude >= -80 && longitude <= -65 && latitude >= 25 && latitude <= 50) return ZoneId.of("America/New_York");
        // Nordamerika (Westküste)
        if (longitude >= -125 && longitude <= -110 && latitude >= 30 && latitude <= 50) return ZoneId.of("America/Los_Angeles");
        // Asien (China/Japan)
        if (longitude >= 100 && longitude <= 145 && latitude >= 20 && latitude <= 45) return ZoneId.of("Asia/Shanghai");
        // Australien (Ostküste)
        if (longitude >= 145 && longitude <= 155 && latitude >= -45 && latitude <= -10) return ZoneId.of("Australia/Sydney");


        // Fallback: Generiere Zone basierend auf Längengrad-Offset
        // Beachte: Dies berücksichtigt keine Sommerzeit oder politische Grenzen!
        try {
            int offsetHours = (int) Math.round(longitude / 15.0);
            // Stelle sicher, dass der Offset gültig ist (-18 bis +18)
            offsetHours = Math.max(-18, Math.min(18, offsetHours));
            return ZoneId.ofOffset("UTC", ZoneOffset.ofHours(offsetHours));
        } catch (DateTimeException e) {
            logger.error("Konnte keine Offset-basierte Zone für Longitude {} erstellen: {}", longitude, e.getMessage());
            return ZoneId.systemDefault(); // Letzter Fallback
        }
    }

    /**
     * Innere Klasse für Zeitinformationen eines Flughafens.
     */
    public static class AirportTimeInfo {
        private final LocalDateTime localTime;
        private final String timezoneString;
        private final ZoneId zoneId;

        public AirportTimeInfo(LocalDateTime localTime, String timezoneString, ZoneId zoneId) {
            // Null checks added for robustness
            this.localTime = java.util.Objects.requireNonNull(localTime, "localTime cannot be null");
            this.timezoneString = java.util.Objects.requireNonNull(timezoneString, "timezoneString cannot be null");
            this.zoneId = java.util.Objects.requireNonNull(zoneId, "zoneId cannot be null");
        }

        public LocalDateTime getLocalTime() {
            return localTime;
        }

        public String getTimezoneString() {
            return timezoneString;
        }

        @Override
        public String toString() {
            return "AirportTimeInfo{" +
                    "localTime=" + localTime +
                    ", timezoneString='" + timezoneString + '\'' +
                    ", zoneId=" + zoneId +
                    '}';
        }
    }
}
