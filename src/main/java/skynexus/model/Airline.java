package skynexus.model;

import skynexus.service.SystemSettingsService;
import skynexus.util.ValidationUtils;

/**
 * Repräsentiert eine Fluggesellschaft mit ihrem Identifikationscode.
 * Diese Klasse implementiert das Singleton-Muster, da SkyNexus aktuell nur eine einzige
 * Airline im System unterstützt, deren Details aus den Systemeinstellungen geladen werden.
 * Die Fluggesellschaftsdaten werden in der system_settings-Tabelle mit folgenden Schlüsseln gespeichert:
 * - airline.name: Name der Fluggesellschaft
 * - airline.icao: ICAO-Code
 * - airline.country: Land der Fluggesellschaft
 */
public class Airline {
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
     * @return true, wenn das Speichern erfolgreich war, sonst false
     */
    public boolean saveChanges() {
        try {
            SystemSettingsService settingsService = SystemSettingsService.getInstance();
            settingsService.saveSetting("airline.name", this.name);
            settingsService.saveSetting("airline.icao", this.icaoCode);
            settingsService.saveSetting("airline.country", this.country);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        if (id != null && id != 1L) {
            this.id = 1L;
        } else {
            this.id = id != null ? id : DEFAULT_ID;
        }
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        ValidationUtils.validateNotEmpty(name, "Name");
        this.name = name;
    }

    public String getIcaoCode() {
        return this.icaoCode;
    }

    public void setIcaoCode(String icaoCode) {
        ValidationUtils.validateAirlineICAO(icaoCode);
        this.icaoCode = icaoCode;
    }

    public String getCountry() {
        return this.country;
    }

    public void setCountry(String country) {
        ValidationUtils.validateNotEmpty(country, "Land");
        this.country = country;
    }
}
