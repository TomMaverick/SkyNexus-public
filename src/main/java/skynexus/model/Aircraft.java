package skynexus.model;

import skynexus.enums.AircraftStatus;
import skynexus.util.ValidationUtils;

import java.time.LocalDate;

/**
 * Modellklasse für ein konkretes Flugzeug mit eindeutiger Registrierungsnummer und spezifischen Daten.
 */
public class Aircraft {
    private Long id;
    private AircraftType type;
    private String registrationNo;
    private LocalDate buildDate;
    private AircraftStatus status;
    private Airline airline;
    private Airport currentLocation;

    /**
     * Konstruktor für ein neues Aircraft-Objekt mit Pflichtfeldern.
     *
     * @param type            Flugzeugtyp
     * @param registrationNo  Registrierungsnummer (z. B. "D-AIAB")
     * @param buildDate       Baujahr des Flugzeugs
     * @param airline         Fluggesellschaft, der das Flugzeug gehört
     * @param currentLocation Aktueller Standort des Flugzeugs (kann null sein, dann wird Default verwendet)
     * @throws IllegalArgumentException wenn Validierungsfehler auftreten
     */
    public Aircraft(AircraftType type, String registrationNo, LocalDate buildDate, Airline airline, Airport currentLocation) {
        this.setType(type);
        this.setRegistrationNo(registrationNo);
        this.setBuildDate(buildDate);
        this.setAirline(airline);
        this.setCurrentLocation(ValidationUtils.validateAndGetDefaultAirport(currentLocation));

        this.status = AircraftStatus.AVAILABLE;
    }

    /**
     * Gibt die ID des Flugzeugs zurück.
     *
     * @return ID des Flugzeugs
     */
    public Long getId() {
        return this.id;
    }

    /**
     * Setzt die ID des Flugzeugs.
     *
     * @param id Die neue ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Gibt den Flugzeugtyp zurück.
     *
     * @return Flugzeugtyp
     */
    public AircraftType getType() {
        return this.type;
    }

    /**
     * Setzt den Flugzeugtyp.
     *
     * @param type Der neue Flugzeugtyp
     * @throws IllegalArgumentException wenn der Typ null ist
     */
    public void setType(AircraftType type) {
        ValidationUtils.validateNotNull(type, "Flugzeugtyp");
        this.type = type;
    }

    /**
     * Gibt die Registrierungsnummer des Flugzeugs zurück.
     *
     * @return Registrierungsnummer
     */
    public String getRegistrationNo() {
        return this.registrationNo;
    }

    /**
     * Setzt die Registrierungsnummer des Flugzeugs.
     *
     * @param registrationNo Die neue Registrierungsnummer
     * @throws IllegalArgumentException wenn die Nummer leer ist
     */
    public void setRegistrationNo(String registrationNo) {
        ValidationUtils.validateNotEmpty(registrationNo, "Registrierungsnummer");
        this.registrationNo = registrationNo;
    }

    /**
     * Gibt das Baujahr des Flugzeugs zurück.
     *
     * @return Baujahr
     */
    public LocalDate getBuildDate() {
        return this.buildDate;
    }

    /**
     * Setzt das Baujahr des Flugzeugs.
     *
     * @param buildDate Das neue Baujahr
     * @throws IllegalArgumentException wenn das Datum null ist oder in der Zukunft liegt
     */
    public void setBuildDate(LocalDate buildDate) {
        ValidationUtils.validateNotNull(buildDate, "Baujahr");
        ValidationUtils.validateDateNotInFuture(buildDate, "Baujahr");
        this.buildDate = buildDate;
    }

    /**
     * Gibt den aktuellen Status des Flugzeugs zurück.
     *
     * @return Aktueller Status
     */
    public AircraftStatus getStatus() {
        return this.status;
    }

    /**
     * Setzt den Status des Flugzeugs.
     *
     * @param status Der neue Status
     * @throws IllegalArgumentException wenn der Status null ist
     */
    public void setStatus(AircraftStatus status) {
        ValidationUtils.validateNotNull(status, "Status");
        this.status = status;
    }

    /**
     * Gibt die Fluggesellschaft zurück, der das Flugzeug gehört.
     *
     * @return Fluggesellschaft
     */
    public Airline getAirline() {
        return airline;
    }

    /**
     * Setzt die Fluggesellschaft, der das Flugzeug gehört.
     *
     * @param airline Die neue Fluggesellschaft
     * @throws IllegalArgumentException wenn die Airline null ist
     */
    public void setAirline(Airline airline) {
        ValidationUtils.validateNotNull(airline, "Fluggesellschaft");
        this.airline = airline;
    }

    /**
     * Gibt den aktuellen Standort des Flugzeugs zurück.
     *
     * @return Der aktuelle Flughafen
     */
    public Airport getCurrentLocation() {
        return currentLocation;
    }

    /**
     * Setzt den aktuellen Standort des Flugzeugs.
     * Falls null übergeben wird, wird der Standard-Flughafen aus den Systemeinstellungen verwendet.
     *
     * @param currentLocation Der neue Standort des Flugzeugs (kann null sein)
     */
    public void setCurrentLocation(Airport currentLocation) {
        this.currentLocation = ValidationUtils.validateAndGetDefaultAirport(currentLocation);
    }

    /**
     * Liefert eine String-Repräsentation des Flugzeugs.
     *
     * @return String mit Typ, Registrierungsnummer und Standort des Flugzeugs
     */
    @Override
    public String toString() {
        return this.type.getFullName() + " (" + this.registrationNo + ") at " +
                (this.currentLocation != null ? this.currentLocation.getIcaoCode() : "unknown location");
    }
}
