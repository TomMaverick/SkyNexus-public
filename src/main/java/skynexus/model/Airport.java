package skynexus.model;

import skynexus.util.ValidationUtils;

/**
 * Repräsentiert einen Flughafen mit seiner geografischen Lage und Identifikationscodes.
 * Speichert ICAO-Code und Koordinaten des Flughafens für die Standortbestimmung und Distanzberechnung.
 */
public class Airport {
    // Attribute
    private Long id;
    private String icaoCode;
    private String name;
    private String city;
    private String country;
    private double latitude;  // Breitengrad in Dezimalgrad (-90 bis 90), z.B. 50.0379 für Frankfurt
    private double longitude; // Längengrad in Dezimalgrad (-180 bis 180), z.B. 8.5622 für Frankfurt

    /**
     * Standard-Konstruktor
     */
    public Airport() {
    }

    /**
     * Konstruktor mit Pflichtfeldern
     *
     * @param icaoCode 4-stelliger ICAO-Code
     * @param name     Name des Flughafens
     * @param city     Stadt des Flughafens
     * @param country  Land des Flughafens
     * @throws IllegalArgumentException wenn Validierungsfehler auftreten
     */
    public Airport(String icaoCode, String name, String city, String country) {
        this.setIcaoCode(icaoCode);
        this.setName(name);
        this.setCity(city);
        this.setCountry(country);
    }

    /**
     * Vollständiger Konstruktor mit allen Daten inklusive Koordinaten
     *
     * @param icaoCode  4-stelliger ICAO-Code
     * @param name      Name des Flughafens
     * @param city      Stadt des Flughafens
     * @param country   Land des Flughafens
     * @param latitude  Breitengrad in Dezimalgrad (-90 bis 90)
     * @param longitude Längengrad in Dezimalgrad (-180 bis 180)
     * @throws IllegalArgumentException wenn Validierungsfehler auftreten
     */
    public Airport(String icaoCode, String name, String city, String country, double latitude, double longitude) {
        this.setIcaoCode(icaoCode);
        this.setName(name);
        this.setCity(city);
        this.setCountry(country);
        this.setLatitude(latitude);
        this.setLongitude(longitude);
    }

    /**
     * Gibt die ID des Flughafens zurück.
     *
     * @return ID des Flughafens
     */
    public Long getId() {
        return this.id;
    }

    /**
     * Setzt die ID des Flughafens.
     *
     * @param id Die neue ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Gibt den ICAO-Code des Flughafens zurück.
     *
     * @return ICAO-Code des Flughafens
     */
    public String getIcaoCode() {
        return this.icaoCode;
    }

    /**
     * Setzt den ICAO-Code des Flughafens.
     *
     * @param icaoCode Der neue ICAO-Code
     * @throws IllegalArgumentException wenn der ICAO-Code ungültig ist
     */
    public void setIcaoCode(String icaoCode) {
        ValidationUtils.validateAirportICAO(icaoCode);
        this.icaoCode = icaoCode;
    }

    /**
     * Gibt den Namen des Flughafens zurück.
     *
     * @return Name des Flughafens
     */
    public String getName() {
        return this.name;
    }

    /**
     * Setzt den Namen des Flughafens.
     *
     * @param name Der neue Name
     * @throws IllegalArgumentException wenn der Name leer ist
     */
    public void setName(String name) {
        ValidationUtils.validateNotEmpty(name, "Name");
        this.name = name;
    }

    /**
     * Gibt die Stadt des Flughafens zurück.
     *
     * @return Stadt des Flughafens
     */
    public String getCity() {
        return this.city;
    }

    /**
     * Setzt die Stadt des Flughafens.
     *
     * @param city Die neue Stadt
     * @throws IllegalArgumentException wenn die Stadt leer ist
     */
    public void setCity(String city) {
        ValidationUtils.validateNotEmpty(city, "Stadt");
        this.city = city;
    }

    /**
     * Gibt das Land des Flughafens zurück.
     *
     * @return Land des Flughafens
     */
    public String getCountry() {
        return this.country;
    }

    /**
     * Setzt das Land des Flughafens.
     *
     * @param country Das neue Land
     * @throws IllegalArgumentException wenn das Land leer ist
     */
    public void setCountry(String country) {
        ValidationUtils.validateNotEmpty(country, "Land");
        this.country = country;
    }

    /**
     * Gibt den Breitengrad des Flughafens zurück.
     * Wird für Distanzberechnungen und geografische Darstellungen verwendet.
     *
     * @return Breitengrad des Flughafens in Dezimalgrad
     */
    public double getLatitude() {
        return this.latitude;
    }

    /**
     * Setzt den Breitengrad des Flughafens.
     * Pflichtfeld für Distanzberechnungen zwischen Flughäfen.
     *
     * @param latitude Der neue Breitengrad in Dezimalgrad
     * @throws IllegalArgumentException wenn der Breitengrad außerhalb des gültigen Bereichs liegt
     */
    public void setLatitude(double latitude) {
        ValidationUtils.validateLatitude(latitude);
        this.latitude = latitude;
    }

    /**
     * Gibt den Längengrad des Flughafens zurück.
     * Wird für Distanzberechnungen und geografische Darstellungen verwendet.
     *
     * @return Längengrad des Flughafens in Dezimalgrad
     */
    public double getLongitude() {
        return this.longitude;
    }

    /**
     * Setzt den Längengrad des Flughafens.
     * Pflichtfeld für Distanzberechnungen zwischen Flughäfen.
     *
     * @param longitude Der neue Längengrad in Dezimalgrad
     * @throws IllegalArgumentException wenn der Längengrad außerhalb des gültigen Bereichs liegt
     */
    public void setLongitude(double longitude) {
        ValidationUtils.validateLongitude(longitude);
        this.longitude = longitude;
    }

    /**
     * Liefert eine String-Repräsentation des Flughafens.
     *
     * @return Formatierte Darstellung: ICAO - Name
     */
    @Override
    public String toString() {
        return this.icaoCode + " - " + this.name;
    }
}
