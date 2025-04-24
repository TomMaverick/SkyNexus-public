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
 * Beinhaltet Unterstützung für Rückflüge.
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
     * Nützlich für Fälle, in denen die Ankunftszeit manuell angepasst wird oder genau bekannt ist.
     */
    private LocalDateTime arrivalDateTime;

    /**
     * Standardkonstruktor für Frameworks/Persistenzschichten.
     * Initialisiert Standardwerte für Flags und potenziell komplexe Typen.
     */
    public Flight() {
        this.status = FlightStatus.SCHEDULED;
        this.paxEconomy = 0;
        this.paxBusiness = 0;
        this.paxFirst = 0;
    }

    /**
     * Konstruktor zum Erstellen eines neuen Fluges basierend auf einer Route und wesentlichen Details.
     * Validiert alle Pflichteingaben und berechnet die initiale Flugzeit. Verwendet Setter zur Initialisierung.
     *
     * @param flightNumber  Die Flugnummer (z.B. "SNX100"). Muss gültig sein.
     * @param route         Die geplante Route. Darf nicht null sein. Liefert Flughäfen, Distanz.
     * @param departureDate Das Abflugdatum. Darf nicht null sein.
     * @param departureTime Die Abflugzeit. Darf nicht null sein.
     * @param aircraft      Das zugewiesene Flugzeug. Darf nicht null sein.
     * @param priceEconomy  Preis für Economy Class. Darf nicht negativ sein.
     * @param priceBusiness Preis für Business Class. Darf nicht negativ sein.
     * @param priceFirst    Preis für First Class. Darf nicht negativ sein.
     * @param status        Der initiale Flugstatus. Darf nicht null sein.
     * @throws IllegalArgumentException wenn eine Validierung fehlschlägt.
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
            this.setFlightTimeMinutes(60); // Setze einen minimalen Standardwert
        }
    }

    /**
     * Gibt das kombinierte Abflugdatum und die Abflugzeit zurück.
     *
     * @return LocalDateTime des Abflugs oder null, wenn Datum oder Zeit nicht gesetzt sind.
     */
    public LocalDateTime getDepartureDateTime() {
        if (this.departureDate != null && this.departureTime != null) {
            return LocalDateTime.of(this.departureDate, this.departureTime);
        }
        return null;
    }

    /**
     * Gibt das kombinierte Ankunftsdatum und die Ankunftszeit zurück.
     * Gibt die explizit gesetzte Ankunftszeit zurück, falls verfügbar, andernfalls wird sie dynamisch berechnet.
     *
     * @return LocalDateTime der Ankunft oder null, wenn Abfluginformationen oder Flugzeit fehlen
     * und keine explizite Ankunftszeit gesetzt ist.
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
     * Gibt das berechnete Ankunftsdatum basierend auf Abflug und Flugdauer zurück.
     * Hinweis: Verwenden Sie getArrivalDateTime().toLocalDate() zur Konsistenz, wenn eine explizite Ankunftszeit gesetzt ist.
     *
     * @return Berechnetes Ankunftsdatum oder null, wenn Berechnung nicht möglich ist.
     */
    public LocalDate getArrivalDate() {
        LocalDateTime arrivalDT = getArrivalDateTime(); // Verwendet Berechnung oder expliziten Wert
        return (arrivalDT != null) ? arrivalDT.toLocalDate() : null;
    }

    /**
     * Gibt die berechnete Ankunftszeit basierend auf Abflug und Flugdauer zurück.
     * Hinweis: Verwenden Sie getArrivalDateTime().toLocalTime() zur Konsistenz, wenn eine explizite Ankunftszeit gesetzt ist.
     *
     * @return Berechnete Ankunftszeit oder null, wenn Berechnung nicht möglich ist.
     */
    public LocalTime getArrivalTime() {
        LocalDateTime arrivalDT = getArrivalDateTime(); // Verwendet Berechnung oder expliziten Wert
        return (arrivalDT != null) ? arrivalDT.toLocalTime() : null;
    }

    /**
     * Berechnet die Gesamtzahl der auf diesem Flug gebuchten Passagiere.
     *
     * @return Summe der Passagiere in allen Klassen.
     */
    public int getTotalPax() {
        return this.paxEconomy + this.paxBusiness + this.paxFirst;
    }

    /**
     * Aktualisiert die flightTimeMinutes basierend auf der aktuellen distanceKm und der Geschwindigkeit des zugewiesenen Flugzeugs.
     */
    private void updateFlightTime() {
        String flightIdForLog = this.flightNumber != null ? this.flightNumber : (this.id != null ? "ID:" + this.id : "Unbekannt");

        if (this.aircraft == null) {
            logger.debug("Flugzeit-Update übersprungen für Flug {}: Flugzeug ist null.", flightIdForLog);
            return; // Keine Neuberechnung ohne Flugzeug
        }
        if (this.aircraft.getType() == null) {
            logger.warn("Flugzeit-Update übersprungen für Flug {}: Flugzeugtyp ist null für Flugzeug {}.", flightIdForLog, this.aircraft.getRegistrationNo());
            return; // Keine Neuberechnung ohne Flugzeugtyp
        }
        if (this.distanceKm <= 0) {
            logger.debug("Flugzeit-Update übersprungen für Flug {}: Ungültige Distanz ({} km).", flightIdForLog, this.distanceKm);
            return; // Keine Neuberechnung ohne gültige Distanz
        }

        double speed = this.aircraft.getType().getSpeedKmh();
        if (speed <= 0) {
            logger.warn("Flugzeit-Update übersprungen für Flug {}: Ungültige Geschwindigkeit ({} km/h) für Flugzeugtyp {}.", flightIdForLog, speed, this.aircraft.getType().getFullName());
            return; // Keine Neuberechnung ohne gültige Geschwindigkeit
        }

        try {
            int newFlightTime = FlightUtils.calculateFlightTime(this.distanceKm, speed);
            this.setFlightTimeMinutes(newFlightTime); // Ruft intern die Validierung auf
            logger.debug("Flugzeit für Flug {} aktualisiert auf {} Minuten (Distanz: {} km, Geschwindigkeit: {} km/h).", flightIdForLog, newFlightTime, this.distanceKm, speed);
        } catch (IllegalArgumentException e) {
            logger.error("Fehler beim Aktualisieren der Flugzeit für Flug {}: {}", flightIdForLog, e.getMessage());
        } catch (Exception e) {
            logger.error("Unerwarteter Fehler beim Berechnen der Flugzeit für Flug {}: {}", flightIdForLog, e.getMessage(), e);
        }
    }

    /**
     * Formatiert die Flugzeit (in Minuten) in das HH:MM-Format.
     *
     * @return Flugzeit als "HH:MM"-String oder "--:--" wenn die Zeit ungültig ist (<= 0).
     */
    public String getFormattedFlightTime() {
        if (this.flightTimeMinutes <= 0) {
            return "--:--";
        }
        return FlightUtils.formatFlightTime(this.flightTimeMinutes);
    }

    /**
     * Gibt die ID des Fluges zurück.
     *
     * @return Die ID des Fluges oder null, wenn nicht gesetzt.
     */
    public Long getId() {
        return id;
    }

    /**
     * Setzt die ID des Fluges. Normalerweise von der Persistenzschicht verwaltet.
     *
     * @param id Die neue ID.
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Gibt die Flugnummer zurück.
     *
     * @return Die Flugnummer.
     */
    public String getFlightNumber() {
        return flightNumber;
    }

    /**
     * Setzt die Flugnummer.
     *
     * @param flightNumber Die neue Flugnummer.
     * @throws IllegalArgumentException wenn die Flugnummer ungültig ist.
     */
    public void setFlightNumber(String flightNumber) {
        ValidationUtils.validateFlightNumber(flightNumber);
        this.flightNumber = flightNumber;
    }

    /**
     * Gibt die Route des Fluges zurück.
     *
     * @return Die Route oder null, wenn nicht gesetzt.
     */
    public Route getRoute() {
        return route;
    }

    /**
     * Setzt die Route für den Flug.
     * Aktualisiert Abflug-/Ankunftsflughäfen, Distanz und berechnet die Flugzeit neu
     * basierend auf den Daten der neuen Route und dem aktuell zugewiesenen Flugzeug.
     *
     * @param route Die neue Route. Darf nicht null sein.
     * @throws IllegalArgumentException wenn die Route null ist.
     */
    public void setRoute(Route route) {
        ValidationUtils.validateNotNull(route, "Route");
        this.route = route;
        this.setDepartureAirport(route.getDepartureAirport());
        this.setArrivalAirport(route.getArrivalAirport());
        this.setDistanceKm(route.getDistanceKm());

        // Prüfe, ob die Routen-Flugzeit gültig ist und setze sie, falls ja.
        int routeTime = route.getFlightTimeMinutes();
        if (routeTime > 0) {
            this.setFlightTimeMinutes(routeTime); // Überschreibt ggf. die berechnete Zeit
        } else {

            logger.debug("Route {} hat keine gültige Flugzeit ({}), verwende berechnete Zeit für Flug {}.", route.getRouteCode(), routeTime, this.flightNumber);
        }
    }

    /**
     * Gibt den Abflughafen zurück.
     *
     * @return Der Abflughafen oder null, wenn nicht gesetzt.
     */
    public Airport getDepartureAirport() {
        return departureAirport;
    }

    /**
     * Setzt den Abflughafen.
     * Hinweis: Dies überschreibt den von der Route abgeleiteten Flughafen. Überlegen Sie, ob Distanz/Zeit
     * neu berechnet werden sollten, wenn Abflug-/Ankunftsflughäfen unabhängig von der Route geändert werden.
     *
     * @param departureAirport Der neue Abflughafen. Darf nicht null sein.
     * @throws IllegalArgumentException wenn der Flughafen null ist.
     */
    public void setDepartureAirport(Airport departureAirport) {
        ValidationUtils.validateNotNull(departureAirport, "Abflughafen");
        this.departureAirport = departureAirport;
    }

    /**
     * Gibt den Zielflughafen zurück.
     *
     * @return Der Zielflughafen oder null, wenn nicht gesetzt.
     */
    public Airport getArrivalAirport() {
        return arrivalAirport;
    }


    /**
     * Setzt den Zielflughafen.
     *
     * @param arrivalAirport Der neue Zielflughafen. Darf nicht null sein.
     * @throws IllegalArgumentException wenn der Flughafen null ist.
     */
    public void setArrivalAirport(Airport arrivalAirport) {
        ValidationUtils.validateNotNull(arrivalAirport, "Zielflughafen");
        this.arrivalAirport = arrivalAirport;
    }

    /**
     * Gibt das Abflugdatum zurück.
     *
     * @return Das Abflugdatum oder null, wenn nicht gesetzt.
     */
    public LocalDate getDepartureDate() {
        return departureDate;
    }

    /**
     * Setzt das Abflugdatum.
     *
     * @param departureDate Das neue Abflugdatum.
     * @throws IllegalArgumentException wenn das Datum null ist.
     */
    public void setDepartureDate(LocalDate departureDate) {
        ValidationUtils.validateNotNull(departureDate, "Abflugdatum");
        this.departureDate = departureDate;
    }

    /**
     * Gibt die Abflugzeit zurück.
     *
     * @return Die Abflugzeit oder null, wenn nicht gesetzt.
     */
    public LocalTime getDepartureTime() {
        return departureTime;
    }

    /**
     * Setzt die Abflugzeit. Löscht eine explizit gesetzte Ankunftszeit.
     *
     * @param departureTime Die neue Abflugzeit.
     * @throws IllegalArgumentException wenn die Zeit null ist.
     */
    public void setDepartureTime(LocalTime departureTime) {
        ValidationUtils.validateNotNull(departureTime, "Abflugzeit");
        this.departureTime = departureTime;
        this.arrivalDateTime = null;
    }

    /**
     * Gibt das für den Flug zugewiesene Flugzeug zurück.
     *
     * @return Das Flugzeug oder null, wenn nicht gesetzt.
     */
    public Aircraft getAircraft() {
        return aircraft;
    }

    /**
     * Setzt das Flugzeug für den Flug und aktualisiert die Flugzeit entsprechend,
     * falls die Distanz bekannt ist.
     *
     * @param aircraft Das neue Flugzeug. Darf nicht null sein.
     * @throws IllegalArgumentException wenn das Flugzeug null ist.
     */
    public void setAircraft(Aircraft aircraft) {
        ValidationUtils.validateNotNull(aircraft, "Flugzeug");
        boolean needsTimeUpdate = this.aircraft == null || !this.aircraft.equals(aircraft); // Prüfen, ob sich das Flugzeug tatsächlich ändert
        this.aircraft = aircraft;

        // Flugzeit neu berechnen, wenn Flugzeug geändert wird UND Distanz bekannt ist
        if (needsTimeUpdate && this.distanceKm > 0) {
            updateFlightTime();
        } else if (needsTimeUpdate) {
            logger.debug("Flugzeug für Flug {} gesetzt, aber Flugzeit kann nicht aktualisiert werden, da Distanz unbekannt oder null ist.", this.flightNumber != null ? this.flightNumber : "unbekannt");
        }
    }

    /**
     * Gibt den Preis für die Economy Class zurück.
     *
     * @return Der Economy-Preis.
     */
    public double getPriceEconomy() {
        return priceEconomy;
    }

    /**
     * Setzt den Preis für die Economy Class.
     *
     * @param priceEconomy Der neue Economy-Preis. Muss nicht-negativ sein.
     * @throws IllegalArgumentException wenn der Preis negativ ist.
     */
    public void setPriceEconomy(double priceEconomy) {
        ValidationUtils.validateNotNegative(priceEconomy, "Economy-Preis");
        this.priceEconomy = priceEconomy;
    }

    /**
     * Gibt den Preis für die Business Class zurück.
     *
     * @return Der Business-Preis.
     */
    public double getPriceBusiness() {
        return priceBusiness;
    }

    /**
     * Setzt den Preis für die Business Class.
     *
     * @param priceBusiness Der neue Business-Preis. Muss nicht-negativ sein.
     * @throws IllegalArgumentException wenn der Preis negativ ist.
     */
    public void setPriceBusiness(double priceBusiness) {
        ValidationUtils.validateNotNegative(priceBusiness, "Business-Preis");
        this.priceBusiness = priceBusiness;
    }

    /**
     * Gibt den Preis für die First Class zurück.
     *
     * @return Der First-Class-Preis.
     */
    public double getPriceFirst() {
        return priceFirst;
    }

    /**
     * Setzt den Preis für die First Class.
     *
     * @param priceFirst Der neue First-Class-Preis. Muss nicht-negativ sein.
     * @throws IllegalArgumentException wenn der Preis negativ ist.
     */
    public void setPriceFirst(double priceFirst) {
        ValidationUtils.validateNotNegative(priceFirst, "First-Class-Preis");
        this.priceFirst = priceFirst;
    }

    /**
     * Gibt den Status des Fluges zurück.
     *
     * @return Der Flugstatus.
     */
    public FlightStatus getStatus() {
        return status;
    }

    /**
     * Setzt den Status des Fluges.
     *
     * @param status Der neue Flugstatus. Darf nicht null sein.
     * @throws IllegalArgumentException wenn der Status null ist.
     */
    public void setStatus(FlightStatus status) {
        ValidationUtils.validateNotNull(status, "Status");
        this.status = status;
    }

    /**
     * Gibt die Flugdistanz in Kilometern zurück.
     *
     * @return Die Distanz in km.
     */
    public double getDistanceKm() {
        return distanceKm;
    }

    /**
     * Setzt die Flugdistanz und berechnet die Flugzeit neu, falls ein Flugzeug zugewiesen ist.
     * Hinweis: Dies könnte die von der Route abgeleitete Distanz überschreiben.
     *
     * @param distanceKm Die neue Distanz in Kilometern. Muss positiv sein.
     * @throws IllegalArgumentException wenn die Distanz nicht positiv ist.
     */
    public void setDistanceKm(double distanceKm) {
        ValidationUtils.validatePositive(distanceKm, "Flugdistanz");
        boolean needsTimeUpdate = this.distanceKm != distanceKm; // Prüfen, ob sich die Distanz ändert
        this.distanceKm = distanceKm;

        // Flugzeit neu berechnen, wenn Distanz geändert wird und Flugzeug bekannt ist
        if (needsTimeUpdate && this.aircraft != null) {
            updateFlightTime();
        } else if (needsTimeUpdate) {
            logger.debug("Distanz für Flug {} gesetzt, aber Flugzeit kann nicht aktualisiert werden, da Flugzeug null ist.", this.flightNumber != null ? this.flightNumber : "unbekannt");
        }
    }

    /**
     * Gibt die Flugzeit in Minuten zurück.
     *
     * @return Die Flugzeit in Minuten.
     */
    public int getFlightTimeMinutes() {
        return flightTimeMinutes;
    }

    /**
     * Setzt die Flugzeit in Minuten.
     * Validiert, dass die Zeit positiv ist und löscht jede explizit gesetzte Ankunftszeit,
     * da die Änderung der Dauer diese ungültig macht.
     *
     * @param flightTimeMinutes Die neue Flugzeit in Minuten. Muss positiv sein.
     * @throws IllegalArgumentException wenn flightTimeMinutes nicht positiv ist.
     */
    public void setFlightTimeMinutes(int flightTimeMinutes) {
        ValidationUtils.validatePositive(flightTimeMinutes, "Flugzeit");
        if (this.flightTimeMinutes != flightTimeMinutes) {
            this.flightTimeMinutes = flightTimeMinutes;
            // Daueränderung macht jede explizit gesetzte Ankunftszeit ungültig
            this.arrivalDateTime = null;
            logger.trace("Explizite Ankunftszeit für Flug {} gelöscht wegen Änderung der Flugzeit.", flightNumber);
        }
    }

    /**
     * Setzt das explizit berechnete oder überschriebene Ankunftsdatum und die Ankunftszeit.
     * Verwenden Sie dies, wenn die Ankunftszeit nicht dynamisch aus Abflug + Dauer berechnet werden soll.
     * Validiert optional, ob die Ankunftszeit nach der Abflugzeit liegt.
     *
     * @param arrivalDateTime Das explizite Ankunftsdatum und die Ankunftszeit oder null, um zur dynamischen Berechnung zurückzukehren.
     * @throws IllegalArgumentException wenn arrivalDateTime nicht nach der Abflugzeit liegt (optional).
     */
    public void setArrivalDateTime(LocalDateTime arrivalDateTime) {
        LocalDateTime departure = getDepartureDateTime();
        if (arrivalDateTime != null && departure != null && !arrivalDateTime.isAfter(departure)) {
            logger.warn("Versuch, ungültige explizite Ankunftszeit ({}) zu setzen, die nicht nach dem Abflug ({}) liegt für Flug {}.", arrivalDateTime, departure, flightNumber);
            throw new IllegalArgumentException("Explizite Ankunftszeit muss nach der Abflugzeit liegen.");
        }
        this.arrivalDateTime = arrivalDateTime;
        if (arrivalDateTime != null) {
            logger.debug("Explizite Ankunftszeit {} für Flug {} gesetzt.", arrivalDateTime, flightNumber);
        } else {
            logger.debug("Explizite Ankunftszeit für Flug {} entfernt, wird dynamisch berechnet.", flightNumber);
        }
    }


    /**
     * Gibt die Anzahl der Economy-Passagiere zurück.
     *
     * @return Anzahl Economy-Passagiere.
     */
    public int getPaxEconomy() {
        return paxEconomy;
    }

    /**
     * Setzt die Anzahl der Economy-Passagiere.
     *
     * @param paxEconomy Die neue Anzahl. Muss nicht-negativ sein.
     * @throws IllegalArgumentException wenn die Anzahl negativ ist.
     */
    public void setPaxEconomy(int paxEconomy) {
        ValidationUtils.validateNotNegative(paxEconomy, "Economy-Passagiere");
        // Optional: Kapazitätsvalidierung hinzufügen
        // validateTotalCapacity();
        this.paxEconomy = paxEconomy;
    }

    /**
     * Gibt die Anzahl der Business-Passagiere zurück.
     *
     * @return Anzahl Business-Passagiere.
     */
    public int getPaxBusiness() {
        return paxBusiness;
    }

    /**
     * Setzt die Anzahl der Business-Passagiere.
     *
     * @param paxBusiness Die neue Anzahl. Muss nicht-negativ sein.
     * @throws IllegalArgumentException wenn die Anzahl negativ ist.
     */
    public void setPaxBusiness(int paxBusiness) {
        ValidationUtils.validateNotNegative(paxBusiness, "Business-Passagiere");
        // validateTotalCapacity();
        this.paxBusiness = paxBusiness;
    }

    /**
     * Gibt die Anzahl der First-Class-Passagiere zurück.
     *
     * @return Anzahl First-Class-Passagiere.
     */
    public int getPaxFirst() {
        return paxFirst;
    }

    /**
     * Setzt die Anzahl der First-Class-Passagiere.
     *
     * @param paxFirst Die neue Anzahl. Muss nicht-negativ sein.
     * @throws IllegalArgumentException wenn die Anzahl negativ ist.
     */
    public void setPaxFirst(int paxFirst) {
        ValidationUtils.validateNotNegative(paxFirst, "First-Class-Passagiere");
        // validateTotalCapacity();
        this.paxFirst = paxFirst;
    }

    /**
     * Gibt eine String-Repräsentation des Fluges zurück.
     *
     * @return Formatierte String-Darstellung des Fluges.
     */
    @Override
    public String toString() {
        return String.format("Flight[%s: %s -> %s am %s %s, Status: %s, Flugzeug: %s, Pax: %d, Zeit: %s%s%s]", flightNumber != null ? flightNumber : "N/A", departureAirport != null ? departureAirport.getIcaoCode() : "N/A", arrivalAirport != null ? arrivalAirport.getIcaoCode() : "N/A", departureDate != null ? departureDate : "N/A", departureTime != null ? departureTime : "--:--", status != null ? status : "N/A", aircraft != null ? aircraft.getRegistrationNo() : "N/A", getTotalPax(), getFormattedFlightTime(), id != null ? String.format(", ID: %d", id) : "");
    }

    /**
     * Vergleicht dieses Flugobjekt mit einem anderen Objekt auf Gleichheit.
     * Flüge gelten als gleich, wenn ihre IDs übereinstimmen (sofern beide nicht null sind).
     * Andernfalls basiert der Vergleich auf Flugnummer, kombiniertem Abflugdatum/-zeit und Abflughafen.
     *
     * @param o Das zu vergleichende Objekt.
     * @return true, wenn die Objekte als gleich betrachtet werden, sonst false.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Flight flight = (Flight) o;

        // Primärer Vergleich basierend auf ID, wenn beide nicht null sind
        if (this.id != null && flight.id != null) {
            return this.id.equals(flight.id);
        }

        // Fallback-Vergleich für nicht persistierte oder neue Objekte
        // Vergleich basierend auf natürlichen Schlüsseln: Flugnummer, Abflugdatum/-zeit und Abflughafen
        return Objects.equals(this.flightNumber, flight.flightNumber) && Objects.equals(this.getDepartureDateTime(), flight.getDepartureDateTime()) && Objects.equals(this.departureAirport, flight.departureAirport);
    }

    /**
     * Gibt einen Hashcode für dieses Flugobjekt zurück.
     * Basiert auf der ID, falls verfügbar.
     * Andernfalls basiert der Hashcode auf denselben Feldern, die im Fallback-equals() verwendet werden
     * (Flugnummer, kombiniertes Abflugdatum/-zeit, Abflughafen).
     *
     * @return Der Hashcode für dieses Objekt.
     */
    @Override
    public int hashCode() {
        // Hashcode basiert auf ID, falls verfügbar
        if (id != null) {
            return Objects.hash(id);
        }
        // Fallback-Hashcode basiert auf denselben Feldern wie im Fallback-equals()
        // Wichtig: getDepartureDateTime() kann null sein
        return Objects.hash(flightNumber, getDepartureDateTime(), departureAirport);
    }
}
