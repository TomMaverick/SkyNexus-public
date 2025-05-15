package skynexus.model;

import skynexus.util.ValidationUtils;

/**
 * Repräsentiert einen Flughafen mit seiner geografischen Lage und Identifikationscode.
 * Speichert ICAO-Code und Koordinaten des Flughafens für die Standortbestimmung und Distanzberechnung.
 */
public class Airport {
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


    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIcaoCode() {
        return this.icaoCode;
    }

    public void setIcaoCode(String icaoCode) {
        ValidationUtils.validateAirportICAO(icaoCode);
        this.icaoCode = icaoCode;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        ValidationUtils.validateNotEmpty(name, "Name");
        this.name = name;
    }

    public String getCity() {
        return this.city;
    }

    public void setCity(String city) {
        ValidationUtils.validateNotEmpty(city, "Stadt");
        this.city = city;
    }

    public String getCountry() {
        return this.country;
    }

    public void setCountry(String country) {
        ValidationUtils.validateNotEmpty(country, "Land");
        this.country = country;
    }

    public double getLatitude() {
        return this.latitude;
    }

    public void setLatitude(double latitude) {
        ValidationUtils.validateLatitude(latitude);
        this.latitude = latitude;
    }

    public double getLongitude() {
        return this.longitude;
    }

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
