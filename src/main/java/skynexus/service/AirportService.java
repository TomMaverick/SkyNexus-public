package skynexus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.database.DatabaseConnectionManager;
import skynexus.database.DatabaseHelper.SQLFunction;
import skynexus.model.Airport;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service-Klasse für die Verwaltung von Flughäfen in der Datenbank.
 * Bietet Methoden zum Laden, Speichern, Aktualisieren und Löschen von Flughäfen.
 * Implementiert Caching für verbesserte Performance.
 */
public class AirportService {
    private static final Logger logger = LoggerFactory.getLogger(AirportService.class);
    // Einfacher Cache für Flughäfen mit Verfallszeit
    private static final Map<Long, Airport> airportCache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 5 * 60 * 1000; // 5 Minuten
    private static AirportService instance;
    private static List<Airport> allAirportsCache = null;
    private static long lastCacheRefresh = 0;

    /**
     * Standard-Konstruktor
     */
    public AirportService() {
    }

    /**
     * Gibt die Singleton-Instanz des AirportService zurück
     *
     * @return Die einzige Instanz des AirportService
     */
    public static synchronized AirportService getInstance() {
        if (instance == null) {
            instance = new AirportService();
        }
        return instance;
    }

    /**
     * Führt eine Datenbankoperation mit automatischer Verbindungsverwaltung aus
     *
     * @param operation Die auszuführende Datenbankoperation
     * @param <T>       Der Rückgabetyp der Operation
     * @return Das Ergebnis der Operation
     * @throws SQLException Bei Datenbankfehlern
     */
    private <T> T withConnection(SQLFunction<Connection, T> operation) throws SQLException {
        try (DatabaseConnectionManager.ConnectionScope scope = DatabaseConnectionManager.getInstance().createConnectionScope()) {
            return scope.execute(operation);
        }
    }

    /**
     * Gibt den Standard-Flughafen (ID=1) zurück, der für neue Benutzer verwendet wird.
     * Verwendet Cache wenn verfügbar.
     *
     * @return Der Standard-Flughafen
     */
    public Airport getDefaultAirport() {
        try {
            Airport airport = getAirportById(1L);
            if (airport == null) {
                logger.warn("Standard-Flughafen (ID=1) konnte nicht gefunden werden. Bitte in der Datenbank anlegen.");
                // Fallback-Flughafen erstellen mit Grunddaten
                airport = new Airport();
                airport.setId(1L);
                airport.setIcaoCode("EDDF");
                airport.setName("Frankfurt Airport");
                airport.setCity("Frankfurt");
                airport.setCountry("DE");
                airport.setLatitude(50.0379);
                airport.setLongitude(8.5622);
            }
            return airport;
        } catch (Exception e) {
            logger.error("Fehler beim Abrufen des Standard-Flughafens: {}", e.getMessage());
            // Fallback-Flughafen erstellen im Fehlerfall
            Airport airport = new Airport();
            airport.setId(1L);
            airport.setIcaoCode("EDDF");
            airport.setName("Frankfurt Airport");
            airport.setCity("Frankfurt");
            airport.setCountry("DE");
            airport.setLatitude(50.0379);
            airport.setLongitude(8.5622);
            return airport;
        }
    }

    /**
     * Lädt alle Flughäfen aus der Datenbank
     * Implementiert Caching für verbesserte Performance.
     *
     * @return Liste aller Flughäfen
     */
    public List<Airport> getAllAirports() {
        long now = System.currentTimeMillis();

        // Prüfe, ob Cache gültig ist
        if (allAirportsCache != null && (now - lastCacheRefresh) < CACHE_EXPIRY_MS) {
            logger.debug("Verwende gecachte Flughafenliste (Cache-Alter: {} ms)", now - lastCacheRefresh);
            return new ArrayList<>(allAirportsCache);
        }

        String sql = "SELECT a.id, a.icao_code, a.name, a.city, c.country, a.latitude, a.longitude " +
                "FROM airports a JOIN countries c ON a.country_id = c.id ORDER BY a.name";

        try {
            List<Airport> airports = withConnection(conn -> {
                List<Airport> result = new ArrayList<>();
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {

                    while (rs.next()) {
                        Airport airport = extractAirportFromResultSet(rs);
                        result.add(airport);
                        // Auch in ID-Cache speichern
                        airportCache.put(airport.getId(), airport);
                    }
                    logger.info("{} Flughäfen aus der Datenbank geladen", result.size());
                    return result;
                }
            });

            // Cache aktualisieren
            allAirportsCache = new ArrayList<>(airports);
            lastCacheRefresh = now;

            return airports;
        } catch (SQLException e) {
            logger.error("Fehler beim Laden der Flughäfen: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Speichert einen neuen oder aktualisiert einen vorhandenen Flughafen
     *
     * @param airport Der zu speichernde Flughafen
     * @return true wenn erfolgreich gespeichert/aktualisiert, sonst false
     */
    public boolean saveAirport(Airport airport) {
        if (airport == null) {
            logger.warn("Versuch, einen null-Flughafen zu speichern");
            return false;
        }

        // Validierung der ICAO- und IATA-Codes
        validateAirportCodes(airport);

        try {
            boolean result = withConnection(conn -> {
                if (airport.getId() == null) {
                    return insertAirport(conn, airport);
                } else {
                    return updateAirport(conn, airport);
                }
            });

            if (result) {
                // Cache invalidieren
                invalidateCache();
            }

            return result;
        } catch (SQLException e) {
            logger.error("Fehler beim Speichern des Flughafens {}: {}",
                    airport.getIcaoCode(), e.getMessage());
            return false;
        }
    }

    /**
     * Validiert ICAO-Code eines Flughafens
     *
     * @param airport Der zu validierende Flughafen
     */
    private void validateAirportCodes(Airport airport) {
        // ICAO-Code muss exakt 4 Buchstaben enthalten
        if (!airport.getIcaoCode().matches("[A-Z]{4}")) {
            logger.warn("Ungültiger ICAO-Code für Flughafen: {}", airport.getIcaoCode());
        }
    }

    /**
     * Fügt einen neuen Flughafen in die Datenbank ein
     */
    private boolean insertAirport(Connection conn, Airport airport) throws SQLException {
        // Zuerst country_id aus der countries-Tabelle abrufen
        long countryId = getCountryId(conn, airport.getCountry());
        if (countryId == -1) {
            logger.error("Land '{}' nicht in der Datenbank gefunden", airport.getCountry());
            return false;
        }

        String sql = "INSERT INTO airports (icao_code, name, city, country_id, latitude, longitude) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, airport.getIcaoCode());
            stmt.setString(2, airport.getName());
            stmt.setString(3, airport.getCity());
            stmt.setLong(4, countryId);
            stmt.setDouble(5, airport.getLatitude());
            stmt.setDouble(6, airport.getLongitude());

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        airport.setId(generatedKeys.getLong(1));
                        logger.info("Neuer Flughafen gespeichert: {} (ID: {})",
                                airport.getIcaoCode(), airport.getId());
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * Ermittelt die country_id für einen Ländernamen
     *
     * @param conn Die Datenbankverbindung
     * @param countryName Der Ländername
     * @return Die country_id oder -1, wenn nicht gefunden
     */
    private long getCountryId(Connection conn, String countryName) throws SQLException {
        String sql = "SELECT id FROM countries WHERE country = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, countryName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }
        return -1;
    }

    /**
     * Aktualisiert einen vorhandenen Flughafen in der Datenbank
     */
    public boolean updateAirport(Connection conn, Airport airport) throws SQLException {
        // Zuerst country_id aus der countries-Tabelle abrufen
        long countryId = getCountryId(conn, airport.getCountry());
        if (countryId == -1) {
            logger.error("Land '{}' nicht in der Datenbank gefunden", airport.getCountry());
            return false;
        }

        String sql = "UPDATE airports SET icao_code=?, name=?, city=?, country_id=?, " +
                "latitude=?, longitude=? WHERE id=?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, airport.getIcaoCode());
            stmt.setString(2, airport.getName());
            stmt.setString(3, airport.getCity());
            stmt.setLong(4, countryId);
            stmt.setDouble(5, airport.getLatitude());
            stmt.setDouble(6, airport.getLongitude());
            stmt.setLong(7, airport.getId());

            int result = stmt.executeUpdate();
            if (result > 0) {
                logger.info("Flughafen aktualisiert: {} (ID: {})",
                        airport.getIcaoCode(), airport.getId());
            }
            return result > 0;
        }
    }

    /**
     * Aktualisiert einen vorhandenen Flughafen in der Datenbank
     */
    public boolean updateAirport(Airport airport) {
        if (airport == null || airport.getId() == null) {
            logger.warn("Versuch, einen ungültigen Flughafen zu aktualisieren");
            return false;
        }

        try {
            boolean result = withConnection(conn -> updateAirport(conn, airport));

            if (result) {
                // Cache invalidieren
                invalidateCache();

                // Airport im ID-Cache aktualisieren
                airportCache.put(airport.getId(), airport);
            }

            return result;
        } catch (SQLException e) {
            logger.error("Fehler beim Aktualisieren des Flughafens {}: {}",
                    airport.getIcaoCode(), e.getMessage());
            return false;
        }
    }

    /**
     * Löscht einen Flughafen aus der Datenbank
     *
     * @param airportId ID des zu löschenden Flughafens
     * @return true wenn erfolgreich gelöscht, sonst false
     */
    public boolean deleteAirport(Long airportId) {
        if (airportId == null) {
            logger.warn("Versuch, einen Flughafen mit null-ID zu löschen");
            return false;
        }

        // Prüfen, ob der Flughafen von Airlines oder Routen verwendet wird
        if (isAirportInUse(airportId)) {
            logger.warn("Flughafen mit ID {} kann nicht gelöscht werden, da er in Verwendung ist", airportId);
            return false;
        }

        String sql = "DELETE FROM airports WHERE id = ?";

        try {
            boolean result = withConnection(conn -> {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, airportId);
                    int rows = stmt.executeUpdate();

                    if (rows > 0) {
                        logger.info("Flughafen mit ID {} gelöscht", airportId);
                    } else {
                        logger.warn("Kein Flughafen mit ID {} gefunden zum Löschen", airportId);
                    }
                    return rows > 0;
                }
            });

            if (result) {
                // Cache invalidieren
                invalidateCache();

                // Aus ID-Cache entfernen
                airportCache.remove(airportId);
            }

            return result;
        } catch (SQLException e) {
            logger.error("Fehler beim Löschen des Flughafens (ID: {}): {}", airportId, e.getMessage());
            return false;
        }
    }

    /**
     * Prüft, ob ein Flughafen in Verwendung ist (durch Routen oder Flüge)
     *
     * @param airportId Die zu prüfende Flughafen-ID
     * @return true wenn in Verwendung, sonst false
     */
    public boolean isAirportInUse(Long airportId) {
        // SQL zur Prüfung auf Verwendung in Routen
        String routeSql = "SELECT COUNT(*) FROM routes WHERE departure_airport_id = ? OR arrival_airport_id = ?";

        try {
            return withConnection(conn -> {
                // Prüfe auf Verwendung in Routen
                try (PreparedStatement stmt = conn.prepareStatement(routeSql)) {
                    stmt.setLong(1, airportId);
                    stmt.setLong(2, airportId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            return true;
                        }
                    }
                    return false;
                }
            });
        } catch (SQLException e) {
            logger.error("Fehler bei der Prüfung auf Verwendung des Flughafens: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Prüft, ob für einen Flughafen aktive Flüge (SCHEDULED) existieren
     *
     * @param airportId Die zu prüfende Flughafen-ID
     * @return true wenn aktive Flüge existieren, sonst false
     */
    public boolean hasActiveFlights(Long airportId) {
        String sql = "SELECT COUNT(*) FROM flights f " +
                "JOIN routes r ON f.route_id = r.id " +
                "WHERE (r.departure_airport_id = ? OR r.arrival_airport_id = ?) " +
                "AND f.status = 'SCHEDULED' " +
                "AND f.departure_datetime > NOW()";

        try {
            return withConnection(conn -> {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, airportId);
                    stmt.setLong(2, airportId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            int activeFlights = rs.getInt(1);
                            logger.debug("Flughafen {} hat {} aktive Flüge", airportId, activeFlights);
                            return activeFlights > 0;
                        }
                        return false;
                    }
                }
            });
        } catch (SQLException e) {
            logger.error("Fehler bei der Prüfung auf aktive Flüge für Flughafen {}: {}", airportId, e.getMessage());
            return true;
        }
    }

    /**
     * Ermittelt die Anzahl der aktiven Flüge für einen Flughafen
     *
     * @param airportId Die zu prüfende Flughafen-ID
     * @return Die Anzahl der aktiven Flüge
     */
    public int getActiveFlightCount(Long airportId) {
        String sql = "SELECT COUNT(*) FROM flights f " +
                "JOIN routes r ON f.route_id = r.id " +
                "WHERE (r.departure_airport_id = ? OR r.arrival_airport_id = ?) " +
                "AND f.status = 'SCHEDULED' " +
                "AND f.departure_datetime > NOW()";

        try {
            return withConnection(conn -> {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, airportId);
                    stmt.setLong(2, airportId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt(1);
                        }
                        return 0;
                    }
                }
            });
        } catch (SQLException e) {
            logger.error("Fehler beim Zählen der aktiven Flüge für Flughafen {}: {}", airportId, e.getMessage());
            return -1;
        }
    }

    /**
     * Sucht einen Flughafen anhand seiner ID
     * Verwendet Cache wenn verfügbar.
     *
     * @param id Die ID des gesuchten Flughafens
     * @return Der gefundene Flughafen oder null
     */
    public Airport getAirportById(Long id) {
        if (id == null) {
            logger.warn("Versuch, einen Flughafen mit null-ID zu laden");
            return null;
        }

        // Prüfe Cache
        Airport cachedAirport = airportCache.get(id);
        if (cachedAirport != null) {
            logger.debug("Flughafen mit ID {} aus Cache geladen", id);
            return cachedAirport;
        }

        String sql = "SELECT a.id, a.icao_code, a.name, a.city, c.country, a.latitude, a.longitude " +
                "FROM airports a JOIN countries c ON a.country_id = c.id WHERE a.id = ?";

        try {
            // Im Cache speichern

            return withConnection(conn -> {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, id);

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            Airport result = extractAirportFromResultSet(rs);
                            logger.debug("Flughafen mit ID {} geladen: {}", id, result.getName());

                            // Im Cache speichern
                            airportCache.put(id, result);

                            return result;
                        } else {
                            logger.warn("Kein Flughafen gefunden mit ID: {}", id);
                            return null;
                        }
                    }
                }
            });
        } catch (SQLException e) {
            logger.error("Fehler beim Laden des Flughafens mit ID {}: {}", id, e.getMessage());
            return null;
        }
    }

    /**
     * Extrahiert ein Airport-Objekt aus einem ResultSet
     */
    private Airport extractAirportFromResultSet(ResultSet rs) throws SQLException {
        Airport airport = new Airport();
        airport.setId(rs.getLong("id"));
        airport.setIcaoCode(rs.getString("icao_code"));
        airport.setName(rs.getString("name"));
        airport.setCity(rs.getString("city"));
        airport.setCountry(rs.getString("country"));
        airport.setLatitude(rs.getDouble("latitude"));
        airport.setLongitude(rs.getDouble("longitude"));
        return airport;
    }

    /**
     * Ermittelt alle Routen, die über einen bestimmten Flughafen führen
     *
     * @param airportId Die zu prüfende Flughafen-ID
     * @return Liste der Route-Codes und deren Details
     */
    public List<String> getAssociatedRoutes(Long airportId) {
        String sql = "SELECT r.route_code, " +
                "dep.icao_code as departure_icao, dep.name as departure_name, " +
                "arr.icao_code as arrival_icao, arr.name as arrival_name " +
                "FROM routes r " +
                "JOIN airports dep ON r.departure_airport_id = dep.id " +
                "JOIN airports arr ON r.arrival_airport_id = arr.id " +
                "WHERE r.departure_airport_id = ? OR r.arrival_airport_id = ?";

        try {
            return withConnection(conn -> {
                List<String> routes = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, airportId);
                    stmt.setLong(2, airportId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String routeInfo = String.format("%s (%s → %s)",
                                    rs.getString("route_code"),
                                    rs.getString("departure_icao"),
                                    rs.getString("arrival_icao"));
                            routes.add(routeInfo);
                        }
                    }
                }
                return routes;
            });
        } catch (SQLException e) {
            logger.error("Fehler beim Ermitteln der Routen für Flughafen {}: {}", airportId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Ermittelt alle Flüge, die über einen bestimmten Flughafen führen (unabhängig vom Status)
     *
     * @param airportId Die zu prüfende Flughafen-ID
     * @return Liste der Flugnummern und deren Details
     */
    public List<String> getAssociatedFlights(Long airportId) {
        String sql = "SELECT f.flight_number, f.departure_datetime, f.status, r.route_code " +
                "FROM flights f " +
                "JOIN routes r ON f.route_id = r.id " +
                "WHERE r.departure_airport_id = ? OR r.arrival_airport_id = ? " +
                "ORDER BY f.departure_datetime DESC " +
                "LIMIT 10"; // Limitiere auf 10 zur besseren Übersicht

        try {
            return withConnection(conn -> {
                List<String> flights = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, airportId);
                    stmt.setLong(2, airportId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String flightInfo = String.format("%s - %s (%s) - Status: %s",
                                    rs.getString("flight_number"),
                                    rs.getString("route_code"),
                                    rs.getTimestamp("departure_datetime"),
                                    rs.getString("status"));
                            flights.add(flightInfo);
                        }
                    }
                }
                return flights;
            });
        } catch (SQLException e) {
            logger.error("Fehler beim Ermitteln der Flüge für Flughafen {}: {}", airportId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Ermittelt eine Zusammenfassung aller Abhängigkeiten für einen Flughafen
     *
     * @param airportId Die zu prüfende Flughafen-ID
     * @return Detaillierte Zusammenfassung der Abhängigkeiten
     */
    public String getDependencySummary(Long airportId) {
        StringBuilder summary = new StringBuilder();

        // Routen ermitteln
        List<String> routes = getAssociatedRoutes(airportId);
        if (!routes.isEmpty()) {
            summary.append("Routen (").append(routes.size()).append("):\n");
            for (String route : routes) {
                summary.append("  • ").append(route).append("\n");
            }
            summary.append("\n");
        }

        // Aktive Flüge ermitteln
        int activeFlights = getActiveFlightCount(airportId);
        if (activeFlights > 0) {
            summary.append("Aktive Flüge: ").append(activeFlights).append("\n\n");
        }

        // Alle Flüge ermitteln (inkl. vergangene)
        List<String> allFlights = getAssociatedFlights(airportId);
        if (!allFlights.isEmpty()) {
            summary.append("Letzte Flüge (max. 10):\n");
            for (String flight : allFlights) {
                summary.append("  • ").append(flight).append("\n");
            }
        }

        return summary.toString();
    }

    /**
     * Invalidiert den Cache.
     * Sollte aufgerufen werden, wenn Flughäfen hinzugefügt, aktualisiert oder gelöscht werden.
     */
    public void invalidateCache() {
        airportCache.clear();
        allAirportsCache = null;
        lastCacheRefresh = 0;
        logger.debug("Airport-Cache invalidiert");
    }
}
