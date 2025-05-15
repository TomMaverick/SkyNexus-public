package skynexus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.database.DatabaseConnectionManager;
import skynexus.database.DatabaseHelper.SQLFunction;
import skynexus.model.Airline;
import skynexus.model.Airport;
import skynexus.model.Route;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Service-Klasse für die Verwaltung von Routen.
 * Bietet CRUD-Operationen für Route-Objekte.
 */
public class RouteService {
    private static final Logger logger = LoggerFactory.getLogger(RouteService.class);
    private static RouteService instance;

    private final AirportService airportService;


    /**
     * Privater Konstruktor für Singleton-Pattern
     */
    private RouteService() {
        this.airportService = AirportService.getInstance();
    }

    /**
     * Gibt die Singleton-Instanz zurück
     */
    public static synchronized RouteService getInstance() {
        if (instance == null) {
            instance = new RouteService();
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
     * Lädt alle Routen aus der Datenbank
     */
    public List<Route> getAllRoutes() {
        return getAllRoutesWithDependencies();
    }

    /**
     * Lädt alle Routen mit ihren abhängigen Objekten in einer optimierten Abfrage
     * Diese Methode reduziert das N+1 Problem durch Verwendung von JOINs
     * Angepasst für Single-Airline-Architektur und normalisierte Datenbankstruktur
     */
    public List<Route> getAllRoutesWithDependencies() {
        String sql = "SELECT r.id, r.route_code, r.departure_airport_id, r.arrival_airport_id, " +
                "r.distance_km, r.flight_time_minutes, r.active, " +
                "dep.id as dep_id, dep.name as dep_name, dep.icao_code as dep_icao, " +
                "dep.city as dep_city, dc.country as dep_country, " +
                "dep.latitude as dep_lat, dep.longitude as dep_lon, " +
                "arr.id as arr_id, arr.name as arr_name, arr.icao_code as arr_icao, " +
                "arr.city as arr_city, ac.country as arr_country, " +
                "arr.latitude as arr_lat, arr.longitude as arr_lon " +
                "FROM routes r " +
                "JOIN airports dep ON r.departure_airport_id = dep.id " +
                "JOIN countries dc ON dep.country_id = dc.id " +
                "JOIN airports arr ON r.arrival_airport_id = arr.id " +
                "JOIN countries ac ON arr.country_id = ac.id " +
                "ORDER BY r.route_code";

        try {
            return withConnection(conn -> {
                List<Route> routes = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        // Erstelle Abflughafen-Objekt direkt aus dem ResultSet
                        Airport departureAirport = new Airport();
                        departureAirport.setId(rs.getLong("dep_id"));
                        departureAirport.setName(rs.getString("dep_name"));
                        departureAirport.setIcaoCode(rs.getString("dep_icao"));
                        departureAirport.setCity(rs.getString("dep_city"));
                        departureAirport.setCountry(rs.getString("dep_country"));
                        departureAirport.setLatitude(rs.getDouble("dep_lat"));
                        departureAirport.setLongitude(rs.getDouble("dep_lon"));

                        // Erstelle Zielflughafen-Objekt direkt aus dem ResultSet
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

                        // Erstelle Route-Objekt
                        Route route = new Route();
                        route.setId(rs.getLong("id"));
                        route.setRouteCode(rs.getString("route_code"));
                        route.setDepartureAirport(departureAirport);
                        route.setArrivalAirport(arrivalAirport);
                        route.setDistanceKm(rs.getDouble("distance_km"));
                        route.setFlightTimeMinutes(rs.getInt("flight_time_minutes"));
                        route.setOperator(airline);
                        route.setActive(rs.getBoolean("active"));

                        routes.add(route);
                    }
                    logger.info("{} Routen mit Abhängigkeiten aus der Datenbank geladen", routes.size());
                    return routes;
                }
            });
        } catch (SQLException e) {
            logger.error("Fehler beim Laden der Routen mit Abhängigkeiten: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Speichert eine Route in der Datenbank (Insert oder Update)
     */
    public boolean saveRoute(Route route) {
        if (route == null) {
            logger.warn("Versuch, eine null-Route zu speichern");
            return false;
        }

        validateRoute(route);

        try {
            return withConnection(conn -> {
                if (route.getId() == null) {
                    return insertRoute(conn, route);
                } else {
                    return updateRoute(conn, route);
                }
            });
        } catch (SQLException e) {
            logger.error("Fehler beim Speichern der Route: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validiert die Routendaten
     * Angepasst für Single-Airline-Architektur
     */
    private void validateRoute(Route route) {
        if (route.getDepartureAirport() == null || route.getDepartureAirport().getId() == null) {
            throw new IllegalArgumentException("Abflughafen darf nicht null sein");
        }

        if (route.getArrivalAirport() == null || route.getArrivalAirport().getId() == null) {
            throw new IllegalArgumentException("Zielflughafen darf nicht null sein");
        }

        // Operator wird automatisch als Airline.getInstance() gesetzt
        if (route.getOperator() == null) {
            route.setOperator(Airline.getInstance());
        }

        if (route.getDistanceKm() <= 0) {
            throw new IllegalArgumentException("Distanz muss größer als 0 sein");
        }

        if (route.getFlightTimeMinutes() <= 0) {
            throw new IllegalArgumentException("Flugzeit muss größer als 0 sein");
        }
    }

    /**
     * Fügt eine neue Route in die Datenbank ein
     * Angepasst für Single-Airline-Architektur ohne operator_id
     */
    private boolean insertRoute(Connection conn, Route route) throws SQLException {
        String sql = "INSERT INTO routes (route_code, departure_airport_id, arrival_airport_id, " +
                "distance_km, flight_time_minutes, active) VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setRouteParameters(stmt, route);

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        route.setId(keys.getLong(1));
                        logger.info("Neue Route gespeichert: {} (ID: {})",
                                route.getRouteCode(), route.getId());
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * Aktualisiert eine vorhandene Route in der Datenbank
     * Angepasst für Single-Airline-Architektur ohne operator_id
     */
    private boolean updateRoute(Connection conn, Route route) throws SQLException {
        String sql = "UPDATE routes SET route_code=?, departure_airport_id=?, arrival_airport_id=?, " +
                "distance_km=?, flight_time_minutes=?, active=? WHERE id=?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            setRouteParameters(stmt, route);
            stmt.setLong(7, route.getId());

            int result = stmt.executeUpdate();
            if (result > 0) {
                logger.info("Route aktualisiert: {} (ID: {})",
                        route.getRouteCode(), route.getId());
            }
            return result > 0;
        }
    }

    /**
     * Setzt die Parameter für ein PreparedStatement zum Speichern einer Route
     * Angepasst für Single-Airline-Architektur ohne operator_id
     */
    private void setRouteParameters(PreparedStatement stmt, Route route) throws SQLException {
        stmt.setString(1, route.getRouteCode());
        stmt.setLong(2, route.getDepartureAirport().getId());
        stmt.setLong(3, route.getArrivalAirport().getId());
        stmt.setDouble(4, route.getDistanceKm());
        stmt.setInt(5, route.getFlightTimeMinutes());
        stmt.setBoolean(6, route.isActive());
    }

    /**
     * Gibt alle Routen zurück, da in der Single-Airline-Architektur
     * alle Routen zur Standard-Airline gehören.
     *
     * @param airline Die Airline-Instanz (wird ignoriert)
     * @return Liste aller Routen
     * @deprecated In der Single-Airline-Architektur nicht mehr notwendig,
     *             verwende stattdessen getAllRoutes()
     */
    @Deprecated
    public List<Route> getRoutesByAirline(Airline airline) {
        logger.debug("getRoutesByAirline() aufgerufen - gibt einfach alle Routen zurück");
        return getAllRoutes();
    }

    /**
     * Gibt alle Routen zurück, da in der Single-Airline-Architektur
     * alle Routen zur Standard-Airline gehören.
     *
     * @param airlineId Die Airline-ID (wird ignoriert)
     * @return Liste aller Routen
     * @deprecated In der Single-Airline-Architektur nicht mehr notwendig,
     *             verwende stattdessen getAllRoutes()
     */
    @Deprecated
    public List<Route> getRoutesByAirline(Long airlineId) {
        logger.debug("getRoutesByAirline(ID) aufgerufen - gibt einfach alle Routen zurück");
        return getAllRoutes();
    }

    /**
     * Findet eine spezifische Route anhand von Abflug-, Zielflughafen und Airline.
     *
     * @param departureAirport Der Abflughafen.
     * @param arrivalAirport   Der Zielflughafen.
     * @param airline          Die betreibende Airline.
     * @return Die gefundene Route oder null, wenn keine passende Route existiert.
     */
    public Route findRouteByAirportsAndAirline(Airport departureAirport, Airport arrivalAirport, Airline airline) {
        if (departureAirport == null || arrivalAirport == null || airline == null ||
                departureAirport.getId() == null || arrivalAirport.getId() == null || airline.getId() == null) {
            logger.warn("Ungültige Parameter für findRouteByAirportsAndAirline: Departure={}, Arrival={}, Airline={}",
                    departureAirport, arrivalAirport, airline);
            return null;
        }

        // Verwende die optimierte Abfrage mit JOINs, um N+1 Probleme zu vermeiden
        String sql = "SELECT r.id, r.route_code, r.departure_airport_id, r.arrival_airport_id, " +
                "r.distance_km, r.flight_time_minutes, r.active, " +
                "dep.id as dep_id, dep.name as dep_name, dep.icao_code as dep_icao, " +
                "dep.city as dep_city, dc.country as dep_country, " +
                "dep.latitude as dep_lat, dep.longitude as dep_lon, " +
                "arr.id as arr_id, arr.name as arr_name, arr.icao_code as arr_icao, " +
                "arr.city as arr_city, ac.country as arr_country, " +
                "arr.latitude as arr_lat, arr.longitude as arr_lon " +
                "FROM routes r " +
                "JOIN airports dep ON r.departure_airport_id = dep.id " +
                "JOIN countries dc ON dep.country_id = dc.id " +
                "JOIN airports arr ON r.arrival_airport_id = arr.id " +
                "JOIN countries ac ON arr.country_id = ac.id " +
                "WHERE r.departure_airport_id = ? AND r.arrival_airport_id = ?";

        try {
            return withConnection(conn -> {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, departureAirport.getId());
                    stmt.setLong(2, arrivalAirport.getId());

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            // Erstelle Abflughafen
                            Airport dep = new Airport();
                            dep.setId(rs.getLong("dep_id"));
                            dep.setName(rs.getString("dep_name"));
                            dep.setIcaoCode(rs.getString("dep_icao"));
                            dep.setCity(rs.getString("dep_city"));
                            dep.setCountry(rs.getString("dep_country"));
                            dep.setLatitude(rs.getDouble("dep_lat"));
                            dep.setLongitude(rs.getDouble("dep_lon"));

                            // Erstelle Zielflughafen
                            Airport arr = new Airport();
                            arr.setId(rs.getLong("arr_id"));
                            arr.setName(rs.getString("arr_name"));
                            arr.setIcaoCode(rs.getString("arr_icao"));
                            arr.setCity(rs.getString("arr_city"));
                            arr.setCountry(rs.getString("arr_country"));
                            arr.setLatitude(rs.getDouble("arr_lat"));
                            arr.setLongitude(rs.getDouble("arr_lon"));

                            // Verwende die Singleton-Instanz der Airline
                            Airline op = Airline.getInstance();

                            // Erstelle Route
                            Route route = new Route();
                            route.setId(rs.getLong("id"));
                            route.setRouteCode(rs.getString("route_code"));
                            route.setDepartureAirport(dep); // Verwende das gerade erstellte Objekt
                            route.setArrivalAirport(arr);   // Verwende das gerade erstellte Objekt
                            route.setDistanceKm(rs.getDouble("distance_km"));
                            route.setFlightTimeMinutes(rs.getInt("flight_time_minutes"));
                            route.setOperator(op);          // Verwende das gerade erstellte Objekt
                            route.setActive(rs.getBoolean("active"));

                            logger.debug("Route gefunden für {} -> {} mit Airline {}: ID {}",
                                    departureAirport.getIcaoCode(), arrivalAirport.getIcaoCode(), airline.getIcaoCode()
                                    , route.getId());
                            return route;
                        } else {
                            logger.debug("Keine Route gefunden für {} -> {} mit Airline {}",
                                    departureAirport.getIcaoCode(), arrivalAirport.getIcaoCode(), airline.getIcaoCode());
                            return null;
                        }
                    }
                }
            });
        } catch (SQLException e) {
            logger.error("Fehler beim Suchen der Route für {} -> {} mit Airline {}: {}",
                    departureAirport.getIcaoCode(), arrivalAirport.getIcaoCode(), airline.getIcaoCode(), e.getMessage());
            return null;
        }
    }
}
