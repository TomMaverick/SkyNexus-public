package skynexus.service;

import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.database.DatabaseConnectionManager;
import skynexus.enums.AircraftStatus;
import skynexus.enums.FlightStatus;
import skynexus.model.*;
import skynexus.util.FlightUtils;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors; // Import hinzugefügt, da er im Original benötigt wurde

/**
 * Service-Klasse für die Verwaltung von Flügen.
 * Bietet CRUD-Operationen für Flight-Objekte.
 */
public class FlightService {
    private static final Logger logger = LoggerFactory.getLogger(FlightService.class);
    private static final long CACHE_EXPIRY_MS = 60 * 1000; // 1 Minute
    private static FlightService instance;
    private final AirportService airportService;
    private final AircraftService aircraftService;
    private final RouteService routeService;
    // Caching für Flüge
    private final Map<Long, Flight> flightCache = new ConcurrentHashMap<>();
    // Cache für gefilterte Flugabfragen (z.B. nach Datum oder Route)
    private final Map<String, List<Flight>> filteredFlightCache = new ConcurrentHashMap<>();
    private List<Flight> allFlightsCache = null;
    private long lastCacheRefresh = 0;
    
    /**
     * Holt eine Datenbankverbindung vom DatabaseConnectionManager
     */
    private Connection getConnection() throws SQLException {
        return DatabaseConnectionManager.getInstance().getConnection();
    }

    /**
     * Privater Konstruktor für Singleton-Pattern
     */
    private FlightService() {
        this.airportService = AirportService.getInstance();
        this.aircraftService = AircraftService.getInstance();
        this.routeService = RouteService.getInstance();
    }

    /**
     * Gibt die Singleton-Instanz zurück
     */
    public static synchronized FlightService getInstance() {
        if (instance == null) {
            instance = new FlightService();
        }
        return instance;
    }

    /**
     * Lädt alle Flüge aus der Datenbank.
     * Verwendet Caching für verbesserte Performance.
     *
     * @param excludeCompleted Optional: Wenn true, werden abgeschlossene Flüge (COMPLETED) nicht zurückgegeben
     * @return Liste aller Flüge (gefiltert, falls angegeben)
     */
    public List<Flight> getAllFlights(boolean excludeCompleted) {
        long now = System.currentTimeMillis();

        // Prüfe, ob Cache gültig ist
        if (allFlightsCache != null && (now - lastCacheRefresh) < CACHE_EXPIRY_MS) {
            logger.debug("Verwende gecachte Flüge (Cache-Alter: {} ms)", now - lastCacheRefresh);

            // Wenn nötig, gefilterte Liste zurückgeben
            if (excludeCompleted) {
                return allFlightsCache.stream()
                        .filter(flight -> flight.getStatus() != FlightStatus.COMPLETED)
                        .collect(Collectors.toList()); // Korrigierter Import
            } else {
                return new ArrayList<>(allFlightsCache);
            }
        }

        logger.debug("Lade alle Flüge mit optimierter JOIN-Abfrage...");
        List<Flight> flights = getAllFlightsWithDependencies();

        // Cache aktualisieren
        allFlightsCache = new ArrayList<>(flights);
        flightCache.clear();

        for (Flight flight : flights) {
            flightCache.put(flight.getId(), flight);
        }

        lastCacheRefresh = now;
        logger.debug("Flüge in Cache aktualisiert: {} Flüge", flights.size());

        // Wenn nötig, gefilterte Liste zurückgeben
        if (excludeCompleted) {
            return flights.stream()
                    .filter(flight -> flight.getStatus() != FlightStatus.COMPLETED)
                    .collect(Collectors.toList()); // Korrigierter Import
        } else {
            return flights;
        }
    }

    /**
     * Überladene Methode für Abwärtskompatibilität, die alle Flüge zurückgibt.
     *
     * @return Liste aller Flüge, einschließlich abgeschlossener Flüge
     */
    public List<Flight> getAllFlights() {
        return getAllFlights(false);
    }

    /**
     * Gibt nur aktive (nicht abgeschlossene) Flüge zurück.
     * Nützlich für Benutzeroberflächen, die nur aktuelle Flüge anzeigen sollen.
     *
     * @return Liste aller aktiven Flüge (Status ≠ COMPLETED)
     */
    public List<Flight> getActiveFlights() {
        return getAllFlights(true);
    }

    /**
     * Aktualisiert automatisch die Status aller Flüge basierend auf der aktuellen Zeit
     * und den geplanten Flugzeiten.
     * Status-Regeln:
     * - SCHEDULED: Bis 30 Minuten vor Abflug
     * - BOARDING: 30 Minuten vor Abflug bis Abflug
     * - DEPARTED: Erste 10 Minuten nach Abflug
     * - FLYING: Hauptteil des Fluges
     * - LANDED: Letzte 10 Minuten vor Ankunft
     * - DEPLANING: 15 Minuten nach Landung
     * - DONE: Nach dem Deplaning
     */
    public void updateFlightStatuses() {
        List<Flight> flights = getAllFlights();
        LocalDateTime now = LocalDateTime.now();

        for (Flight flight : flights) {
            LocalDateTime departureDateTime = null;
            LocalDateTime arrivalDateTime = null;

            // Stelle departureDateTime und arrivalDateTime zusammen
            if (flight.getDepartureDate() != null && flight.getDepartureTime() != null) {
                departureDateTime = LocalDateTime.of(flight.getDepartureDate(), flight.getDepartureTime());
            }

            if (flight.getArrivalDate() != null && flight.getArrivalTime() != null) {
                arrivalDateTime = LocalDateTime.of(flight.getArrivalDate(), flight.getArrivalTime());
            }

            // Wenn keine Abflug- oder Ankunftszeit definiert ist, kann kein Status berechnet werden
            if (departureDateTime == null || arrivalDateTime == null) {
                continue;
            }

            // Neuen Status basierend auf der aktuellen Zeit bestimmen
            FlightStatus newStatus = calculateFlightStatus(now, departureDateTime, arrivalDateTime);

            // Wenn sich der Status geändert hat, aktualisieren
            if (newStatus != flight.getStatus()) {
                flight.setStatus(newStatus);
                updateFlightStatusInDatabase(flight.getId(), newStatus);

                // Auch den Flugzeugstatus aktualisieren, wenn nötig
                updateAircraftStatus(flight);

                logger.info("Flug {} Status aktualisiert auf {}", flight.getFlightNumber(), newStatus);
            }
        }
    }

    /**
     * Berechnet den Flugstatus basierend auf der aktuellen Zeit und den geplanten Flugzeiten
     */
    private FlightStatus calculateFlightStatus(LocalDateTime now, LocalDateTime departureDateTime, LocalDateTime arrivalDateTime) {
        // Zeitdifferenzen berechnen
        long minutesToDeparture = ChronoUnit.MINUTES.between(now, departureDateTime);
        long minutesFromDeparture = ChronoUnit.MINUTES.between(departureDateTime, now);
        long minutesToArrival = ChronoUnit.MINUTES.between(now, arrivalDateTime);
        long minutesFromArrival = ChronoUnit.MINUTES.between(arrivalDateTime, now);

        // Status bestimmen basierend auf den Zeitdifferenzen
        if (minutesFromArrival > 15) {
            return FlightStatus.COMPLETED; // Nach 15 Minuten nach Ankunft
        } else if (minutesFromArrival >= 0) {
            return FlightStatus.DEPLANING; // Bis 15 Minuten nach Ankunft
        } else if (minutesToArrival <= 10) {
            return FlightStatus.LANDED; // Letzte 10 Minuten vor Ankunft
        } else if (minutesFromDeparture > 10) {
            return FlightStatus.FLYING; // Hauptteil des Fluges
        } else if (minutesFromDeparture >= 0) {
            return FlightStatus.DEPARTED; // Erste 10 Minuten nach Abflug
        } else if (minutesToDeparture <= 30) {
            return FlightStatus.BOARDING; // 30 Minuten vor Abflug
        } else {
            return FlightStatus.SCHEDULED; // Standardstatus
        }
    }

    /**
     * Aktualisiert den Flugstatus in der Datenbank
     */
    private void updateFlightStatusInDatabase(Long flightId, FlightStatus status) {
        String sql = "UPDATE flights SET status = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status.name());
            stmt.setLong(2, flightId);

            int result = stmt.executeUpdate();
            if (result > 0) {
                logger.debug("Flugstatus in der Datenbank aktualisiert: {} für Flug ID {}", status, flightId);
            } else {
                logger.warn("Konnte Flugstatus für Flug ID {} nicht aktualisieren", flightId);
            }

        } catch (SQLException e) {
            logger.error("Fehler beim Aktualisieren des Flugstatus für ID {}: {}", flightId, e.getMessage());
        }
    }

    /**
     * Aktualisiert den Flugzeugstatus basierend auf dem Flugstatus
     */
    private void updateAircraftStatus(Flight flight) {
        if (flight.getAircraft() == null) {
            return;
        }

        Aircraft aircraft = flight.getAircraft();
        AircraftStatus newStatus;

        // Flugzeugstatus basierend auf dem Flugstatus bestimmen
        switch (flight.getStatus()) {
            case BOARDING:
            case DEPARTED:
            case FLYING:
            case LANDED:
            case DEPLANING:
                newStatus = AircraftStatus.FLYING;
                break;
            case SCHEDULED:
                newStatus = AircraftStatus.SCHEDULED;
                break;
            case COMPLETED:
            default:
                newStatus = AircraftStatus.AVAILABLE;
                break;
        }

        // Wenn sich der Flugzeugstatus geändert hat, aktualisieren
        if (newStatus != aircraft.getStatus()) {
            aircraft.setStatus(newStatus);
            updateAircraftStatusInDatabase(aircraft.getId(), newStatus);
            logger.info("Flugzeug {} Status aktualisiert auf {}", aircraft.getRegistrationNo(), newStatus);
        }
    }

    /**
     * Aktualisiert den Flugzeugstatus in der Datenbank
     */
    private void updateAircraftStatusInDatabase(Long aircraftId, AircraftStatus status) {
        String sql = "UPDATE aircraft SET status = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status.name());
            stmt.setLong(2, aircraftId);

            int result = stmt.executeUpdate();
            if (result > 0) {
                logger.debug("Flugzeugstatus in der Datenbank aktualisiert: {} für Flugzeug ID {}", status, aircraftId);
            } else {
                logger.warn("Konnte Flugzeugstatus für Flugzeug ID {} nicht aktualisieren", aircraftId);
            }

        } catch (SQLException e) {
            logger.error("Fehler beim Aktualisieren des Flugzeugstatus für ID {}: {}", aircraftId, e.getMessage());
        }
    }

    /**
     * Berechnet die Blockzeit eines Fluges (Boarding-Start bis Deboarding-Ende).
     * Diese Zeit wird verwendet, um zu prüfen, ob ein Flugzeug für einen bestimmten Zeitraum verfügbar ist.
     *
     * @param flight Der Flug, für den die Blockzeit berechnet werden soll
     * @return Ein Pair mit der Start- und Endzeit der Blockzeit
     */
    public Pair<LocalDateTime, LocalDateTime> calculateFlightBlockTime(Flight flight) {
        if (flight == null || flight.getDepartureDateTime() == null) {
            logger.warn("Kann Blockzeit nicht berechnen: Ungültiger Flug oder fehlende Abflugzeit");
            return null;
        }

        // Konstanten für zusätzliche Zeiten
        final int BOARDING_MINUTES = 30;  // Zeit für Boarding vor Abflug
        final int DEBOARDING_MINUTES = 20;  // Zeit für Deboarding nach Landung

        // Berechne Start der Blockzeit (Beginn des Boardings)
        LocalDateTime blockStart = flight.getDepartureDateTime().minusMinutes(BOARDING_MINUTES);

        // Berechne Ende der Blockzeit (Ende des Deboardings)
        LocalDateTime arrivalTime = flight.getArrivalDateTime();
        if (arrivalTime == null) {
            logger.warn("Kann Blockzeitende nicht berechnen: Fehlende Ankunftszeit für Flug {}", flight.getFlightNumber());
            return null;
        }

        LocalDateTime blockEnd = arrivalTime.plusMinutes(DEBOARDING_MINUTES);

        return new Pair<>(blockStart, blockEnd);
    }

    /**
     * Berechnet die Blockzeit für einen Flug basierend auf Abflugzeit und Route.
     *
     * @param departureDateTime Die Abflugzeit
     * @param route Die Flugroute
     * @return Ein Pair mit der Start- und Endzeit der Blockzeit oder null bei Fehler
     */
    public Pair<LocalDateTime, LocalDateTime> calculateBlockTimeForRoute(LocalDateTime departureDateTime, Route route) {
        if (departureDateTime == null || route == null) {
            logger.warn("Kann Blockzeit nicht berechnen: Ungültige Abflugzeit oder Route");
            return null;
        }

        // Konstanten für zusätzliche Zeiten
        final int BOARDING_MINUTES = 30;  // Zeit für Boarding vor Abflug
        final int DEBOARDING_MINUTES = 20;  // Zeit für Deboarding nach Landung

        // Berechne Start der Blockzeit (Beginn des Boardings)
        LocalDateTime blockStart = departureDateTime.minusMinutes(BOARDING_MINUTES);

        // Berechne Ende der Blockzeit (Ende des Deboardings)
        try {
            int flightMinutes = route.getFlightTimeMinutes();
            if (flightMinutes <= 0) {
                logger.warn("Ungültige Flugzeit in Route: {} Minuten", flightMinutes);
                return null;
            }

            // Berechne Ankunftszeit
            Object[] arrivalDateTime = FlightUtils.calculateArrivalTime(
                    departureDateTime.toLocalDate(), departureDateTime.toLocalTime(), flightMinutes);

            if (arrivalDateTime != null && arrivalDateTime.length > 1 &&
                    arrivalDateTime[0] instanceof LocalDate && arrivalDateTime[1] instanceof LocalTime) {
                LocalDateTime arrivalTime = LocalDateTime.of(
                        (LocalDate) arrivalDateTime[0], (LocalTime) arrivalDateTime[1]);

                LocalDateTime blockEnd = arrivalTime.plusMinutes(DEBOARDING_MINUTES);
                return new Pair<>(blockStart, blockEnd);
            } else {
                logger.warn("Fehler bei Berechnung der Ankunftszeit für Route");
                return null;
            }
        } catch (Exception e) {
            logger.error("Fehler bei Berechnung der Blockzeit: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Findet alle Flüge für ein bestimmtes Flugzeug in einem Zeitraum.
     *
     * @param aircraft Das Flugzeug
     * @param startTime Startzeit des Zeitraums
     * @param endTime Endzeit des Zeitraums
     * @param excludeFlightId Optional: ID eines Fluges, der von der Suche ausgeschlossen werden soll (z.B. bei Bearbeitung)
     * @return Liste der Flüge in diesem Zeitraum
     */
    public List<Flight> findFlightsByAircraftAndTimeRange(Aircraft aircraft, LocalDateTime startTime,
                                                          LocalDateTime endTime, Long excludeFlightId) {
        if (aircraft == null || startTime == null || endTime == null) {
            logger.warn("Ungültige Parameter für Flugsuche nach Zeitraum");
            return new ArrayList<>();
        }

        List<Flight> result = new ArrayList<>();

        // Hole alle Flüge für dieses Flugzeug
        List<Flight> allFlights = getAllFlights();

        // Filtere nach Flugzeug und Zeitraum
        for (Flight flight : allFlights) {
            if (flight.getAircraft() == null || !flight.getAircraft().getId().equals(aircraft.getId())) {
                continue;
            }

            // Ausschließen des bearbeiteten Fluges, falls eine ID angegeben wurde
            if (flight.getId() != null && flight.getId().equals(excludeFlightId)) {
                continue;
            }

            // Berechne Blockzeit des Fluges
            Pair<LocalDateTime, LocalDateTime> blockTime = calculateFlightBlockTime(flight);
            if (blockTime == null) {
                continue;
            }

            LocalDateTime flightStart = blockTime.getKey();
            LocalDateTime flightEnd = blockTime.getValue();

            // Prüfe auf Überschneidung
            // (Start1 <= Ende2) UND (Ende1 >= Start2)
            if (flightStart.isBefore(endTime) || flightStart.isEqual(endTime)) {
                if (flightEnd.isAfter(startTime) || flightEnd.isEqual(startTime)) {
                    result.add(flight);
                }
            }
        }

        return result;
    }

    /**
     * Prüft, ob ein Flugzeug für einen bestimmten Zeitraum verfügbar ist.
     *
     * @param aircraft Das zu prüfende Flugzeug
     * @param startTime Startzeit des Zeitraums
     * @param endTime Endzeit des Zeitraums
     * @param excludeFlightId Optional: ID eines Fluges, der bei der Prüfung ausgeschlossen werden soll
     * @return true wenn verfügbar, false wenn nicht verfügbar
     */
    public boolean isAircraftAvailableForTimeRange(Aircraft aircraft, LocalDateTime startTime,
                                                   LocalDateTime endTime, Long excludeFlightId) {
        if (aircraft == null || startTime == null || endTime == null) {
            logger.warn("Ungültige Parameter für Verfügbarkeitsprüfung");
            return false;
        }

        // Prüfe, ob das Flugzeug grundsätzlich verfügbar ist (status)
        // Ignoriere den Status, wenn der Flug bearbeitet wird (excludeFlightId != null)
        if (excludeFlightId == null && aircraft.getStatus() != AircraftStatus.AVAILABLE) {
            logger.debug("Flugzeug {} ist nicht verfügbar (Status: {})",
                    aircraft.getRegistrationNo(), aircraft.getStatus());
            return false;
        }

        // Prüfe, ob es Flüge mit diesem Flugzeug im angegebenen Zeitraum gibt
        List<Flight> overlappingFlights = findFlightsByAircraftAndTimeRange(aircraft, startTime, endTime, excludeFlightId);

        boolean isAvailable = overlappingFlights.isEmpty();

        if (!isAvailable) {
            logger.debug("Flugzeug {} ist nicht verfügbar im Zeitraum {} bis {} - {} überlappende Flüge gefunden",
                    aircraft.getRegistrationNo(), startTime, endTime, overlappingFlights.size());
        }

        return isAvailable;
    }

    /**
     * Lädt alle Abflüge für einen bestimmten Flughafen und ein bestimmtes Datum
     * mit optionaler Limitierung der Anzahl.
     * Verwendet Caching für verbesserte Performance.
     * Angepasst für Single-Airline-Architektur und normalisierte Datenbankstruktur
     *
     * @param airportId Die ID des Flughafens
     * @param date      Das Datum der Abflüge
     * @param limit     Maximale Anzahl an zurückgegebenen Flügen (optional)
     * @return Liste der Abflüge vom angegebenen Flughafen am angegebenen Datum
     */
    public List<Flight> getDeparturesForAirport(Long airportId, LocalDate date, Integer limit) {
        if (airportId == null || date == null) {
            return new ArrayList<>();
        }

        // Cache-Schlüssel erstellen
        String cacheKey = "dep_" + airportId + "_" + date + "_" + (limit != null ? limit : "all");

        // Prüfe, ob Cache gültig ist
        if (filteredFlightCache.containsKey(cacheKey) && (System.currentTimeMillis() - lastCacheRefresh) < CACHE_EXPIRY_MS) {
            logger.debug("Verwende gecachte Abflüge für Flughafen {} am {}", airportId, date);
            return new ArrayList<>(filteredFlightCache.get(cacheKey));
        }

        List<Flight> departures = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // SQL-Abfrage mit JOINs und direktem Filter für Flughafen und Datum
        StringBuilder sql = new StringBuilder(
                "SELECT f.id, f.flight_number, f.route_id, f.departure_airport_id, f.departure_datetime, " +
                        "f.arrival_airport_id, f.aircraft_id, " +
                        "f.price_economy, f.price_business, f.price_first, f.status, " +
                        "r.distance_km, r.flight_time_minutes, f.pax_economy, f.pax_business, f.pax_first, " +
                        // Abflughafen-Felder
                        "dep.id as dep_id, dep.name as dep_name, dep.icao_code as dep_icao, " +
                        "dep.city as dep_city, dc.country as dep_country, " +
                        "dep.latitude as dep_lat, dep.longitude as dep_lon, " +
                        // Zielflughafen-Felder
                        "arr.id as arr_id, arr.name as arr_name, arr.icao_code as arr_icao, " +
                        "arr.city as arr_city, ac.country as arr_country, " +
                        "arr.latitude as arr_lat, arr.longitude as arr_lon, " +
                        // Flugzeug-Felder
                        "a.id as aircraft_id, a.registration_no, a.type_id, a.build_date, a.status as ac_status, " +
                        // Flugzeugtyp-Felder
                        "at.id as act_id, at.manufacturer_id, m.name as manufacturer, at.model, at.pax_capacity, at.cargo_capacity, " +
                        "at.max_range_km, at.speed_kmh " +
                        "FROM flights f " +
                        "JOIN routes r ON f.route_id = r.id " +
                        "JOIN airports dep ON f.departure_airport_id = dep.id " +
                        "JOIN countries dc ON dep.country_id = dc.id " +
                        "JOIN airports arr ON f.arrival_airport_id = arr.id " +
                        "JOIN countries ac ON arr.country_id = ac.id " +
                        "JOIN aircraft a ON f.aircraft_id = a.id " +
                        "JOIN aircraft_types at ON a.type_id = at.id " +
                        "JOIN manufacturers m ON at.manufacturer_id = m.id " +
                        "WHERE f.departure_airport_id = ? AND DATE(f.departure_datetime) = ? " +
                        // Sortiere zuerst zukünftige Flüge (nach aktueller Zeit), dann vergangene
                        "ORDER BY CASE WHEN f.departure_datetime >= ? THEN 0 ELSE 1 END, " +
                        "f.departure_datetime");

        // Limit hinzufügen, falls angegeben
        if (limit != null && limit > 0) {
            sql.append(" LIMIT ?");
        }

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            // Parameter setzen
            stmt.setLong(1, airportId);
            stmt.setDate(2, Date.valueOf(date));
            stmt.setTimestamp(3, Timestamp.valueOf(now)); // Flüge ab jetzt

            if (limit != null && limit > 0) {
                stmt.setInt(4, limit);
            }

            // Debug-Ausgabe der SQL-Abfrage
            logger.debug("Starte SQL-Abfrage für Abflüge: Airport={}, Datum={}, nun={}",
                    airportId, date, now);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    departures.add(extractFlightFromJoinedResultSet(rs));
                }
            }
            logger.debug("{} Abflüge für Flughafen ID {} am {} geladen",
                    departures.size(), airportId, date);

            // Im Cache speichern
            filteredFlightCache.put(cacheKey, new ArrayList<>(departures));

        } catch (SQLException e) {
            logger.error("Fehler beim Laden der Abflüge für Flughafen ID {} am {}: {}",
                    airportId, date, e.getMessage());
        }

        return departures;
    }

    /**
     * Lädt alle Ankünfte für einen bestimmten Flughafen und ein bestimmtes Datum
     * mit optionaler Limitierung der Anzahl.
     * Verwendet Caching für verbesserte Performance.
     * Angepasst für normalisierte Datenbankstruktur mit departure_datetime und
     * countries-Tabelle.
     *
     * @param airportId Die ID des Flughafens
     * @param date      Das Datum der Ankünfte
     * @param limit     Maximale Anzahl an zurückgegebenen Flügen (optional)
     * @return Liste der Ankünfte am angegebenen Flughafen am angegebenen Datum
     */
    public List<Flight> getArrivalsForAirport(Long airportId, LocalDate date, Integer limit) {
        if (airportId == null || date == null) {
            return new ArrayList<>();
        }

        // Cache-Schlüssel erstellen
        String cacheKey = "arr_" + airportId + "_" + date + "_" + (limit != null ? limit : "all");

        // Prüfe, ob Cache gültig ist
        if (filteredFlightCache.containsKey(cacheKey) && (System.currentTimeMillis() - lastCacheRefresh) < CACHE_EXPIRY_MS) {
            logger.debug("Verwende gecachte Ankünfte für Flughafen {} am {}", airportId, date);
            return new ArrayList<>(filteredFlightCache.get(cacheKey));
        }

        List<Flight> allPotentialArrivals = new ArrayList<>();

        // Neue Implementierung: Alle Flüge für den Zielflughafen laden und dann dynamisch filtern
        String sql = "SELECT f.id, f.flight_number, f.route_id, f.departure_airport_id, f.departure_datetime, " +
                "f.arrival_airport_id, f.aircraft_id, " +
                "f.price_economy, f.price_business, f.price_first, f.status, " +
                "r.distance_km, r.flight_time_minutes, f.pax_economy, f.pax_business, f.pax_first, " +
                // Abflughafen-Felder
                "dep.id as dep_id, dep.name as dep_name, dep.icao_code as dep_icao, " +
                "dep.city as dep_city, dc.country as dep_country, " +
                "dep.latitude as dep_lat, dep.longitude as dep_lon, " +
                // Zielflughafen-Felder
                "arr.id as arr_id, arr.name as arr_name, arr.icao_code as arr_icao, " +
                "arr.city as arr_city, ac.country as arr_country, " +
                "arr.latitude as arr_lat, arr.longitude as arr_lon, " +
                // Flugzeug-Felder
                "a.id as aircraft_id, a.registration_no, a.type_id, a.build_date, a.status as ac_status, " +
                // Flugzeugtyp-Felder
                "at.id as act_id, at.manufacturer_id, m.name as manufacturer, at.model, at.pax_capacity, at.cargo_capacity, " +
                "at.max_range_km, at.speed_kmh " +
                "FROM flights f " +
                "JOIN routes r ON f.route_id = r.id " +
                "JOIN airports dep ON f.departure_airport_id = dep.id " +
                "JOIN countries dc ON dep.country_id = dc.id " +
                "JOIN airports arr ON f.arrival_airport_id = arr.id " +
                "JOIN countries ac ON arr.country_id = ac.id " +
                "JOIN aircraft a ON f.aircraft_id = a.id " +
                "JOIN aircraft_types at ON a.type_id = at.id " +
                "JOIN manufacturers m ON at.manufacturer_id = m.id " +
                "WHERE f.arrival_airport_id = ? " +
                "AND DATE(f.departure_datetime) BETWEEN ? AND ? " +
                "ORDER BY f.departure_datetime";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // Parameter setzen
            stmt.setLong(1, airportId);

            // Wir laden Flüge mit Abflug vom Vortag bis zum Folgetag, um sicherzustellen,
            // dass wir alle potenziellen Ankünfte am gewünschten Tag erfassen
            stmt.setDate(2, Date.valueOf(date.minusDays(1)));
            stmt.setDate(3, Date.valueOf(date.plusDays(1)));

            // Debug-Ausgabe der SQL-Abfrage
            logger.debug("Starte SQL-Abfrage für potenzielle Ankünfte: Airport={}, Datumsbereich={} bis {}",
                    airportId, date.minusDays(1), date.plusDays(1));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    allPotentialArrivals.add(extractFlightFromJoinedResultSet(rs));
                }
            }
            logger.debug("{} potenzielle Ankünfte für Flughafen ID {} geladen",
                    allPotentialArrivals.size(), airportId);

            // Nun filtern wir die Flüge basierend auf der berechneten Ankunftszeit
            List<Flight> arrivals = new ArrayList<>();
            for (Flight flight : allPotentialArrivals) {
                LocalDate arrivalDate = flight.getArrivalDate();
                if (arrivalDate != null && arrivalDate.equals(date)) {
                    arrivals.add(flight);
                }
            }

            // Sortiere nach berechneter Ankunftszeit
            arrivals.sort((f1, f2) -> {
                LocalDateTime arrival1 = LocalDateTime.of(
                        f1.getArrivalDate() != null ? f1.getArrivalDate() : LocalDate.MIN,
                        f1.getArrivalTime());
                LocalDateTime arrival2 = LocalDateTime.of(
                        f2.getArrivalDate() != null ? f2.getArrivalDate() : LocalDate.MIN,
                        f2.getArrivalTime());
                return arrival1.compareTo(arrival2);
            });

            // Anwendung des Limits
            if (limit != null && limit > 0 && arrivals.size() > limit) {
                arrivals = arrivals.subList(0, limit);
            }

            logger.debug("{} gefilterte Ankünfte für Flughafen ID {} am {} nach dynamischer Berechnung",
                    arrivals.size(), airportId, date);

            // Im Cache speichern
            filteredFlightCache.put(cacheKey, new ArrayList<>(arrivals));

            return arrivals;

        } catch (SQLException e) {
            logger.error("Fehler beim Laden der Ankünfte für Flughafen ID {} am {}: {}",
                    airportId, date, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Leert alle Caches.
     * Sollte aufgerufen werden, wenn Flüge hinzugefügt, aktualisiert oder gelöscht werden.
     */
    public void invalidateCache() {
        flightCache.clear();
        allFlightsCache = null;
        filteredFlightCache.clear();
        lastCacheRefresh = 0;
        logger.debug("Flight-Cache invalidiert");
    }

    /**
     * Extrahiert ein Flight-Objekt aus einem ResultSet mit JOINs
     * Diese Methode vermeidet rekursive Datenbankabfragen
     * Angepasst für Single-Airline-Architektur ohne airlines-Tabelle
     */
    private Flight extractFlightFromJoinedResultSet(ResultSet rs) throws SQLException {
        // Abflughafen-Objekt erstellen
        Airport departureAirport = new Airport();
        departureAirport.setId(rs.getLong("dep_id"));
        departureAirport.setName(rs.getString("dep_name"));
        departureAirport.setIcaoCode(rs.getString("dep_icao"));
        departureAirport.setCity(rs.getString("dep_city"));
        departureAirport.setCountry(rs.getString("dep_country"));
        departureAirport.setLatitude(rs.getDouble("dep_lat"));
        departureAirport.setLongitude(rs.getDouble("dep_lon"));

        // Zielflughafen-Objekt erstellen
        Airport arrivalAirport = new Airport();
        arrivalAirport.setId(rs.getLong("arr_id"));
        arrivalAirport.setName(rs.getString("arr_name"));
        arrivalAirport.setIcaoCode(rs.getString("arr_icao"));
        arrivalAirport.setCity(rs.getString("arr_city"));
        arrivalAirport.setCountry(rs.getString("arr_country"));
        arrivalAirport.setLatitude(rs.getDouble("arr_lat"));
        arrivalAirport.setLongitude(rs.getDouble("arr_lon"));

        // Verwende die Singleton-Instanz der Airline
        Airline airline = Airline.getInstance();

        // Flugzeugtyp-Objekt erstellen
        AircraftType aircraftType = new AircraftType();
        aircraftType.setId(rs.getLong("act_id"));

        // Hersteller-Objekt erstellen statt Enum.valueOf()
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setId(rs.getLong("manufacturer_id"));
        manufacturer.setName(rs.getString("manufacturer"));
        aircraftType.setManufacturer(manufacturer);

        aircraftType.setModel(rs.getString("model"));
        aircraftType.setPaxCapacity(rs.getInt("pax_capacity"));
        aircraftType.setCargoCapacity(rs.getDouble("cargo_capacity"));
        aircraftType.setMaxRangeKm(rs.getDouble("max_range_km"));
        aircraftType.setSpeedKmh(rs.getDouble("speed_kmh"));

        // Flugzeug-Objekt erstellen - verwenden wir die Setter statt des Konstruktors
        Aircraft aircraft = new Aircraft(aircraftType, rs.getString("registration_no"),
                rs.getDate("build_date").toLocalDate(), airline, null); // null als Standort

        // Setzen wir einen temporären Standort basierend auf der Airline
        Airport tempLocation = new Airport();
        tempLocation.setId(1L); // Frankfurt
        tempLocation.setIcaoCode("EDDF");
        tempLocation.setName("Frankfurt Airport");
        aircraft.setCurrentLocation(tempLocation);
        aircraft.setId(rs.getLong("aircraft_id"));

        // Sicherheitsmaßnahme für den Fall, dass in der Datenbank noch alte Status stehen
        // die nicht mehr im Enum existieren
        try {
            aircraft.setStatus(AircraftStatus.valueOf(rs.getString("ac_status")));
        } catch (IllegalArgumentException e) {
            logger.warn("Ungültiger AircraftStatus in der Datenbank: {}. Setze auf AVAILABLE.", rs.getString("ac_status"));
            aircraft.setStatus(AircraftStatus.AVAILABLE);
            // Aktualisiere in der Datenbank
            updateAircraftStatusInDatabase(aircraft.getId(), AircraftStatus.AVAILABLE);
        }

        // Flug-Objekt erstellen
        Flight flight = new Flight();
        flight.setId(rs.getLong("id"));
        flight.setFlightNumber(rs.getString("flight_number"));

        // Wir laden die Route später bei Bedarf - für Dashboard nicht unbedingt erforderlich
        flight.setDepartureAirport(departureAirport);
        flight.setArrivalAirport(arrivalAirport);

        // Konvertiere departure_datetime in Datum und Zeit
        Timestamp departureDatetime = rs.getTimestamp("departure_datetime");
        if (departureDatetime != null) {
            LocalDateTime ldt = departureDatetime.toLocalDateTime();
            flight.setDepartureDate(ldt.toLocalDate());
            flight.setDepartureTime(ldt.toLocalTime());
        }

        // Flugzeit aus der Datenbank laden oder berechnen
        try {
            // Versuche, flight_time_minutes aus der routes-Tabelle zu laden
            int flightTime = rs.getInt("flight_time_minutes");
            if (!rs.wasNull() && flightTime > 0) {
                flight.setFlightTimeMinutes(flightTime);
                logger.debug("Flugzeit aus der Datenbank geladen: {} Minuten", flightTime);
            } else if (flight.getRoute() != null) {
                // Fallback auf Route
                flight.setFlightTimeMinutes(flight.getRoute().getFlightTimeMinutes());
                logger.debug("Flugzeit aus Route berechnet: {} Minuten", flight.getFlightTimeMinutes());
            } else if (flight.getDistanceKm() > 0 && aircraftType.getSpeedKmh() > 0) {
                // Fallback auf Distanz und Flugzeuggeschwindigkeit
                flight.setFlightTimeMinutes(FlightUtils.calculateFlightTime(
                        flight.getDistanceKm(), aircraftType.getSpeedKmh()));
                logger.debug("Flugzeit aus Distanz und Geschwindigkeit berechnet: {} Minuten", flight.getFlightTimeMinutes());
            } else {
                // Letzter Fallback: Standardflugzeit von 2 Stunden
                flight.setFlightTimeMinutes(120);
                logger.warn("Keine Flugzeit für Flug {} verfügbar, Standard (120 min) gesetzt", rs.getLong("id"));
            }
        } catch (SQLException e) {
            // Fehler beim Lesen der flight_time_minutes
            logger.warn("Fehler beim Lesen der flight_time_minutes: {}", e.getMessage());
            // Standardflugzeit setzen
            flight.setFlightTimeMinutes(120);
        }

        flight.setAircraft(aircraft);
        flight.setPriceEconomy(rs.getDouble("price_economy"));
        flight.setPriceBusiness(rs.getDouble("price_business"));
        flight.setPriceFirst(rs.getDouble("price_first"));

        // Sicherheitsmaßnahme für den Fall, dass in der Datenbank noch alte Status stehen
        // die nicht mehr im Enum existieren
        try {
            flight.setStatus(FlightStatus.valueOf(rs.getString("status")));
        } catch (IllegalArgumentException e) {
            logger.warn("Ungültiger FlightStatus in der Datenbank: {}. Setze auf SCHEDULED.", rs.getString("status"));
            flight.setStatus(FlightStatus.SCHEDULED);
            // Aktualisiere in der Datenbank
            updateFlightStatusInDatabase(flight.getId(), FlightStatus.SCHEDULED);
        }

        flight.setDistanceKm(rs.getDouble("distance_km"));
        flight.setPaxEconomy(rs.getInt("pax_economy"));
        flight.setPaxBusiness(rs.getInt("pax_business"));
        flight.setPaxFirst(rs.getInt("pax_first"));

        return flight;
    }

    /**
     * Lädt alle Flüge mit ihren abhängigen Objekten in einer optimierten Abfrage
     * Diese Methode reduziert das N+1 Problem durch Verwendung von JOINs
     * Angepasst für Single-Airline-Architektur und normalisierte Datenbankstruktur
     */
    public List<Flight> getAllFlightsWithDependencies() {
        List<Flight> flights = new ArrayList<>();

        // Optimierte SQL-Abfrage mit JOINs für alle relevanten Tabellen
        String sql = "SELECT f.id, f.flight_number, f.route_id, f.departure_airport_id, f.departure_datetime, " +
                "f.arrival_airport_id, f.aircraft_id, " +
                "f.price_economy, f.price_business, f.price_first, f.status, " +
                "r.distance_km, r.flight_time_minutes, f.pax_economy, f.pax_business, f.pax_first, " +
                // Abflughafen-Felder
                "dep.id as dep_id, dep.name as dep_name, dep.icao_code as dep_icao, " +
                "dep.city as dep_city, dc.country as dep_country, " +
                "dep.latitude as dep_lat, dep.longitude as dep_lon, " +
                // Zielflughafen-Felder
                "arr.id as arr_id, arr.name as arr_name, arr.icao_code as arr_icao, " +
                "arr.city as arr_city, ac.country as arr_country, " +
                "arr.latitude as arr_lat, arr.longitude as arr_lon, " +
                // Flugzeug-Felder
                "a.id as aircraft_id, a.registration_no, a.type_id, a.build_date, a.status as ac_status, " +
                // Flugzeugtyp-Felder
                "at.id as act_id, at.manufacturer_id, m.name as manufacturer, at.model, at.pax_capacity, at.cargo_capacity, " +
                "at.max_range_km, at.speed_kmh " +
                "FROM flights f " +
                "JOIN routes r ON f.route_id = r.id " +
                "JOIN airports dep ON f.departure_airport_id = dep.id " +
                "JOIN countries dc ON dep.country_id = dc.id " +
                "JOIN airports arr ON f.arrival_airport_id = arr.id " +
                "JOIN countries ac ON arr.country_id = ac.id " +
                "JOIN aircraft a ON f.aircraft_id = a.id " +
                "JOIN aircraft_types at ON a.type_id = at.id " +
                "JOIN manufacturers m ON at.manufacturer_id = m.id " +
                "ORDER BY f.departure_datetime";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                try {
                    flights.add(extractFlightFromJoinedResultSet(rs));
                } catch (Exception e) {
                    logger.error("Fehler beim Extrahieren eines Flugs aus ResultSet: {}", e.getMessage());
                }
            }
            logger.info("{} Flüge mit Abhängigkeiten aus der Datenbank geladen", flights.size());

        } catch (SQLException e) {
            logger.error("Fehler beim Laden der Flüge mit Abhängigkeiten: {}", e.getMessage());
        }

        return flights;
    }

    /**
     * Lädt einen Flug anhand seiner ID.
     * Verwendet Caching für verbesserte Performance.
     */
    public Flight getFlightById(Long id) {
        if (id == null) {
            logger.warn("Versuch, einen Flug mit null-ID zu laden");
            return null;
        }

        // Prüfe Cache
        Flight cachedFlight = flightCache.get(id);
        if (cachedFlight != null) {
            logger.debug("Flug mit ID {} aus Cache geladen", id);
            return cachedFlight;
        }

        // Optimierte SQL-Abfrage mit JOINs für alle Abhängigkeiten
        String sql = "SELECT f.id, f.flight_number, f.route_id, f.departure_airport_id, f.departure_datetime, " +
                "f.arrival_airport_id, f.aircraft_id, " +
                "f.price_economy, f.price_business, f.price_first, f.status, " +
                "r.distance_km, r.flight_time_minutes, f.pax_economy, f.pax_business, f.pax_first, " +
                // Abflughafen-Felder
                "dep.id as dep_id, dep.name as dep_name, dep.icao_code as dep_icao, " +
                "dep.city as dep_city, dc.country as dep_country, " +
                "dep.latitude as dep_lat, dep.longitude as dep_lon, " +
                // Zielflughafen-Felder
                "arr.id as arr_id, arr.name as arr_name, arr.icao_code as arr_icao, " +
                "arr.city as arr_city, ac.country as arr_country, " +
                "arr.latitude as arr_lat, arr.longitude as arr_lon, " +
                // Flugzeug-Felder
                "a.id as aircraft_id, a.registration_no, a.type_id, a.build_date, a.status as ac_status, " +
                // Flugzeugtyp-Felder
                "at.id as act_id, at.manufacturer_id, m.name as manufacturer, at.model, at.pax_capacity, at.cargo_capacity, " +
                "at.max_range_km, at.speed_kmh " +
                "FROM flights f " +
                "JOIN routes r ON f.route_id = r.id " +
                "JOIN airports dep ON f.departure_airport_id = dep.id " +
                "JOIN countries dc ON dep.country_id = dc.id " +
                "JOIN airports arr ON f.arrival_airport_id = arr.id " +
                "JOIN countries ac ON arr.country_id = ac.id " +
                "JOIN aircraft a ON f.aircraft_id = a.id " +
                "JOIN aircraft_types at ON a.type_id = at.id " +
                "JOIN manufacturers m ON at.manufacturer_id = m.id " +
                "WHERE f.id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Flight flight = extractFlightFromJoinedResultSet(rs);
                    logger.debug("Flug mit ID {} geladen: {}", id, flight.getFlightNumber());

                    // Im Cache speichern
                    flightCache.put(id, flight);

                    return flight;
                }
            }
        } catch (SQLException e) {
            logger.error("Fehler beim Laden des Flugs mit ID {}: {}", id, e.getMessage());
        }

        logger.warn("Kein Flug mit ID {} gefunden", id);
        return null;
    }

    /**
     * Speichert einen Flug in der Datenbank (Insert oder Update).
     * Invalidiert den Cache nach erfolgreicher Speicherung.
     */
    public boolean saveFlight(Flight flight) {
        if (flight == null) {
            logger.warn("Versuch, einen null-Flug zu speichern");
            return false;
        }

        validateFlight(flight);

        try (Connection conn = getConnection()) {
            boolean result;
            if (flight.getId() == null) {
                result = insertFlight(conn, flight);
            } else {
                result = updateFlight(conn, flight);
            }

            // Bei Erfolg Cache invalidieren
            if (result) {
                invalidateCache();
            }

            return result;
        } catch (SQLException e) {
            logger.error("Fehler beim Speichern des Flugs: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validiert die Flugdaten
     */
    private void validateFlight(Flight flight) {
        if (flight.getFlightNumber() == null || flight.getFlightNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Flugnummer darf nicht leer sein");
        }

        if (flight.getDepartureAirport() == null || flight.getDepartureAirport().getId() == null) {
            throw new IllegalArgumentException("Abflughafen darf nicht null sein");
        }

        if (flight.getArrivalAirport() == null || flight.getArrivalAirport().getId() == null) {
            throw new IllegalArgumentException("Zielflughafen darf nicht null sein");
        }

        if (flight.getAircraft() == null || flight.getAircraft().getId() == null) {
            throw new IllegalArgumentException("Flugzeug darf nicht null sein");
        }

        if (flight.getDepartureDate() == null) {
            throw new IllegalArgumentException("Abflugdatum darf nicht null sein");
        }
    }

    /**
     * Fügt einen neuen Flug in die Datenbank ein.
     * Angepasst für die neue Struktur ohne arrival_date und arrival_time Spalten.
     */
    private boolean insertFlight(Connection conn, Flight flight) throws SQLException {
        // Sicherstellen, dass Flugzeit gesetzt ist
        if (flight.getFlightTimeMinutes() <= 0 && flight.getRoute() != null) {
            flight.setFlightTimeMinutes(flight.getRoute().getFlightTimeMinutes());
        }

        String sql = "INSERT INTO flights (flight_number, route_id, departure_airport_id, departure_datetime, " +
                "arrival_airport_id, aircraft_id, " +
                "price_economy, price_business, price_first, status, " +
                "pax_economy, pax_business, pax_first) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setFlightParameters(stmt, flight);

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        flight.setId(keys.getLong(1));
                        logger.info("Neuer Flug gespeichert: {} (ID: {})",
                                flight.getFlightNumber(), flight.getId());
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * Aktualisiert einen vorhandenen Flug in der Datenbank.
     * Angepasst für die neue Struktur mit departure_datetime.
     */
    private boolean updateFlight(Connection conn, Flight flight) throws SQLException {
        // Sicherstellen, dass Flugzeit gesetzt ist
        if (flight.getFlightTimeMinutes() <= 0 && flight.getRoute() != null) {
            flight.setFlightTimeMinutes(flight.getRoute().getFlightTimeMinutes());
        }

        String sql = "UPDATE flights SET flight_number=?, route_id=?, departure_airport_id=?, " +
                "departure_datetime=?, arrival_airport_id=?, " +
                "aircraft_id=?, price_economy=?, price_business=?, price_first=?, " +
                "status=?, pax_economy=?, pax_business=?, pax_first=? " +
                "WHERE id=?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            setFlightParameters(stmt, flight);
            // Der Index für die ID in der WHERE-Klausel muss nach den anderen Parametern kommen.
            // Da setFlightParameters 13 Parameter setzt, ist der Index für die ID 14.
            stmt.setLong(14, flight.getId()); // Korrigierter Index

            int result = stmt.executeUpdate();
            if (result > 0) {
                logger.info("Flug aktualisiert: {} (ID: {})",
                        flight.getFlightNumber(), flight.getId());
            }
            return result > 0;
        }
    }

    /**
     * Setzt die Parameter für ein PreparedStatement zum Speichern eines Flugs.
     * Angepasst für die neue Struktur mit departure_datetime.
     */
    private void setFlightParameters(PreparedStatement stmt, Flight flight) throws SQLException {
        stmt.setString(1, flight.getFlightNumber());

        // Optionale Route
        if (flight.getRoute() != null && flight.getRoute().getId() != null) {
            stmt.setLong(2, flight.getRoute().getId());
        } else {
            stmt.setNull(2, Types.BIGINT);
        }

        stmt.setLong(3, flight.getDepartureAirport().getId());

        // Kombinieren von Datum und Zeit zu einem LocalDateTime
        if (flight.getDepartureDate() != null && flight.getDepartureTime() != null) {
            LocalDateTime departureDateTime = LocalDateTime.of(flight.getDepartureDate(), flight.getDepartureTime());
            stmt.setTimestamp(4, Timestamp.valueOf(departureDateTime));
        } else if (flight.getDepartureDate() != null) {
            // Fallback falls nur Datum vorhanden
            LocalDateTime departureDateTime = LocalDateTime.of(flight.getDepartureDate(), LocalTime.MIDNIGHT);
            stmt.setTimestamp(4, Timestamp.valueOf(departureDateTime));
        } else {
            stmt.setNull(4, Types.TIMESTAMP);
        }

        stmt.setLong(5, flight.getArrivalAirport().getId());
        stmt.setLong(6, flight.getAircraft().getId());
        stmt.setDouble(7, flight.getPriceEconomy());
        stmt.setDouble(8, flight.getPriceBusiness());
        stmt.setDouble(9, flight.getPriceFirst());
        stmt.setString(10, flight.getStatus().name());
        stmt.setInt(11, flight.getPaxEconomy());
        stmt.setInt(12, flight.getPaxBusiness());
        stmt.setInt(13, flight.getPaxFirst());
        // HIER WAR DER FEHLER: Der duplizierte Block wurde entfernt.
    }


    /**
     * Löscht einen Flug aus der Datenbank.
     * Invalidiert den Cache nach erfolgreicher Löschung.
     */
    public boolean deleteFlight(Long flightId) {
        if (flightId == null) {
            logger.warn("Versuch, einen Flug mit null-ID zu löschen");
            return false;
        }

        String sql = "DELETE FROM flights WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, flightId);
            int result = stmt.executeUpdate();

            if (result > 0) {
                logger.info("Flug mit ID {} gelöscht", flightId);
                // Cache invalidieren
                invalidateCache();
            }
            return result > 0;

        } catch (SQLException e) {
            logger.error("Fehler beim Löschen des Flugs mit ID {}: {}", flightId, e.getMessage());
            return false;
        }
    }

    /**
     * Findet Flüge für eine bestimmte Route.
     * Verwendet Caching für verbesserte Performance.
     */
    public List<Flight> getFlightsByRoute(Route route) {
        if (route == null || route.getId() == null) {
            logger.warn("Ungültige Route für Flugabfrage");
            return new ArrayList<>();
        }

        // Cache-Schlüssel erstellen
        String cacheKey = "route_" + route.getId();

        // Prüfe, ob Cache gültig ist
        if (filteredFlightCache.containsKey(cacheKey) && (System.currentTimeMillis() - lastCacheRefresh) < CACHE_EXPIRY_MS) {
            logger.debug("Verwende gecachte Flüge für Route {}", route.getRouteCode());
            return new ArrayList<>(filteredFlightCache.get(cacheKey));
        }

        List<Flight> flights = new ArrayList<>();
        // Angepasst für die neue Datenbankstruktur mit departure_datetime statt departure_date/time
        String sql = "SELECT f.id, f.flight_number, f.route_id, f.departure_airport_id, f.departure_datetime, " +
                "f.arrival_airport_id, f.aircraft_id, " +
                "f.price_economy, f.price_business, f.price_first, f.status, " +
                "r.distance_km, r.flight_time_minutes, f.pax_economy, f.pax_business, f.pax_first, " +
                // Abflughafen-Felder
                "dep.id as dep_id, dep.name as dep_name, dep.icao_code as dep_icao, " +
                "dep.city as dep_city, dc.country as dep_country, " +
                "dep.latitude as dep_lat, dep.longitude as dep_lon, " +
                // Zielflughafen-Felder
                "arr.id as arr_id, arr.name as arr_name, arr.icao_code as arr_icao, " +
                "arr.city as arr_city, ac.country as arr_country, " +
                "arr.latitude as arr_lat, arr.longitude as arr_lon, " +
                // Flugzeug-Felder
                "a.id as aircraft_id, a.registration_no, a.type_id, a.build_date, a.status as ac_status, " +
                // Flugzeugtyp-Felder
                "at.id as act_id, at.manufacturer_id, m.name as manufacturer, at.model, at.pax_capacity, at.cargo_capacity, " +
                "at.max_range_km, at.speed_kmh " +
                "FROM flights f " +
                "JOIN routes r ON f.route_id = r.id " +
                "JOIN airports dep ON f.departure_airport_id = dep.id " +
                "JOIN countries dc ON dep.country_id = dc.id " +
                "JOIN airports arr ON f.arrival_airport_id = arr.id " +
                "JOIN countries ac ON arr.country_id = ac.id " +
                "JOIN aircraft a ON f.aircraft_id = a.id " +
                "JOIN aircraft_types at ON a.type_id = at.id " +
                "JOIN manufacturers m ON at.manufacturer_id = m.id " +
                "WHERE f.route_id = ? ORDER BY f.departure_datetime";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, route.getId());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    flights.add(extractFlightFromJoinedResultSet(rs)); // Jetzt nutzen wir die korrekte Methode
                }
            }
            logger.info("{} Flüge für Route {} geladen", flights.size(), route.getRouteCode());

            // Im Cache speichern
            filteredFlightCache.put(cacheKey, new ArrayList<>(flights));

        } catch (SQLException e) {
            logger.error("Fehler beim Laden der Flüge für Route {}: {}",
                    route.getRouteCode(), e.getMessage());
        }

        return flights;
    }

    /**
     * Prüft, ob ein Flugzeug aktive (geplante) Flüge hat.
     * 
     * @param aircraftId ID des zu prüfenden Flugzeugs
     * @return true, wenn mindestens ein SCHEDULED Flug existiert
     */
    public boolean hasScheduledFlightsForAircraft(Long aircraftId) {
        if (aircraftId == null) {
            return false;
        }
        
        String sql = "SELECT COUNT(*) FROM flights WHERE aircraft_id = ? AND status = 'SCHEDULED'";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
             
            stmt.setLong(1, aircraftId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("Fehler beim Prüfen von aktiven Flügen für Flugzeug ID {}: {}", aircraftId, e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Prüft, ob ein Flugzeug sich aktuell in einem laufenden Flug befindet
     * (BOARDING, DEPARTED, FLYING, LANDED, DEPLANING).
     * 
     * @param aircraftId ID des zu prüfenden Flugzeugs
     * @return true, wenn das Flugzeug in einem aktiven Flug ist
     */
    public boolean hasInProgressFlightsForAircraft(Long aircraftId) {
        if (aircraftId == null) {
            return false;
        }
        
        String sql = "SELECT COUNT(*) FROM flights WHERE aircraft_id = ? AND status IN " +
                     "('BOARDING', 'DEPARTED', 'FLYING', 'LANDED', 'DEPLANING')";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
             
            stmt.setLong(1, aircraftId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("Fehler beim Prüfen von laufenden Flügen für Flugzeug ID {}: {}", aircraftId, e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Synchronisiert den Status aller Flugzeuge mit ihren aktuellen Flügen.
     * Korrigiert fehlerhafte Status (z.B. FLYING ohne aktiven Flug).
     */
    public void synchronizeAircraftStatus() {
        logger.info("Starte Synchronisierung der Flugzeugstatus...");
        
        try {
            List<Aircraft> allAircraft = AircraftService.getInstance().getAllAircraft();
            
            for (Aircraft aircraft : allAircraft) {
                AircraftStatus currentStatus = aircraft.getStatus();
                AircraftStatus correctStatus = calculateCorrectAircraftStatus(aircraft);
                
                if (currentStatus != correctStatus) {
                    logger.info("Korrigiere Flugzeugstatus für {}: {} -> {}", 
                            aircraft.getRegistrationNo(), currentStatus, correctStatus);
                    
                    // Status aktualisieren
                    aircraft.setStatus(correctStatus);
                    updateAircraftStatusInDatabase(aircraft.getId(), correctStatus);
                }
            }
            
            logger.info("Flugzeugstatus-Synchronisierung abgeschlossen");
        } catch (Exception e) {
            logger.error("Fehler bei der Synchronisierung der Flugzeugstatus: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Berechnet den korrekten Status eines Flugzeugs basierend auf seinen Flügen
     */
    private AircraftStatus calculateCorrectAircraftStatus(Aircraft aircraft) {
        if (aircraft == null || aircraft.getId() == null) {
            return AircraftStatus.UNKNOWN;
        }
        
        // Prüfe auf laufende Flüge (BOARDING, DEPARTED, FLYING, LANDED, DEPLANING)
        if (hasInProgressFlightsForAircraft(aircraft.getId())) {
            return AircraftStatus.FLYING;
        }
        
        // Prüfe auf geplante Flüge (SCHEDULED)
        if (hasScheduledFlightsForAircraft(aircraft.getId())) {
            return AircraftStatus.SCHEDULED;
        }
        
        // Keine aktiven oder geplanten Flüge
        return AircraftStatus.AVAILABLE;
    }
}
