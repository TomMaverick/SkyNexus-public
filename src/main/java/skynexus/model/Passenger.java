package skynexus.model;

import org.slf4j.Logger;
import skynexus.enums.Gender;
import skynexus.enums.SeatClass;
import skynexus.util.ValidationUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Repräsentiert einen Passagier mit persönlichen Daten und Buchungsinformationen.
 */
public class Passenger {
    private Long id;                     // Primärschlüssel in der Datenbank
    private String lastName;             // Nachname, z.B. "Müller"
    private String firstName;            // Vorname, z.B. "Hans"
    private Gender gender;               // Geschlecht (MALE, FEMALE, OTHER)
    private LocalDate dateOfBirth;       // Geburtsdatum, z.B. 1990-01-15
    private String nationality;          // Nationalität, z.B. "Deutsch"
    private String passportNumber;       // Reisepassnummer 9-stellig, z.B. "DE1234567"

    private final List<Booking> bookings = new ArrayList<>();

    /**
     * Standardkonstruktor für ORM-Frameworks
     */
    public Passenger() {
    }

    /**
     * Konstruktor mit grundlegenden persönlichen Daten
     */
    public Passenger(String lastName, String firstName, Gender gender,
                     LocalDate dateOfBirth, String nationality) {
        ValidationUtils.validateNotEmpty(lastName, "Nachname");
        ValidationUtils.validateNotEmpty(firstName, "Vorname");
        ValidationUtils.validateNotNull(gender, "Geschlecht");
        ValidationUtils.validateNotNull(dateOfBirth, "Geburtsdatum");
        validateDateOfBirth(dateOfBirth);
        ValidationUtils.validateNotEmpty(nationality, "Nationalität");

        this.setLastName(lastName);
        this.setFirstName(firstName);
        this.setGender(gender);
        this.setDateOfBirth(dateOfBirth);
        this.setNationality(nationality);
    }

    /**
     * Konstruktor mit persönlichen Daten und Reisepass
     */
    public Passenger(String lastName, String firstName, Gender gender, LocalDate dateOfBirth,
                     String nationality, String passportNumber) {
        this(lastName, firstName, gender, dateOfBirth, nationality);
        this.setPassportNumber(passportNumber);
    }

    /**
     * Gibt alle Buchungen dieses Passagiers zurück
     */
    public List<Booking> getBookings() {
        return Collections.unmodifiableList(bookings);
    }

    /**
     * Setzt die Liste der Buchungen
     */
    public void setBookings(List<Booking> bookings) {
        this.bookings.clear();
        if (bookings != null) {
            this.bookings.addAll(bookings);
        }
    }

    /**
     * Gibt eine formatierte Liste der aktiven Buchungen als String zurück.
     */
    public String getBookingsAsString() {
        if (bookings.isEmpty()) {
            return "Keine Buchungen";
        }

        List<Booking> activeBookings = bookings.stream()
                .filter(booking -> booking.getFlight() != null &&
                        booking.getFlight().getStatus() != skynexus.enums.FlightStatus.COMPLETED)
                .sorted((b1, b2) -> {
                    Flight f1 = b1.getFlight();
                    Flight f2 = b2.getFlight();

                    if (f1.getDepartureDate() == null || f2.getDepartureDate() == null) {
                        return 0;
                    }

                    int dateCompare = f1.getDepartureDate().compareTo(f2.getDepartureDate());
                    if (dateCompare != 0) {
                        return dateCompare;
                    }

                    if (f1.getDepartureTime() != null && f2.getDepartureTime() != null) {
                        return f1.getDepartureTime().compareTo(f2.getDepartureTime());
                    }

                    return 0;
                })
                .toList();

        if (activeBookings.isEmpty()) {
            return "Keine aktiven Buchungen";
        }

        Booking nextBooking = activeBookings.get(0);
        Flight flight = nextBooking.getFlight();
        StringBuilder sb = new StringBuilder();

        sb.append(flight.getRouteDisplayName())
                .append(" (")
                .append(flight.getFlightNumber())
                .append(", ")
                .append(getSeatClassShortName(nextBooking.getSeatClass()))
                .append(")");

        if (flight.getDepartureDate() != null) {
            sb.append(" - ")
                    .append(flight.getDepartureDate().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));

            if (flight.getDepartureTime() != null) {
                sb.append(", ")
                        .append(flight.getDepartureTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
            }
        }

        int remainingCount = activeBookings.size() - 1;
        if (remainingCount > 0) {
            sb.append(" und ").append(remainingCount).append(" weitere");
        }

        return sb.toString();
    }

    /**
     * Gibt eine formatierte Liste aller Buchungen zurück.
     */
    public String getAllBookingsAsString() {
        if (bookings.isEmpty()) {
            return "Keine Buchungen";
        }

        List<Booking> futureBookings = bookings.stream()
                .filter(booking -> booking.getFlight() != null &&
                        booking.getFlight().getStatus() != skynexus.enums.FlightStatus.COMPLETED)
                .sorted((b1, b2) -> {
                    Flight f1 = b1.getFlight();
                    Flight f2 = b2.getFlight();

                    if (f1.getDepartureDate() == null || f2.getDepartureDate() == null) {
                        return 0;
                    }

                    int dateCompare = f1.getDepartureDate().compareTo(f2.getDepartureDate());
                    if (dateCompare != 0) {
                        return dateCompare;
                    }

                    if (f1.getDepartureTime() != null && f2.getDepartureTime() != null) {
                        return f1.getDepartureTime().compareTo(f2.getDepartureTime());
                    }

                    return 0;
                })
                .toList();

        if (futureBookings.isEmpty()) {
            return "Keine zukünftigen Buchungen";
        }

        StringBuilder sb = new StringBuilder();
        for (Booking booking : futureBookings) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }

            Flight flight = booking.getFlight();

            sb.append(flight.getRouteDisplayName())
                    .append(" (")
                    .append(flight.getFlightNumber())
                    .append(", ")
                    .append(getSeatClassFullName(booking.getSeatClass()))
                    .append(")");

            if (flight.getDepartureDate() != null) {
                sb.append(" - ")
                        .append(flight.getDepartureDate().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));

                if (flight.getDepartureTime() != null) {
                    sb.append(", ")
                            .append(flight.getDepartureTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
                }
            }

            sb.append(" - Status: ").append(getFlightStatusDisplay(flight.getStatus()));
        }
        return sb.toString();
    }

    /**
     * Gibt den vollen Namen der Sitzklasse zurück
     */
    private String getSeatClassFullName(SeatClass seatClass) {
        if (seatClass == null) {
            return "Unbekannt";
        }
        return switch (seatClass) {
            case ECONOMY -> "Economy";
            case BUSINESS -> "Business";
            case FIRST_CLASS -> "First Class";
        };
    }

    /**
     * Gibt eine lokalisierte Anzeige für den Flugstatus zurück
     */
    private String getFlightStatusDisplay(skynexus.enums.FlightStatus status) {
        if (status == null) {
            return "Unbekannt";
        }
        return switch (status) {
            case SCHEDULED -> "Geplant";
            case BOARDING -> "Boarding";
            case DEPARTED -> "Abgeflogen";
            case FLYING -> "In der Luft";
            case LANDED -> "Gelandet";
            case DEPLANING -> "Ausstieg";
            case COMPLETED -> "Abgeschlossen";
            case UNKNOWN -> "Unbekannt";
        };
    }

    /**
     * Gibt einen Kurzcode für die Sitzklasse zurück
     */
    private String getSeatClassShortName(SeatClass seatClass) {
        if (seatClass == null) {
            return "?";
        }

        return switch (seatClass) {
            case ECONOMY -> "E";
            case BUSINESS -> "B";
            case FIRST_CLASS -> "FC";
        };
    }

    /**
     * Berechnet das Alter des Passagiers
     */
    public int getAge() {
        if (this.dateOfBirth == null) {
            return 0;
        }

        LocalDate now = LocalDate.now();
        int age = now.getYear() - this.dateOfBirth.getYear();

        if (now.getMonthValue() < this.dateOfBirth.getMonthValue() ||
                (now.getMonthValue() == this.dateOfBirth.getMonthValue() &&
                        now.getDayOfMonth() < this.dateOfBirth.getDayOfMonth())) {
            age--;
        }

        return age;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLastName() {
        return this.lastName;
    }

    public void setLastName(String lastName) {
        ValidationUtils.validateNotEmpty(lastName, "Nachname");
        this.lastName = lastName;
    }

    public String getFirstName() {
        return this.firstName;
    }

    public void setFirstName(String firstName) {
        ValidationUtils.validateNotEmpty(firstName, "Vorname");
        this.firstName = firstName;
    }

    public Gender getGender() {
        return this.gender;
    }

    public void setGender(Gender gender) {
        ValidationUtils.validateNotNull(gender, "Geschlecht");
        this.gender = gender;
    }

    public LocalDate getDateOfBirth() {
        return this.dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        ValidationUtils.validateNotNull(dateOfBirth, "Geburtsdatum");
        validateDateOfBirth(dateOfBirth);
        this.dateOfBirth = dateOfBirth;
    }

    /**
     * Validiert das Geburtsdatum auf sinnvolle Werte
     */
    private void validateDateOfBirth(LocalDate dateOfBirth) {
        if (dateOfBirth.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Geburtsdatum kann nicht in der Zukunft liegen");
        }
        if (dateOfBirth.isBefore(LocalDate.now().minusYears(130))) {
            throw new IllegalArgumentException("Geburtsdatum unrealistisch (max. 130 Jahre).");
        }
    }

    public String getNationality() {
        return this.nationality;
    }

    public void setNationality(String nationality) {
        ValidationUtils.validateNotEmpty(nationality, "Nationalität");
        this.nationality = nationality;
    }

    public String getPassportNumber() {
        return this.passportNumber;
    }

    /**
     * Setzt die Passnummer mit Validierung und Normalisierung.
     */
    public void setPassportNumber(String passportNumber) {
        if (passportNumber == null || passportNumber.trim().isEmpty()) {
            this.passportNumber = null;
            return;
        }

        Logger logger = org.slf4j.LoggerFactory.getLogger(Passenger.class);

        try {
            String normalized = ValidationUtils.validatePassportNumber(passportNumber);

            if (!normalized.equals(passportNumber)) {
                logger.info("Passnummer wurde normalisiert: '{}' → '{}'", passportNumber, normalized);
            }

            this.passportNumber = normalized;

        } catch (IllegalArgumentException e) {
            logger.warn("Ungültige Passnummer '{}' abgelehnt: {}", passportNumber, e.getMessage());
            throw new IllegalArgumentException("Passnummer ungültig: " + e.getMessage());
        }
    }

    /**
     * Gibt den vollständigen Namen des Passagiers zurück
     */
    public String getFullName() {
        return this.firstName + " " + this.lastName;
    }

    @Override
    public String toString() {
        return getFullName() + " (" + (bookings.isEmpty() ? "Keine Buchung" : getBookingsAsString()) + ")";
    }
}
