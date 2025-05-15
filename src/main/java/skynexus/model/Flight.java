package skynexus.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.enums.FlightStatus;
import skynexus.util.FlightUtils;
import skynexus.util.ValidationUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Repräsentiert eine spezifische Fluginistanz, einschließlich Passagierzahlen pro Klasse.
 * Ermöglicht Flugplanung und Auslastungsberechnungen.
 */
public class Flight {

    private static final Logger logger = LoggerFactory.getLogger(Flight.class);

    private Long id;
    private String flightNumber;
    private Route route;
    private Airport departureAirport;
    private Airport arrivalAirport;
    private LocalDate departureDate;
    private LocalTime departureTime;
    private Aircraft aircraft;
    private FlightStatus status;
    private double distanceKm;
    private int flightTimeMinutes;

    private double priceEconomy;
    private double priceBusiness;
    private double priceFirst;

    private int paxEconomy;
    private int paxBusiness;
    private int paxFirst;

    /**
     * Speichert ein explizit gesetztes Ankunftsdatum/-zeit.
     * Wenn null, wird die Ankunftszeit dynamisch basierend auf Abflug und Flugdauer berechnet.
     */
    private LocalDateTime arrivalDateTime;

    /**
     * Standardkonstruktor für Frameworks/Persistenzschichten.
     */
    public Flight() {
        this.status = FlightStatus.SCHEDULED;
        this.paxEconomy = 0;
        this.paxBusiness = 0;
        this.paxFirst = 0;
    }

    /**
     * Konstruktor zum Erstellen eines neuen Fluges basierend auf einer Route und wesentlichen Details.
     */
    public Flight(String flightNumber, Route route, LocalDate departureDate, LocalTime departureTime, Aircraft aircraft, double priceEconomy, double priceBusiness, double priceFirst, FlightStatus status) {
        this();
        this.setFlightNumber(flightNumber);
        this.setRoute(route);
        this.setDepartureDate(departureDate);
        this.setDepartureTime(departureTime);
        this.setAircraft(aircraft);
        this.setPriceEconomy(priceEconomy);
        this.setPriceBusiness(priceBusiness);
        this.setPriceFirst(priceFirst);
        this.setStatus(status);

        if (this.flightTimeMinutes <= 0) {
            logger.warn("Flugzeit für Flug {} konnte nicht initialisiert werden. Setze auf Standardwert 60.", flightNumber);
            this.setFlightTimeMinutes(60);
        }
    }

    /**
     * Gibt das kombinierte Abflugdatum und die Abflugzeit zurück.
     */
    public LocalDateTime getDepartureDateTime() {
        if (this.departureDate != null && this.departureTime != null) {
            return LocalDateTime.of(this.departureDate, this.departureTime);
        }
        return null;
    }

    /**
     * Gibt das kombinierte Ankunftsdatum und die Ankunftszeit zurück.
     */
    public LocalDateTime getArrivalDateTime() {
        if (this.arrivalDateTime != null) {
            return this.arrivalDateTime;
        }

        LocalDateTime departure = getDepartureDateTime();
        if (departure != null && this.flightTimeMinutes > 0) {
            return departure.plusMinutes(this.flightTimeMinutes);
        }
        return null;
    }

    /**
     * Gibt das berechnete Ankunftsdatum zurück.
     */
    public LocalDate getArrivalDate() {
        LocalDateTime arrivalDT = getArrivalDateTime();
        return (arrivalDT != null) ? arrivalDT.toLocalDate() : null;
    }

    /**
     * Gibt die berechnete Ankunftszeit zurück.
     */
    public LocalTime getArrivalTime() {
        LocalDateTime arrivalDT = getArrivalDateTime();
        return (arrivalDT != null) ? arrivalDT.toLocalTime() : null;
    }

    public int getTotalPax() {
        return this.paxEconomy + this.paxBusiness + this.paxFirst;
    }

    private void updateFlightTime() {
        String flightIdForLog = this.flightNumber != null ? this.flightNumber : (this.id != null ? "ID:" + this.id : "Unbekannt");

        if (this.aircraft == null || this.aircraft.getType() == null || this.distanceKm <= 0) {
            logger.debug("Flugzeit-Update übersprungen für Flug {}: Fehlende erforderliche Daten", flightIdForLog);
            return;
        }

        double speed = this.aircraft.getType().getSpeedKmh();
        if (speed <= 0) {
            logger.warn("Flugzeit-Update übersprungen für Flug {}: Ungültige Geschwindigkeit", flightIdForLog);
            return;
        }

        try {
            int newFlightTime = FlightUtils.calculateFlightTime(this.distanceKm, speed);
            this.setFlightTimeMinutes(newFlightTime);
            logger.debug("Flugzeit für Flug {} aktualisiert", flightIdForLog);
        } catch (Exception e) {
            logger.error("Fehler beim Aktualisieren der Flugzeit für Flug {}: {}", flightIdForLog, e.getMessage());
        }
    }

    /**
     * Formatiert die Flugzeit in das HH:MM-Format.
     */
    public String getFormattedFlightTime() {
        if (this.flightTimeMinutes <= 0) {
            return "--:--";
        }
        return FlightUtils.formatFlightTime(this.flightTimeMinutes);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public void setFlightNumber(String flightNumber) {
        ValidationUtils.validateFlightNumber(flightNumber);
        this.flightNumber = flightNumber;
    }

    public Route getRoute() {
        return route;
    }

    /**
     * Setzt die Route für den Flug.
     */
    public void setRoute(Route route) {
        ValidationUtils.validateNotNull(route, "Route");
        this.route = route;
        this.setDepartureAirport(route.getDepartureAirport());
        this.setArrivalAirport(route.getArrivalAirport());
        this.setDistanceKm(route.getDistanceKm());

        int routeTime = route.getFlightTimeMinutes();
        if (routeTime > 0) {
            this.setFlightTimeMinutes(routeTime);
        } else {
            logger.debug("Route {} hat keine gültige Flugzeit, verwende berechnete Zeit für Flug {}",
                    route.getRouteCode(), this.flightNumber);
        }
    }

    public Airport getDepartureAirport() {
        return departureAirport;
    }

    public void setDepartureAirport(Airport departureAirport) {
        ValidationUtils.validateNotNull(departureAirport, "Abflughafen");
        this.departureAirport = departureAirport;
    }

    public Airport getArrivalAirport() {
        return arrivalAirport;
    }

    public void setArrivalAirport(Airport arrivalAirport) {
        ValidationUtils.validateNotNull(arrivalAirport, "Zielflughafen");
        this.arrivalAirport = arrivalAirport;
    }

    public LocalDate getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(LocalDate departureDate) {
        ValidationUtils.validateNotNull(departureDate, "Abflugdatum");
        this.departureDate = departureDate;
    }

    public LocalTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalTime departureTime) {
        ValidationUtils.validateNotNull(departureTime, "Abflugzeit");
        this.departureTime = departureTime;
        this.arrivalDateTime = null;
    }

    public Aircraft getAircraft() {
        return aircraft;
    }

    /**
     * Setzt das Flugzeug für den Flug und aktualisiert die Flugzeit.
     */
    public void setAircraft(Aircraft aircraft) {
        ValidationUtils.validateNotNull(aircraft, "Flugzeug");
        boolean needsTimeUpdate = this.aircraft == null || !this.aircraft.equals(aircraft);
        this.aircraft = aircraft;

        if (needsTimeUpdate && this.distanceKm > 0) {
            updateFlightTime();
        } else if (needsTimeUpdate) {
            logger.debug("Flugzeug für Flug {} gesetzt, aber Flugzeit kann nicht aktualisiert werden",
                    this.flightNumber != null ? this.flightNumber : "unbekannt");
        }
    }

    public double getPriceEconomy() {
        return priceEconomy;
    }

    public void setPriceEconomy(double priceEconomy) {
        ValidationUtils.validateNotNegative(priceEconomy, "Economy-Preis");
        this.priceEconomy = priceEconomy;
    }

    public double getPriceBusiness() {
        return priceBusiness;
    }

    public void setPriceBusiness(double priceBusiness) {
        ValidationUtils.validateNotNegative(priceBusiness, "Business-Preis");
        this.priceBusiness = priceBusiness;
    }

    public double getPriceFirst() {
        return priceFirst;
    }

    public void setPriceFirst(double priceFirst) {
        ValidationUtils.validateNotNegative(priceFirst, "First-Class-Preis");
        this.priceFirst = priceFirst;
    }

    public FlightStatus getStatus() {
        return status;
    }

    public void setStatus(FlightStatus status) {
        ValidationUtils.validateNotNull(status, "Status");
        this.status = status;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    /**
     * Setzt die Flugdistanz und berechnet die Flugzeit neu.
     */
    public void setDistanceKm(double distanceKm) {
        ValidationUtils.validatePositive(distanceKm, "Flugdistanz");
        boolean needsTimeUpdate = this.distanceKm != distanceKm;
        this.distanceKm = distanceKm;

        if (needsTimeUpdate && this.aircraft != null) {
            updateFlightTime();
        } else if (needsTimeUpdate) {
            logger.debug("Distanz für Flug {} gesetzt, aber Flugzeit kann nicht aktualisiert werden",
                    this.flightNumber != null ? this.flightNumber : "unbekannt");
        }
    }

    public int getFlightTimeMinutes() {
        return flightTimeMinutes;
    }

    /**
     * Setzt die Flugzeit in Minuten.
     */
    public void setFlightTimeMinutes(int flightTimeMinutes) {
        ValidationUtils.validatePositive(flightTimeMinutes, "Flugzeit");
        if (this.flightTimeMinutes != flightTimeMinutes) {
            this.flightTimeMinutes = flightTimeMinutes;
            this.arrivalDateTime = null;
            logger.trace("Explizite Ankunftszeit für Flug {} gelöscht", flightNumber);
        }
    }

    /**
     * Setzt das explizite Ankunftsdatum und die Ankunftszeit.
     */
    public void setArrivalDateTime(LocalDateTime arrivalDateTime) {
        LocalDateTime departure = getDepartureDateTime();
        if (arrivalDateTime != null && departure != null && !arrivalDateTime.isAfter(departure)) {
            logger.warn("Ungültige explizite Ankunftszeit für Flug {}", flightNumber);
            throw new IllegalArgumentException("Explizite Ankunftszeit muss nach der Abflugzeit liegen.");
        }
        this.arrivalDateTime = arrivalDateTime;
        logger.debug("Ankunftszeit für Flug {} {}gesetzt", flightNumber,
                arrivalDateTime != null ? "" : "zurückgesetzt; wird dynamisch ");
    }

    public int getPaxEconomy() {
        return paxEconomy;
    }

    public void setPaxEconomy(int paxEconomy) {
        ValidationUtils.validateNotNegative(paxEconomy, "Economy-Passagiere");
        this.paxEconomy = paxEconomy;
    }

    public int getPaxBusiness() {
        return paxBusiness;
    }

    public void setPaxBusiness(int paxBusiness) {
        ValidationUtils.validateNotNegative(paxBusiness, "Business-Passagiere");
        this.paxBusiness = paxBusiness;
    }

    public int getPaxFirst() {
        return paxFirst;
    }

    public void setPaxFirst(int paxFirst) {
        ValidationUtils.validateNotNegative(paxFirst, "First-Class-Passagiere");
        this.paxFirst = paxFirst;
    }

    /**
     * Gibt eine String-Repräsentation des Fluges zurück.
     */
    @Override
    public String toString() {
        return String.format("Flight[%s: %s -> %s am %s %s, Status: %s, Flugzeug: %s, Pax: %d, Zeit: %s%s]",
                flightNumber != null ? flightNumber : "N/A",
                departureAirport != null ? departureAirport.getIcaoCode() : "N/A",
                arrivalAirport != null ? arrivalAirport.getIcaoCode() : "N/A",
                departureDate != null ? departureDate : "N/A",
                departureTime != null ? departureTime : "--:--",
                status != null ? status : "N/A",
                aircraft != null ? aircraft.getRegistrationNo() : "N/A",
                getTotalPax(),
                getFormattedFlightTime(),
                id != null ? String.format(", ID: %d", id) : "");
    }

    /**
     * Gibt eine kompakte Route-Darstellung für UI zurück.
     */
    public String getRouteDisplayName() {
        if (route != null) {
            return route.getDisplayName();
        }

        if (departureAirport != null && arrivalAirport != null) {
            String depCity = departureAirport.getCity() != null ? departureAirport.getCity() : departureAirport.getIcaoCode();
            String arrCity = arrivalAirport.getCity() != null ? arrivalAirport.getCity() : arrivalAirport.getIcaoCode();
            return depCity + " → " + arrCity;
        }

        return flightNumber != null ? flightNumber : "Unbekannter Flug";
    }

    /**
     * Vergleicht dieses Flugobjekt mit einem anderen Objekt auf Gleichheit.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Flight flight = (Flight) o;

        if (this.id != null && flight.id != null) {
            return this.id.equals(flight.id);
        }

        return Objects.equals(this.flightNumber, flight.flightNumber) &&
               Objects.equals(this.getDepartureDateTime(), flight.getDepartureDateTime()) &&
               Objects.equals(this.departureAirport, flight.departureAirport);
    }

    /**
     * Gibt einen Hashcode für dieses Flugobjekt zurück.
     */
    @Override
    public int hashCode() {
        if (id != null) {
            return Objects.hash(id);
        }
        return Objects.hash(flightNumber, getDepartureDateTime(), departureAirport);
    }
}
