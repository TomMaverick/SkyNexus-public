package skynexus.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.*;

/**
 * Manager für die Internationalisierung und Lokalisierung der Anwendung.
 * Unterstützt mehrere Sprachen und bietet Methoden zum Abrufen übersetzter Texte.
 * Enthält auch Hilfsmethoden für den Zugriff auf Ressourcenbündel mit Fallback-Mechanismen.
 * HINWEIS: Die Internationalisierung wurde vorübergehend deaktiviert.
 * Aktuell wird nur Deutsch als Sprache unterstützt, unabhängig von den Benutzereinstellungen.
 */
public final class I18nManager {
    // Logger für I18nManager-spezifische Meldungen
    private static final Logger i18nLogger = LoggerFactory.getLogger(I18nManager.class);
    private static final Logger helperLogger = LoggerFactory.getLogger(I18nManager.class.getName() + ".I18nManager"); // Eindeutiger Logger-Name

    private static final String RESOURCE_BASE_NAME = "i18n/messages";

    // Unterstützte Sprachen mit ihren Locale-Objekten
    private static final Map<String, Locale> SUPPORTED_LOCALES = new HashMap<>();
    private static final Locale DEFAULT_LOCALE = Locale.GERMAN;
    private static ResourceBundle bundle;

    static {
        SUPPORTED_LOCALES.put("de", Locale.GERMAN);
        SUPPORTED_LOCALES.put("en", Locale.ENGLISH);
        // Hier können weitere Sprachen hinzugefügt werden
    }

    static {
        loadBundle();
    }

    /**
     * Privater Konstruktor verhindert Instanziierung dieser Utility-Klasse.
     */
    private I18nManager() {
        // Utility-Klasse sollte nicht instanziiert werden
    }

    /**
     * Lädt das ResourceBundle für die aktuelle Sprache.
     * Verwendet immer das deutsche Sprachpaket.
     */
    private static void loadBundle() {
        try {
            // Immer deutsche Locale verwenden
            bundle = ResourceBundle.getBundle(RESOURCE_BASE_NAME, DEFAULT_LOCALE);
            i18nLogger.info("Sprachpaket geladen: {}", DEFAULT_LOCALE.getDisplayLanguage(DEFAULT_LOCALE));
        } catch (MissingResourceException e) {
            i18nLogger.error("Kritischer Fehler: Standard-Sprachpaket nicht gefunden");
            // Fallback auf ein leeres Bundle, um NullPointerExceptions zu vermeiden
            bundle = new ResourceBundle() {
                @Override
                protected Object handleGetObject(String key) { return null; }
                @Override
                public Enumeration<String> getKeys() { return Collections.emptyEnumeration(); }
            };
            // Optional: throw new RuntimeException("Keine Sprachdateien verfügbar", e);
        }
    }

    /**
     * Gibt eine übersetzte Nachricht zurück.
     * Verwendet das intern verwaltete ResourceBundle.
     *
     * @param key Schlüssel der Nachricht
     * @return Übersetzte Nachricht oder Schlüssel in eckigen Klammern bei Fehler.
     */
    public static String getMessage(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        if (bundle == null) {
            i18nLogger.error("ResourceBundle ist null in getMessage für Key: {}", key);
            return "[" + key + "]";
        }

        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            i18nLogger.info("Übersetzungsschlüssel nicht gefunden: {}", key);
            return "[" + key + "]";
        } catch (ClassCastException e) {
            i18nLogger.error("Falscher Typ für Schlüssel {} im ResourceBundle.", key, e);
            return "[" + key + "]";
        }
    }

    /**
     * Gibt eine übersetzte und formatierte Nachricht zurück.
     * Verwendet das intern verwaltete ResourceBundle und MessageFormat.
     *
     * @param key  Schlüssel der Nachricht
     * @param args Argumente für die Formatierung
     * @return Übersetzte und formatierte Nachricht oder Schlüssel bei Fehlern.
     */
    public static String getMessage(String key, Object... args) {
        String pattern = getMessage(key); // Holt den String oder "[key]"

        // Wenn der Schlüssel nicht gefunden wurde, geben wir nur den Schlüssel zurück
        if (pattern.startsWith("[") && pattern.endsWith("]")) {
            return pattern;
        }

        // Nur formatieren, wenn Argumente vorhanden sind
        if (args == null || args.length == 0) {
            return pattern; // Keine Formatierung nötig
        }

        try {
            // Verwende MessageFormat für locale-sensitive Formatierung
            MessageFormat formatter = new MessageFormat(pattern, getCurrentLocale());
            return formatter.format(args);
        } catch (IllegalArgumentException e) {
            // Fehler bei der Formatierung (z.B. falsche Anzahl Argumente)
            i18nLogger.debug("Formatierungsfehler für Schlüssel '{}' mit Pattern '{}': {}", key, pattern, e.getMessage());
            return pattern; // Gebe das unformatierte Pattern zurück
        }
    }

    /**
     * Gibt die aktuell verwendete Locale zurück.
     *
     * @return Die aktuelle Locale (immer Deutsch)
     */
    public static Locale getCurrentLocale() {
        return DEFAULT_LOCALE; // Immer Deutsch zurückgeben
    }

    /**
     * Gibt das ResourceBundle für die aktuelle Sprache zurück.
     *
     * @return Das aktuelle ResourceBundle (immer Deutsch)
     */
    public static ResourceBundle getResourceBundle() {
        // Stelle sicher, dass das Bundle geladen ist (obwohl static init das tun sollte)
        if (bundle == null) {
            loadBundle();
        }
        return bundle;
    }

    /**
     * Holt einen String aus dem *übergebenen* Ressourcenbündel mit Fallback.
     * Nützlich, wenn man mit anderen Bundles als dem Haupt-I18n-Bundle arbeitet.
     *
     * @param resources Das zu verwendende Ressourcenbündel.
     * @param key       Der Schlüssel.
     * @param fallback  Der Fallback-String bei fehlendem Schlüssel oder null-Bundle.
     * @return Den gefundenen String oder den Fallback-String.
     */
    public static String getString(ResourceBundle resources, String key, String fallback) {
        if (resources == null) {
            helperLogger.trace("ResourceBundle ist null für Key '{}', verwende Fallback '{}'", key, fallback);
            return fallback;
        }
        if (key == null) {
            helperLogger.warn("Schlüssel ist null, verwende Fallback '{}'", fallback);
            return fallback;
        }

        try {
            return resources.getString(key);
        } catch (MissingResourceException e) {
            helperLogger.warn("Fehlender Ressourcenschlüssel: '{}', verwende Fallback '{}'", key, fallback);
            return fallback;
        } catch (ClassCastException e) {
            helperLogger.error("Falscher Typ für Schlüssel '{}' im übergebenen ResourceBundle.", key, e);
            return fallback;
        }
    }

    /**
     * Holt einen String aus dem *übergebenen* Ressourcenbündel mit alternativen Schlüsseln.
     * Versucht die Schlüssel in der angegebenen Reihenfolge.
     *
     * @param resources Das zu verwendende Ressourcenbündel.
     * @param fallback  Der Fallback-String, wenn kein Schlüssel gefunden wird.
     * @param keys      Die zu versuchenden Schlüssel.
     * @return Den gefundenen String oder den Fallback-String.
     */
    public static String getStringWithAlternatives(ResourceBundle resources, String fallback, String... keys) {
        if (resources == null) {
            helperLogger.trace("ResourceBundle ist null für alternative Keys, verwende Fallback '{}'", fallback);
            return fallback;
        }
        if (keys == null || keys.length == 0) {
            helperLogger.warn("Keine alternativen Schlüssel angegeben, verwende Fallback '{}'", fallback);
            return fallback;
        }

        for (String key : keys) {
            if (key == null) continue; // Überspringe null-Schlüssel
            try {
                return resources.getString(key);
            } catch (MissingResourceException e) {
                // Versuche den nächsten Schlüssel
                helperLogger.trace("Alternativer Schlüssel '{}' nicht gefunden, versuche nächsten.", key);
            } catch (ClassCastException e) {
                helperLogger.error("Falscher Typ für alternativen Schlüssel '{}' im übergebenen ResourceBundle.", key, e);
                // Versuche trotzdem den nächsten Schlüssel
            }
        }

        // Alle Alternativen fehlgeschlagen
        helperLogger.warn("Keiner der alternativen Ressourcenschlüssel gefunden: {}, verwende Fallback '{}'", Arrays.toString(keys), fallback);
        return fallback;
    }

    /**
     * Formatiert einen String aus dem *übergebenen* Ressourcenbündel mit Argumenten.
     * Verwendet String.format für die Formatierung.
     *
     * @param resources      Das zu verwendende Ressourcenbündel.
     * @param key            Der Schlüssel.
     * @param fallbackFormat Das Fallback-Format bei fehlendem Schlüssel.
     * @param args           Die Argumente für String.format.
     * @return Den formatierten String.
     */
    public static String getFormattedString(ResourceBundle resources, String key, String fallbackFormat, Object... args) {
        String format = getString(resources, key, fallbackFormat);
        if (format == null) {
            // Sollte durch getString eigentlich nicht passieren, aber sicher ist sicher
            format = fallbackFormat != null ? fallbackFormat : "";
        }
        try {
            // Locale.ROOT wird verwendet, um unerwartete locale-spezifische Formatierungen
            // durch String.format zu vermeiden, wenn MessageFormat nicht genutzt wird.
            // Wenn locale-spezifische Formatierung gewünscht ist, sollte MessageFormat verwendet werden.
            return String.format(Locale.ROOT, format, args);
        } catch (IllegalFormatException e) {
            helperLogger.error("Formatierungsfehler für Key '{}' mit Pattern '{}': {}", key, format, e.getMessage());
            return format; // Gebe das unformatierte Pattern zurück
        }
    }

}
