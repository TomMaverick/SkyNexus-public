package skynexus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.database.DatabaseHelper.SQLFunction;
import skynexus.model.User;
import skynexus.util.SessionManager;
import skynexus.util.ValidationUtils;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service-Klasse für die Verwaltung von Benutzern.
 * Bietet Funktionen zur Authentifizierung, Benutzerregistrierung und -verwaltung.
 */
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private static UserService instance;
    private final SecurityService securityService;

    /**
     * Privater Konstruktor für Singleton-Pattern
     */
    private UserService() {
        this.securityService = SecurityService.getInstance();
        logger.debug("UserService initialisiert");
    }

    /**
     * Gibt die Singleton-Instanz des UserService zurück
     *
     * @return Die einzige Instanz des UserService
     */
    public static synchronized UserService getInstance() {
        if (instance == null) {
            instance = new UserService();
        }
        return instance;
    }

    /**
     * Erstellt einen neuen Benutzer mit dem angegebenen Passwort
     *
     * @param username Benutzername
     * @param password Klartextpasswort
     * @return Neu erstellter Benutzer
     * @throws IllegalArgumentException wenn das Passwort nicht den Richtlinien entspricht oder der Benutzername existiert
     * @throws SQLException             bei Datenbankfehlern
     */
    public User createUser(String username, String password)
            throws IllegalArgumentException, SQLException {
        // Parameter validieren
        if (username == null || username.trim().isEmpty()) {
            logger.warn("Benutzer kann nicht erstellt werden: Leerer Benutzername");
            throw new IllegalArgumentException("Der Benutzername darf nicht leer sein.");
        }



        // Passwort validieren
        if (!securityService.isPasswordValid(password)) {
            logger.warn("Benutzer kann nicht erstellt werden: Passwort entspricht nicht den Richtlinien");
            throw new IllegalArgumentException("Das Passwort entspricht nicht den Sicherheitsrichtlinien.");
        }

        // Überprüfen, ob Benutzername bereits existiert
        if (findUserByUsername(username).isPresent()) {
            logger.warn("Benutzer kann nicht erstellt werden: Benutzername '{}' existiert bereits", username);
            throw new IllegalArgumentException("Der Benutzername existiert bereits.");
        }

        // User-Objekt erstellen
        User user = new User();
        user.setUsername(username);
        user.setCreatedAt(LocalDateTime.now());
        user.setActive(true);

        // Passwort-Hashing
        String salt = securityService.generateSalt();
        String passwordHash = securityService.hashPassword(password, salt);
        user.setSalt(salt);
        user.setPasswordHash(passwordHash);

        // In Datenbank speichern
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO users (username, password_hash, salt, created_at, active, is_admin) " +
                             "VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPasswordHash());
            stmt.setString(3, user.getSalt());
            stmt.setTimestamp(4, Timestamp.valueOf(user.getCreatedAt()));
            stmt.setBoolean(5, user.isActive());
            stmt.setBoolean(6, user.isAdmin());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                logger.error("Erstellen des Benutzers fehlgeschlagen, kein Datensatz eingefügt");
                throw new SQLException("Erstellen des Benutzers fehlgeschlagen, kein Datensatz eingefügt.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    user.setId(generatedKeys.getLong(1));
                    logger.info("Neuer Benutzer erstellt: {}{}", user.getUsername(),
                            user.isAdmin() ? " (Admin)" : "");
                    return user;
                } else {
                    logger.error("Erstellen des Benutzers fehlgeschlagen, keine ID erhalten");
                    throw new SQLException("Erstellen des Benutzers fehlgeschlagen, keine ID erhalten.");
                }
            }
        } catch (SQLException e) {
            logger.error("Fehler beim Erstellen des Benutzers: {}", e.getMessage());
            throw e;
        }
    }



    /**
     * Gibt den SecurityService für diese Klasse zurück
     *
     * @return Der SecurityService
     */
    public SecurityService getSecurityService() {
        return securityService;
    }

    /**
     * Authentifiziert einen Benutzer mit Benutzername und Passwort
     *
     * @param username Benutzername
     * @param password Klartextpasswort
     * @return Optional mit dem Benutzer bei erfolgreicher Authentifizierung, sonst leeres Optional
     */
    public Optional<User> authenticateUser(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null) {
            logger.warn("Authentifizierung fehlgeschlagen: Leerer Benutzername oder Passwort");
            return Optional.empty();
        }

        try {
            Optional<User> userOpt = findUserByUsername(username);

            if (userOpt.isPresent()) {
                User user = userOpt.get();

                // Überprüfen, ob das Konto aktiv ist
                if (!user.isActive()) {
                    logger.warn("Anmeldeversuch für inaktiven Benutzer: {}", username);
                    return Optional.empty();
                }

                // Passwort überprüfen
                if (securityService.verifyPassword(password, user)) {
                    // Letzten Login aktualisieren
                    updateLastLogin(user.getId());
                    logger.info("Benutzer erfolgreich authentifiziert: {}", username);
                    return Optional.of(user);
                } else {
                    logger.warn("Authentifizierung fehlgeschlagen für Benutzer: {}", username);
                }
            } else {
                logger.warn("Anmeldeversuch für nicht existierenden Benutzer: {}", username);
            }

            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Fehler bei der Benutzerauthentifizierung: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Sucht einen Benutzer anhand des Benutzernamens
     *
     * @param username Benutzername
     * @return Optional mit dem Benutzer, falls gefunden
     * @throws SQLException bei Datenbankfehlern
     */
    public Optional<User> findUserByUsername(String username) throws SQLException {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }

        String sql = "SELECT u.id, u.username, u.password_hash, u.salt, " +
                "u.last_login, u.created_at, u.active, u.is_admin " +
                "FROM users u WHERE u.username = ?";

        return withConnection(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        User user = extractUserFromResultSet(rs);
                        logger.debug("Benutzer gefunden: {}", username);
                        return Optional.of(user);
                    }
                }
                logger.debug("Benutzer nicht gefunden: {}", username);
                return Optional.empty();
            }
        });
    }

    /**
     * Sucht einen Benutzer anhand seiner ID
     *
     * @param id ID des gesuchten Benutzers
     * @return Optional mit dem Benutzer, falls gefunden
     * @throws SQLException bei Datenbankfehlern
     */
    public Optional<User> findUserById(Long id) throws SQLException {
        if (id == null) {
            return Optional.empty();
        }

        String sql = "SELECT u.id, u.username, u.password_hash, u.salt, " +
                "u.last_login, u.created_at, u.active, u.is_admin " +
                "FROM users u WHERE u.id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User user = extractUserFromResultSet(rs);
                    logger.debug("Benutzer mit ID {} gefunden: {}", id, user.getUsername());
                    return Optional.of(user);
                }
            }
        } catch (SQLException e) {
            logger.error("Fehler beim Abrufen des Benutzers mit ID {}: {}",
                    id, e.getMessage());
            throw e;
        }

        logger.debug("Benutzer mit ID {} nicht gefunden", id);
        return Optional.empty();
    }

    /**
     * Extrahiert einen Benutzer aus einem ResultSet
     */
    private User extractUserFromResultSet(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setSalt(rs.getString("salt"));
        user.setAdmin(rs.getBoolean("is_admin"));

        // Timestamps
        Timestamp lastLoginTs = rs.getTimestamp("last_login");
        if (lastLoginTs != null) {
            user.setLastLogin(lastLoginTs.toLocalDateTime());
        }

        user.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        user.setActive(rs.getBoolean("active"));

        return user;
    }

    /**
     * Aktualisiert den Zeitstempel des letzten Logins
     *
     * @param userId ID des Benutzers
     * @throws SQLException bei Datenbankfehlern
     */
    private void updateLastLogin(Long userId) throws SQLException {
        String sql = "UPDATE users SET last_login = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(2, userId);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                logger.debug("Letzter Login aktualisiert für Benutzer-ID {}", userId);
            } else {
                logger.warn("Benutzer-ID {} nicht gefunden beim Aktualisieren des letzten Logins", userId);
            }
        } catch (SQLException e) {
            logger.error("Fehler beim Aktualisieren des letzten Logins für ID {}: {}",
                    userId, e.getMessage());
            throw e;
        }
    }

    /**
     * Gibt eine Liste aller Benutzer zurück
     *
     * @return Liste der Benutzer
     * @throws SQLException bei Datenbankfehlern
     */
    public List<User> getAllUsers() throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = "SELECT u.id, u.username, u.password_hash, u.salt, " +
                "u.last_login, u.created_at, u.active, u.is_admin " +
                "FROM users u ORDER BY u.username";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                users.add(extractUserFromResultSet(rs));
            }
            logger.info("{} Benutzer aus der Datenbank geladen", users.size());

        } catch (SQLException e) {
            logger.error("Fehler beim Abrufen aller Benutzer: {}", e.getMessage());
            throw e;
        }

        return users;
    }

    /**
     * Setzt den Admin-Status eines Benutzers
     *
     * @param userId  ID des Benutzers
     * @param isAdmin Neuer Admin-Status
     * @return true wenn erfolgreich, sonst false
     * @throws SQLException bei Datenbankfehlern
     */
    public boolean setUserAdminStatus(Long userId, boolean isAdmin) throws SQLException {
        if (userId == null) {
            logger.warn("Admin-Status kann nicht gesetzt werden: Benutzer-ID ist null");
            return false;
        }

        String sql = "UPDATE users SET is_admin = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setBoolean(1, isAdmin);
            stmt.setLong(2, userId);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                logger.info("Admin-Status für Benutzer-ID {} auf {} gesetzt", userId, isAdmin);
                return true;
            } else {
                logger.warn("Kein Benutzer mit ID {} gefunden", userId);
                return false;
            }
        } catch (SQLException e) {
            logger.error("Fehler beim Ändern des Admin-Status für ID {}: {}",
                    userId, e.getMessage());
            throw e;
        }
    }

    /**
     * Aktualisiert den Aktivitätsstatus eines Benutzers
     *
     * @param userId ID des Benutzers
     * @param active Neuer Status (true = aktiv, false = inaktiv)
     * @return true wenn erfolgreich, sonst false
     * @throws SQLException bei Datenbankfehlern
     */
    public boolean setUserActiveStatus(Long userId, boolean active) throws SQLException {
        if (userId == null) {
            logger.warn("Aktivitätsstatus kann nicht gesetzt werden: Benutzer-ID ist null");
            return false;
        }

        String sql = "UPDATE users SET active = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setBoolean(1, active);
            stmt.setLong(2, userId);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                logger.info("Benutzer-ID {} wurde {}", userId, active ? "aktiviert" : "deaktiviert");
                return true;
            } else {
                logger.warn("Kein Benutzer mit ID {} gefunden", userId);
                return false;
            }
        } catch (SQLException e) {
            logger.error("Fehler beim Ändern des Aktivitätsstatus für ID {}: {}",
                    userId, e.getMessage());
            throw e;
        }
    }

    /**
     * Validiert, ob ein Benutzer gelöscht werden darf
     *
     * @param userId ID des zu prüfenden Benutzers
     * @throws IllegalStateException wenn Löschung nicht erlaubt ist
     * @throws SQLException bei Datenbankfehlern
     */
    public void validateUserDeletion(Long userId) throws IllegalStateException, SQLException {
        if (userId == null) {
            throw new IllegalArgumentException("Benutzer-ID darf nicht null sein");
        }

        // Benutzer laden
        Optional<User> targetUserOpt = findUserById(userId);
        if (targetUserOpt.isEmpty()) {
            throw new IllegalStateException("Benutzer nicht gefunden");
        }

        User targetUser = targetUserOpt.get();
        User currentUser = SessionManager.getInstance().getCurrentUser();
        Long currentUserId = currentUser != null ? currentUser.getId() : null;

        // ValidationUtils für Lösch-Validierung verwenden
        ValidationUtils.validateUserDeletion(targetUser.getUsername(), userId, currentUserId);
    }

    /**
     * Löscht einen Benutzer aus der Datenbank
     *
     * @param userId ID des zu löschenden Benutzers
     * @return true wenn erfolgreich, sonst false
     * @throws SQLException bei Datenbankfehlern
     */
    public boolean deleteUser(Long userId) throws SQLException {
        // Validierung ausführen
        validateUserDeletion(userId);

        String sql = "DELETE FROM users WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            int deleted = stmt.executeUpdate();

            if (deleted > 0) {
                logger.info("Benutzer mit ID {} wurde gelöscht", userId);
                return true;
            } else {
                logger.warn("Kein Benutzer mit ID {} gefunden", userId);
                return false;
            }
        } catch (SQLException e) {
            logger.error("Fehler beim Löschen des Benutzers ID {}: {}", userId, e.getMessage());
            throw e;
        }
    }

    /**
     * Hilfsmethode zum Aufbauen der Datenbankverbindung mit automatischer Ressourcenverwaltung
     */
    private Connection getConnection() throws SQLException {
        return skynexus.database.DatabaseConnectionManager.getInstance().getConnection();
    }

    /**
     * Führt eine Datenbankoperation mit automatischer Verbindungsverwaltung aus
     *
     * @param operation Die auszuführende Datenbankoperation
     * @param <T>       Der Rückgabetyp der Operation
     * @return Das Ergebnis der Operation
     * @throws SQLException Bei Datenbankfehlern
     */
    private <T> T withConnection(SQLFunction<Connection, T> operation) throws SQLException {
        try (skynexus.database.DatabaseConnectionManager.ConnectionScope scope = skynexus.database.DatabaseConnectionManager.getInstance().createConnectionScope()) {
            return scope.execute(operation);
        }
    }
}
