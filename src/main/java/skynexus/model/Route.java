package skynexus.model;

import skynexus.util.FlightUtils;
import skynexus.util.ValidationUtils;

/**
 * Repräsentiert eine Route zwischen zwei Flughäfen.
 * Eine Route kann mehrere konkrete Flüge haben.
 */
public class Route {
    private Long id;                     // Primärschlüssel in der Datenbank
    private String routeCode;            // Eindeutiger Route-Code, z.B. "DLH-EDDF-KJFK"
    private Airport departureAirport;    // Abflughafen
    private Airport arrivalAirport;      // Zielflughafen
    private double distanceKm;           // Entfernung in Kilometern, z.B. 6200.0
    private int flightTimeMinutes;       // Flugzeit in Minuten, z.B. 480
    private Airline operator;            // Betreibende Fluggesellschaft
    private boolean active;              // Ist die Route aktiv?

    /**
     * Default-Konstruktor
     */
    public Route() {
        this.active = true;
    }

    /**
     * Konstruktor mit grundlegenden Attributen
     */
    public Route(Airport departureAirport, Airport arrivalAirport, Airline operator) {
        this(); // Ruft den Default-Konstruktor auf
        this.setDepartureAirport(departureAirport);
        this.setArrivalAirport(arrivalAirport);
        this.setOperator(operator);
        generateRouteCode();
        calculateDistance();
    }

    /**
     * Generiert einen Route-Code basierend auf Operator und Flughäfen
     */
    private void generateRouteCode() {
        if (this.operator != null && this.operator.getIcaoCode() != null &&
                this.departureAirport != null && this.arrivalAirport != null) {

            this.routeCode = FlightUtils.generateRouteCode(
                    this.operator.getIcaoCode(),
                    this.departureAirport.getIcaoCode(),
                    this.arrivalAirport.getIcaoCode());
        }
    }

    /**
     * Berechnet die Distanz zwischen den Flughäfen und schätzt die Flugzeit
     */
    private void calculateDistance() {
        if (this.departureAirport != null && this.arrivalAirport != null) {
            // Distanz mit Haversine-Formel berechnen
            setDistanceKm(FlightUtils.calculateDistance(this.departureAirport, this.arrivalAirport));

            // Standardgeschwindigkeit für eine vorläufige Berechnung
            double defaultSpeed = 800.0; // km/h
            int estimatedMinutes = FlightUtils.calculateFlightTime(this.distanceKm, defaultSpeed);
            setFlightTimeMinutes(estimatedMinutes > 0 ? estimatedMinutes : 60); // Mindestens 60 Minuten
        }
    }

    // Getter und Setter mit zentraler Validierung

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRouteCode() {
        return this.routeCode;
    }

    public void setRouteCode(String routeCode) {
        this.routeCode = routeCode;
    }

    public Airport getDepartureAirport() {
        return this.departureAirport;
    }

    public void setDepartureAirport(Airport departureAirport) {
        ValidationUtils.validateNotNull(departureAirport, "Abflughafen");
        this.departureAirport = departureAirport;
        // Route-Code aktualisieren, wenn sich ein Flughafen ändert
        if (this.arrivalAirport != null && this.operator != null) {
            generateRouteCode();
        }
    }

    public Airport getArrivalAirport() {
        return this.arrivalAirport;
    }

    public void setArrivalAirport(Airport arrivalAirport) {
        ValidationUtils.validateNotNull(arrivalAirport, "Zielflughafen");
        this.arrivalAirport = arrivalAirport;
        // Route-Code aktualisieren, wenn sich ein Flughafen ändert
        if (this.departureAirport != null && this.operator != null) {
            generateRouteCode();
        }
    }

    public double getDistanceKm() {
        return this.distanceKm;
    }

    public void setDistanceKm(double distanceKm) {
        ValidationUtils.validateNotNegative(distanceKm, "Distanz");
        this.distanceKm = distanceKm;
    }

    public int getFlightTimeMinutes() {
        return this.flightTimeMinutes;
    }

    public void setFlightTimeMinutes(int flightTimeMinutes) {
        ValidationUtils.validatePositive(flightTimeMinutes, "Flugzeit");
        this.flightTimeMinutes = flightTimeMinutes;
    }

    public Airline getOperator() {
        return this.operator;
    }

    public void setOperator(Airline operator) {
        ValidationUtils.validateNotNull(operator, "Betreiber");
        this.operator = operator;
        if (this.departureAirport != null && this.arrivalAirport != null) {
            generateRouteCode();
        }
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Formatierte Darstellung der Flugzeit
     *
     * @return Flugzeit im Format HH:MM
     */
    public String getFormattedFlightTime() {
        int hours = this.flightTimeMinutes / 60;
        int minutes = this.flightTimeMinutes % 60;
        return String.format("%02d:%02d", hours, minutes);
    }

    @Override
    public String toString() {
        return this.routeCode + " (" + this.departureAirport.getIcaoCode() + " -> " +
                this.arrivalAirport.getIcaoCode() + ", " + getFormattedFlightTime() + ")";
    }
}
