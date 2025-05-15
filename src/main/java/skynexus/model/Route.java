package skynexus.model;

import skynexus.util.FlightUtils;
import skynexus.util.TimeUtils;
import skynexus.util.ValidationUtils;

/**
 * Repräsentiert eine Route zwischen zwei Flughäfen.
 * Eine Route kann mehrere konkrete Flüge haben.
 * Verwendet vereinfachte Route-Codes im Format "EDDF-KJFK".
 */
public class Route {
    private Long id;
    private String routeCode;
    private Airport departureAirport;
    private Airport arrivalAirport;
    private double distanceKm;
    private int flightTimeMinutes;
    private Airline operator;
    private boolean active;

    /**
     * Erstellt eine leere Route mit aktivem Status.
     */
    public Route() {
        this.active = true;
    }

    /**
     * Erstellt eine Route mit grundlegenden Attributen und berechnet automatisch
     * den Routencode und die Distanz.
     *
     * @param departureAirport Abflughafen
     * @param arrivalAirport Zielflughafen
     * @param operator Betreibende Fluggesellschaft
     * @throws IllegalArgumentException wenn einer der Parameter null ist
     */
    public Route(Airport departureAirport, Airport arrivalAirport, Airline operator) {
        this();
        setDepartureAirport(departureAirport);
        setArrivalAirport(arrivalAirport);
        setOperator(operator);
        generateRouteCode();
        calculateDistance();
    }

    /**
     * Generiert einen Route-Code basierend auf Operator und Flughäfen.
     * Format: "EDDF-KJFK"
     */
    private void generateRouteCode() {
        if (this.operator != null && this.operator.getIcaoCode() != null &&
                this.departureAirport != null && this.arrivalAirport != null) {

            this.routeCode = FlightUtils.generateRouteCode(
                    this.departureAirport.getIcaoCode(),
                    this.arrivalAirport.getIcaoCode());
        }
    }

    /**
     * Berechnet die Distanz zwischen den Flughäfen und schätzt die Flugzeit.
     * Verwendet die Haversine-Formel für die Distanzberechnung und eine
     * Standardgeschwindigkeit von 800 km/h für die initiale Flugzeitschätzung.
     */
    private void calculateDistance() {
        if (this.departureAirport != null && this.arrivalAirport != null) {
            setDistanceKm(FlightUtils.calculateDistance(this.departureAirport, this.arrivalAirport));

            double defaultSpeed = 800.0; // km/h
            int estimatedMinutes = FlightUtils.calculateFlightTime(this.distanceKm, defaultSpeed);
            setFlightTimeMinutes(estimatedMinutes > 0 ? estimatedMinutes : 60);
        }
    }

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

    /**
     * Setzt den Abflughafen und aktualisiert den Route-Code, wenn alle notwendigen
     * Informationen vorhanden sind.
     *
     * @param departureAirport Der neue Abflughafen
     * @throws IllegalArgumentException wenn der Abflughafen null ist
     */
    public void setDepartureAirport(Airport departureAirport) {
        ValidationUtils.validateNotNull(departureAirport, "Abflughafen");
        this.departureAirport = departureAirport;

        if (this.arrivalAirport != null && this.operator != null) {
            generateRouteCode();
        }
    }

    public Airport getArrivalAirport() {
        return this.arrivalAirport;
    }

    /**
     * Setzt den Zielflughafen und aktualisiert den Route-Code, wenn alle notwendigen
     * Informationen vorhanden sind.
     *
     * @param arrivalAirport Der neue Zielflughafen
     * @throws IllegalArgumentException wenn der Zielflughafen null ist
     */
    public void setArrivalAirport(Airport arrivalAirport) {
        ValidationUtils.validateNotNull(arrivalAirport, "Zielflughafen");
        this.arrivalAirport = arrivalAirport;

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

    /**
     * Setzt die betreibende Fluggesellschaft und aktualisiert den Route-Code,
     * wenn alle notwendigen Informationen vorhanden sind.
     *
     * @param operator Die neue betreibende Fluggesellschaft
     * @throws IllegalArgumentException wenn der Operator null ist
     */
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

    public String getFormattedFlightTime() {
        return TimeUtils.formatMinutesAsHHMM(this.flightTimeMinutes);
    }

    /**
     * Erstellt eine String-Repräsentation der Route.
     *
     * @return Die Route im Format "EDDF-KJFK (EDDF -> KJFK, 08:20)"
     */
    @Override
    public String toString() {
        return this.routeCode + " (" + this.departureAirport.getIcaoCode() + " -> " +
                this.arrivalAirport.getIcaoCode() + ", " + getFormattedFlightTime() + ")";
    }

    /**
     * Gibt eine kompakte Route-Darstellung für UI zurück.
     * Format: "Frankfurt → New York"
     *
     * @return Kompakte Routendarstellung
     */
    public String getDisplayName() {
        if (departureAirport == null || arrivalAirport == null) {
            return routeCode != null ? routeCode : "Unbekannte Route";
        }

        String depCity = departureAirport.getCity() != null ? departureAirport.getCity() : departureAirport.getIcaoCode();
        String arrCity = arrivalAirport.getCity() != null ? arrivalAirport.getCity() : arrivalAirport.getIcaoCode();

        return depCity + " → " + arrCity;
    }
}
