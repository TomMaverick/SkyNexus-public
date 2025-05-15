package skynexus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.model.User;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Service-Klasse für Sicherheitsfunktionen wie Passwort-Hashing und -Validierung.
 * Verwendet PBKDF2 mit HMAC-SHA256 zur sicheren Speicherung von Passwörtern.
 */
public class SecurityService {
    private static final Logger logger = LoggerFactory.getLogger(SecurityService.class);
    // Passwort-Richtlinien als Regex-Pattern
    // Mindestens 8 Zeichen, mind. 1 Zahl, 1 Kleinbuchstabe, 1 Großbuchstabe, 1 Sonderzeichen
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,}$");
    // Konfiguration für PBKDF2-Algorithmus
    private static final int SALT_LENGTH = 16;     // 16 Bytes = 128 Bits


    // HINWEIS: NUR FÜR TESTZWECKE - Vereinfachte Richtlinie: mindestens 6 beliebige Zeichen
    //private static final Pattern PASSWORD_PATTERN = Pattern.compile(".{6,}$");
    private static final int ITERATIONS = 10000;   // OWASP-empfohlene Iteration für PBKDF2
    private static final int KEY_LENGTH = 256;     // 256 Bits Ausgabelänge
    private static SecurityService instance;

    /**
     * Privater Konstruktor für Singleton-Pattern
     */
    private SecurityService() {
    }

    /**
     * Gibt die Singleton-Instanz des SecurityService zurück
     *
     * @return Die einzige Instanz des SecurityService
     */
    public static synchronized SecurityService getInstance() {
        if (instance == null) {
            instance = new SecurityService();
            logger.debug("SecurityService initialisiert");
        }
        return instance;
    }

    /**
     * Generiert einen zufälligen Salt für das Password-Hashing
     *
     * @return Salt als Base64-String
     */
    public String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Hashed das Passwort mit dem angegebenen Salt
     *
     * @param password Klartextpasswort
     * @param saltStr  Salt als Base64-String
     * @return Hash als Base64-String
     * @throws RuntimeException bei Fehlern im Hashing-Algorithmus
     */
    public String hashPassword(String password, String saltStr) {
        if (password == null || saltStr == null) {
            logger.warn("Null-Werte für Passwort oder Salt übergeben");
            throw new IllegalArgumentException("Passwort und Salt dürfen nicht null sein");
        }

        try {
            byte[] salt = Base64.getDecoder().decode(saltStr);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);

        } catch (NoSuchAlgorithmException e) {
            logger.error("Hashing-Algorithmus nicht verfügbar: {}", e.getMessage());
            throw new RuntimeException("Sicherheitsfehler: Hashing-Algorithmus nicht verfügbar", e);

        } catch (InvalidKeySpecException e) {
            logger.error("Ungültige Parameter für Hashing-Algorithmus: {}", e.getMessage());
            throw new RuntimeException("Sicherheitsfehler: Ungültige Parameter für Hashing", e);

        } catch (IllegalArgumentException e) {
            logger.error("Ungültiger Salt-Wert (kein gültiges Base64): {}", e.getMessage());
            throw new RuntimeException("Ungültiger Salt-Wert", e);
        }
    }

    /**
     * Überprüft, ob das angegebene Passwort den Richtlinien entspricht
     *
     * @param password Das zu überprüfende Passwort
     * @return true, wenn das Passwort den Richtlinien entspricht
     */
    public boolean isPasswordValid(String password) {
        if (password == null || password.isEmpty()) {
            logger.debug("Passwortvalidierung fehlgeschlagen: Passwort ist leer");
            return false;
        }

        boolean isValid = PASSWORD_PATTERN.matcher(password).matches();
        if (!isValid) {
            logger.debug("Passwortvalidierung fehlgeschlagen: Passwort entspricht nicht den Richtlinien");
        }
        return isValid;
    }

    /**
     * Vergleicht ein Klartextpasswort mit dem gespeicherten Hash
     *
     * @param plainPassword Eingegebenes Passwort
     * @param user          Benutzer mit gespeichertem Hash und Salt
     * @return true, wenn das Passwort korrekt ist
     */
    public boolean verifyPassword(String plainPassword, User user) {
        if (plainPassword == null || user == null ||
                user.getPasswordHash() == null || user.getSalt() == null) {
            logger.warn("Passwortverifikation nicht möglich: Fehlende Daten");
            return false;
        }

        try {
            String computedHash = hashPassword(plainPassword, user.getSalt());
            boolean matches = computedHash.equals(user.getPasswordHash());

            if (!matches) {
                logger.debug("Passwort-Verifikation fehlgeschlagen für Benutzer: {}", user.getUsername());
            }

            return matches;
        } catch (Exception e) {
            logger.error("Fehler bei Passwort-Verifikation für Benutzer {}: {}",
                    user.getUsername(), e.getMessage());
            return false;
        }
    }


    /**
     * Verschlüsselt einen String.
     * 1. Kehrt die Reihenfolge der Zeichen um
     * 2. Kodiert das Ergebnis mit Base64
     *
     * @param value Der zu verschlüsselnde String
     * @return Der verschlüsselte String
     */
    public String encrypt(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        // Zeichen umkehren
        String reversed = new StringBuilder(value).reverse().toString();
        // Mit Base64 kodieren
        return Base64.getEncoder().encodeToString(reversed.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Entschlüsselt einen String.
     * 1. Dekodiert den Base64-String
     * 2. Kehrt die Reihenfolge der Zeichen um
     *
     * @param encryptedValue Der verschlüsselte String
     * @return Der entschlüsselte String
     */
    public String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isEmpty()) {
            return "";
        }

        try {
            // Base64 dekodieren
            byte[] decoded = Base64.getDecoder().decode(encryptedValue.trim());
            String reversed = new String(decoded, StandardCharsets.UTF_8);
            // Zeichen wieder umkehren
            return new StringBuilder(reversed).reverse().toString();
        } catch (IllegalArgumentException e) {
            logger.error("Ungültiges Base64-Format beim Entschlüsseln: {}", encryptedValue);
            return "";
        }
    }
}
