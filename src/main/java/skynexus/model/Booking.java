package skynexus.model;

import skynexus.enums.SeatClass;
import skynexus.util.ValidationUtils;

import java.time.LocalDateTime;

/**
 * Repräsentiert eine Buchung eines Passagiers für einen spezifischen Flug.
 * Jeder Passagier kann mehrere Flüge buchen, und jeder Flug kann mehrere Passagiere haben.
 */
public class Booking {
    // Attribute
    private Long id;
    private Passenger passenger;
    private Flight flight;
    private SeatClass seatClass;
    private LocalDateTime bookingDate;

    /**
     * Standardkonstruktor für die Verwendung durch Frameworks.
     * Setzt das Buchungsdatum auf die aktuelle Zeit.
     */
    public Booking() {
        this.bookingDate = LocalDateTime.now();
    }

    /**
     * Konstruktor mit allen erforderlichen Feldern für eine neue Buchung.
     *
     * @param passenger Der Passagier, der bucht
     * @param flight    Der Flug, der gebucht wird
     * @param seatClass Die gewünschte Sitzklasse
     * @throws IllegalArgumentException wenn Validierungsfehler auftreten
     */
    public Booking(Passenger passenger, Flight flight, SeatClass seatClass) {
        this.setPassenger(passenger);
        this.setFlight(flight);
        this.setSeatClass(seatClass);
        this.bookingDate = LocalDateTime.now();
    }

    /**
     * Vollständiger Konstruktor mit allen Feldern, einschließlich ID.
     *
     * @param id          Datenbank-ID
     * @param passenger   Der Passagier
     * @param flight      Der Flug
     * @param seatClass   Die Sitzklasse
     * @param bookingDate Das Buchungsdatum (falls null, wird aktuelles Datum verwendet)
     * @throws IllegalArgumentException wenn Validierungsfehler auftreten
     */
    public Booking(Long id, Passenger passenger, Flight flight, SeatClass seatClass, LocalDateTime bookingDate) {
        this(passenger, flight, seatClass);
        this.id = id;
        this.setBookingDate(bookingDate);
    }

    /**
     * Gibt die ID der Buchung zurück.
     *
     * @return ID der Buchung
     */
    public Long getId() {
        return id;
    }

    /**
     * Setzt die ID der Buchung.
     *
     * @param id Die neue ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Gibt den Passagier zurück, der die Buchung getätigt hat.
     *
     * @return Der Passagier
     */
    public Passenger getPassenger() {
        return passenger;
    }

    /**
     * Setzt den Passagier für die Buchung.
     *
     * @param passenger Der neue Passagier
     * @throws IllegalArgumentException wenn der Passagier null ist
     */
    public void setPassenger(Passenger passenger) {
        ValidationUtils.validateNotNull(passenger, "Passagier");
        this.passenger = passenger;
    }

    /**
     * Gibt den gebuchten Flug zurück.
     *
     * @return Der Flug
     */
    public Flight getFlight() {
        return flight;
    }

    /**
     * Setzt den Flug für die Buchung.
     *
     * @param flight Der neue Flug
     * @throws IllegalArgumentException wenn der Flug null ist
     */
    public void setFlight(Flight flight) {
        ValidationUtils.validateNotNull(flight, "Flug");
        this.flight = flight;
    }

    /**
     * Gibt die gebuchte Sitzklasse zurück.
     *
     * @return Die Sitzklasse
     */
    public SeatClass getSeatClass() {
        return seatClass;
    }

    /**
     * Setzt die Sitzklasse für die Buchung.
     *
     * @param seatClass Die neue Sitzklasse
     * @throws IllegalArgumentException wenn die Sitzklasse null ist
     */
    public void setSeatClass(SeatClass seatClass) {
        ValidationUtils.validateNotNull(seatClass, "Sitzklasse");
        this.seatClass = seatClass;
    }

    /**
     * Gibt das Datum und die Uhrzeit der Buchung zurück.
     *
     * @return Buchungsdatum und -uhrzeit (UTC)
     */
    public LocalDateTime getBookingDate() {
        return bookingDate;
    }

    /**
     * Setzt das Datum und die Uhrzeit der Buchung.
     * Falls null übergeben wird, wird das aktuelle Datum verwendet.
     *
     * @param bookingDate Das neue Buchungsdatum oder null für aktuelles Datum
     */
    public void setBookingDate(LocalDateTime bookingDate) {
        this.bookingDate = bookingDate != null ? bookingDate : LocalDateTime.now();
    }

    /**
     * Liefert eine String-Repräsentation der Buchung.
     *
     * @return Formatierte Darstellung der Buchung mit ID, Passagier, Flug und Sitzklasse
     */
    @Override
    public String toString() {
        return "Buchung " + id + ": " + passenger.getFullName() + " → " +
                flight.getFlightNumber() + " (" + seatClass + ")";
    }
}
