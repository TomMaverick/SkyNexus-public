package skynexus.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.model.User;

/**
 * Verwaltet die aktuelle Benutzersitzung in der Anwendung.
 * Implementiert das Singleton-Pattern, um eine einzige zentrale Instanz zu garantieren.
 */
public class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    private static SessionManager instance;
    private User currentUser;

    /**
     * Privater Konstruktor verhindert direkte Instanziierung
     */
    private SessionManager() {
    }

    /**
     * Gibt die einzige Instanz des SessionManagers zurück (Thread-sicher)
     *
     * @return Die Singleton-Instanz des SessionManagers
     */
    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
            logger.debug("SessionManager initialisiert");
        }
        return instance;
    }

    /**
     * Gibt den aktuell angemeldeten Benutzer zurück
     *
     * @return Der angemeldete Benutzer oder null, wenn niemand angemeldet ist
     */
    public User getCurrentUser() {
        return currentUser;
    }

    /**
     * Setzt den aktuell angemeldeten Benutzer
     *
     * @param currentUser Der anzumeldende Benutzer
     */
    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
        if (currentUser != null) {
            logger.info("Benutzer angemeldet: {}", currentUser.getUsername());
        }
    }

    /**
     * Beendet die aktuelle Sitzung
     */
    public void clearSession() {
        if (currentUser != null) {
            logger.info("Benutzer abgemeldet: {}", currentUser.getUsername());
        }
        this.currentUser = null;
    }
}
