package skynexus.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Properties;

/**
 * Utility-Klasse zum Laden und Speichern von Konfigurationseinstellungen.
 * Unterstützt separate Properties für Anwendung und Datenbankeinstellungen.
 */
public final class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);

    private static final String APP_CONFIG_FILE = "/config/app.properties";
    private static final String DB_CONFIG_FILE = "/config/db.properties";
    private static final String FLIGHT_CONFIG_FILE = "/config/flight.properties";
    private static final Properties appProperties = new Properties();
    private static final Properties dbProperties = new Properties();
    private static final Properties flightProperties = new Properties();

    static {
        loadProperties(APP_CONFIG_FILE, appProperties);
        loadProperties(DB_CONFIG_FILE, dbProperties);
        loadProperties(FLIGHT_CONFIG_FILE, flightProperties);
    }

    /**
     * Privater Konstruktor verhindert Instanziierung dieser Utility-Klasse.
     */
    private Config() {
    }

    /**
     * Lädt Properties aus einer Konfigurationsdatei.
     */
    private static void loadProperties(String fileName, Properties properties) {
        try (InputStream input = Config.class.getResourceAsStream(fileName)) {
            if (input == null) {
                logger.warn("Konfigurationsdatei nicht gefunden: {}", fileName);
                return;
            }
            properties.load(input);
            logger.debug("Konfigurationsdatei geladen: {}", fileName);
        } catch (IOException e) {
            logger.error("Fehler beim Laden der Konfigurationsdatei {}: {}", fileName, e.getMessage());
        }
    }

    // Anwendungseigenschaften

    /**
     * Liest eine Anwendungseigenschaft mit Standardwert.
     */
    public static String getAppProperty(String key, String defaultValue) {
        ValidationUtils.validateNotEmpty(key, "Eigenschaftsschlüssel");
        return appProperties.getProperty(key, defaultValue);
    }

    /**
     * Liest eine Anwendungseigenschaft als Integer mit Standardwert.
     */
    public static int getAppPropertyInt(String key, int defaultValue) {
        ValidationUtils.validateNotEmpty(key, "Eigenschaftsschlüssel");
        String value = appProperties.getProperty(key);
        try {
            return (value != null) ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            logger.warn("Ungültiger Integer-Wert für {}: {}", key, value);
            return defaultValue;
        }
    }

    // Datenbankeigenschaften

    /**
     * Liest eine Datenbankeigenschaft ohne Standardwert.
     */
    public static String getDbProperty(String key) {
        ValidationUtils.validateNotEmpty(key, "Eigenschaftsschlüssel");
        return dbProperties.getProperty(key);
    }

    /**
     * Liest eine Datenbankeigenschaft mit Standardwert.
     */
    public static String getDbProperty(String key, String defaultValue) {
        ValidationUtils.validateNotEmpty(key, "Eigenschaftsschlüssel");
        return dbProperties.getProperty(key, defaultValue);
    }

    /**
     * Liest eine Datenbankeigenschaft als Integer mit Standardwert.
     */
    public static int getDbPropertyInt(String key, int defaultValue) {
        ValidationUtils.validateNotEmpty(key, "Eigenschaftsschlüssel");
        String value = dbProperties.getProperty(key);
        try {
            return (value != null) ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            logger.warn("Ungültiger Integer-Wert für {}: {}", key, value);
            return defaultValue;
        }
    }

    // Speicher- und Lade-Methoden

    /**
     * Lädt alle Konfigurationen neu aus den Dateien.
     */
    public static void reloadAll() {
        logger.info("Konfigurationen werden neu geladen");
        appProperties.clear();
        dbProperties.clear();
        flightProperties.clear();
        loadProperties(APP_CONFIG_FILE, appProperties);
        loadProperties(DB_CONFIG_FILE, dbProperties);
        loadProperties(FLIGHT_CONFIG_FILE, flightProperties);
    }

    // Flug-Eigenschaften

    /**
     * Liest eine Flugeigenschaft als Integer mit Standardwert.
     */
    public static int getFlightPropertyInt(String key, int defaultValue) {
        ValidationUtils.validateNotEmpty(key, "Eigenschaftsschlüssel");
        String value = flightProperties.getProperty(key);
        try {
            return (value != null) ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            logger.warn("Ungültiger Integer-Wert für {}: {}", key, value);
            return defaultValue;
        }
    }

    /**
     * Liest eine Flugeigenschaft als Double mit Standardwert.
     */
    public static double getFlightPropertyDouble(String key, double defaultValue) {
        ValidationUtils.validateNotEmpty(key, "Eigenschaftsschlüssel");
        String value = flightProperties.getProperty(key);
        try {
            return (value != null) ? Double.parseDouble(value) : defaultValue;
        } catch (NumberFormatException e) {
            logger.warn("Ungültiger Double-Wert für {}: {}", key, value);
            return defaultValue;
        }
    }

    // Spezifische Flugkonfigurationen

    // Flugpreis-Properties
    public static double getBasePricePerKm() {
        return getFlightPropertyDouble("flight.basePricePerKm", 0.10);
    }

    public static double getEconomyFactor() {
        return getFlightPropertyDouble("flight.economyFactor", 1.0);
    }

    public static double getBusinessFactor() {
        return getFlightPropertyDouble("flight.businessFactor", 3.5);
    }

    public static double getFirstClassFactor() {
        return getFlightPropertyDouble("flight.firstClassFactor", 6.0);
    }

    // Flughafengebühren
    public static double getDomesticFee() {
        return getFlightPropertyDouble("flight.domesticFee", 25.0);
    }

    public static double getInternationalFee() {
        return getFlightPropertyDouble("flight.internationalFee", 75.0);
    }

    // Turnaround-Zeit
    public static int getMinTurnaroundMinutes() {
        return getFlightPropertyInt("flight.minTurnaroundMinutes", 60);
    }

    public static int getMaxTurnaroundHours() {
        return getFlightPropertyInt("flight.maxTurnaroundHours", 24);
    }

    public static int getDefaultTurnaroundHours() {
        return getFlightPropertyInt("flight.defaultTurnaroundHours", 1);
    }

    public static int getDefaultTurnaroundMinutes() {
        return getFlightPropertyInt("flight.defaultTurnaroundMinutes", 0);
    }

    // Flugzeitberechnung
    public static double getDefaultAircraftSpeed() {
        return getFlightPropertyDouble("flight.defaultAircraftSpeed", 800.0);
    }

    public static double getRouteDistanceBuffer() {
        return getFlightPropertyDouble("flight.routeDistanceBuffer", 1.1);
    }
}
