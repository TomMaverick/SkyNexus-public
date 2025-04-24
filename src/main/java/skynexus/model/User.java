package skynexus.model;

import skynexus.util.ValidationUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Repräsentiert einen Benutzer des Systems mit seinen Zugangsdaten
 * und optionalen Zuordnungen zu Flughafen und Airline.
 */
public class User {
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private Long id;                     // Primärschlüssel in der Datenbank
    private String username;             // Benutzername, z.B. "admin"
    private String passwordHash;         // Passwort-Hash (PBKDF2)
    private String salt;                 // Salt für Passwort-Hashing
    private Airport airport;             // Zugeordneter Flughafen
    private Airline airline;             // Zugeordnete Airline
    private LocalDateTime lastLogin;     // Zeitpunkt des letzten Logins
    private LocalDateTime createdAt;     // Erstellungszeitpunkt
    private boolean active;              // Ist der Benutzer aktiv?
    private boolean admin;               // Hat der Benutzer Admin-Rechte?

    /**
     * Standard-Konstruktor mit Standardwerten
     */
    public User() {
        this.createdAt = LocalDateTime.now();
        this.active = true;
        this.admin = false; // Standardmäßig kein Admin
    }

    /**
     * Konstruktor mit Benutzernamen
     *
     * @param username Benutzername
     */
    public User(String username) {
        this();
        validateUsername(username);
        this.setUsername(username);
    }

    /**
     * Validiert den Benutzernamen auf Mindestlänge
     */
    private void validateUsername(String username) {
        if (username.length() < 3) {
            throw new IllegalArgumentException("Benutzername muss mindestens 3 Zeichen lang sein");
        }
    }

    // Getters und Setters mit zentraler Validierung
    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        ValidationUtils.validateNotEmpty(username, "Benutzername");
        validateUsername(username);
        this.username = username;
    }

    public String getPasswordHash() {
        return this.passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        ValidationUtils.validateNotEmpty(passwordHash, "Passwort-Hash");
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return this.salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public Airport getAirport() {
        return this.airport;
    }

    public void setAirport(Airport airport) {
        this.airport = airport;
    }

    public Airline getAirline() {
        return this.airline;
    }

    public void setAirline(Airline airline) {
        this.airline = airline;
    }

    public LocalDateTime getLastLogin() {
        return this.lastLogin;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        ValidationUtils.validateNotNull(createdAt, "Erstellungsdatum");
        this.createdAt = createdAt;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isAdmin() {
        return this.admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    /**
     * Formatierte Darstellung mit optionaler Airline/Airport Information
     * und verhindert NPEs, wenn diese nicht gesetzt sind
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.username);
        if (this.admin) sb.append(" (Admin)");

        if (this.airline != null || this.airport != null) {
            sb.append(" (");
            if (this.airline != null) sb.append(this.airline.getName());
            if (this.airline != null && this.airport != null) sb.append(" @ ");
            if (this.airport != null) sb.append(this.airport.getName());
            sb.append(")");
        }

        return sb.toString();
    }

    /**
     * @return Formatiertes Erstellungsdatum
     */
    public String getFormattedCreationDate() {
        return this.createdAt.format(DATE_FORMATTER);
    }

    /**
     * @return Formatiertes letztes Login-Datum oder "Nie" wenn null
     */
    public String getFormattedLastLogin() {
        return this.lastLogin != null ? this.lastLogin.format(DATE_FORMATTER) : "Nie";
    }
}
