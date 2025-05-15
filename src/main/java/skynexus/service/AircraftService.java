package skynexus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.database.DatabaseConnectionManager;
import skynexus.enums.AircraftStatus;
import skynexus.model.*;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service-Klasse für die Verwaltung von Flugzeugen und Flugzeugtypen.
 * Bietet Datenbankzugriff für Flugzeuge und deren Typen.
 */
public class AircraftService {
    private static final Logger logger = LoggerFactory.getLogger(AircraftService.class);
    private static AircraftService instance;
    private final ManufacturerService manufacturerService;

    private AircraftService() {
        this.manufacturerService = ManufacturerService.getInstance();
    }

    public static synchronized AircraftService getInstance() {
        if (instance == null) {
            instance = new AircraftService();
        }
        return instance;
    }

    /**
     * Lädt alle verfügbaren Flugzeuge aus der Datenbank.
     */
    public List<Aircraft> getAllAircraft() {
        List<Aircraft> aircraftList = new ArrayList<>();
        String sql = "SELECT a.id, a.type_id, a.registration_no, a.build_date, a.status, a.location_id, " +
                "t.model, t.pax_capacity, t.cargo_capacity, t.max_range_km, t.speed_kmh, t.cost_per_h, " +
                "m.id as manufacturer_id, m.name as manufacturer_name, " +
                "ap.icao_code as location_icao, ap.name as location_name, ap.city as location_city, " +
                "c.country as location_country, " +
                "ap.latitude as location_lat, ap.longitude as location_lon " +
                "FROM aircraft a " +
                "JOIN aircraft_types t ON a.type_id = t.id " +
                "JOIN manufacturers m ON t.manufacturer_id = m.id " +
                "LEFT JOIN airports ap ON a.location_id = ap.id " +
                "LEFT JOIN countries c ON ap.country_id = c.id " +
                "ORDER BY a.registration_no";

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                try {
                    aircraftList.add(extractAircraftFromResultSet(rs));
                } catch (SQLException | IllegalArgumentException | NullPointerException e) {
                    long aircraftId = -1;
                    try {
                        aircraftId = rs.getLong("id");
                    } catch (SQLException ignored) {
                    }
                    logger.error("Fehler beim Extrahieren von Flugzeugdaten: {}", e.getMessage());
                }
            }
            logger.info("{} Flugzeuge aus DB geladen", aircraftList.size());

        } catch (SQLException e) {
            logger.error("Fehler beim Laden der Flugzeuge: {}", e.getMessage());
        }

        return aircraftList;
    }

    /**
     * Extrahiert ein Aircraft-Objekt aus einem ResultSet.
     */
    private Aircraft extractAircraftFromResultSet(ResultSet rs) throws SQLException {
        // Hersteller erstellen
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setId(rs.getLong("manufacturer_id"));
        manufacturer.setName(rs.getString("manufacturer_name"));

        // Flugzeugtyp erstellen
        AircraftType type = new AircraftType();
        type.setId(rs.getLong("type_id"));
        type.setManufacturer(manufacturer);
        type.setModel(rs.getString("model"));
        type.setPaxCapacity(rs.getInt("pax_capacity"));
        type.setCargoCapacity(rs.getDouble("cargo_capacity"));
        type.setMaxRangeKm(rs.getDouble("max_range_km"));
        type.setSpeedKmh(rs.getDouble("speed_kmh"));
        type.setCostPerHour(rs.getDouble("cost_per_h"));

        // Standard-Airline verwenden
        Airline airline = Airline.getInstance();

        // Standort erstellen (optional)
        Airport currentLocation = null;
        long locationId = rs.getLong("location_id");
        if (!rs.wasNull()) {
            currentLocation = new Airport();
            currentLocation.setId(locationId);
            currentLocation.setIcaoCode(rs.getString("location_icao"));
            currentLocation.setName(rs.getString("location_name"));
            currentLocation.setCity(rs.getString("location_city"));
            currentLocation.setCountry(rs.getString("location_country"));
            currentLocation.setLatitude(rs.getDouble("location_lat"));
            currentLocation.setLongitude(rs.getDouble("location_lon"));
        }

        // Baudatum als LocalDate
        LocalDate buildDate = null;
        Date sqlDate = rs.getDate("build_date");
        if (sqlDate != null) {
            buildDate = sqlDate.toLocalDate();
        } else {
            logger.warn("Fehlendes Baudatum für Flugzeug {}", rs.getString("registration_no"));
        }

        // Flugzeug erstellen
        Aircraft aircraft = new Aircraft(type, rs.getString("registration_no"), buildDate, airline, currentLocation);
        aircraft.setId(rs.getLong("id"));

        String statusStr = rs.getString("status");
        if (statusStr == null) {
            throw new NullPointerException("Status-Wert ist NULL in der Datenbank für aircraft_id " + rs.getLong("id"));
        }

        try {
            aircraft.setStatus(AircraftStatus.valueOf(statusStr));
        } catch (IllegalArgumentException e) {
            logger.error("Unbekannter Status '{}' für Flugzeug ID {}", statusStr, rs.getLong("id"));
            aircraft.setStatus(AircraftStatus.UNKNOWN);
        }

        return aircraft;
    }

    /**
     * Lädt Flugzeugtypen für einen spezifischen Hersteller
     */
    public List<AircraftType> getAircraftTypesByManufacturer(Manufacturer manufacturer) {
        if (manufacturer == null) {
            logger.warn("Versuch, Flugzeugtypen mit null-Hersteller zu laden");
            return new ArrayList<>();
        }

        List<AircraftType> types = new ArrayList<>();
        String sql = "SELECT t.id, t.model, t.pax_capacity, t.cargo_capacity, " +
                "t.max_range_km, t.speed_kmh, t.cost_per_h, " +
                "m.id as manufacturer_id, m.name as manufacturer_name " +
                "FROM aircraft_types t " +
                "JOIN manufacturers m ON t.manufacturer_id = m.id " +
                "WHERE t.manufacturer_id = ? " +
                "ORDER BY t.model";

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, manufacturer.getId());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    try {
                        AircraftType type = new AircraftType();
                        type.setId(rs.getLong("id"));

                        // Hersteller setzen
                        Manufacturer mfr = new Manufacturer();
                        mfr.setId(rs.getLong("manufacturer_id"));
                        mfr.setName(rs.getString("manufacturer_name"));
                        type.setManufacturer(mfr);

                        type.setModel(rs.getString("model"));
                        type.setPaxCapacity(rs.getInt("pax_capacity"));
                        type.setCargoCapacity(rs.getDouble("cargo_capacity"));
                        type.setMaxRangeKm(rs.getDouble("max_range_km"));
                        type.setSpeedKmh(rs.getDouble("speed_kmh"));
                        type.setCostPerHour(rs.getDouble("cost_per_h"));

                        types.add(type);
                    } catch (SQLException | IllegalArgumentException | NullPointerException e) {
                        logger.error("Fehler beim Extrahieren von Flugzeugtyp-Daten: {}", e.getMessage());
                    }
                }
            }

            logger.info("{} Flugzeugtypen für Hersteller {} geladen", types.size(), manufacturer.getName());

        } catch (SQLException e) {
            logger.error("Fehler beim Laden der Flugzeugtypen: {}", e.getMessage());
        }

        return types;
    }

    /**
     * Speichert einen Flugzeugtyp in der Datenbank (fügt ein oder aktualisiert).
     */
    public boolean saveAircraftType(AircraftType type) {
        if (type == null) {
            logger.warn("Versuch, einen null-Flugzeugtyp zu speichern");
            return false;
        }

        // Grundlegende Validierung
        if (type.getManufacturer() == null || type.getModel() == null || type.getModel().isEmpty()) {
            logger.warn("Ungültiger Flugzeugtyp: Hersteller oder Modell fehlt");
            return false;
        }

        // Sicherstellen, dass der Hersteller existiert
        if (type.getManufacturer().getId() == null) {
            logger.info("Speichere Hersteller: {}", type.getManufacturer().getName());
            boolean saved = manufacturerService.saveManufacturer(type.getManufacturer());
            if (!saved) {
                logger.error("Konnte Hersteller nicht speichern");
                return false;
            }
        }

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            if (type.getId() == null) {
                // Neuen Typ einfügen
                return insertAircraftType(conn, type);
            } else {
                // Vorhandenen Typ aktualisieren
                return updateAircraftType(conn, type);
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                logger.error("Fehler: Flugzeugtyp existiert bereits");
            } else {
                logger.error("SQL-Fehler beim Speichern des Flugzeugtyps: {}", e.getMessage());
            }
            return false;
        } catch (Exception e) {
            logger.error("Fehler beim Speichern des Flugzeugtyps: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Fügt einen neuen Flugzeugtyp in die Datenbank ein.
     */
    private boolean insertAircraftType(Connection conn, AircraftType type) throws SQLException {
        String sql = "INSERT INTO aircraft_types (manufacturer_id, model, pax_capacity, cargo_capacity, " +
                "max_range_km, speed_kmh, cost_per_h) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, type.getManufacturer().getId());
            stmt.setString(2, type.getModel());
            stmt.setInt(3, type.getPaxCapacity());
            stmt.setDouble(4, type.getCargoCapacity());
            stmt.setDouble(5, type.getMaxRangeKm());
            stmt.setDouble(6, type.getSpeedKmh());
            stmt.setDouble(7, type.getCostPerHour());

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        type.setId(keys.getLong(1));
                        logger.info("Neuer Flugzeugtyp gespeichert: {} (ID: {})",
                                type.getFullName(), type.getId());
                        return true;
                    } else {
                        logger.warn("Flugzeugtyp eingefügt, aber keine ID erhalten");
                        return false;
                    }
                }
            } else {
                logger.warn("Einfügen des Flugzeugtyps fehlgeschlagen");
                return false;
            }
        }
    }

    /**
     * Aktualisiert einen vorhandenen Flugzeugtyp in der Datenbank.
     */
    private boolean updateAircraftType(Connection conn, AircraftType type) throws SQLException {
        String sql = "UPDATE aircraft_types SET manufacturer_id=?, model=?, pax_capacity=?, " +
                "cargo_capacity=?, max_range_km=?, speed_kmh=?, cost_per_h=? WHERE id=?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, type.getManufacturer().getId());
            stmt.setString(2, type.getModel());
            stmt.setInt(3, type.getPaxCapacity());
            stmt.setDouble(4, type.getCargoCapacity());
            stmt.setDouble(5, type.getMaxRangeKm());
            stmt.setDouble(6, type.getSpeedKmh());
            stmt.setDouble(7, type.getCostPerHour());
            stmt.setLong(8, type.getId());

            int result = stmt.executeUpdate();
            if (result > 0) {
                logger.info("Flugzeugtyp aktualisiert: {} (ID: {})",
                        type.getFullName(), type.getId());
                return true;
            } else {
                logger.warn("Aktualisierung des Flugzeugtyps fehlgeschlagen");
                return false;
            }
        }
    }

    /**
     * Speichert ein Flugzeug in der Datenbank (fügt ein oder aktualisiert).
     */
    public boolean saveAircraft(Aircraft aircraft) {
        if (aircraft == null) {
            logger.warn("Versuch, ein null-Flugzeug zu speichern");
            return false;
        }

        if (aircraft.getType() == null || aircraft.getType().getId() == null) {
            logger.warn("Flugzeug ohne gültigen Typ");
            // Optional: Versuchen, den Typ zu speichern
            if (aircraft.getType() != null && !saveAircraftType(aircraft.getType())) {
                logger.error("Konnte Flugzeugtyp nicht speichern");
                return false;
            }
            if (aircraft.getType() == null || aircraft.getType().getId() == null) {
                logger.error("Flugzeugtyp fehlt");
                return false;
            }
        }

        // Standard-Airline verwenden
        if (aircraft.getAirline() == null) {
            logger.info("Standard-Airline wird verwendet");
            aircraft.setAirline(Airline.getInstance());
        }

        // Validierung des BuildDate
        if (aircraft.getBuildDate() == null) {
            logger.warn("Flugzeug ohne Baudatum");
            return false;
        }

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            if (aircraft.getId() == null) {
                return insertAircraft(conn, aircraft);
            } else {
                return updateAircraft(conn, aircraft);
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                logger.error("Fehler: Registrierung existiert bereits");
            } else {
                logger.error("SQL-Fehler beim Speichern des Flugzeugs: {}", e.getMessage());
            }
            return false;
        } catch (Exception e) {
            logger.error("Fehler beim Speichern des Flugzeugs: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Fügt ein neues Flugzeug in die Datenbank ein.
     */
    private boolean insertAircraft(Connection conn, Aircraft aircraft) throws SQLException {
        String sql = "INSERT INTO aircraft (type_id, registration_no, build_date, status, location_id) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, aircraft.getType().getId());
            stmt.setString(2, aircraft.getRegistrationNo());
            stmt.setDate(3, Date.valueOf(aircraft.getBuildDate()));
            stmt.setString(4, aircraft.getStatus().name());

            // Standort setzen
            if (aircraft.getCurrentLocation() != null && aircraft.getCurrentLocation().getId() != null) {
                stmt.setLong(5, aircraft.getCurrentLocation().getId());
            } else {
                stmt.setNull(5, Types.BIGINT);
            }

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        aircraft.setId(keys.getLong(1));
                        logger.info("Neues Flugzeug gespeichert: {} (ID: {})",
                                aircraft.getRegistrationNo(), aircraft.getId());
                        return true;
                    } else {
                        logger.warn("Flugzeug eingefügt, aber keine ID erhalten");
                        return false;
                    }
                }
            } else {
                logger.warn("Einfügen des Flugzeugs fehlgeschlagen");
                return false;
            }
        }
    }

    /**
     * Aktualisiert ein vorhandenes Flugzeug in der Datenbank.
     */
    private boolean updateAircraft(Connection conn, Aircraft aircraft) throws SQLException {
        String sql = "UPDATE aircraft SET type_id=?, registration_no=?, build_date=?, status=?, location_id=? " +
                "WHERE id=?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, aircraft.getType().getId());
            stmt.setString(2, aircraft.getRegistrationNo());
            stmt.setDate(3, Date.valueOf(aircraft.getBuildDate()));
            stmt.setString(4, aircraft.getStatus().name());

            // Standort setzen (kann null sein)
            if (aircraft.getCurrentLocation() != null && aircraft.getCurrentLocation().getId() != null) {
                stmt.setLong(5, aircraft.getCurrentLocation().getId());
            } else {
                stmt.setNull(5, Types.BIGINT);
            }

            stmt.setLong(6, aircraft.getId());

            int result = stmt.executeUpdate();
            if (result > 0) {
                logger.info("Flugzeug aktualisiert: {} (ID: {})",
                        aircraft.getRegistrationNo(), aircraft.getId());
                return true;
            } else {
                logger.warn("Aktualisierung des Flugzeugs fehlgeschlagen");
                return false;
            }
        }
    }

    /**
     * Aktualisiert ein vorhandenes Flugzeug in der Datenbank.
     */
    public boolean updateAircraft(Aircraft aircraft) {
        if (aircraft == null || aircraft.getId() == null) {
            logger.warn("Ungültiges Flugzeug für Aktualisierung");
            return false;
        }

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            return updateAircraft(conn, aircraft);
        } catch (SQLException e) {
            logger.error("SQL-Fehler beim Aktualisieren des Flugzeugs: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Fehler beim Aktualisieren des Flugzeugs: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Löscht ein Flugzeug aus der Datenbank
     */
    public boolean deleteAircraft(Aircraft aircraft) {
        if (aircraft == null || aircraft.getId() == null) {
            logger.warn("Ungültiges Flugzeug für Löschung");
            return false;
        }

        String sql = "DELETE FROM aircraft WHERE id = ?";

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, aircraft.getId());
            int result = stmt.executeUpdate();

            if (result > 0) {
                logger.info("Flugzeug gelöscht: {} (ID: {})",
                        aircraft.getRegistrationNo(), aircraft.getId());
                return true;
            } else {
                logger.warn("Löschen des Flugzeugs fehlgeschlagen");
                return false;
            }

        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("foreign key constraint fails")) {
                logger.error("Fehler: Flugzeug hat noch Abhängigkeiten (z.B. Flüge)");
            } else {
                logger.error("SQL-Fehler beim Löschen des Flugzeugs: {}", e.getMessage());
            }
            return false;
        } catch (Exception e) {
            logger.error("Fehler beim Löschen des Flugzeugs: {}", e.getMessage());
            return false;
        }
    }
}
