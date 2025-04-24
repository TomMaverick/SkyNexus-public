package skynexus.util;

import skynexus.model.Aircraft;
import skynexus.model.Airport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Zentrale Utility-Klasse zur Validierung von Eingabedaten im SkyNexus-System.
 */
public final class ValidationUtils {

    private static final Logger logger = LoggerFactory.getLogger(ValidationUtils.class);

    private ValidationUtils() {}

    //==========================================================================
    // Allgemeine Validierungen
    //==========================================================================

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
     * Wirft eine Exception, wenn der String leer, null oder ungültig ist.
     *
     * @param value der zu parsende String
     * @param fieldName Name des Feldes für die Fehlermeldung
     * @return den geparsten double-Wert
     * @throws IllegalArgumentException wenn der String leer, null oder nicht als gültige Zahl geparst werden kann
     */
    public static double parseDoubleWithCommaOrPoint(String value, String fieldName) {
        validateNotEmpty(value, fieldName); // Sicherstellen, dass Input nicht leer ist
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

    public static void validateRange(int value, int min, int max, String fieldName) {
        if (value < min || value > max) {
            logger.warn("Validierung fehlgeschlagen: {} liegt außerhalb des Bereichs [{}, {}] (Wert: {}).", fieldName, min, max, value);
            throw new IllegalArgumentException(fieldName + " (aktuell: " + value +
                    ") muss zwischen " + min + " und " + max + " liegen.");
        }
    }

    public static void validatePattern(String value, String pattern,
                                       String fieldName, String formatDescription) {
        validateNotEmpty(value, fieldName); // Musterprüfung impliziert nicht leeren String
        if (!value.matches(pattern)) {
            logger.warn("Validierung fehlgeschlagen: {} entspricht nicht dem Muster '{}' ('{}').", fieldName, pattern, value);
            throw new IllegalArgumentException(fieldName + " hat ein ungültiges Format. "
                    + formatDescription);
        }
    }

    //==========================================================================
    // Luftfahrt Validierungen
    //==========================================================================

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
            logger.warn("Validierung fehlgeschlagen: Ungültige Flugnummer '{}'. Erwartet: 3 Buchstaben + 1-4 Ziffern.", flightNumber);
            throw new IllegalArgumentException("Ungültige Flugnummer (Format z.B. SKX123 erwartet).");
        }
    }

    public static void validateCapacity(int available, int requested, String message) {
        if (requested > available) {
            logger.warn("Validierung fehlgeschlagen: Kapazität nicht ausreichend ({} > {}).", requested, available);
            throw new IllegalArgumentException(message);
        }
    }

    //==========================================================================
    // Geografische Validierungen
    //==========================================================================

    /**
     * Prüft, ob ein Breitengrad gültig ist (-90 bis 90).
     * Erwartet einen primitiven double, da Koordinaten jetzt Pflichtfelder sind.
     *
     * @param latitude der zu prüfende Breitengrad
     * @throws IllegalArgumentException wenn der Breitengrad ungültig ist
     */
    public static void validateLatitude(double latitude) {
        if (latitude < -90 || latitude > 90) {
            logger.warn("Validierung fehlgeschlagen: Breitengrad außerhalb des Bereichs [-90, 90] (Wert: {}).", latitude);
            throw new IllegalArgumentException("Breitengrad muss zwischen -90 und 90 liegen.");
        }
    }

    /**
     * Prüft, ob ein Längengrad gültig ist (-180 bis 180).
     * Erwartet einen primitiven double, da Koordinaten jetzt Pflichtfelder sind.
     *
     * @param longitude der zu prüfende Längengrad
     * @throws IllegalArgumentException wenn der Längengrad ungültig ist
     */
    public static void validateLongitude(double longitude) {
        if (longitude < -180 || longitude > 180) {
            logger.warn("Validierung fehlgeschlagen: Längengrad außerhalb des Bereichs [-180, 180] (Wert: {}).", longitude);
            throw new IllegalArgumentException("Längengrad muss zwischen -180 und 180 liegen.");
        }
    }

    //==========================================================================
    // Datums- und Zeitvalidierungen
    //==========================================================================
    public static void validateDateNotInPast(LocalDate date, String fieldName) {
        validateNotNull(date, fieldName);
        if (date.isBefore(LocalDate.now())) {
            logger.warn("Validierung fehlgeschlagen: {} liegt in der Vergangenheit ({}).", fieldName, date);
            throw new IllegalArgumentException(fieldName + " darf nicht in der Vergangenheit liegen.");
        }
    }

    /**
     * Prüft, ob ein Datum nicht in der Zukunft liegt.
     *
     * @param date das zu prüfende Datum
     * @param fieldName Name des Feldes für die Fehlermeldung
     * @throws IllegalArgumentException wenn das Datum null ist oder in der Zukunft liegt
     */
    public static void validateDateNotInFuture(LocalDate date, String fieldName) {
        validateNotNull(date, fieldName);
        if (date.isAfter(LocalDate.now())) {
            logger.warn("Validierung fehlgeschlagen: {} liegt in der Zukunft ({}).", fieldName, date);
            throw new IllegalArgumentException(fieldName + " darf nicht in der Zukunft liegen.");
        }
    }

    public static void validateArrivalAfterDeparture(LocalDateTime departure, LocalDateTime arrival) {
        validateNotNull(departure, "Abflugzeitpunkt");
        validateNotNull(arrival, "Ankunftszeitpunkt");
        if (!arrival.isAfter(departure)) {
            logger.warn("Validierung fehlgeschlagen: Ankunft ({}) liegt nicht nach Abflug ({}).", arrival, departure);
            throw new IllegalArgumentException("Ankunftszeit muss nach Abflugzeit liegen.");
        }
    }

    public static boolean isValidFlightTime(LocalDate departureDate, LocalTime departureTime,
                                            LocalDate arrivalDate, LocalTime arrivalTime) {
        try {
            validateNotNull(departureDate, "Abflugdatum");
            validateNotNull(departureTime, "Abflugzeit");
            validateNotNull(arrivalDate, "Ankunftsdatum");
            validateNotNull(arrivalTime, "Ankunftszeit");
            LocalDateTime departure = LocalDateTime.of(departureDate, departureTime);
            LocalDateTime arrival = LocalDateTime.of(arrivalDate, arrivalTime);
            return arrival.isAfter(departure);
        } catch (Exception e) {
            logger.error("Fehler bei der Prüfung der Flugzeit: {}", e.getMessage());
            return false;
        }
    }

    //==========================================================================
    // Reisepass Validierungen
    //==========================================================================
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
        if (passportNumber == null) return ValidationResult.invalid("Reisepassnummer darf nicht null sein.");
        String trimmed = passportNumber.trim();
        if (trimmed.isEmpty()) return ValidationResult.invalid("Reisepassnummer darf nicht leer sein.");
        String normalized = trimmed.toUpperCase();
        if (!normalized.equals(passportNumber)) logger.trace("Normalisiert: '{}' → '{}'", passportNumber, normalized);
        if (normalized.length() != 9) return ValidationResult.invalid("Ungültige Passnummer: Format muss Ländercode (2 Zeichen) + 7 alphanumerische Zeichen sein, gefunden: " + normalized.length() + " Zeichen.");
        String countryCode = normalized.substring(0, 2);
        String number = normalized.substring(2);
        if (!countryCode.matches("[A-Z]{2}")) return ValidationResult.invalid("Ungültiger Ländercode '" + countryCode + "'. Muss aus 2 Großbuchstaben bestehen.");
        if (!number.matches("[A-Z0-9]{7}")) {
            StringBuilder invalidChars = new StringBuilder();
            for (int i = 0; i < number.length(); i++) {
                char c = number.charAt(i);
                if (!((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) invalidChars.append(c);
            }
            String errorMsg = "Ungültiger Nummernteil. Muss aus 7 alphanumerischen Zeichen bestehen (nur Großbuchstaben und Ziffern).";
            if (invalidChars.length() > 0) errorMsg += " Ungültige Zeichen: " + invalidChars;
            return ValidationResult.invalid(errorMsg);
        }
        return ValidationResult.valid(normalized);
    }

    private static class ValidationResult {
        final boolean valid; final String normalizedValue; final String errorMessage;
        ValidationResult(boolean v, String n, String e) { valid = v; normalizedValue = n; errorMessage = e; }
        boolean isValid() { return valid; } String getNormalizedValue() { return normalizedValue; } String getErrorMessage() { return errorMessage; }
        static ValidationResult valid(String n) { return new ValidationResult(true, n, null); }
        static ValidationResult invalid(String e) { return new ValidationResult(false, null, e); }
    }

    //==========================================================================
    // Modell-spezifische Validierungen
    //==========================================================================
    public static void validateAircraftLocation(Aircraft aircraft, Airport requiredLocation) throws IllegalArgumentException {
        validateNotNull(aircraft, "Flugzeug");
        validateNotNull(requiredLocation, "Erforderlicher Standort");
        Airport currentLocation = aircraft.getCurrentLocation();
        if (currentLocation == null) {
            logger.warn("Validierung fehlgeschlagen: Flugzeug '{}' hat keinen definierten Standort.", aircraft.getRegistrationNo());
            throw new IllegalArgumentException("Das Flugzeug '" + aircraft.getRegistrationNo() + "' hat keinen definierten Standort.");
        }
        if (requiredLocation.getId() == null) {
            logger.error("Validierung nicht möglich: Erforderlicher Standort '{}' hat keine ID.", requiredLocation.getIcaoCode());
            throw new IllegalStateException("Erforderlicher Standort hat keine ID für den Vergleich.");
        }
        if (!requiredLocation.getId().equals(currentLocation.getId())) {
            logger.warn("Validierung fehlgeschlagen: Flugzeug '{}' ist in {} (ID:{}), benötigt wird {} (ID:{}).", aircraft.getRegistrationNo(), currentLocation.getIcaoCode(), currentLocation.getId(), requiredLocation.getIcaoCode(), requiredLocation.getId());
            throw new IllegalArgumentException("Das Flugzeug '" + aircraft.getRegistrationNo() + "' befindet sich in " + currentLocation.getIcaoCode() + ", nicht am erforderlichen Standort " + requiredLocation.getIcaoCode() + ".");
        }
        logger.debug("Flugzeug '{}' befindet sich am korrekten Standort {}.", aircraft.getRegistrationNo(), requiredLocation.getIcaoCode());
    }

    /**
     * Initialisiert einen Airport als Standardflughafen, wenn er null ist.
     * Lädt den Default-Flughafen aus den Systemeinstellungen.
     *
     * @param location Der zu prüfende Flughafen-Standort
     * @return Ein gültiger Airport, entweder der übergebene oder der Default-Flughafen
     */
    public static Airport validateAndGetDefaultAirport(Airport location) {
        if (location != null) return location;

        try {
            // Systemeinstellungen-Service importieren und Singleton-Instanz holen
            skynexus.service.SystemSettingsService settingsService = skynexus.service.SystemSettingsService.getInstance();
            // Default Airport ID aus den Systemeinstellungen holen
            Long defaultAirportId = settingsService.getDefaultAirportId();

            // Airport aus der Datenbank laden
            String sql = "SELECT * FROM airports WHERE id = ?";
            Airport defaultAirport = skynexus.database.DatabaseHelper.executeQuery(
                sql,
                rs -> {
                    if (rs.next()) {
                        Airport airport = new Airport();
                        airport.setId(rs.getLong("id"));
                        airport.setIcaoCode(rs.getString("icao_code"));
                        airport.setName(rs.getString("name"));
                        airport.setCity(rs.getString("city"));

                        // Optional: Auch Land und Koordinaten setzen, wenn vorhanden
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
                logger.debug("Default-Flughafen aus DB geladen: {} (ID: {})", defaultAirport.getIcaoCode(), defaultAirport.getId());
                return defaultAirport;
            }

            // Fallback, wenn DB-Zugriff fehlschlägt
            logger.warn("Konnte Default-Flughafen nicht laden, verwende Fallback-Werte.");
            Airport fallbackAirport = new Airport();
            fallbackAirport.setId(1L);
            fallbackAirport.setIcaoCode("EDDF");
            fallbackAirport.setName("Frankfurt Airport");
            fallbackAirport.setCity("Frankfurt");
            return fallbackAirport;

        } catch (Exception e) {
            logger.error("Fehler beim Laden des Default-Flughafens: {}", e.getMessage());
            // Fallback, wenn ein Fehler auftritt
            Airport fallbackAirport = new Airport();
            fallbackAirport.setId(1L);
            fallbackAirport.setIcaoCode("EDDF");
            fallbackAirport.setName("Frankfurt Airport");
            fallbackAirport.setCity("Frankfurt");
            return fallbackAirport;
        }
    }
}
