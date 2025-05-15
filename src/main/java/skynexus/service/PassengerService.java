package skynexus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.database.DatabaseConnectionManager;
import skynexus.enums.Gender;
import skynexus.enums.SeatClass;
import skynexus.model.Booking;
import skynexus.model.Flight;
import skynexus.model.Passenger;
import skynexus.util.ValidationUtils; // Sicherstellen, dass dieser Import korrekt ist

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Service-Klasse für die Verwaltung von Passagieren mit erweiterter Funktionalität für Mehrfachbuchungen.
 * Bietet Methoden zum Laden, Speichern und Verwalten von Passagieren und deren Buchungen.
 * (Refaktorisierte Version zur Behebung von Speicherproblemen und zur Verbesserung der Lesbarkeit - Final Fixes)
 */
public class PassengerService {
    private static final Logger logger = LoggerFactory.getLogger(PassengerService.class);
    private static final long CACHE_EXPIRY_MS = 60000; // 1 Minute Cache-Gültigkeit für Pax-Zahlen
    private static PassengerService instance;
    private final FlightService flightService;
    // Cache für die Passagierzahlen, um wiederholte DB-Abfragen zu vermeiden
    private final Map<Long, Integer> passengerCountCache = new ConcurrentHashMap<>();
    private long lastCacheUpdateTime = 0;

    // --- SQL Konstanten ---
    private static final String SQL_GET_ALL_COUNTRIES = """
        SELECT country, nationality, code_2, code_3 FROM countries
        ORDER BY CASE WHEN nationality = 'Deutsch' THEN 0 ELSE 1 END, nationality
        """;
    private static final String SQL_CHECK_PASSPORT_UNIQUENESS = """
        SELECT p.id, p.first_name, p.last_name FROM passengers p
        WHERE p.passport_number = ?
        AND (? IS NULL OR p.id != ?)
        """;
    private static final String SQL_GET_NATIONALITY_ID = "SELECT id FROM countries WHERE LOWER(nationality) = LOWER(?)";
    private static final String SQL_GET_BOOKINGS_FOR_PASSENGERS = """
        SELECT b.id, b.passenger_id, b.flight_id, b.seat_class, b.booking_datetime_utc,
               f.flight_number, f.status, f.departure_datetime, f.route_id, f.aircraft_id,
               r.distance_km, r.flight_time_minutes
        FROM passenger_bookings b
        JOIN flights f ON b.flight_id = f.id
        JOIN routes r ON f.route_id = r.id
        WHERE b.passenger_id IN (%s)
        ORDER BY b.passenger_id, f.departure_datetime DESC
        """;
    private static final String SQL_INSERT_BOOKING = "INSERT INTO passenger_bookings (passenger_id, flight_id, seat_class, booking_datetime_utc) VALUES (?, ?, ?, ?)";
    private static final String SQL_UPDATE_BOOKING = "UPDATE passenger_bookings SET passenger_id=?, flight_id=?, seat_class=? WHERE id=?";
    private static final String SQL_DELETE_BOOKING = "DELETE FROM passenger_bookings WHERE id = ?";
    private static final String SQL_GET_PASSENGER_COUNTS_ALL = "SELECT flight_id, COUNT(*) as passenger_count FROM passenger_bookings GROUP BY flight_id";
    private static final String SQL_GET_ALL_PASSENGERS = """
        SELECT p.id, p.last_name, p.first_name, p.gender, p.date_of_birth, c.nationality, p.passport_number
        FROM passengers p
        JOIN countries c ON p.nationality_id = c.id
        ORDER BY p.last_name, p.first_name
        """;
    private static final String SQL_INSERT_PASSENGER = """
        INSERT INTO passengers (last_name, first_name, gender, date_of_birth, nationality_id, passport_number)
        VALUES (?, ?, ?, ?, ?, ?)
        """;
    private static final String SQL_UPDATE_PASSENGER = """
        UPDATE passengers SET last_name=?, first_name=?, gender=?, date_of_birth=?, nationality_id=?, passport_number=?
        WHERE id=?
        """;
    private static final String SQL_UPDATE_FLIGHT_PAX_COUNT = "UPDATE flights SET pax_economy=?, pax_business=?, pax_first=? WHERE id=?";
    private static final String SQL_DELETE_PASSENGER = "DELETE FROM passengers WHERE id = ?";
    private static final String SQL_GET_EXISTING_BOOKINGS_FOR_PAX = "SELECT b.id, b.flight_id, b.seat_class FROM passenger_bookings b WHERE b.passenger_id = ?";
    private static final String SQL_GET_FLIGHT_PAX_FOR_UPDATE = "SELECT pax_economy, pax_business, pax_first FROM flights WHERE id = ? FOR UPDATE";


    /**
     * Privater Konstruktor (Singleton-Pattern)
     */
    private PassengerService() {
        this.flightService = FlightService.getInstance();
        if (this.flightService == null) {
            logger.error("FlightService konnte nicht initialisiert werden!");
            throw new IllegalStateException("FlightService ist null im PassengerService Konstruktor.");
        }
    }

    /**
     * Gibt die Singleton-Instanz des PassengerService zurück.
     */
    public static synchronized PassengerService getInstance() {
        if (instance == null) {
            instance = new PassengerService();
        }
        return instance;
    }

    /**
     * Liefert alle Länder aus der countries-Tabelle.
     */
    public List<Map<String, String>> getAllCountries() {
        List<Map<String, String>> countries = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SQL_GET_ALL_COUNTRIES)) {

            while (rs.next()) {
                Map<String, String> country = new HashMap<>();
                country.put("country", rs.getString("country"));
                country.put("nationality", rs.getString("nationality"));
                country.put("code_2", rs.getString("code_2"));
                country.put("code_3", rs.getString("code_3"));
                countries.add(country);
            }
            logger.debug("{} Länder aus der Datenbank geladen.", countries.size());
        } catch (SQLException e) {
            logger.error("Fehler beim Laden der Länder: {}", e.getMessage(), e);
        }
        return countries;
    }

    /**
     * Prüft, ob eine Passnummer eindeutig ist.
     */
    public boolean isPassportNumberUnique(String passportNumber, Long passengerId) throws RuntimeException {
        logger.debug("Prüfe Eindeutigkeit von Passnummer '{}' für Passagier-ID: {}", passportNumber, passengerId);

        if (passportNumber == null || passportNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Passnummer darf nicht leer sein.");
        }

        String normalizedPassportNumber;
        try {
            normalizedPassportNumber = ValidationUtils.validatePassportNumber(passportNumber);
            if (!normalizedPassportNumber.equals(passportNumber)) {
                logger.info("Passnummer für Eindeutigkeitsprüfung normalisiert: '{}' -> '{}'",
                        passportNumber, normalizedPassportNumber);
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Ungültige Passnummer '{}' zur Eindeutigkeitsprüfung: {}", passportNumber, e.getMessage());
            throw e;
        }

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_CHECK_PASSPORT_UNIQUENESS)) {

            stmt.setString(1, normalizedPassportNumber);
            stmt.setObject(2, passengerId, Types.BIGINT);
            stmt.setObject(3, passengerId, Types.BIGINT);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long existingId = rs.getLong("id");
                    String existingFirstName = rs.getString("first_name");
                    String existingLastName = rs.getString("last_name");
                    String errorMsg = String.format(
                            "Passnummer %s wird bereits von %s %s (ID: %d) verwendet.",
                            normalizedPassportNumber, existingFirstName, existingLastName, existingId);
                    logger.warn("Eindeutigkeitsprüfung fehlgeschlagen: {}", errorMsg);
                    throw new IllegalArgumentException(errorMsg);
                }
                logger.debug("Passnummer '{}' ist eindeutig.", normalizedPassportNumber);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Datenbankfehler bei Passnummer-Eindeutigkeitsprüfung für '{}': {}", normalizedPassportNumber, e.getMessage(), e);
            throw new RuntimeException("Datenbankfehler bei der Passnummernüberprüfung: " + e.getMessage(), e);
        }
    }


    /**
     * Ermittelt die nationality_id für eine gegebene Nationalitätsbezeichnung.
     */
    private Long getNationalityId(Connection conn, String nationality) throws SQLException, IllegalArgumentException {
        if (nationality == null || nationality.trim().isEmpty()) {
            logger.error("getNationalityId: Leere Nationalität übergeben.");
            throw new IllegalArgumentException("Nationalität darf nicht leer sein.");
        }

        String trimmedNationality = nationality.trim();
        logger.debug("Suche Nationalität-ID für: '{}'", trimmedNationality);

        try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_NATIONALITY_ID)) {
            stmt.setString(1, trimmedNationality);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Long id = rs.getLong("id");
                    logger.info("Nationalität-ID {} für '{}' gefunden.", id, trimmedNationality);
                    return id;
                } else {
                    logger.error("FEHLER: Nationalität '{}' wurde nicht in der 'countries'-Tabelle gefunden!", trimmedNationality);
                    logAvailableNationalities(conn);
                    throw new IllegalArgumentException("Nationalität '" + trimmedNationality + "' ist ungültig oder nicht in der Datenbank vorhanden.");
                }
            }
        }
    }


    // --- Methoden für Buchungen ---

    public boolean hasFlightAvailableSeats(Flight flight, SeatClass seatClass) {
        if (flight == null || seatClass == null || flight.getAircraft() == null || flight.getAircraft().getType() == null) {
            logger.warn("Ungültige Eingabe für Sitzplatzverfügbarkeitsprüfung.");
            return false;
        }
        int maxPaxTotal = flight.getAircraft().getType().getPaxCapacity();
        int bookedPaxTotal = flight.getPaxEconomy() + flight.getPaxBusiness() + flight.getPaxFirst();
        if (bookedPaxTotal >= maxPaxTotal) {
            logger.debug("Flug {} ist voll ({} / {} Plätze belegt)", flight.getFlightNumber(), bookedPaxTotal, maxPaxTotal);
            return false;
        }
        int maxPaxEconomy = (int) (maxPaxTotal * 0.8);
        int maxPaxBusiness = (int) (maxPaxTotal * 0.15);
        int maxPaxFirst = maxPaxTotal - maxPaxEconomy - maxPaxBusiness;
        return switch (seatClass) {
            case ECONOMY -> flight.getPaxEconomy() < maxPaxEconomy;
            case BUSINESS -> flight.getPaxBusiness() < maxPaxBusiness;
            case FIRST_CLASS -> flight.getPaxFirst() < maxPaxFirst;
        };
    }

    public List<Booking> getBookingsForPassenger(Long passengerId) {
        if (passengerId == null) {
            logger.warn("Versuch, Buchungen für null-Passagier-ID zu laden.");
            return new ArrayList<>();
        }
        Map<Long, List<Booking>> bookingsMap = getBookingsForPassengers(List.of(passengerId));
        return bookingsMap.getOrDefault(passengerId, new ArrayList<>());
    }

    /**
     * Lädt historische (abgeschlossene) Buchungen für einen Passagier.
     * Filtert nur Flüge mit Status COMPLETED.
     *
     * @param passengerId Die ID des Passagiers
     * @return Liste der historischen Buchungen
     */
    public List<Booking> getHistoricalBookings(Long passengerId) {
        if (passengerId == null) {
            logger.warn("Versuch, historische Buchungen für null-Passagier-ID zu laden.");
            return new ArrayList<>();
        }

        List<Booking> allBookings = getBookingsForPassenger(passengerId);
        return allBookings.stream()
            .filter(booking -> booking.getFlight() != null &&
                    booking.getFlight().getStatus() == skynexus.enums.FlightStatus.COMPLETED)
            .collect(java.util.stream.Collectors.toList());
    }

    public Map<Long, List<Booking>> getBookingsForPassengers(List<Long> passengerIds) {
        Map<Long, List<Booking>> bookingsMap = new HashMap<>();
        if (passengerIds == null || passengerIds.isEmpty()) {
            return bookingsMap;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(passengerIds.size(), "?"));
        String sql = String.format(SQL_GET_BOOKINGS_FOR_PASSENGERS, placeholders);
        Map<Long, Flight> flightsCache = new HashMap<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < passengerIds.size(); i++) {
                stmt.setLong(i + 1, passengerIds.get(i));
            }
            logger.debug("Führe Batch-Buchungsabfrage aus für IDs: {}", passengerIds);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Long bookingId = rs.getLong("id");
                    Long currentPassengerId = rs.getLong("passenger_id");
                    Long flightId = rs.getLong("flight_id");
                    String seatClassStr = rs.getString("seat_class");
                    Timestamp bookingTimestamp = rs.getTimestamp("booking_datetime_utc");
                    Flight flight = flightsCache.computeIfAbsent(flightId, flightService::getFlightById);

                    if (flight == null) {
                        logger.warn("Buchung {} übersprungen: Flug mit ID {} nicht gefunden.", bookingId, flightId);
                        continue;
                    }
                    try {
                        SeatClass seatClass = SeatClass.valueOf(seatClassStr);
                        LocalDateTime bookingDate = (bookingTimestamp != null) ? bookingTimestamp.toLocalDateTime() : null;
                        Passenger placeholderPassenger = new Passenger();
                        placeholderPassenger.setId(currentPassengerId);
                        Booking booking = new Booking(bookingId, placeholderPassenger, flight, seatClass, bookingDate);
                        bookingsMap.computeIfAbsent(currentPassengerId, k -> new ArrayList<>()).add(booking);
                    } catch (IllegalArgumentException e) {
                        logger.error("Ungültige SeatClass '{}' in DB für Buchung ID {}. Überspringe.", seatClassStr, bookingId, e);
                    }
                }
            }
            logger.debug("Buchungen für {} Passagiere geladen.", bookingsMap.size());
        } catch (SQLException e) {
            logger.error("Fehler beim Batch-Laden der Buchungen für Passagier-IDs {}: {}", passengerIds, e.getMessage(), e);
        }
        return bookingsMap;
    }

    public Map<Long, Integer> getPassengerCountsForAllFlights() {
        long currentTime = System.currentTimeMillis();
        if (!passengerCountCache.isEmpty() && (currentTime - lastCacheUpdateTime) < CACHE_EXPIRY_MS) {
            logger.debug("Verwende gecachte Passagierzahlen (Alter: {} ms)", currentTime - lastCacheUpdateTime);
            return new HashMap<>(passengerCountCache);
        }
        logger.debug("Lade Passagierzahlen für alle Flüge neu aus der DB.");
        Map<Long, Integer> passengerCounts = new HashMap<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SQL_GET_PASSENGER_COUNTS_ALL)) {
            while (rs.next()) {
                passengerCounts.put(rs.getLong("flight_id"), rs.getInt("passenger_count"));
            }
            passengerCountCache.clear(); passengerCountCache.putAll(passengerCounts);
            lastCacheUpdateTime = currentTime;
            logger.info("Passagierzahlen für {} Flüge neu geladen.", passengerCounts.size());
        } catch (SQLException e) {
            logger.error("Fehler beim Laden der Passagierzahlen für alle Flüge: {}", e.getMessage(), e);
            passengerCountCache.clear(); lastCacheUpdateTime = 0; return new HashMap<>();
        }
        return passengerCounts;
    }

    public void clearPassengerCountCache() {
        passengerCountCache.clear(); lastCacheUpdateTime = 0;
        logger.debug("Passagierzahlen-Cache geleert.");
    }

    // --- Methoden zum Laden von Passagieren ---

    public List<Passenger> getAllPassengers() {
        List<Passenger> passengers = new ArrayList<>();
        Map<Long, List<Booking>> allBookings = null;
        List<Long> passengerIds = new ArrayList<>();

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SQL_GET_ALL_PASSENGERS)) {
            while (rs.next()) {
                Passenger passenger = extractPassengerFromResultSet(rs);
                passengers.add(passenger);
                passengerIds.add(passenger.getId());
            }
            logger.info("{} Passagiere initial aus DB geladen.", passengers.size());
            if (!passengerIds.isEmpty()) {
                allBookings = getBookingsForPassengers(passengerIds);
            }
        } catch (SQLException e) {
            logger.error("Fehler beim Laden der Passagiere: {}", e.getMessage(), e);
            return passengers;
        }

        if (allBookings != null) {
            for (Passenger p : passengers) {
                List<Booking> bookingsForP = allBookings.getOrDefault(p.getId(), new ArrayList<>());
                // KORREKTUR: Klassische for-Schleife statt forEach mit Lambda
                for (Booking b : bookingsForP) {
                    b.setPassenger(p); // Setze die korrekte Passagier-Referenz in jeder Buchung
                }
                p.setBookings(bookingsForP);
            }
            logger.debug("Buchungen für {} Passagiere zugewiesen.", passengers.size());
        }
        return passengers;
    }


    // --- Methoden zum Speichern und Löschen von Passagieren ---

    /**
     * Speichert einen Passagier in der Datenbank (neu oder Update).
     */
    public boolean savePassenger(Passenger passenger) {
        if (passenger == null) {
            logger.warn("savePassenger: Null-Passenger-Objekt übergeben.");
            return false;
        }

        logger.debug("Starte Speichervorgang für Passagier: {}", passenger.getFullName());
        logger.debug("Passnummer vor Validierung: '{}'", passenger.getPassportNumber());

        String normalizedPassportNumber;
        try {
            if (passenger.getPassportNumber() == null || passenger.getPassportNumber().trim().isEmpty()) {
                logger.error("Passnummer darf nicht leer sein für Passagier: {} {}", passenger.getFirstName(), passenger.getLastName());
                throw new IllegalArgumentException("Passnummer darf nicht leer sein.");
            }
            normalizedPassportNumber = ValidationUtils.validatePassportNumber(passenger.getPassportNumber());
            passenger.setPassportNumber(normalizedPassportNumber);
            logger.debug("Passnummer für Speichern validiert/normalisiert: '{}'", normalizedPassportNumber);
            isPassportNumberUnique(normalizedPassportNumber, passenger.getId());
        } catch (RuntimeException e) { // Fängt IllegalArgumentException und RuntimeException von isPassportNumberUnique
            logger.error("Validierungsfehler vor dem Speichern für Passagier '{}': {}", passenger.getFullName(), e.getMessage());
            return false; // Frühzeitiger Ausstieg bei Validierungsfehler
        }

        Connection conn = null;
        boolean autoCommitOriginal = false;
        boolean success = false;

        try {
            conn = getConnection();
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);
            logger.debug("Transaktion für savePassenger (Passagier: '{}', ID: {}) gestartet.", passenger.getFullName(), passenger.getId());

            Long nationalityId = getNationalityId(conn, passenger.getNationality()); // Kann Exceptions werfen
            logger.debug("Ermittelte nationality_id: {} für Nationalität: {}", nationalityId, passenger.getNationality());

            boolean passengerSaved;
            if (passenger.getId() == null) {
                logger.info("Neuer Passagier wird eingefügt: {}", passenger.getFullName());
                passengerSaved = insertPassengerInternal(conn, passenger, nationalityId, normalizedPassportNumber);
                logger.debug("insertPassengerInternal Ergebnis: {}", passengerSaved);
            } else {
                logger.info("Bestehender Passagier (ID={}) wird aktualisiert: {}", passenger.getId(), passenger.getFullName());
                passengerSaved = updatePassengerInternal(conn, passenger, nationalityId, normalizedPassportNumber);
                logger.debug("updatePassengerInternal Ergebnis: {}", passengerSaved);
            }

            boolean bookingsSaved = false; // Standardmäßig als fehlgeschlagen annehmen
            if (passengerSaved && passenger.getId() != null) {
                logger.debug("Speichere Buchungen für Passagier ID {}", passenger.getId());
                bookingsSaved = saveBookingsForPassengerInternal(conn, passenger);
                logger.debug("saveBookingsForPassengerInternal Ergebnis: {}", bookingsSaved);
            } else if (!passengerSaved) {
                logger.error("Passagier konnte nicht gespeichert werden, Buchungen werden übersprungen. Rollback wird durchgeführt.");
            } else {
                logger.error("Passagier-ID ist nach dem Einfügen immer noch null. Rollback wird durchgeführt.");
                passengerSaved = false;
            }

            if (passengerSaved && bookingsSaved) {
                conn.commit();
                success = true;
                logger.info("ERFOLG: Passagier '{}' (ID: {}) und Buchungen gespeichert, Transaktion committed.",
                        passenger.getFullName(), passenger.getId());
                clearPassengerCountCache();
            } else {
                logger.error("FEHLER: Speichern fehlgeschlagen für '{}' - passengerSaved={}, bookingsSaved={}",
                        passenger.getFullName(), passengerSaved, false);
                if (!conn.getAutoCommit()) {
                    conn.rollback();
                    logger.debug("Transaktion zurückgerollt");
                }
                else {
                    logger.warn("Rollback nicht durchgeführt, AutoCommit war bereits an.");
                }
            }

        } catch (SQLException e) {
            logSQLError(e, "savePassenger SQL", passenger); // Mehr Kontext
            if (conn != null) {
                try {
                    if (!conn.getAutoCommit()) conn.rollback();
                    logger.info("Transaktion nach SQL-Fehler zurückgerollt.");
                } catch (SQLException rEx) {
                    logger.error("Rollback nach SQL-Fehler fehlgeschlagen!", rEx);
                }
            }
        } catch (IllegalArgumentException e) { // Fängt Fehler von getNationalityId
            logger.error("FEHLER Validierung (z.B. Nationalität) beim Speichern von '{}': {}", passenger.getFullName(), e.getMessage());
            if (conn != null) {
                try {
                    if (!conn.getAutoCommit()) conn.rollback();
                    logger.info("Transaktion nach Validierungsfehler zurückgerollt.");
                } catch (SQLException rEx) {
                    logger.error("Rollback nach Validierungsfehler fehlgeschlagen!", rEx);
                }
            }
            success = false;
        } catch (Exception e) { // Fängt alle anderen Fehler
            logger.error("FEHLER Unerwartet beim Speichern von '{}': {}", passenger.getFullName(), e.getMessage(), e);
            if (conn != null) {
                try {
                    if (!conn.getAutoCommit()) conn.rollback();
                    logger.info("Transaktion nach unerwartetem Fehler zurückgerollt.");
                } catch (SQLException rEx) {
                    logger.error("Rollback nach unerwartetem Fehler fehlgeschlagen!", rEx);
                }
            }
            success = false;
        } finally {
            closeConnection(conn, autoCommitOriginal);
        }

        // Füge Logging hinzu, um den Rückgabewert zu sehen
        logger.info("savePassenger für '{}' abgeschlossen mit Ergebnis: {}", passenger.getFullName(), success);
        return success;
    }


    private boolean insertPassengerInternal(Connection conn, Passenger passenger, Long nationalityId, String normalizedPassportNumber) throws SQLException {
        logger.debug("insertPassengerInternal: Füge Passagier '{}' ein.", passenger.getFullName());
        try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_PASSENGER, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, passenger.getLastName());
            stmt.setString(2, passenger.getFirstName());
            stmt.setString(3, passenger.getGender().name());
            stmt.setDate(4, Date.valueOf(passenger.getDateOfBirth()));
            stmt.setLong(5, nationalityId);
            stmt.setString(6, normalizedPassportNumber);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        long newId = keys.getLong(1); passenger.setId(newId);
                        logger.info("INSERT ERFOLG: Neuer Passagier '{}' eingefügt mit ID={}.", passenger.getFullName(), newId); return true;
                    } else {
                        logger.error("INSERT FEHLER: Keine generierte ID erhalten für '{}'.", passenger.getFullName()); return false;
                    }
                }
            } else {
                logger.error("INSERT FEHLER: Keine Zeilen eingefügt für Passagier '{}'.", passenger.getFullName()); return false;
            }
        }
    }

    private boolean updatePassengerInternal(Connection conn, Passenger passenger, Long nationalityId, String normalizedPassportNumber) throws SQLException {
        if (passenger.getId() == null) { logger.error("UPDATE FEHLER: Passagier hat keine ID für Update."); return false; }
        logger.debug("updatePassengerInternal: Aktualisiere Passagier ID {}.", passenger.getId());
        try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_PASSENGER)) {
            stmt.setString(1, passenger.getLastName()); stmt.setString(2, passenger.getFirstName());
            stmt.setString(3, passenger.getGender().name()); stmt.setDate(4, Date.valueOf(passenger.getDateOfBirth()));
            stmt.setLong(5, nationalityId); stmt.setString(6, normalizedPassportNumber);
            stmt.setLong(7, passenger.getId());
            int result = stmt.executeUpdate();
            if (result > 0) {
                logger.info("UPDATE ERFOLG: Passagier ID {} erfolgreich aktualisiert.", passenger.getId()); return true;
            } else {
                logger.warn("UPDATE WARNUNG: Keine Zeilen aktualisiert. Passagier mit ID {} nicht gefunden?", passenger.getId()); return false;
            }
        }
    }

    private boolean saveBookingsForPassengerInternal(Connection conn, Passenger passenger) throws SQLException {
        if (passenger.getId() == null) { logger.error("saveBookingsForPassengerInternal: Passagier hat keine ID."); return false; }
        Long passengerId = passenger.getId();
        logger.debug("saveBookingsForPassengerInternal: Starte Abgleich für Passagier ID {}", passengerId);
        Map<Long, Booking> existingDbBookingsMap = new HashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_EXISTING_BOOKINGS_FOR_PAX)) {
            stmt.setLong(1, passengerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Long bookingId = rs.getLong("id"); Long flightId = rs.getLong("flight_id"); String seatClassStr = rs.getString("seat_class");
                    try {
                        SeatClass seatClass = SeatClass.valueOf(seatClassStr); Booking dbBooking = new Booking();
                        dbBooking.setId(bookingId); dbBooking.setPassenger(passenger);
                        Flight minimalFlight = new Flight(); minimalFlight.setId(flightId); dbBooking.setFlight(minimalFlight);
                        dbBooking.setSeatClass(seatClass); existingDbBookingsMap.put(bookingId, dbBooking);
                    } catch (IllegalArgumentException e) { logger.error("Ungültige SeatClass '{}' in DB für Buchung ID {} bei Passagier {}. Überspringe.", seatClassStr, bookingId, passengerId, e); }
                }
            }
        }
        logger.debug("Found {} existing bookings in DB for passenger {}", existingDbBookingsMap.size(), passengerId);
        List<Booking> bookingsToInsert = new ArrayList<>(); List<Booking> bookingsToUpdate = new ArrayList<>();
        List<Long> dbBookingIdsToDelete = new ArrayList<>(existingDbBookingsMap.keySet());
        for (Booking uiBooking : passenger.getBookings()) {
            if (uiBooking.getFlight() == null || uiBooking.getFlight().getId() == null || uiBooking.getSeatClass() == null) { logger.warn("Überspringe ungültige Buchung (Flug/Klasse fehlt) in UI-Liste für Passagier {}", passengerId); continue; }
            uiBooking.setPassenger(passenger);
            if (uiBooking.getId() == null) { bookingsToInsert.add(uiBooking); logger.trace("Booking for flight {} marked for INSERT.", uiBooking.getFlight().getId()); }
            else {
                dbBookingIdsToDelete.remove(uiBooking.getId()); Booking existingDbBooking = existingDbBookingsMap.get(uiBooking.getId());
                if (existingDbBooking == null) { logger.warn("UI-Buchung hat ID {}, aber nicht in DB gefunden für Passagier {}. Behandle als INSERT.", uiBooking.getId(), passengerId); uiBooking.setId(null); bookingsToInsert.add(uiBooking); }
                else { if (!uiBooking.getFlight().getId().equals(existingDbBooking.getFlight().getId()) || uiBooking.getSeatClass() != existingDbBooking.getSeatClass()) { bookingsToUpdate.add(uiBooking); logger.trace("Booking ID {} marked for UPDATE.", uiBooking.getId()); }
                else { logger.trace("Booking ID {} is unchanged.", uiBooking.getId()); }
                }
            }
        }
        logger.debug("Processing bookings for passenger {}: {} inserts, {} updates, {} deletes.", passengerId, bookingsToInsert.size(), bookingsToUpdate.size(), dbBookingIdsToDelete.size());
        if (!dbBookingIdsToDelete.isEmpty()) {
            logger.info("Lösche {} Buchungen für Passagier {}", dbBookingIdsToDelete.size(), passengerId);
            try (PreparedStatement deleteStmt = conn.prepareStatement(SQL_DELETE_BOOKING)) {
                for (Long idToDelete : dbBookingIdsToDelete) {
                    Booking bookingToDelete = existingDbBookingsMap.get(idToDelete);
                    Flight flightToUpdate = flightService.getFlightById(bookingToDelete.getFlight().getId());
                    if (flightToUpdate != null) { updateFlightPassengerCountInternal(conn, flightToUpdate, bookingToDelete.getSeatClass(), -1); }
                    else { logger.warn("Flug ID {} für zu löschende Buchung ID {} nicht gefunden. Pax-Zahl nicht angepasst.", bookingToDelete.getFlight().getId(), idToDelete); }
                    deleteStmt.setLong(1, idToDelete); deleteStmt.addBatch();
                }
                int[] deleteResults = deleteStmt.executeBatch(); logger.debug("Delete batch executed with {} results.", deleteResults.length);
            }
        }
        if (!bookingsToUpdate.isEmpty()) {
            logger.info("Aktualisiere {} Buchungen für Passagier {}", bookingsToUpdate.size(), passengerId);
            try (PreparedStatement updateStmt = conn.prepareStatement(SQL_UPDATE_BOOKING)) {
                for (Booking bookingToUpdate : bookingsToUpdate) {
                    Booking oldBooking = existingDbBookingsMap.get(bookingToUpdate.getId());
                    Flight oldFlight = flightService.getFlightById(oldBooking.getFlight().getId());
                    Flight newFlight = flightService.getFlightById(bookingToUpdate.getFlight().getId());
                    if (oldFlight != null) { updateFlightPassengerCountInternal(conn, oldFlight, oldBooking.getSeatClass(), -1); }
                    else { logger.warn("Alter Flug ID {} für Buchungsupdate ID {} nicht gefunden. Alte Pax-Zahl nicht angepasst.", oldBooking.getFlight().getId(), bookingToUpdate.getId()); }
                    if (newFlight != null) { updateFlightPassengerCountInternal(conn, newFlight, bookingToUpdate.getSeatClass(), 1); }
                    else { logger.error("Neuer Flug ID {} für Buchungsupdate ID {} nicht gefunden. Neue Pax-Zahl nicht angepasst!", bookingToUpdate.getFlight().getId(), bookingToUpdate.getId()); continue; }
                    updateStmt.setLong(1, passengerId); updateStmt.setLong(2, bookingToUpdate.getFlight().getId());
                    updateStmt.setString(3, bookingToUpdate.getSeatClass().name()); updateStmt.setLong(4, bookingToUpdate.getId()); updateStmt.addBatch();
                }
                int[] updateResults = updateStmt.executeBatch(); logger.debug("Update batch executed with {} results.", updateResults.length);
            }
        }
        if (!bookingsToInsert.isEmpty()) {
            logger.info("Füge {} neue Buchungen für Passagier {} ein.", bookingsToInsert.size(), passengerId);
            try (PreparedStatement insertStmt = conn.prepareStatement(SQL_INSERT_BOOKING, Statement.RETURN_GENERATED_KEYS)) {
                for (Booking bookingToInsert : bookingsToInsert) {
                    LocalDateTime now = LocalDateTime.now(); bookingToInsert.setBookingDate(now);
                    Flight flightToBook = flightService.getFlightById(bookingToInsert.getFlight().getId());
                    if (flightToBook == null) { logger.error("Flug ID {} für neue Buchung nicht gefunden! Überspringe Insert.", bookingToInsert.getFlight().getId()); continue; }
                    updateFlightPassengerCountInternal(conn, flightToBook, bookingToInsert.getSeatClass(), 1);
                    insertStmt.setLong(1, passengerId); insertStmt.setLong(2, bookingToInsert.getFlight().getId());
                    insertStmt.setString(3, bookingToInsert.getSeatClass().name()); insertStmt.setTimestamp(4, Timestamp.valueOf(now)); insertStmt.addBatch();
                }
                int[] insertResults = insertStmt.executeBatch(); logger.debug("Insert batch executed with {} results.", insertResults.length);
                try (ResultSet generatedKeys = insertStmt.getGeneratedKeys()) {
                    int index = 0;
                    for (Booking insertedBooking : bookingsToInsert) {
                        if (flightService.getFlightById(insertedBooking.getFlight().getId()) != null) { // Check if flight was found before batch add
                            if (generatedKeys.next()) {
                                insertedBooking.setId(generatedKeys.getLong(1));
                                logger.debug("Neue Buchung ID {} für Passagier {} gesetzt.", insertedBooking.getId(), passengerId); index++;
                            }
                        }
                    }
                    long expectedInserts = bookingsToInsert.stream().filter(b -> flightService.getFlightById(b.getFlight().getId()) != null).count();
                    if (index != expectedInserts) { logger.warn("Anzahl generierter Keys ({}) stimmt nicht mit Anzahl erwarteter neuer Buchungen ({}) überein.", index, expectedInserts); }
                }
            }
        }
        logger.debug("saveBookingsForPassengerInternal: Abgleich für Passagier ID {} abgeschlossen.", passengerId);
        return true;
    }

    public boolean deletePassenger(Passenger passenger) {
        if (passenger == null || passenger.getId() == null) { logger.warn("Versuch, einen ungültigen oder nicht gespeicherten Passagier zu löschen."); return false; }
        Long passengerId = passenger.getId();
        logger.info("Versuche Passagier '{}' (ID: {}) zu löschen.", passenger.getFullName(), passengerId);
        Connection conn = null; boolean autoCommitOriginal = false; boolean success = false;
        try {
            conn = getConnection(); autoCommitOriginal = conn.getAutoCommit(); conn.setAutoCommit(false);
            logger.debug("Transaktion für deletePassenger (ID: {}) gestartet.", passengerId);
            List<Booking> bookingsToDelete = getBookingsForPassengerInternal(conn, passengerId);
            logger.debug("Passe Passagierzahlen für {} Buchungen an (Passagier ID {}).", bookingsToDelete.size(), passengerId);
            for (Booking booking : bookingsToDelete) {
                if (booking.getFlight() != null && booking.getSeatClass() != null) {
                    Flight flight = flightService.getFlightById(booking.getFlight().getId());
                    if (flight != null) { updateFlightPassengerCountInternal(conn, flight, booking.getSeatClass(), -1); }
                    else { logger.warn("Flug ID {} für Buchung ID {} beim Löschen von Passagier {} nicht gefunden. Pax-Zahl nicht angepasst.", booking.getFlight().getId(), booking.getId(), passengerId); }
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_PASSENGER)) {
                stmt.setLong(1, passengerId); int result = stmt.executeUpdate();
                if (result > 0) {
                    conn.commit(); success = true; logger.info("Passagier gelöscht: {} (ID: {})", passenger.getFullName(), passengerId);
                    clearPassengerCountCache();
                } else {
                    logger.warn("Löschen des Passagiers ID {} hat keine Zeilen beeinflusst (nicht gefunden?). Rollback.", passengerId); conn.rollback();
                }
            }
        } catch (SQLException e) {
            logger.error("SQL-Fehler beim Löschen des Passagiers {} (ID: {}): {}", passenger.getFullName(), passengerId, e.getMessage(), e);
            if (conn != null) { try { conn.rollback(); logger.info("Rollback nach SQL-Fehler durchgeführt."); } catch (SQLException ex) { logger.error("Rollback nach SQL-Fehler fehlgeschlagen: {}", ex.getMessage(), ex); } }
        } catch (Exception e) {
            logger.error("Unerwarteter Fehler beim Löschen des Passagiers {} (ID: {}): {}", passenger.getFullName(), passengerId, e.getMessage(), e);
            if (conn != null) { try { conn.rollback(); logger.info("Rollback nach unerwartetem Fehler durchgeführt."); } catch (SQLException ex) { logger.error("Rollback nach Fehler fehlgeschlagen: {}", ex.getMessage(), ex); } }
        } finally { closeConnection(conn, autoCommitOriginal); }
        return success;
    }

    private List<Booking> getBookingsForPassengerInternal(Connection conn, Long passengerId) throws SQLException {
        List<Booking> bookings = new ArrayList<>();
        String sql = "SELECT b.id, b.flight_id, b.seat_class FROM passenger_bookings b WHERE b.passenger_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, passengerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Long bookingId = rs.getLong("id"); Long flightId = rs.getLong("flight_id"); String seatClassStr = rs.getString("seat_class");
                    try {
                        SeatClass seatClass = SeatClass.valueOf(seatClassStr); Booking b = new Booking();
                        b.setId(bookingId); Flight f = new Flight(); f.setId(flightId); b.setFlight(f);
                        b.setSeatClass(seatClass); bookings.add(b);
                    } catch (IllegalArgumentException e) { logger.warn("Ungültige SeatClass {} für Buchung {} bei interner Abfrage.", seatClassStr, bookingId); }
                }
            }
        }
        return bookings;
    }

    // --- Hilfsmethoden ---

    private Passenger extractPassengerFromResultSet(ResultSet rs) throws SQLException {
        Passenger passenger = new Passenger();
        passenger.setId(rs.getLong("id"));
        passenger.setLastName(rs.getString("last_name"));
        passenger.setFirstName(rs.getString("first_name"));
        try { passenger.setGender(Gender.valueOf(rs.getString("gender"))); }
        catch (IllegalArgumentException e) { logger.error("Ungültiger Gender-Wert '{}' in DB für Passagier ID {}. Setze auf OTHER.", rs.getString("gender"), rs.getLong("id")); passenger.setGender(Gender.OTHER); }
        Date dobDate = rs.getDate("date_of_birth");
        if (dobDate != null) { passenger.setDateOfBirth(dobDate.toLocalDate()); }
        else { logger.warn("Geburtsdatum ist NULL in DB für Passagier ID {}.", rs.getLong("id")); }
        passenger.setNationality(rs.getString("nationality"));
        passenger.setPassportNumber(rs.getString("passport_number"));
        passenger.setBookings(new ArrayList<>());
        return passenger;
    }

    private Connection getConnection() throws SQLException {
        Connection conn = DatabaseConnectionManager.getInstance().getConnection();
        if (conn == null) { logger.error("Konnte keine Datenbankverbindung erhalten!"); throw new SQLException("Datenbankverbindung ist null."); }
        return conn;
    }

    private void closeConnection(Connection conn, boolean autoCommitOriginal) {
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    try { conn.setAutoCommit(autoCommitOriginal); }
                    catch (SQLException e) { logger.error("Fehler beim Zurücksetzen von AutoCommit: {}", e.getMessage(), e); }
                    conn.close(); logger.trace("Datenbankverbindung geschlossen.");
                }
            } catch (SQLException e) { logger.error("Fehler beim Schließen der Datenbankverbindung: {}", e.getMessage(), e); }
        }
    }

    private void updateFlightPassengerCountInternal(Connection conn, Flight flight, SeatClass seatClass, int delta) throws SQLException {
        if (flight == null || flight.getId() == null || seatClass == null) { logger.warn("Ungültige Daten für Passagierzahl-Update (Flug oder Klasse fehlt)."); return; }
        Long flightId = flight.getId();
        logger.debug("updateFlightPassengerCountInternal: Passe Pax für Flug ID {}, Klasse {}, Delta {} an.", flightId, seatClass, delta);
        int currentPaxEconomy; int currentPaxBusiness; int currentPaxFirst;
        try (PreparedStatement selectStmt = conn.prepareStatement(SQL_GET_FLIGHT_PAX_FOR_UPDATE)) {
            selectStmt.setLong(1, flightId);
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (rs.next()) {
                    currentPaxEconomy = rs.getInt("pax_economy"); currentPaxBusiness = rs.getInt("pax_business"); currentPaxFirst = rs.getInt("pax_first");
                    logger.trace("Aktuelle Pax für Flug {}: E={}, B={}, F={}", flightId, currentPaxEconomy, currentPaxBusiness, currentPaxFirst);
                } else { logger.error("Flug mit ID {} nicht gefunden für Passagierzahl-Update!", flightId); throw new SQLException("Flug mit ID " + flightId + " nicht gefunden."); }
            }
        }
        int newPaxEconomy = currentPaxEconomy; int newPaxBusiness = currentPaxBusiness; int newPaxFirst = currentPaxFirst;
        switch (seatClass) {
            case ECONOMY -> newPaxEconomy = Math.max(0, currentPaxEconomy + delta);
            case BUSINESS -> newPaxBusiness = Math.max(0, currentPaxBusiness + delta);
            case FIRST_CLASS -> newPaxFirst = Math.max(0, currentPaxFirst + delta);
        }
        logger.trace("Neue berechnete Pax für Flug {}: E={}, B={}, F={}", flightId, newPaxEconomy, newPaxBusiness, newPaxFirst);
        try (PreparedStatement updateStmt = conn.prepareStatement(SQL_UPDATE_FLIGHT_PAX_COUNT)) {
            updateStmt.setInt(1, newPaxEconomy); updateStmt.setInt(2, newPaxBusiness);
            updateStmt.setInt(3, newPaxFirst); updateStmt.setLong(4, flightId);
            int result = updateStmt.executeUpdate();
            if (result > 0) { logger.debug("Passagierzahl für Flug ID {} erfolgreich aktualisiert.", flightId); }
            else { logger.warn("Update der Passagierzahl für Flug ID {} hat keine Zeilen beeinflusst (trotz FOR UPDATE).", flightId); }
        }
    }

    private void logAvailableNationalities(Connection conn) {
        try {
            int totalCount = 0;
            try (Statement countStmt = conn.createStatement(); ResultSet countRs = countStmt.executeQuery("SELECT COUNT(*) AS total FROM countries")) { if (countRs.next()) { totalCount = countRs.getInt("total"); } }
            logger.info("Diagnose: countries-Tabelle enthält {} Nationalitäten.", totalCount); if (totalCount == 0) return;
            StringBuilder sb = new StringBuilder("Diagnose: Stichprobe verfügbarer Nationalitäten: ");
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT nationality FROM countries ORDER BY nationality LIMIT 15")) {
                int count = 0; while (rs.next()) { if (count > 0) sb.append(", "); sb.append("'").append(rs.getString("nationality")).append("'"); count++; }
            }
            logger.info(sb.toString());
        } catch (SQLException e) { logger.warn("Diagnose: Fehler beim Auflisten der Nationalitäten: {}", e.getMessage()); }
    }

    private void logSQLError(SQLException e, String operation, Passenger passenger) {
        String passengerName = (passenger != null) ? passenger.getFullName() : "Unbekannt";
        String passportNum = (passenger != null && passenger.getPassportNumber() != null) ? passenger.getPassportNumber() : "N/A";
        String nationality = (passenger != null && passenger.getNationality() != null) ? passenger.getNationality() : "N/A";
        logger.error("SQL-FEHLER bei Operation '{}' für Passagier '{}' (Pass: {}, Nationalität: {}): {}", operation, passengerName, passportNum, nationality, e.getMessage());
        logger.error("  -> SQL-State: {}", e.getSQLState()); logger.error("  -> Fehlercode: {}", e.getErrorCode());
        if (e.getSQLState() != null) {
            if (e.getSQLState().startsWith("23")) { // Integritätsverletzung
                if (e.getMessage().toLowerCase().contains("foreign key constraint") && (e.getMessage().toLowerCase().contains("nationality_id") || e.getMessage().toLowerCase().contains("fk_passengers_countries"))) { logger.error("  -> Mögliche Ursache: Foreign Key Fehler - Nationalität '{}' konnte keiner gültigen ID zugeordnet werden.", nationality); }
                else if (e.getMessage().toLowerCase().contains("unique constraint") && (e.getMessage().toLowerCase().contains("passport_number") || e.getMessage().toLowerCase().contains("idx_passport_number"))) { logger.error("  -> Mögliche Ursache: Unique Key Fehler - Passnummer '{}' existiert bereits.", passportNum); }
                else { logger.error("  -> Mögliche Ursache: Andere Integritätsverletzung (Constraint Check, etc.)"); }
            } else if (e.getSQLState().startsWith("08")) { logger.error("  -> Mögliche Ursache: Datenbankverbindungsfehler."); }
            else if (e.getSQLState().startsWith("42")) { logger.error("  -> Mögliche Ursache: SQL-Syntaxfehler oder fehlende Berechtigungen."); }
        }
        if (e.getNextException() != null) { logger.error("  -> Verschachtelte Exception: ", e.getNextException()); }
    }


}
