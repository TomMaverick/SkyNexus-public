package skynexus.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.model.Airline;
import skynexus.model.Airport;
import skynexus.service.AirportService;
import skynexus.service.PassengerService;
import skynexus.service.SystemSettingsService;
import skynexus.util.ValidationUtils;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Controller für die Ersteinrichtung des Systems beim ersten Start.
 */
public class FirstRunSetupController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(FirstRunSetupController.class);

    // Services
    private final AirportService airportService;
    private final SystemSettingsService settingsService;
    private final PassengerService passengerService;

    // --- FXML Felder ---
    @FXML private TextField airlineNameField;
    @FXML private TextField airlineIcaoField;
    @FXML private TextField airportNameField;
    @FXML private TextField airportIcaoField;
    @FXML private TextField airportCityField;
    @FXML private ComboBox<Map<String, String>> airportCountryCombo;
    @FXML private TextField airportLatField;
    @FXML private TextField airportLongField;
    @FXML private Button saveButton;
    @FXML private Label statusLabel;
    @FXML private Label errorLabel;

    // Listener
    private SetupCompletedListener setupCompletedListener;

    // Regex für erlaubte Zeichen in Namen/Stadt
    // Erlaubt: Buchstaben (inkl. Umlaute), Leerzeichen, Bindestrich, Apostroph, Punkt
    private static final String VALID_NAME_CHARS_REGEX = "^[a-zA-ZäöüÄÖÜß\\s\\-'.]+$";
    private static final String VALID_NAME_CHARS_DESCRIPTION = "Nur Buchstaben, Umlaute, Leerzeichen, Bindestrich, Apostroph, Punkt erlaubt.";


    /**
     * Konstruktor
     */
    public FirstRunSetupController() {
        this.airportService = AirportService.getInstance();
        this.settingsService = SystemSettingsService.getInstance();
        this.passengerService = PassengerService.getInstance();
        logger.debug("FirstRunSetupController Instanz erstellt.");
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("FirstRunSetupController wird initialisiert");
        Platform.runLater(() -> {
            if (errorLabel != null) errorLabel.setText("");
            if (statusLabel != null) statusLabel.setText("");
        });

        try {
            settingsService.ensureSystemSettingsTableExists();
        } catch (Exception e) {
            logger.error("Fehler beim Sicherstellen der system_settings Tabelle", e);
            showError("Kritischer Fehler: Systemeinstellungen können nicht initialisiert werden.");
            if(saveButton != null) saveButton.setDisable(true);
            return;
        }

        populateCountryComboBox();
        setDefaultValues();
        addInputValidationListeners();

        logger.info("FirstRunSetupController initialisiert");
    }

    /**
     * Füllt die ComboBox mit Ländern aus dem PassengerService.
     * Zeigt Ländernamen an und sortiert danach.
     */
    private void populateCountryComboBox() {
        try {
            List<Map<String, String>> countries = passengerService.getAllCountries();
            countries.sort(Comparator.comparing(countryMap -> countryMap.getOrDefault("country", ""), String.CASE_INSENSITIVE_ORDER));

            ObservableList<Map<String, String>> countryList = FXCollections.observableArrayList(countries);
            airportCountryCombo.setItems(countryList);

            StringConverter<Map<String, String>> converter = new StringConverter<>() {
                @Override
                public String toString(Map<String, String> countryMap) {
                    if (countryMap == null) return null;
                    String name = countryMap.getOrDefault("country", "Unbekannt");
                    String code = countryMap.getOrDefault("code_2", "");
                    return name + (!code.isEmpty() ? " (" + code.toUpperCase() + ")" : "");
                }

                @Override
                public Map<String, String> fromString(String string) {
                    return airportCountryCombo.getItems().stream()
                            .filter(map -> toString(map).equals(string))
                            .findFirst()
                            .orElse(null);
                }
            };
            airportCountryCombo.setConverter(converter);

            Map<String, String> defaultCountry = countries.stream()
                    .filter(map -> "DE".equalsIgnoreCase(map.get("code_2")))
                    .findFirst()
                    .orElse(null);

            if (defaultCountry != null) {
                airportCountryCombo.setValue(defaultCountry);
                logger.debug("Standardland '{}' ausgewählt.", converter.toString(defaultCountry));
            } else {
                logger.warn("Standardland 'DE' nicht in der Liste gefunden.");
                airportCountryCombo.getSelectionModel().selectFirst();
            }

        } catch (Exception e) {
            logger.error("Fehler beim Laden der Länder für die ComboBox", e);
            showError("Fehler: Länderliste konnte nicht geladen werden.");
            if (airportCountryCombo != null) airportCountryCombo.setDisable(true);
        }
    }

    /**
     * Setzt Standardwerte in die Eingabefelder.
     */
    private void setDefaultValues() {
        Airline currentAirline = Airline.getInstance();
        airlineNameField.setText(currentAirline.getName());
        airlineIcaoField.setText(currentAirline.getIcaoCode());

        airportNameField.setText("Frankfurt Intl. Airport");
        airportIcaoField.setText("EDDF");
        airportCityField.setText("Frankfurt");
        airportLatField.setText("50.0379");
        airportLongField.setText("8.5622");
    }

    /**
     * Fügt Listener zu Eingabefeldern hinzu, um die Validierung live auszuführen.
     */
    private void addInputValidationListeners() {
        airportNameField.textProperty().addListener((obs, oldV, newV) -> validateInputs());
        airportIcaoField.textProperty().addListener((obs, oldV, newV) -> validateInputs());
        airportCityField.textProperty().addListener((obs, oldV, newV) -> validateInputs());
        airportLatField.textProperty().addListener((obs, oldV, newV) -> validateInputs());
        airportLongField.textProperty().addListener((obs, oldV, newV) -> validateInputs());
        airlineNameField.textProperty().addListener((obs, oldV, newV) -> validateInputs());
        airlineIcaoField.textProperty().addListener((obs, oldV, newV) -> validateInputs());
        airportCountryCombo.valueProperty().addListener((obs, oldV, newV) -> validateInputs());

        validateInputs();
        logger.debug("Input Validation Listeners hinzugefügt und initiale Validierung durchgeführt.");
    }


    /**
     * Handler für den Speichern-Button.
     */
    @FXML
    private void handleSave() {
        logger.debug("handleSave aufgerufen.");
        if (!validateInputs()) {
            logger.warn("Validierung fehlgeschlagen. Speichern abgebrochen.");
            return;
        }

        saveButton.setDisable(true);
        clearError();
        showStatusMessage("Speichere Einstellungen...");

        Thread saveThread = new Thread(this::saveSettings);
        saveThread.setDaemon(true);
        saveThread.start();
    }

    /**
     * Validiert alle Eingabefelder mithilfe von ValidationUtils.
     * Zeigt Fehler im errorLabel an und steuert den saveButton.
     * @return true wenn alle Eingaben gültig sind, sonst false
     */
    private boolean validateInputs() {
        logger.trace("Starte Eingabevalidierung...");
        clearError();

        boolean isValid;
        try {
            // --- Flughafen Validierung ---
            String airportName = airportNameField.getText();
            ValidationUtils.validateNotEmpty(airportName, "Flughafen Name");
            ValidationUtils.validatePattern(airportName, VALID_NAME_CHARS_REGEX, "Flughafen Name", VALID_NAME_CHARS_DESCRIPTION); // NEU

            ValidationUtils.validateAirportICAO(airportIcaoField.getText().toUpperCase());

            String airportCity = airportCityField.getText();
            ValidationUtils.validateNotEmpty(airportCity, "Stadt");
            ValidationUtils.validatePattern(airportCity, VALID_NAME_CHARS_REGEX, "Stadt", VALID_NAME_CHARS_DESCRIPTION); // NEU

            ValidationUtils.validateNotNull(airportCountryCombo.getValue(), "Land");

            double latitude = ValidationUtils.parseDoubleWithCommaOrPoint(airportLatField.getText(), "Breitengrad");
            ValidationUtils.validateLatitude(latitude);
            double longitude = ValidationUtils.parseDoubleWithCommaOrPoint(airportLongField.getText(), "Längengrad");
            ValidationUtils.validateLongitude(longitude);

            // --- Airline Validierung ---
            String airlineName = airlineNameField.getText();
            ValidationUtils.validateNotEmpty(airlineName, "Airline Name");
            ValidationUtils.validatePattern(airlineName, VALID_NAME_CHARS_REGEX, "Airline Name", VALID_NAME_CHARS_DESCRIPTION); // NEU

            ValidationUtils.validateAirlineICAO(airlineIcaoField.getText().toUpperCase());
            // --- Ende Validierungen ---

            isValid = true;
            logger.trace("Validierung erfolgreich.");

        } catch (IllegalArgumentException e) {
            logger.warn("Validierungsfehler: {}", e.getMessage());
            showError(e.getMessage());
            isValid = false;
        } catch (Exception e) {
            logger.error("Unerwarteter Fehler bei der Validierung.", e);
            showError("Ein unerwarteter Fehler ist bei der Prüfung aufgetreten.");
            isValid = false;
        }

        final boolean finalIsValid = isValid;
        Platform.runLater(() -> {
            if (saveButton != null) {
                saveButton.setDisable(!finalIsValid);
                logger.trace("Save Button disable state set to: {}", !finalIsValid);
            }
        });

        return isValid;
    }

    /**
     * Speichert die Einstellungen. Wird im Hintergrund-Thread ausgeführt.
     */
    private void saveSettings() {
        try {
            Map<String, String> selectedCountryMap = airportCountryCombo.getValue();
            String countryCode = selectedCountryMap.get("code_2").toUpperCase();
            String countryName = selectedCountryMap.getOrDefault("country", countryCode);
            logger.debug("Verwende Ländercode: {}, Land: {}", countryCode, countryName);

            // 1. Airline aktualisieren und speichern
            Airline airline = Airline.getInstance();
            airline.setName(airlineNameField.getText().trim());
            airline.setIcaoCode(airlineIcaoField.getText().trim().toUpperCase());
            airline.setCountry(countryName);

            boolean airlineSaved = airline.saveChanges();
            if (!airlineSaved) {
                Platform.runLater(() -> showError("Fehler beim Speichern der Airline-Einstellungen."));
                logger.error("Speichern der Airline-Einstellungen fehlgeschlagen.");
                enableSaveButtonOnFxThread();
                return;
            }
            logger.info("Airline-Einstellungen '{}' erfolgreich in Systemeinstellungen gespeichert.", airline.getName());

            // 2. Flughafen erstellen und speichern
            Airport airport = new Airport();
            airport.setName(airportNameField.getText().trim());
            airport.setIcaoCode(airportIcaoField.getText().trim().toUpperCase());
            airport.setCity(airportCityField.getText().trim());
            airport.setCountry(countryName);

            try {
                double latitude = ValidationUtils.parseDoubleWithCommaOrPoint(airportLatField.getText(), "Breitengrad");
                double longitude = ValidationUtils.parseDoubleWithCommaOrPoint(airportLongField.getText(), "Längengrad");
                airport.setLatitude(latitude);
                airport.setLongitude(longitude);
                logger.debug("Koordinaten gesetzt: Lat={}, Long={}", airport.getLatitude(), airport.getLongitude());
            } catch (IllegalArgumentException e) {
                logger.error("Fehler beim Parsen der Koordinaten während des Speicherns (sollte nicht passieren): {}", e.getMessage());
                Platform.runLater(() -> showError("Fehlerhafte Koordinaten beim Speichern."));
                enableSaveButtonOnFxThread();
                return;
            }

            boolean airportSaved = airportService.saveAirport(airport);
            if (!airportSaved || airport.getId() == null) {
                Platform.runLater(() -> showError("Fehler beim Speichern des Flughafens."));
                logger.error("Speichern des Flughafens fehlgeschlagen oder keine ID zurückgegeben.");
                enableSaveButtonOnFxThread();
                return;
            }
            logger.info("Flughafen '{}' erfolgreich gespeichert mit ID {}.", airport.getName(), airport.getId());

            // 3. System-Einstellungen speichern
            settingsService.setDefaultAirlineId(airline.getId() != null ? airline.getId() : 1L);
            settingsService.setDefaultAirportId(airport.getId());
            settingsService.markSystemAsInitialized();
            logger.info("Systemeinstellungen aktualisiert: Default Airline ID={}, Default Airport ID={}",
                    airline.getId() != null ? airline.getId() : 1L, airport.getId());

            // 4. UI aktualisieren (Erfolg)
            Platform.runLater(() -> {
                showSuccess("Einstellungen erfolgreich gespeichert. Das System ist jetzt einsatzbereit.");
                clearError();

                Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(2.5), event -> {
                    if (setupCompletedListener != null) {
                        logger.info("Setup abgeschlossen, rufe Listener auf.");
                        setupCompletedListener.onSetupCompleted();
                    } else {
                        logger.warn("Kein SetupCompletedListener gesetzt, keine Weiterleitung.");
                    }
                }));
                timeline.play();
            });

        } catch (Exception e) {
            logger.error("Schwerwiegender Fehler beim Speichern der Einstellungen", e);
            Platform.runLater(() -> showError("Fehler beim Speichern: " + e.getMessage()));
            enableSaveButtonOnFxThread();
        }
    }

    /**
     * Aktiviert den Save-Button (im FX-Thread).
     */
    private void enableSaveButtonOnFxThread() {
        Platform.runLater(() -> {
            if (saveButton != null) saveButton.setDisable(false);
        });
    }

    /**
     * Zeigt eine Fehlermeldung an.
     */
    private void showError(String message) {
        Platform.runLater(() -> {
            if (errorLabel != null) {
                errorLabel.setText(message);
                logger.debug("Fehlermeldung angezeigt: {}", message);
            } else {
                logger.error("errorLabel ist null, kann Fehler nicht anzeigen: {}", message);
            }
            if (statusLabel != null) statusLabel.setText("");
        });
    }

    /**
     * Löscht die Fehlermeldung.
     */
    private void clearError() {
        Platform.runLater(() -> {
            if (errorLabel != null) errorLabel.setText("");
        });
    }


    /**
     * Zeigt eine Erfolgs-/Statusmeldung an.
     */
    private void showSuccess(String message) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(message);
                statusLabel.getStyleClass().setAll("status-label", "success-label");
                logger.debug("Erfolgsmeldung angezeigt: {}", message);
            } else {
                logger.error("statusLabel ist null, kann Erfolg nicht anzeigen: {}", message);
            }
            if (errorLabel != null) errorLabel.setText("");
        });
    }

    /**
     * Zeigt eine neutrale Statusmeldung an.
     */
    private void showStatusMessage(String message) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(message);
                statusLabel.getStyleClass().setAll("status-label", "status-message");
                logger.debug("Statusmeldung angezeigt: {}", message);
            } else {
                logger.error("statusLabel ist null, kann Status nicht anzeigen: {}", message);
            }
            if (errorLabel != null) errorLabel.setText("");
        });
    }

    /**
     * Setzt den Listener für erfolgreiche Einrichtung.
     */
    public void setSetupCompletedListener(SetupCompletedListener listener) {
        this.setupCompletedListener = listener;
    }

    /**
     * Handler für den Klick auf den Koordinaten-Link.
     */
    @FXML
    public void coordinateLinkClicked(javafx.event.ActionEvent actionEvent) {
        String url = "https://www.gpskoordinaten.de/";
        logger.debug("Öffne Link: {}", url);
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                logger.warn("Desktop.browse wird auf diesem System nicht unterstützt.");
                showError("Link konnte nicht im Browser geöffnet werden.");
            }
        } catch (IOException | URISyntaxException e) {
            logger.error("Fehler beim Öffnen des Links {}", url, e);
            showError("Fehler beim Öffnen des Links.");
        } catch (Exception e) {
            logger.error("Unerwarteter Fehler beim Öffnen des Links {}", url, e);
            showError("Unerwarteter Fehler beim Öffnen des Links.");
        }
    }

    /**
     * Interface für Benachrichtigung über Abschluss der Einrichtung.
     */
    public interface SetupCompletedListener {
        void onSetupCompleted();
    }
}
