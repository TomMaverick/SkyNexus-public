package skynexus.controller;

import skynexus.model.User;

/**
 * Interface für die Benachrichtigung über Authentifizierungsereignisse
 */
public interface AuthenticationListener {
    /**
     * Wird aufgerufen, wenn ein Benutzer erfolgreich authentifiziert wurde
     *
     * @param user Der authentifizierte Benutzer
     */
    void onLoginSuccessful(User user);

    /**
     * Wird aufgerufen, wenn ein Benutzer sich ausloggt
     */
    void onLogout();
}
