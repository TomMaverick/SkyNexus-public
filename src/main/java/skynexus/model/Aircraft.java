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
     * @throws IllegalArgumentException wenn Validierungsfehler auftreten.
     */
    public Aircraft(AircraftType type, String registrationNo, LocalDate buildDate, Airline airline, Airport currentLocation) {
        this.setType(type);
        this.setRegistrationNo(registrationNo);
        this.setBuildDate(buildDate);
        this.setAirline(airline);
        this.setCurrentLocation(ValidationUtils.validateAndGetDefaultAirport(currentLocation));

        this.status = AircraftStatus.AVAILABLE;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AircraftType getType() {
        return this.type;
    }

    public void setType(AircraftType type) {
        ValidationUtils.validateNotNull(type, "Flugzeugtyp");
        this.type = type;
    }

    public String getRegistrationNo() {
        return this.registrationNo;
    }

    public void setRegistrationNo(String registrationNo) {
        ValidationUtils.validateNotEmpty(registrationNo, "Registrierungsnummer");
        this.registrationNo = registrationNo;
    }

    public LocalDate getBuildDate() {
        return this.buildDate;
    }

    public void setBuildDate(LocalDate buildDate) {
        ValidationUtils.validateNotNull(buildDate, "Baujahr");
        ValidationUtils.validateDateNotInFuture(buildDate, "Baujahr");
        this.buildDate = buildDate;
    }

    public AircraftStatus getStatus() {
        return this.status;
    }

    public void setStatus(AircraftStatus status) {
        ValidationUtils.validateNotNull(status, "Status");
        this.status = status;
    }

    public Airline getAirline() {
        return airline;
    }

    public void setAirline(Airline airline) {
        ValidationUtils.validateNotNull(airline, "Fluggesellschaft");
        this.airline = airline;
    }

    public Airport getCurrentLocation() {
        return currentLocation;
    }

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
