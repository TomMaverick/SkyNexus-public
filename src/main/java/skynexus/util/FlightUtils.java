package skynexus.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.model.Airport;

import java.time.DateTimeException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Utility-Klasse für flugspezifische Berechnungen wie Distanz, Flugzeit, Preise und Status.
 */
public final class FlightUtils { // Name geändert zu FlightUtils

    private static final Logger logger = LoggerFactory.getLogger(FlightUtils.class); // Logger angepasst

    // Konstanten, die (noch) nicht konfigurierbar sind
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final int TAKEOFF_LANDING_TIME = 30; // Zeit für Start/Landung in Minuten

    /**
     * Privater Konstruktor verhindert Instanziierung.
     */
    private FlightUtils() {
        // Utility-Klasse
    }


    /**
     * Berechnet die Distanz zwischen zwei Flughäfen mittels Haversine-Formel.
     *
     * @param departure Abflughafen.
     * @param arrival   Zielflughafen.
     * @return Distanz in Kilometern (gerundet, mind. 1.0), oder 1.0 bei ungültigen Eingaben.
     */
    public static double calculateDistance(Airport departure, Airport arrival) {
        if (departure == null || arrival == null) {
            logger.warn("Distanzberechnung nicht möglich: Flughafen ist null");
            return 1.0;
        }
        try {
            // Delegiert an die Methode mit Koordinaten
            return calculateDistance(departure.getLatitude(), departure.getLongitude(),
                    arrival.getLatitude(), arrival.getLongitude());
        } catch (Exception e) {
            // Fängt unerwartete Fehler ab, die nicht von der unteren Methode behandelt wurden
            logger.error("Unerwarteter Fehler bei der Distanzberechnung zwischen Flughäfen {} und {}: {}",
                    departure.getIcaoCode(), arrival.getIcaoCode(), e.getMessage(), e);
            return 1.0; // Sicherer Fallback-Wert
        }
    }

    /**
     * Berechnet die Distanz zwischen zwei Koordinaten mittels Haversine-Formel.
     *
     * @param lat1 Breitengrad des ersten Punkts.
     * @param lon1 Längengrad des ersten Punkts.
     * @param lat2 Breitengrad des zweiten Punkts.
     * @param lon2 Längengrad des zweiten Punkts.
     * @return Distanz in Kilometern (gerundet, mind. 1.0), oder 1.0 bei Fehlern.
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        try {
            // Validierung der Koordinaten (wirft IllegalArgumentException bei Fehlern)
            ValidationUtils.validateLatitude(lat1);
            ValidationUtils.validateLongitude(lon1);
            ValidationUtils.validateLatitude(lat2);
            ValidationUtils.validateLongitude(lon2);

            // Wenn Koordinaten identisch sind, ist die Distanz 0, geben aber Mindestwert zurück
            if (lat1 == lat2 && lon1 == lon2) return 1.0;

            // Umrechnung in Radian für die Formel
            double dLat = Math.toRadians(lat2 - lat1);
            double dLon = Math.toRadians(lon2 - lon1);

            // Haversine-Formel anwenden
            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                            Math.sin(dLon / 2) * Math.sin(dLon / 2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            double distance = EARTH_RADIUS_KM * c;

            // Runden auf eine Nachkommastelle
            double roundedDistance = Math.round(distance * 10.0) / 10.0;

            // Sicherstellen, dass mindestens 1.0 zurückgegeben wird
            return Math.max(1.0, roundedDistance);

        } catch (IllegalArgumentException e) {
            // Fehler bei der Koordinatenvalidierung
            logger.warn("Distanzberechnung nicht möglich: Ungültige Koordinaten. Lat1={}, Lon1={}, Lat2={}, Lon2={}. Fehler: {}",
                    lat1, lon1, lat2, lon2, e.getMessage());
            return 1.0; // Sicherer Fallback-Wert
        } catch (Exception e) {
            // Andere unerwartete Fehler
            logger.error("Unerwarteter Fehler bei der Distanzberechnung zwischen Koordinaten: {}", e.getMessage(), e);
            return 1.0; // Sicherer Fallback-Wert
        }
    }

    /**
     * Berechnet die Flugzeit basierend auf Distanz und Flugzeuggeschwindigkeit.
     *
     * @param distanceKm Distanz in Kilometern.
     * @param speedKmh   Geschwindigkeit des Flugzeugs in km/h.
     * @return Flugzeit in Minuten (inkl. Start-/Landezeit), mind. {@code TAKEOFF_LANDING_TIME}.
     */
    public static int calculateFlightTime(double distanceKm, double speedKmh) {
        // Grundlegende Plausibilitätsprüfung
        if (Double.isNaN(distanceKm) || Double.isInfinite(distanceKm) ||
                Double.isNaN(speedKmh) || Double.isInfinite(speedKmh) ||
                distanceKm <= 0 || speedKmh <= 0) {
            logger.warn("Flugzeitberechnung nicht möglich: Ungültige Distanz ({}) oder Geschwindigkeit ({}). Verwende Mindestzeit.", distanceKm, speedKmh);
            return TAKEOFF_LANDING_TIME;
        }

        try {
            // Reine Flugzeit in Minuten + Puffer für Start/Landung
            double flightTimeMinutes = (distanceKm / speedKmh * 60.0) + TAKEOFF_LANDING_TIME;

            // Prüfen auf Überlauf, bevor in int gecastet wird
            if (flightTimeMinutes > Integer.MAX_VALUE) {
                logger.warn("Berechnete Flugzeit überschreitet Integer.MAX_VALUE: {}. Gebe MAX_VALUE zurück.", flightTimeMinutes);
                return Integer.MAX_VALUE;
            }

            // Aufrunden auf die nächste ganze Minute
            int flightMinutes = (int) Math.ceil(flightTimeMinutes);

            // Sicherstellen, dass die Mindestzeit nicht unterschritten wird
            return Math.max(flightMinutes, TAKEOFF_LANDING_TIME);
        } catch (Exception e) {
            logger.error("Unerwarteter Fehler bei der Flugzeitberechnung für Distanz {} km, Geschwindigkeit {} km/h: {}",
                    distanceKm, speedKmh, e.getMessage(), e);
            return TAKEOFF_LANDING_TIME; // Sicherer Fallback-Wert
        }
    }

    /**
     * Generiert einen Routencode basierend auf Abflug- und Zielflughafen-ICAO-Codes.
     *
     * @param departureCode ICAO-Code des Abflughafens.
     * @param arrivalCode   ICAO-Code des Zielflughafens.
     * @return Generierter Routencode (z.B. "EDDF-KJFK").
     * @throws IllegalArgumentException wenn Codes ungültig sind.
     */
    public static String generateRouteCode(String departureCode, String arrivalCode) {
        // Validierung der Eingaben
        ValidationUtils.validateNotEmpty(departureCode, "Abflughafen-Code für Routencode");
        ValidationUtils.validateNotEmpty(arrivalCode, "Zielflughafen-Code für Routencode");
        // Optional: operatorCode validieren, falls er doch verwendet wird
        // ValidationUtils.validateNotEmpty(operatorCode, "Operator-Code für Routencode");

        // Einfache Verkettung der Codes
        return departureCode + "-" + arrivalCode;
    }

    /**
     * Formatiert Minuten als HH:MM (z.B. für Flugzeit).
     *
     * @param minutes Zu formatierende Minuten (nicht negativ).
     * @return Formatierter String "HH:MM" oder "00:00" bei Fehlern.
     */
    public static String formatFlightTime(int minutes) {
        try {
            // Validierung der Eingabe
            ValidationUtils.validateNotNegative(minutes, "Minuten für Flugzeitformatierung");

            // Umrechnung in Stunden und Minuten
            long hours = minutes / 60;
            long mins = minutes % 60;

            // Formatierung mit führenden Nullen
            return String.format("%02d:%02d", hours, mins);
        } catch (IllegalArgumentException e) {
            logger.warn("Fehler bei Flugzeitformatierung: {}", e.getMessage());
            return "00:00"; // Sicherer Fallback-Wert
        }
    }

    /**
     * Berechnet die voraussichtliche Ankunftszeit basierend auf Abflugzeit und Flugdauer.
     *
     * @param departureDate     Abflugdatum.
     * @param departureTime     Abflugzeit.
     * @param flightTimeMinutes Flugdauer in Minuten.
     * @return Ein Object-Array mit [LocalDate arrivalDate, LocalTime arrivalTime] oder null bei Fehlern.
     */
    public static Object[] calculateArrivalTime(LocalDate departureDate, // Rückgabetyp geändert
                                                LocalTime departureTime,
                                                int flightTimeMinutes) {
        try {
            // Eingabevalidierung
            ValidationUtils.validateNotNull(departureDate, "Abflugdatum für Ankunftszeitberechnung");
            ValidationUtils.validateNotNull(departureTime, "Abflugzeit für Ankunftszeitberechnung");
            ValidationUtils.validatePositive(flightTimeMinutes, "Flugzeit für Ankunftszeitberechnung");

            // Kombiniere Datum und Zeit zum Abflugzeitpunkt
            LocalDateTime departure = LocalDateTime.of(departureDate, departureTime);

            // Addiere Flugdauer hinzu
            LocalDateTime arrival = departure.plusMinutes(flightTimeMinutes);

            logger.debug("Berechnete Ankunftszeit: {} für Abflug {} + {} Minuten",
                    arrival, departure, flightTimeMinutes);

            // --- Änderung hier: Gebe LocalDate und LocalTime in einem Array zurück ---
            return new Object[]{arrival.toLocalDate(), arrival.toLocalTime()};

        } catch (IllegalArgumentException e) {
            logger.warn("Fehler bei Ankunftszeitberechnung: {}", e.getMessage());
            return null; // Bei Validierungsfehler null zurückgeben
        } catch (DateTimeException e) {
            logger.error("DateTimeException bei Ankunftszeitberechnung (Datum/Zeit ungültig?) für Abflug {} {}: {}",
                    departureDate, departureTime, e.getMessage(), e);
            return null; // Bei Zeit-API-Fehlern null zurückgeben
        } catch (Exception e) {
            // Fängt andere unerwartete Fehler ab
            logger.error("Unerwarteter Fehler bei Ankunftszeitberechnung für Abflug {} {}, Dauer {}: {}",
                    departureDate, departureTime, flightTimeMinutes, e.getMessage(), e);
            return null;
        }
    }
}
