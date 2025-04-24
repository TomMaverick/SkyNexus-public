package skynexus.model;

import skynexus.service.SystemSettingsService;
import skynexus.util.ValidationUtils;

/**
 * Repräsentiert eine Fluggesellschaft mit ihren Identifikationscodes.
 * Diese Klasse implementiert das Singleton-Muster, da SkyNexus aktuell nur eine einzige
 * Airline im System unterstützt, deren Details aus den Systemeinstellungen geladen werden.
 * Die Fluggesellschaftsdaten werden in der system_settings-Tabelle mit folgenden Schlüsseln gespeichert:
 * - airline.name: Name der Fluggesellschaft
 * - airline.icao: ICAO-Code
 * - airline.country: Land der Fluggesellschaft
 */
public class Airline {
    // Standardwerte
    private static final String DEFAULT_NAME = "SkyNexus Air";
    private static final String DEFAULT_ICAO_CODE = "SNX";
    private static final String DEFAULT_COUNTRY = "Deutschland";
    private static final Long DEFAULT_ID = 1L;

    // Singleton-Instanz
    private static Airline instance;

    // Attribute
    private Long id;
    private String name;
    private String icaoCode;
    private String country;

    /**
     * Privater Konstruktor zum Verhindern von direkten Instanziierungen.
     * Initialisiert die Airline mit Default-Werten.
     */
    private Airline() {
        this.id = DEFAULT_ID;
        this.name = DEFAULT_NAME;
        this.icaoCode = DEFAULT_ICAO_CODE;
        this.country = DEFAULT_COUNTRY;
    }

    /**
     * Gibt die Singleton-Instanz der Airline zurück.
     * Lädt die Airline-Daten aus den Systemeinstellungen.
     *
     * @return Die einzige Instanz der Airline
     */
    public static synchronized Airline getInstance() {
        if (instance == null) {
            instance = new Airline();
            loadFromSystemSettings();
        }
        return instance;
    }

    /**
     * Lädt die Airline-Daten aus den Systemeinstellungen.
     */
    private static void loadFromSystemSettings() {
        try {
            SystemSettingsService settingsService = SystemSettingsService.getInstance();

            // Daten aus den Systemeinstellungen laden
            String name = settingsService.getSetting("airline.name", DEFAULT_NAME);
            String icaoCode = settingsService.getSetting("airline.icao", DEFAULT_ICAO_CODE);
            String country = settingsService.getSetting("airline.country", DEFAULT_COUNTRY);

            // Daten setzen mit Validierung durch die Setter
            instance.setName(name);
            instance.setIcaoCode(icaoCode);
            instance.setCountry(country);
        } catch (Exception e) {
            // Bei einem Fehler werden die Standardwerte beibehalten
        }
    }

    /**
     * Speichert die Änderungen an der Airline in den Systemeinstellungen.
     *
     * @return true wenn das Speichern erfolgreich war, sonst false
     */
    public boolean saveChanges() {
        try {
            SystemSettingsService settingsService = SystemSettingsService.getInstance();

            // Alle Werte speichern
            settingsService.saveSetting("airline.name", this.name);
            settingsService.saveSetting("airline.icao", this.icaoCode);
            settingsService.saveSetting("airline.country", this.country);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gibt die ID der Airline zurück.
     *
     * @return ID der Airline (immer 1)
     */
    public Long getId() {
        return this.id;
    }

    /**
     * Setzt die ID der Airline.
     * In der Singleton-Architektur bleibt die ID immer 1.
     *
     * @param id Die zu setzende ID (wird auf 1 normalisiert)
     */
    public void setId(Long id) {
        if (id != null && id != 1L) {
            // Wir setzen die ID trotzdem, aber nur auf 1
            this.id = 1L;
        } else {
            this.id = id != null ? id : DEFAULT_ID;
        }
    }

    /**
     * Gibt den Namen der Airline zurück.
     *
     * @return Name der Airline
     */
    public String getName() {
        return this.name;
    }

    /**
     * Setzt den Namen der Airline.
     *
     * @param name Der neue Name
     * @throws IllegalArgumentException wenn der Name leer ist
     */
    public void setName(String name) {
        ValidationUtils.validateNotEmpty(name, "Name");
        this.name = name;
    }

    /**
     * Gibt den ICAO-Code der Airline zurück.
     *
     * @return ICAO-Code der Airline
     */
    public String getIcaoCode() {
        return this.icaoCode;
    }

    /**
     * Setzt den ICAO-Code der Airline.
     *
     * @param icaoCode Der neue ICAO-Code
     * @throws IllegalArgumentException wenn der ICAO-Code ungültig ist
     */
    public void setIcaoCode(String icaoCode) {
        ValidationUtils.validateAirlineICAO(icaoCode);
        this.icaoCode = icaoCode;
    }

    /**
     * Gibt das Land der Airline zurück.
     *
     * @return Land der Airline
     */
    public String getCountry() {
        return this.country;
    }

    /**
     * Setzt das Land der Airline.
     *
     * @param country Das neue Land
     * @throws IllegalArgumentException wenn das Land leer ist
     */
    public void setCountry(String country) {
        ValidationUtils.validateNotEmpty(country, "Land");
        this.country = country;
    }

    /**
     * Setzt die Airline auf ihre Standardwerte zurück.
     * Dies kann nützlich sein, wenn die Airline-Konfiguration fehlschlägt.
     */
    public void resetToDefaults() {
        this.name = DEFAULT_NAME;
        this.icaoCode = DEFAULT_ICAO_CODE;
        this.country = DEFAULT_COUNTRY;
    }

    /**
     * Lädt die Airline-Daten neu aus den Systemeinstellungen.
     */
    public void reload() {
        loadFromSystemSettings();
    }
}
