package skynexus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.database.DatabaseConnectionManager;
import skynexus.model.Manufacturer;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Service-Klasse für die Verwaltung von Flugzeugherstellern.
 */
public class ManufacturerService {
    private static final Logger logger = LoggerFactory.getLogger(ManufacturerService.class);
    private static ManufacturerService instance;

    private ManufacturerService() {
    }

    public static synchronized ManufacturerService getInstance() {
        if (instance == null) {
            instance = new ManufacturerService();
        }
        return instance;
    }

    /**
     * Lädt alle verfügbaren Flugzeughersteller aus der Datenbank
     */
    public List<Manufacturer> getAllManufacturers() {
        List<Manufacturer> manufacturers = new ArrayList<>();
        String sql = "SELECT id, name FROM manufacturers ORDER BY name";

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                try {
                    Manufacturer manufacturer = new Manufacturer();
                    manufacturer.setId(rs.getLong("id"));
                    manufacturer.setName(rs.getString("name"));
                    manufacturers.add(manufacturer);
                } catch (Exception e) {
                    logger.error("Fehler beim Verarbeiten eines Herstellers: {}", e.getMessage());
                }
            }
            logger.info("{} Hersteller geladen", manufacturers.size());

        } catch (SQLException e) {
            logger.error("Fehler beim Laden der Hersteller: {}", e.getMessage());
        }

        return manufacturers;
    }

    /**
     * Speichert einen Hersteller in der Datenbank
     */
    public boolean saveManufacturer(Manufacturer manufacturer) {
        if (manufacturer == null) {
            logger.warn("Versuch, einen null-Hersteller zu speichern");
            return false;
        }

        if (manufacturer.getName() == null || manufacturer.getName().isEmpty()) {
            logger.warn("Hersteller ohne Namen kann nicht gespeichert werden");
            return false;
        }

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            if (manufacturer.getId() == null) {
                return insertManufacturer(conn, manufacturer);
            } else {
                return updateManufacturer(conn, manufacturer);
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                logger.error("Fehler: Hersteller existiert bereits");
            } else {
                logger.error("Fehler beim Speichern des Herstellers: {}", e.getMessage());
            }
            return false;
        }
    }

    /**
     * Fügt einen neuen Hersteller ein
     */
    private boolean insertManufacturer(Connection conn, Manufacturer manufacturer) throws SQLException {
        String sql = "INSERT INTO manufacturers (name) VALUES (?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, manufacturer.getName());

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        manufacturer.setId(keys.getLong(1));
                        logger.info("Neuer Hersteller gespeichert: {} (ID: {})",
                                manufacturer.getName(), manufacturer.getId());
                        return true;
                    } else {
                        logger.warn("Hersteller eingefügt, aber keine ID erhalten");
                        return false;
                    }
                }
            } else {
                logger.warn("Einfügen des Herstellers fehlgeschlagen");
                return false;
            }
        }
    }

    /**
     * Aktualisiert einen vorhandenen Hersteller
     */
    private boolean updateManufacturer(Connection conn, Manufacturer manufacturer) throws SQLException {
        String sql = "UPDATE manufacturers SET name = ? WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, manufacturer.getName());
            stmt.setLong(2, manufacturer.getId());

            int result = stmt.executeUpdate();
            if (result > 0) {
                logger.info("Hersteller aktualisiert: {} (ID: {})",
                        manufacturer.getName(), manufacturer.getId());
                return true;
            } else {
                logger.warn("Aktualisierung des Herstellers fehlgeschlagen");
                return false;
            }
        }
    }
}
