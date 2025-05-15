package skynexus.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.database.DatabaseConnectionManager;
import skynexus.service.SecurityService;
import skynexus.util.Config;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Controller für die Einstellungen-Ansicht.
 */
public class SettingsViewController {
    private static final Logger logger = LoggerFactory.getLogger(SettingsViewController.class);
    private static final String CONFIG_PATH = "src/main/resources/config/db.properties";
    private final SecurityService securityService = SecurityService.getInstance();
    private final Properties dbProperties = new Properties();

    @FXML private Label settingsTitleLabel;
    @FXML private Label dbHostLabel;
    @FXML private Label dbNameLabel;
    @FXML private Label dbUserLabel;
    @FXML private Label dbPasswordLabel;
    @FXML private TextField dbHostField;
    @FXML private TextField dbNameField;
    @FXML private TextField dbUserField;
    @FXML private PasswordField dbPasswordField;
    @FXML private Button saveButton;

    @FXML private Label maxConnectionsLabel;
    @FXML private TextField maxConnectionsField;
    @FXML private Label connectionTimeoutLabel;
    @FXML private TextField connectionTimeoutField;
    @FXML private Label checkIntervalLabel;
    @FXML private TextField checkIntervalField;

    @FXML
    public void initialize() {
        logger.debug("SettingsView wird initialisiert");

        loadDatabaseSettings();
        updateUIText();

        saveButton.setOnAction(event -> saveSettings());

        logger.debug("SettingsView initialisiert");
    }

    public void setMainController(MainViewController controller) {
        // Nur für Controller-Referenz
    }

    /**
     * Lädt die Datenbankeinstellungen aus der db.properties-Datei
     */
    private void loadDatabaseSettings() {
        try (InputStream input = getClass().getResourceAsStream("/config/db.properties")) {
            if (input != null) {
                dbProperties.load(input);

                // DB-URL in Host und Namen aufteilen
                String dbUrl = dbProperties.getProperty("db.url", "jdbc:mariadb://localhost:3306/SkyNexus");
                dbHostField.setText(extractHostFromUrl(dbUrl));
                dbNameField.setText(extractDbNameFromUrl(dbUrl));

                // Benutzername und Passwort
                dbUserField.setText(dbProperties.getProperty("db.user", ""));

                // Passwort setzen
                boolean isEncrypted = Boolean.parseBoolean(dbProperties.getProperty("db.password.encrypted", "false"));
                String password = dbProperties.getProperty("db.password", "");

                if (isEncrypted && !password.isEmpty()) {
                    try {
                        String decrypted = securityService.decrypt(password);
                        dbPasswordField.setText(decrypted);
                    } catch (Exception e) {
                        logger.error("Fehler beim Entschlüsseln des Passworts: {}", e.getMessage());
                        dbPasswordField.setText("");
                    }
                } else {
                    dbPasswordField.setText(password);
                }

                dbPasswordField.setPromptText("********");

                // Erweiterte Datenbankeinstellungen
                maxConnectionsField.setText(dbProperties.getProperty("db.maxPoolSize", "7"));
                connectionTimeoutField.setText(dbProperties.getProperty("db.connectionTimeout", "5000"));
                checkIntervalField.setText(dbProperties.getProperty("db.connection.check.interval", "10"));

                logger.debug("Datenbankeinstellungen geladen");
            } else {
                logger.warn("Konfigurationsdatei nicht gefunden");
                setDefaultDatabaseSettings();
            }
        } catch (IOException e) {
            logger.error("Fehler beim Laden der Einstellungen: {}", e.getMessage());
            setDefaultDatabaseSettings();
            showError("Fehler", "Einstellungen konnten nicht geladen werden: " + e.getMessage());
        }
    }

    /**
     * Setzt Standard-Datenbankeinstellungen
     */
    private void setDefaultDatabaseSettings() {
        dbHostField.setText("localhost:3306");
        dbNameField.setText("SkyNexus");
        dbUserField.setText("root");
        dbPasswordField.setText("");

        maxConnectionsField.setText("7");
        connectionTimeoutField.setText("5000");
        checkIntervalField.setText("10");
    }

    /**
     * Extrahiert den Host und Port aus einer JDBC-URL
     */
    private String extractHostFromUrl(String url) {
        try {
            return url.split("//")[1].split("/")[0];
        } catch (Exception e) {
            logger.warn("Ungültiges URL-Format: {}", url);
            return "localhost:3306";
        }
    }

    /**
     * Extrahiert den Datenbanknamen aus einer JDBC-URL
     */
    private String extractDbNameFromUrl(String url) {
        try {
            return url.substring(url.lastIndexOf("/") + 1);
        } catch (Exception e) {
            logger.warn("Ungültiges URL-Format: {}", url);
            return "SkyNexus";
        }
    }

    /**
     * Aktualisiert die UI-Texte
     */
    private void updateUIText() {
        settingsTitleLabel.setText("Einstellungen");
        dbHostLabel.setText("Datenbank Host:");
        dbNameLabel.setText("Datenbank Name:");
        dbUserLabel.setText("Benutzername:");
        dbPasswordLabel.setText("Passwort:");

        maxConnectionsLabel.setText("Maximale Verbindungen:");
        connectionTimeoutLabel.setText("Verbindungs-Timeout (ms):");
        checkIntervalLabel.setText("Verbindungsprüfung (s):");

        saveButton.setText("Speichern");
    }

    /**
     * Speichert die aktuellen Einstellungen
     */
    private void saveSettings() {
        if (!validateInputs()) {
            return;
        }

        String dbUrl = "jdbc:mariadb://" + dbHostField.getText() + "/" + dbNameField.getText();
        String dbUser = dbUserField.getText();
        String dbPassword = dbPasswordField.getText();
        String encryptedPassword = securityService.encrypt(dbPassword);

        File configFile = new File(CONFIG_PATH);
        if (!prepareConfigFile(configFile)) {
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
            writer.write("# Datenbankeinstellungen\n");

            writer.write("db.url=" + dbUrl + "\n");
            writer.write("db.user=" + dbUser + "\n");
            writer.write("db.password=" + encryptedPassword + "\n");
            writer.write("db.password.encrypted=true\n");

            writer.write("\n# Verbindungspool-Konfiguration\n");
            writer.write("db.maxPoolSize=" + maxConnectionsField.getText() + "\n");
            writer.write("db.connectionTimeout=" + connectionTimeoutField.getText() + "\n");
            writer.write("db.connection.check.interval=" + checkIntervalField.getText() + "\n");

            writer.write("\n# Gespeichert am: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");

            Config.reloadAll();
            DatabaseConnectionManager.getInstance().reloadConfig();

            showInfo("Einstellungen gespeichert", "Die Datenbankeinstellungen wurden erfolgreich gespeichert.");

            logger.info("Datenbankeinstellungen aktualisiert");
        } catch (IOException e) {
            logger.error("Fehler beim Speichern: {}", e.getMessage());
            showError("Fehler", "Einstellungen konnten nicht gespeichert werden: " + e.getMessage());
        }
    }

    /**
     * Validiert die Benutzereingaben
     */
    private boolean validateInputs() {
        if (dbHostField.getText().trim().isEmpty()) {
            showError("Validierungsfehler", "Der Datenbank-Host darf nicht leer sein.");
            return false;
        }

        if (!dbHostField.getText().contains(":")) {
            showError("Validierungsfehler", "Das Host:Port Format ist ungültig. Bitte verwenden Sie 'host:port'.");
            return false;
        }

        if (dbNameField.getText().trim().isEmpty()) {
            showError("Validierungsfehler", "Der Datenbankname darf nicht leer sein.");
            return false;
        }

        if (validateNumericField(maxConnectionsField, "Max. Verbindungen", 1, 100)) return false;
        if (validateNumericField(connectionTimeoutField, "Verbindungs-Timeout", 1000, 120000)) return false;
        return !validateNumericField(checkIntervalField, "Prüfintervall", 1, 600);
    }

    /**
     * Validiert ein numerisches Eingabefeld
     */
    private boolean validateNumericField(TextField field, String fieldName, int min, int max) {
        try {
            String value = field.getText().trim();
            if (value.isEmpty()) {
                showError("Validierungsfehler", fieldName + " darf nicht leer sein.");
                return true;
            }

            int numValue = Integer.parseInt(value);
            if (numValue < min || numValue > max) {
                showError("Validierungsfehler", fieldName + " muss zwischen " + min + " und " + max + " liegen.");
                return true;
            }

            return false;
        } catch (NumberFormatException e) {
            showError("Validierungsfehler", fieldName + " muss eine Zahl sein.");
            return true;
        }
    }

    /**
     * Bereitet die Konfigurationsdatei zum Schreiben vor
     */
    private boolean prepareConfigFile(File configFile) {
        if (!configFile.getParentFile().exists()) {
            boolean created = configFile.getParentFile().mkdirs();
            if (!created) {
                showError("Fehler", "Konfigurationsverzeichnis konnte nicht erstellt werden.");
                logger.error("Konnte Verzeichnis nicht erstellen: {}", configFile.getParent());
                return false;
            }
        }
        return true;
    }

    /**
     * Zeigt eine Informationsmeldung an
     */
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Zeigt eine Fehlermeldung an
     */
    private void showError(String title, String message) {
        logger.error("{}: {}", title, message);

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
