package skynexus.model;

import org.slf4j.Logger;
import skynexus.enums.Gender;
import skynexus.enums.SeatClass;
import skynexus.util.ValidationUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

    // Neue Beziehung zu Buchungen
    private List<Booking> bookings = new ArrayList<>();

    /**
     * Standardkonstruktor für ORM-Frameworks
     */
    public Passenger() {
        // Leerer Konstruktor für Frameworks wie JPA/Hibernate
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
     * Fügt eine neue Buchung für diesen Passagier hinzu
     *
     * @param flight    Der zu buchende Flug
     * @param seatClass Die gewünschte Sitzklasse
     * @return Die neu erstellte Buchung
     */
    public Booking addBooking(Flight flight, SeatClass seatClass) {
        ValidationUtils.validateNotNull(flight, "Flug");
        ValidationUtils.validateNotNull(seatClass, "Sitzklasse");

        // Prüfen, ob der Flug bereits gebucht ist
        if (hasBookedFlight(flight)) {
            throw new IllegalArgumentException("Der Passagier hat diesen Flug bereits gebucht");
        }

        // Neue Buchung erstellen
        Booking booking = new Booking(this, flight, seatClass);
        bookings.add(booking);

        // Passagierzahl für die entsprechende Klasse erhöhen
        switch (seatClass) {
            case ECONOMY:
                flight.setPaxEconomy(flight.getPaxEconomy() + 1);
                break;
            case BUSINESS:
                flight.setPaxBusiness(flight.getPaxBusiness() + 1);
                break;
            case FIRST_CLASS:
                flight.setPaxFirst(flight.getPaxFirst() + 1);
                break;
        }

        return booking;
    }

    /**
     * Entfernt eine Buchung für diesen Passagier
     *
     * @param booking Die zu entfernende Buchung
     * @return true wenn die Buchung erfolgreich entfernt wurde
     */
    public boolean removeBooking(Booking booking) {
        if (booking == null || !bookings.contains(booking)) {
            return false;
        }

        Flight flight = booking.getFlight();
        SeatClass seatClass = booking.getSeatClass();

        // Passagierzahl für die entsprechende Klasse reduzieren
        if (flight != null && seatClass != null) {
            switch (seatClass) {
                case ECONOMY:
                    flight.setPaxEconomy(Math.max(0, flight.getPaxEconomy() - 1));
                    break;
                case BUSINESS:
                    flight.setPaxBusiness(Math.max(0, flight.getPaxBusiness() - 1));
                    break;
                case FIRST_CLASS:
                    flight.setPaxFirst(Math.max(0, flight.getPaxFirst() - 1));
                    break;
            }
        }

        return bookings.remove(booking);
    }

    /**
     * Prüft, ob der Passagier einen bestimmten Flug bereits gebucht hat
     *
     * @param flight Der zu prüfende Flug
     * @return true wenn der Flug bereits gebucht wurde
     */
    public boolean hasBookedFlight(Flight flight) {
        if (flight == null) {
            return false;
        }

        return bookings.stream()
                .anyMatch(booking -> booking.getFlight().getId().equals(flight.getId()));
    }

    /**
     * Gibt alle Buchungen dieses Passagiers zurück
     *
     * @return Eine unmodifizierbare Liste aller Buchungen
     */
    public List<Booking> getBookings() {
        return Collections.unmodifiableList(bookings);
    }

    /**
     * Setzt die Liste der Buchungen (z.B. beim Laden aus der Datenbank)
     *
     * @param bookings Die Liste der Buchungen
     */
    public void setBookings(List<Booking> bookings) {
        this.bookings.clear();
        if (bookings != null) {
            this.bookings.addAll(bookings);
        }
    }

    /**
     * Gibt eine formatierte Liste der aktiven Buchungen als String zurück
     * Format: "SNX120 (E) - 20.04.2025 - 16:20"
     * Abgeschlossene Flüge (COMPLETED) werden ausgeblendet.
     * Flüge werden chronologisch sortiert (nächster Flug zuerst).
     *
     * @return Formatierter String mit allen aktiven Buchungen
     */
    public String getBookingsAsString() {
        if (bookings.isEmpty()) {
            return "Keine Buchungen";
        }

        // Filtere abgeschlossene Flüge heraus und sortiere nach Datum/Uhrzeit
        List<Booking> activeBookings = bookings.stream()
                .filter(booking -> booking.getFlight() != null &&
                        booking.getFlight().getStatus() != skynexus.enums.FlightStatus.COMPLETED)
                .sorted((b1, b2) -> {
                    // Sortiere nach Abflugdatum/-zeit (nächster Flug zuerst)
                    Flight f1 = b1.getFlight();
                    Flight f2 = b2.getFlight();

                    if (f1.getDepartureDate() == null || f2.getDepartureDate() == null) {
                        return 0;
                    }

                    // Vergleiche das Datum
                    int dateCompare = f1.getDepartureDate().compareTo(f2.getDepartureDate());
                    if (dateCompare != 0) {
                        return dateCompare;
                    }

                    // Wenn gleiches Datum, vergleiche die Uhrzeit
                    if (f1.getDepartureTime() != null && f2.getDepartureTime() != null) {
                        return f1.getDepartureTime().compareTo(f2.getDepartureTime());
                    }

                    return 0;
                })
                .collect(Collectors.toList());

        if (activeBookings.isEmpty()) {
            return "Keine aktiven Buchungen";
        }

        StringBuilder sb = new StringBuilder();
        for (Booking booking : activeBookings) {
            if (sb.length() > 0) {
                sb.append(", ");
            }

            Flight flight = booking.getFlight();

            // Flugnummer und Klasse
            sb.append(flight.getFlightNumber())
                    .append(" (")
                    .append(getSeatClassShortName(booking.getSeatClass()))
                    .append(")");

            // Datum und Uhrzeit hinzufügen
            if (flight.getDepartureDate() != null) {
                sb.append(" - ")
                        .append(flight.getDepartureDate().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));

                if (flight.getDepartureTime() != null) {
                    sb.append(" - ")
                            .append(flight.getDepartureTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
                }
            }
        }
        return sb.toString();
    }

    /**
     * Gibt eine formatierte Liste aller Buchungen zurück (inklusive abgeschlossener)
     * Format: "SNX120 (E) - 20.04.2025 - 16:20"
     *
     * @return Formatierter String mit allen Buchungen
     */
    public String getAllBookingsAsString() {
        if (bookings.isEmpty()) {
            return "Keine Buchungen";
        }

        StringBuilder sb = new StringBuilder();
        for (Booking booking : bookings) {
            if (sb.length() > 0) {
                sb.append("\n");
            }

            Flight flight = booking.getFlight();

            // Flugnummer und Klasse
            sb.append(flight.getFlightNumber())
                    .append(" (")
                    .append(getSeatClassShortName(booking.getSeatClass()))
                    .append(")");

            // Datum und Uhrzeit hinzufügen
            if (flight.getDepartureDate() != null) {
                sb.append(" - ")
                        .append(flight.getDepartureDate().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));

                if (flight.getDepartureTime() != null) {
                    sb.append(" - ")
                            .append(flight.getDepartureTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
                }
            }

            // Flugstatus hinzufügen
            sb.append(" - Status: ").append(flight.getStatus());
        }
        return sb.toString();
    }

    /**
     * Gibt einen Kurzcode für die Sitzklasse zurück (E, B, FC)
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
     * Gibt einen benutzerfreundlichen Namen für die Sitzklasse zurück
     */
    private String getSeatClassDisplayName(SeatClass seatClass) {
        if (seatClass == null) {
            return "Unbekannt";
        }

        return switch (seatClass) {
            case ECONOMY -> "Economy";
            case BUSINESS -> "Business";
            case FIRST_CLASS -> "First";
        };
    }

    /**
     * Berechnet das Alter des Passagiers
     *
     * @return Alter in Jahren
     */
    public int getAge() {
        if (this.dateOfBirth == null) {
            return 0;
        }

        LocalDate now = LocalDate.now();
        int age = now.getYear() - this.dateOfBirth.getYear();

        // Geburtstag dieses Jahr schon vorbei?
        if (now.getMonthValue() < this.dateOfBirth.getMonthValue() ||
                (now.getMonthValue() == this.dateOfBirth.getMonthValue() &&
                        now.getDayOfMonth() < this.dateOfBirth.getDayOfMonth())) {
            age--;
        }

        return age;
    }

    // Getter und Setter mit zentraler Validierung

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
     * Setzt die Passnummer mit obligatorischer Validierung und Normalisierung.
     * Stellt sicher, dass die Passnummer immer einheitlich und korrekt gespeichert wird.
     *
     * @param passportNumber Die zu setzende Passnummer
     */
    public void setPassportNumber(String passportNumber) {
        if (passportNumber == null || passportNumber.trim().isEmpty()) {
            this.passportNumber = null;
            return;
        }

        // Logger für Nachverfolgung
        Logger logger = org.slf4j.LoggerFactory.getLogger(Passenger.class);

        try {
            // Explizit über ValidationUtils normalisieren - KRITISCH für konsistente DB-Speicherung
            String normalized = ValidationUtils.validatePassportNumber(passportNumber);

            // Loggen, wenn eine Normalisierung stattgefunden hat
            if (!normalized.equals(passportNumber)) {
                logger.info("Passnummer wurde normalisiert: '{}' → '{}'", passportNumber, normalized);
            }

            // Die garantiert normalisierte Version speichern
            this.passportNumber = normalized;

        } catch (IllegalArgumentException e) {
            // WICHTIGE ÄNDERUNG: Wir werfen den Fehler weiter, um sicherzustellen,
            // dass keine ungültigen Passnummern gespeichert werden
            logger.warn("Ungültige Passnummer '{}' abgelehnt: {}", passportNumber, e.getMessage());
            throw new IllegalArgumentException("Passnummer ungültig: " + e.getMessage());
        }
    }

    /**
     * Gibt den vollständigen Namen des Passagiers zurück
     *
     * @return Vollständiger Name
     */
    public String getFullName() {
        return this.firstName + " " + this.lastName;
    }

    @Override
    public String toString() {
        return getFullName() + " (" + (bookings.isEmpty() ? "Keine Buchung" : getBookingsAsString()) + ")";
    }
}
