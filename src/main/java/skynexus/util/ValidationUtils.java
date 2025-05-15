package skynexus.util;

import skynexus.model.Airport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

/**
 * Zentrale Utility-Klasse zur Validierung von Eingabedaten im SkyNexus-System.
 */
public final class ValidationUtils {

    private static final Logger logger = LoggerFactory.getLogger(ValidationUtils.class);

    private ValidationUtils() {}

    // Allgemeine Validierungen

    public static void validateNotNull(Object value, String fieldName) {
        if (value == null) {
            logger.warn("Validierung fehlgeschlagen: {} darf nicht null sein.", fieldName);
            throw new IllegalArgumentException(fieldName + " darf nicht null sein.");
        }
    }

    public static void validateNotEmpty(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            logger.warn("Validierung fehlgeschlagen: {} darf nicht leer sein.", fieldName);
            throw new IllegalArgumentException(fieldName + " darf nicht leer sein.");
        }
    }

    /**
     * Parst einen String als Double, der Komma oder Punkt als Dezimaltrennzeichen enthalten kann.
     */
    public static double parseDoubleWithCommaOrPoint(String value, String fieldName) {
        validateNotEmpty(value, fieldName);
        String normalizedValue = value.trim().replace(',', '.');
        try {
            return Double.parseDouble(normalizedValue);
        } catch (NumberFormatException e) {
            logger.warn("Validierung fehlgeschlagen: {} enthält keine gültige Dezimalzahl ('{}').", fieldName, value);
            throw new IllegalArgumentException(fieldName + " enthält keine gültige Dezimalzahl.");
        }
    }

    public static void validateNotNegative(double value, String fieldName) {
        if (value < 0) {
            logger.warn("Validierung fehlgeschlagen: {} darf nicht negativ sein (Wert: {}).", fieldName, value);
            throw new IllegalArgumentException(fieldName + " darf nicht negativ sein.");
        }
    }

    public static void validatePositive(double value, String fieldName) {
        if (value <= 0) {
            logger.warn("Validierung fehlgeschlagen: {} muss positiv sein (Wert: {}).", fieldName, value);
            throw new IllegalArgumentException(fieldName + " muss größer als 0 sein.");
        }
    }

    public static void validatePattern(String value, String pattern,
                                       String fieldName, String formatDescription) {
        validateNotEmpty(value, fieldName);
        if (!value.matches(pattern)) {
            logger.warn("Validierung fehlgeschlagen: {} entspricht nicht dem Muster '{}'.", fieldName, pattern);
            throw new IllegalArgumentException(fieldName + " hat ein ungültiges Format. " + formatDescription);
        }
    }

    // Benutzer und Authentifizierungsvalidierungen

    /**
     * Prüft, ob ein Benutzername die Mindestlänge erfüllt.
     */
    public static void validateUsernameLength(String username, int minLength) {
        validateNotEmpty(username, "Benutzername");
        if (username.length() < minLength) {
            logger.warn("Validierung fehlgeschlagen: Benutzername zu kurz (min. {} Zeichen).", minLength);
            throw new IllegalArgumentException("Benutzername muss mindestens " + minLength + " Zeichen lang sein");
        }
    }

    /**
     * Prüft, ob ein Benutzername die Standardmindestlänge (3 Zeichen) erfüllt.
     */
    public static void validateUsernameLength(String username) {
        validateUsernameLength(username, 3);
    }

    /**
     * Validiert die Löschung eines Benutzers nach Sicherheitsrichtlinien.
     */
    public static void validateUserDeletion(String username, Long targetUserId, Long currentUserId) {
        validateNotEmpty(username, "Benutzername");
        validateNotNull(targetUserId, "Zielbenutzer-ID");

        // Standard-Admin Schutz
        if ("admin".equals(username)) {
            throw new IllegalStateException("Der Standard-Administrator kann nicht gelöscht werden");
        }

        // Selbstlöschung verhindern
        if (currentUserId != null && currentUserId.equals(targetUserId)) {
            throw new IllegalStateException("Sie können sich nicht selbst löschen");
        }
    }

    // Luftfahrt Validierungen

    public static void validateAirportICAO(String icaoCode) {
        validateNotEmpty(icaoCode, "Flughafen ICAO");
        if (!icaoCode.matches("^[A-Z]{4}$")) {
            logger.warn("Validierung fehlgeschlagen: Ungültiger Flughafen ICAO-Code '{}'.", icaoCode);
            throw new IllegalArgumentException("Ungültiger Flughafen ICAO-Code (genau 4 Großbuchstaben erwartet).");
        }
    }

    public static void validateAirlineICAO(String icaoCode) {
        validateNotEmpty(icaoCode, "Airline ICAO");
        if (!icaoCode.matches("^[A-Z]{3}$")) {
            logger.warn("Validierung fehlgeschlagen: Ungültiger Airline ICAO-Code '{}'.", icaoCode);
            throw new IllegalArgumentException("Ungültiger Airline ICAO-Code (genau 3 Großbuchstaben erwartet).");
        }
    }

    public static void validateFlightNumber(String flightNumber) {
        validateNotEmpty(flightNumber, "Flugnummer");
        if (!flightNumber.matches("^[A-Z]{3}\\d{1,4}$")) {
            logger.warn("Validierung fehlgeschlagen: Ungültige Flugnummer '{}'.", flightNumber);
            throw new IllegalArgumentException("Ungültige Flugnummer (Format z.B. SKX123 erwartet).");
        }
    }

    // Geografische Validierungen

    /**
     * Prüft, ob ein Breitengrad gültig ist (-90 bis 90).
     */
    public static void validateLatitude(double latitude) {
        if (latitude < -90 || latitude > 90) {
            logger.warn("Validierung fehlgeschlagen: Breitengrad außerhalb des Bereichs [-90, 90].", latitude);
            throw new IllegalArgumentException("Breitengrad muss zwischen -90 und 90 liegen.");
        }
    }

    /**
     * Prüft, ob ein Längengrad gültig ist (-180 bis 180).
     */
    public static void validateLongitude(double longitude) {
        if (longitude < -180 || longitude > 180) {
            logger.warn("Validierung fehlgeschlagen: Längengrad außerhalb des Bereichs [-180, 180].", longitude);
            throw new IllegalArgumentException("Längengrad muss zwischen -180 und 180 liegen.");
        }
    }

    /**
     * Prüft, ob ein Datum nicht in der Zukunft liegt.
     */
    public static void validateDateNotInFuture(LocalDate date, String fieldName) {
        validateNotNull(date, fieldName);
        if (date.isAfter(LocalDate.now())) {
            logger.warn("Validierung fehlgeschlagen: {} liegt in der Zukunft.", fieldName);
            throw new IllegalArgumentException(fieldName + " darf nicht in der Zukunft liegen.");
        }
    }

    // Reisepass Validierungen

    /**
     * Validiert eine Passnummer.
     */
    public static String validatePassportNumber(String passportNumber) {
        logger.debug("Validiere Passnummer: '{}'", passportNumber);
        ValidationResult result = validatePassportNumberInternal(passportNumber);
        if (!result.isValid()) {
            logger.warn("Validierung der Passnummer fehlgeschlagen: {}", result.getErrorMessage());
            throw new IllegalArgumentException(result.getErrorMessage());
        }
        logger.debug("Validierung erfolgreich: '{}'", result.getNormalizedValue());
        return result.getNormalizedValue();
    }

    private static ValidationResult validatePassportNumberInternal(String passportNumber) {
        if (passportNumber == null)
            return ValidationResult.invalid("Reisepassnummer darf nicht null sein.");

        String trimmed = passportNumber.trim();
        if (trimmed.isEmpty())
            return ValidationResult.invalid("Reisepassnummer darf nicht leer sein.");

        String normalized = trimmed.toUpperCase();
        if (normalized.length() != 9)
            return ValidationResult.invalid("Ungültige Passnummer: Format muss Ländercode (2 Zeichen) + 7 alphanumerische Zeichen sein.");

        String countryCode = normalized.substring(0, 2);
        String number = normalized.substring(2);

        if (!countryCode.matches("[A-Z]{2}"))
            return ValidationResult.invalid("Ungültiger Ländercode '" + countryCode + "'. Muss aus 2 Großbuchstaben bestehen.");

        if (!number.matches("[A-Z0-9]{7}")) {
            return ValidationResult.invalid("Ungültiger Nummernteil. Muss aus 7 alphanumerischen Zeichen bestehen (nur Großbuchstaben und Ziffern).");
        }

        return ValidationResult.valid(normalized);
    }

    private static class ValidationResult {
        final boolean valid;
        final String normalizedValue;
        final String errorMessage;

        ValidationResult(boolean v, String n, String e) {
            valid = v;
            normalizedValue = n;
            errorMessage = e;
        }

        boolean isValid() { return valid; }
        String getNormalizedValue() { return normalizedValue; }
        String getErrorMessage() { return errorMessage; }

        static ValidationResult valid(String n) { return new ValidationResult(true, n, null); }
        static ValidationResult invalid(String e) { return new ValidationResult(false, null, e); }
    }

    /**
     * Initialisiert einen Airport als Standardflughafen, wenn er null ist.
     */
    public static Airport validateAndGetDefaultAirport(Airport location) {
        if (location != null) return location;

        try {
            skynexus.service.SystemSettingsService settingsService = skynexus.service.SystemSettingsService.getInstance();
            Long defaultAirportId = settingsService.getDefaultAirportId();

            Airport defaultAirport = skynexus.database.DatabaseHelper.executeQuery(
                "SELECT * FROM airports WHERE id = ?",
                rs -> {
                    if (rs.next()) {
                        Airport airport = new Airport();
                        airport.setId(rs.getLong("id"));
                        airport.setIcaoCode(rs.getString("icao_code"));
                        airport.setName(rs.getString("name"));
                        airport.setCity(rs.getString("city"));

                        try { airport.setCountry(rs.getString("country")); } catch (Exception ignored) {}
                        try { airport.setLatitude(rs.getDouble("latitude")); } catch (Exception ignored) {}
                        try { airport.setLongitude(rs.getDouble("longitude")); } catch (Exception ignored) {}

                        return airport;
                    }
                    return null;
                },
                defaultAirportId
            );

            if (defaultAirport != null) {
                logger.debug("Default-Flughafen aus DB geladen: {}", defaultAirport.getIcaoCode());
                return defaultAirport;
            }

            logger.warn("Konnte Default-Flughafen nicht laden, verwende Fallback-Werte.");
            Airport fallbackAirport = new Airport();
            fallbackAirport.setId(1L);
            fallbackAirport.setIcaoCode("EDDF");
            fallbackAirport.setName("Frankfurt Airport");
            fallbackAirport.setCity("Frankfurt");
            return fallbackAirport;

        } catch (Exception e) {
            logger.error("Fehler beim Laden des Default-Flughafens: {}", e.getMessage());
            Airport fallbackAirport = new Airport();
            fallbackAirport.setId(1L);
            fallbackAirport.setIcaoCode("EDDF");
            fallbackAirport.setName("Frankfurt Airport");
            fallbackAirport.setCity("Frankfurt");
            return fallbackAirport;
        }
    }
}
