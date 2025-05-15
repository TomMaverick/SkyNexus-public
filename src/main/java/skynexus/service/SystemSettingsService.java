package skynexus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.database.DatabaseConnectionManager;
import skynexus.database.DatabaseHelper.SQLFunction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Service-Klasse für die Verwaltung von Systemeinstellungen.
 * Bietet Funktionen zum Lesen und Schreiben von Einstellungen in der Datenbank.
 * Implementiert das Singleton-Pattern.
 */
public class SystemSettingsService {
    // Schlüssel für Systemeinstellungen
    public static final String KEY_DEFAULT_AIRLINE_ID = "default_airline_id";
    public static final String KEY_DEFAULT_AIRPORT_ID = "default_airport_id";
    public static final String KEY_SYSTEM_INITIALIZED = "system_initialized";
    private static final Logger logger = LoggerFactory.getLogger(SystemSettingsService.class);
    private static SystemSettingsService instance;
    private Map<String, String> settingsCache;

    /**
     * Privater Konstruktor für Singleton-Muster
     */
    private SystemSettingsService() {
        this.settingsCache = new HashMap<>();
    }

    /**
     * Gibt die Singleton-Instanz zurück
     *
     * @return Die einzige Instanz des SystemSettingsService
     */
    public static synchronized SystemSettingsService getInstance() {
        if (instance == null) {
            instance = new SystemSettingsService();
        }
        return instance;
    }

    /**
     * Lädt alle Einstellungen aus der Datenbank
     *
     * @return Map mit allen Einstellungen
     */
    public Map<String, String> getAllSettings() {
        if (!settingsCache.isEmpty()) {
            return new HashMap<>(settingsCache);
        }

        String sql = "SELECT setting_key, setting_value FROM system_settings";

        try {
            Map<String, String> settings = withConnection(conn -> {
                Map<String, String> result = new HashMap<>();
                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        String key = rs.getString("setting_key");
                        String value = rs.getString("setting_value");
                        result.put(key, value);
                    }
                    return result;
                }
            });

            this.settingsCache = settings;
            return settings;
        } catch (SQLException e) {
            logger.error("Fehler beim Laden der Systemeinstellungen: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Gibt den Wert einer bestimmten Einstellung zurück
     *
     * @param key          Schlüssel der Einstellung
     * @param defaultValue Standardwert, falls die Einstellung nicht existiert
     * @return Wert der Einstellung oder Standardwert
     */
    public String getSetting(String key, String defaultValue) {
        if (settingsCache.isEmpty()) {
            getAllSettings();
        }

        return settingsCache.getOrDefault(key, defaultValue);
    }

    /**
     * Speichert eine Einstellung in der Datenbank
     *
     * @param key   Schlüssel der Einstellung
     * @param value Wert der Einstellung
     * @return true bei Erfolg, sonst false
     */
    public boolean saveSetting(String key, String value) {
        String sql = "INSERT INTO system_settings (setting_key, setting_value) " +
                "VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE setting_value = ?";

        try {
            boolean result = withConnection(conn -> {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, key);
                    stmt.setString(2, value);
                    stmt.setString(3, value);

                    int rowsAffected = stmt.executeUpdate();
                    return rowsAffected > 0;
                }
            });

            if (result) {
                // Cache aktualisieren
                settingsCache.put(key, value);
                logger.info("Einstellung gespeichert: {}={}", key, value);
            }

            return result;
        } catch (SQLException e) {
            logger.error("Fehler beim Speichern der Einstellung {}={}: {}",
                    key, value, e.getMessage());
            return false;
        }
    }

    /**
     * Prüft, ob das System bereits initialisiert wurde
     *
     * @return true wenn initialisiert, sonst false
     */
    public boolean isSystemInitialized() {
        String value = getSetting(KEY_SYSTEM_INITIALIZED, "false");
        return Boolean.parseBoolean(value);
    }

    /**
     * Markiert das System als initialisiert
     *
     * @return true bei Erfolg, sonst false
     */
    public boolean markSystemAsInitialized() {
        return saveSetting(KEY_SYSTEM_INITIALIZED, "true");
    }

    /**
     * Gibt die ID des Standard-Flughafens zurück
     *
     * @return ID des Standard-Flughafens oder 1 als Fallback
     */
    public Long getDefaultAirportId() {
        String value = getSetting(KEY_DEFAULT_AIRPORT_ID, "1");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            logger.warn("Ungültige default_airport_id: {}, verwende 1", value);
            return 1L;
        }
    }

    /**
     * Setzt die ID des Standard-Flughafens
     *
     * @param id ID des Flughafens
     * @return true bei Erfolg, sonst false
     */
    public boolean setDefaultAirportId(Long id) {
        return saveSetting(KEY_DEFAULT_AIRPORT_ID, id.toString());
    }

    /**
     * Setzt die ID der Standard-Airline
     *
     * @param id ID der Airline
     * @return true bei Erfolg, sonst false
     */
    public boolean setDefaultAirlineId(Long id) {
        return saveSetting(KEY_DEFAULT_AIRLINE_ID, id.toString());
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
     * Überprüft, ob die system_settings-Tabelle existiert, und erstellt sie bei Bedarf
     */
    public void ensureSystemSettingsTableExists() {
        String createTableSQL =
                "CREATE TABLE IF NOT EXISTS system_settings (" +
                        "setting_key VARCHAR(255) PRIMARY KEY, " +
                        "setting_value TEXT NOT NULL" +
                        ")";

        try {
            withConnection(conn -> {
                try (PreparedStatement stmt = conn.prepareStatement(createTableSQL)) {
                    stmt.execute();
                    logger.info("system_settings-Tabelle überprüft/erstellt");
                    return true;
                }
            });
        } catch (SQLException e) {
            logger.error("Fehler beim Erstellen der system_settings-Tabelle: {}", e.getMessage());
        }
    }
}
