package skynexus.model;

import skynexus.util.TimeUtils;
import skynexus.util.ValidationUtils;

import java.time.LocalDateTime;

/**
 * Repräsentiert einen Benutzer des Systems mit seinen Zugangsdaten.
 * Speichert Authentifizierungsinformationen sowie Berechtigungen und
 * Zuordnungen zu Flughafen und Airline.
 */
public class User {
    private Long id;
    private String username;
    private String passwordHash;
    private String salt;

    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
    private boolean active;
    private boolean admin;

    /**
     * Erstellt einen neuen Benutzer mit Standardwerten.
     * Der Benutzer wird aktiv gesetzt, hat keine Administratorrechte
     * und der Erstellungszeitpunkt wird auf die aktuelle Zeit gesetzt.
     */
    public User() {
        this.createdAt = LocalDateTime.now();
        this.active = true;
        this.admin = false;
    }

    /**
     * Erstellt einen neuen Benutzer mit dem angegebenen Benutzernamen.
     *
     * @param username Der Benutzername, mindestens 3 Zeichen lang
     * @throws IllegalArgumentException wenn der Benutzername weniger als 3 Zeichen hat
     */
    public User(String username) {
        this();
        setUsername(username);
    }

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
        ValidationUtils.validateUsernameLength(username);
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
     * Erstellt eine String-Repräsentation des Benutzers mit optionalen
     * Informationen zu Administratorstatus.
     *
     * @return Die formatierte Benutzerdarstellung
     */
    @Override
    public String toString() {
        return this.username + (this.admin ? " (Admin)" : "");
    }

    /**
     * Formatiert das Erstellungsdatum des Benutzers.
     *
     * @return Das formatierte Erstellungsdatum im Format "dd.MM.yyyy HH:mm"
     */
    public String getFormattedCreationDate() {
        return TimeUtils.formatStandardDateTime(this.createdAt, "");
    }

    /**
     * Formatiert den Zeitpunkt des letzten Logins.
     *
     * @return Das formatierte Datum des letzten Logins oder "Nie" wenn nie eingeloggt
     */
    public String getFormattedLastLogin() {
        return TimeUtils.formatStandardDateTime(this.lastLogin, "Nie");
    }
}
