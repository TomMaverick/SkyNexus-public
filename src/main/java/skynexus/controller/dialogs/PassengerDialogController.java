package skynexus.controller.dialogs;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.enums.FlightStatus;
import skynexus.enums.Gender;
import skynexus.enums.SeatClass;
import skynexus.model.Booking;
import skynexus.model.Flight;
import skynexus.model.Passenger;
import skynexus.service.FlightService;
import skynexus.service.PassengerService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller für die Passagier-Dialoge (Erstellen und Bearbeiten)
 */
public class PassengerDialogController {
    private static final Logger logger = LoggerFactory.getLogger(PassengerDialogController.class);

    // Services
    private final PassengerService passengerService = PassengerService.getInstance();
    private final FlightService flightService = FlightService.getInstance();

    // Dialog-Context
    private Dialog<Passenger> dialog;
    private Passenger passenger;
    private final SimpleObjectProperty<Boolean> formValid = new SimpleObjectProperty<>(false);
    private final ObservableList<Booking> bookings = FXCollections.observableArrayList();

    // UI-Elemente
    @FXML
    private TextField lastNameField;
    @FXML
    private TextField firstNameField;
    @FXML
    private ComboBox<Gender> genderCombo;
    @FXML
    private DatePicker dateOfBirthPicker;
    @FXML
    private ComboBox<Map<String, String>> nationalityCombo;
    @FXML
    private TextField countryCodeField;
    @FXML
    private TextField passportNumberField;
    @FXML
    private Label errorLabel;
    @FXML
    private ListView<Booking> bookingsListView;
    @FXML
    private Button addBookingButton;
    @FXML
    private Button removeBookingButton;
    @FXML
    private CheckBox showFutureFlights;
    @FXML
    private CheckBox showPastFlights;

    /**
     * Initialisiert den Dialog-Controller nach dem Laden des FXML.
     */
    @FXML
    private void initialize() {
        logger.debug("Initialisiere PassengerDialogController");

        setupUI();
        setupValidation();
    }

    /**
     * Initialisiert die UI-Elemente des Dialogs.
     */
    private void setupUI() {
        // Gender-ComboBox
        genderCombo.setItems(FXCollections.observableArrayList(Gender.values()));
        genderCombo.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Gender item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(formatGender(item));
                }
            }
        });
        genderCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Gender item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(formatGender(item));
                }
            }
        });

        // Nationalitäten-ComboBox
        List<Map<String, String>> countries = passengerService.getAllCountries();
        nationalityCombo.setItems(FXCollections.observableArrayList(countries));
        nationalityCombo.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Map<String, String> country, boolean empty) {
                super.updateItem(country, empty);
                if (empty || country == null) {
                    setText(null);
                } else {
                    setText(country.get("nationality"));
                    if ("Deutsch".equals(country.get("nationality"))) {
                        setStyle("-fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        nationalityCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Map<String, String> country, boolean empty) {
                super.updateItem(country, empty);
                if (empty || country == null) {
                    setText(null);
                } else {
                    setText(country.get("nationality"));
                }
            }
        });

        // Nationalität ändert Ländercode
        nationalityCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // Stellen sicher, dass der Ländercode in Großbuchstaben ist
                String countryCode = newVal.get("code_2").toUpperCase();
                countryCodeField.setText(countryCode);
                passportNumberField.setDisable(false);
                passportNumberField.setPromptText("7 alphanumerische Zeichen");

                logger.debug("Ländercode aus Nationalität gesetzt: '{}' für '{}'",
                        countryCode, newVal.get("nationality"));
            } else {
                countryCodeField.setText("");
                passportNumberField.setDisable(true);
                passportNumberField.setPromptText("Nationalität auswählen");
            }

            // Hier ein leichtes Delay einbauen, damit UI-Updates abgeschlossen sind
            Platform.runLater(this::validateForm);
        });

        // Stellen sicher, dass der Ländercode immer in Großbuchstaben ist und nicht editierbar
        countryCodeField.setDisable(true);  // Readonly-Feld
        countryCodeField.setStyle("-fx-opacity: 0.9;");  // Visuell leicht gedimmt, aber lesbar

        // Force uppercase wenn sich das Feld trotz Readonly ändert
        countryCodeField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(newVal.toUpperCase())) {
                String uppercased = newVal.toUpperCase();
                logger.debug("Ländercode in Großbuchstaben konvertiert: '{}' -> '{}'", newVal, uppercased);

                Platform.runLater(() -> countryCodeField.setText(uppercased));
            }
        });

        // Passportnummer in Großbuchstaben umwandeln und auf 7 Zeichen begrenzen
        passportNumberField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;

            // Begrenze auf maximal 7 Zeichen
            if (newVal.length() > 7) {
                Platform.runLater(() -> passportNumberField.setText(oldVal));
                return;
            }

            // Wandle Kleinbuchstaben in Großbuchstaben um
            if (!newVal.equals(newVal.toUpperCase())) {
                String uppercased = newVal.toUpperCase();
                Platform.runLater(() -> {
                    // Cursor-Position speichern
                    int caretPos = passportNumberField.getCaretPosition();
                    passportNumberField.setText(uppercased);
                    // Cursor-Position wiederherstellen
                    passportNumberField.positionCaret(Math.min(caretPos, uppercased.length()));
                });
            } else {
                // Zurücksetzen aller Fehleranzeigen
                clearError();

                // Sofortige Validierung nach 7 Zeichen
                if (newVal.length() == 7 && nationalityCombo.getValue() != null) {
                    Platform.runLater(this::validateForm);
                } else {
                    validateForm();
                }
            }
        });

        // Fehleranzeige einrichten
        errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 11px;");
        errorLabel.setVisible(false);

        // Buchungsliste einrichten (nur wenn BookingsListView vorhanden ist - Edit-Dialog)
        if (bookingsListView != null) {
            bookingsListView.setItems(bookings);
            bookingsListView.setCellFactory(param -> new ListCell<>() {
                @Override
                protected void updateItem(Booking booking, boolean empty) {
                    super.updateItem(booking, empty);
                    if (empty || booking == null || booking.getFlight() == null) {
                        setText(null);
                    } else {
                        setText(formatBookingDisplay(booking));
                    }
                }
            });

            // Entfernen-Button nur aktivieren, wenn Buchung ausgewählt
            if (removeBookingButton != null) {
                removeBookingButton.setDisable(true);
                bookingsListView.getSelectionModel().selectedItemProperty().addListener(
                        (obs, oldVal, newVal) -> removeBookingButton.setDisable(newVal == null));
            }

            // Checkbox-Filter einrichten
            setupBookingFilters();
        }
    }

    /**
     * Richtet die Validierung für alle Formularfelder ein.
     */
    private void setupValidation() {
        // Validierung bei Änderung der Felder
        lastNameField.textProperty().addListener((obs, oldVal, newVal) -> validateForm());
        firstNameField.textProperty().addListener((obs, oldVal, newVal) -> validateForm());
        genderCombo.valueProperty().addListener((obs, oldVal, newVal) -> validateForm());
        dateOfBirthPicker.valueProperty().addListener((obs, oldVal, newVal) -> validateForm());
        nationalityCombo.valueProperty().addListener((obs, oldVal, newVal) -> validateForm());

        // Initiale Validierung
        validateForm();
    }

    /**
     * Validiert alle Formularfelder und aktualisiert den Zustand des Dialogs.
     * Diese Methode verwendet ValidationUtils für zentrale Validierungen.
     * Eine klare Struktur minimiert Thread-Synchronisationsprobleme.
     */
    private void validateForm() {
        // Zurücksetzen aller Fehleranzeigen zu Beginn
        clearError();

        // Vorherigen Formularstatus speichern
        boolean wasValid = formValid.get();

        // Standardmäßig setzen wir das Formular auf ungültig, bis alle Prüfungen bestanden sind
        formValid.set(false);

        try {
            // 1. Persönliche Daten validieren
            validatePersonalDetails();

            // 2. Passnummer validieren
            final String fullPassportNumber = validatePassportNumber();

            // 3. Eindeutigkeit der Passnummer in der Datenbank prüfen
            validatePassportUniqueness(fullPassportNumber);

            // 4. Alle Validierungen bestanden - Formular als gültig markieren
            // Wenn wir bis hierher kommen, ist das Formular gültig!
            formValid.set(true);

            // UI-Updates auf JavaFX Thread ausführen - kompakt in einem einzigen Aufruf
            Platform.runLater(this::updateUIAfterSuccessfulValidation);

            logger.debug("Validierung erfolgreich: Formular ist gültig");

        } catch (IllegalArgumentException e) {
            // Validierungsfehler abfangen und anzeigen
            showError(e.getMessage());
            logger.debug("Validierung fehlgeschlagen: {}", e.getMessage());
        } catch (Exception e) {
            // Unerwartete Fehler abfangen
            showError("Validierungsfehler: " + e.getMessage());
            logger.error("Unerwarteter Validierungsfehler: {}", e.getMessage(), e);
        }

        // Logging, wenn sich der Status geändert hat
        if (wasValid != formValid.get()) {
            logger.debug("Formularstatus geändert: {} -> {}", wasValid, formValid.get());
        }
    }

    /**
     * Validiert die persönlichen Daten des Passagiers (Name, Geschlecht, Geburtsdatum, Nationalität).
     * @throws IllegalArgumentException wenn ein Feld ungültig ist
     */
    private void validatePersonalDetails() {
        // Nachname
        if (lastNameField.getText() == null || lastNameField.getText().trim().isEmpty()) {
            throw new IllegalArgumentException("Nachname darf nicht leer sein");
        }

        // Vereinfachte Validierung für Namen (a-z, A-Z, Leerzeichen, Bindestrich, Apostroph)
        String simpleNameRegex = "^[a-zA-Z \\-']+$";
        if (!lastNameField.getText().matches(simpleNameRegex)) {
            throw new IllegalArgumentException("Nachname enthält ungültige Zeichen");
        }

        // Vorname
        if (firstNameField.getText() == null || firstNameField.getText().trim().isEmpty()) {
            throw new IllegalArgumentException("Vorname darf nicht leer sein");
        }

        if (!firstNameField.getText().matches(simpleNameRegex)) {
            throw new IllegalArgumentException("Vorname enthält ungültige Zeichen");
        }

        // Geschlecht
        if (genderCombo.getValue() == null) {
            throw new IllegalArgumentException("Bitte wählen Sie ein Geschlecht");
        }

        // Geburtsdatum
        if (dateOfBirthPicker.getValue() == null) {
            throw new IllegalArgumentException("Geburtsdatum darf nicht leer sein");
        }

        LocalDate dob = dateOfBirthPicker.getValue();
        if (dob.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Geburtsdatum kann nicht in der Zukunft liegen");
        }

        if (dob.isBefore(LocalDate.now().minusYears(130))) {
            throw new IllegalArgumentException("Geburtsdatum unrealistisch (max. 130 Jahre)");
        }

        // Nationalität
        if (nationalityCombo.getValue() == null) {
            throw new IllegalArgumentException("Bitte wählen Sie eine Nationalität");
        }
    }

    /**
     * Validiert die Passnummer und gibt die vollständige, validierte Passnummer zurück.
     *
     * @return Die vollständige validierte Passnummer (Ländercode + Basisnummer)
     * @throws IllegalArgumentException wenn die Passnummer ungültig ist
     */
    private String validatePassportNumber() {
        // Reisepassnummer - nur prüfen, wenn nicht deaktiviert
        if (passportNumberField.isDisabled()) {
            throw new IllegalArgumentException("Nationalität auswählen, um Passnummer einzugeben");
        }

        // Prüfen ob Passnummer eingegeben wurde
        if (passportNumberField.getText() == null || passportNumberField.getText().trim().isEmpty()) {
            throw new IllegalArgumentException("Reisepassnummer darf nicht leer sein");
        }

        // Basis-Passnummer normalisieren
        String passportBase = passportNumberField.getText().trim().toUpperCase();

        // Basisnummer-Längenprüfung
        if (passportBase.length() != 7) {
            throw new IllegalArgumentException("Reisepassnummer muss genau 7 Zeichen lang sein");
        }

        // Ländercode ermitteln und normalisieren
        Map<String, String> selectedCountry = nationalityCombo.getValue();
        if (selectedCountry == null) {
            throw new IllegalArgumentException("Bitte wählen Sie eine Nationalität aus");
        }

        String countryCode = selectedCountry.get("code_2").toUpperCase();

        // Vollständige Passnummer zusammensetzen
        final String fullPassportNumber = countryCode + passportBase;

        // Detailliertes Debug-Logging
        logger.debug("Validierung - Passnummer: '{}' = '{}' + '{}'",
                fullPassportNumber, countryCode, passportBase);

        // Zentralisierte Validierung durch ValidationUtils
        try {
            // Diese Methode wirft eine Exception bei Ungültigkeit
            skynexus.util.ValidationUtils.validatePassportNumber(fullPassportNumber);
        } catch (IllegalArgumentException e) {
            logger.debug("ValidationUtils-Fehler: {}", e.getMessage());
            throw e; // Originalfehler weitergeben
        }

        return fullPassportNumber;
    }

    /**
     * Prüft die Eindeutigkeit der Passnummer in der Datenbank.
     *
     * @param fullPassportNumber Die zu prüfende vollständige Passnummer
     * @throws IllegalArgumentException wenn die Passnummer nicht eindeutig ist
     */
    private void validatePassportUniqueness(String fullPassportNumber) {
        passengerService.isPassportNumberUnique(
                fullPassportNumber,
                (passenger != null) ? passenger.getId() : null
        );
    }

    /**
     * Aktualisiert die UI nach erfolgreicher Validierung.
     * Diese Methode muss im JavaFX Application Thread ausgeführt werden.
     */
    private void updateUIAfterSuccessfulValidation() {
        // Fehleranzeigen nochmals explizit zurücksetzen
        clearError();

        // Sicherstellen, dass normalisierte Werte in UI-Feldern sind
        if (nationalityCombo.getValue() != null) {
            String passportBase = passportNumberField.getText().trim().toUpperCase();
            String countryCode = nationalityCombo.getValue().get("code_2").toUpperCase();

            passportNumberField.setText(passportBase);
            countryCodeField.setText(countryCode);
        }

        // Formular als gültig markieren
        formValid.set(true);

        // Speichern-Button aktivieren
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(
            dialog.getDialogPane().getButtonTypes().stream()
            .filter(bt -> bt.getButtonData() == ButtonBar.ButtonData.OK_DONE)
            .findFirst().orElse(ButtonType.OK));

        if (saveButton != null) {
            saveButton.setDisable(false);
            logger.debug("Speichern-Button aktiviert");
        } else {
            logger.warn("Speichern-Button nicht gefunden");
        }

        logger.debug("Formularvalidierung erfolgreich abgeschlossen, formValid={}", formValid.get());
    }

    /**
     * Zeigt eine Fehlermeldung im Dialog an.
     *
     * @param message Die anzuzeigende Fehlermeldung
     */
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);

        // Hervorhebung des Passportnummern-Feldes bei Duplikat-Passportnummern
        if (message.contains("Passnummer") && message.contains("wird bereits von")) {
            passportNumberField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
        } else {
            passportNumberField.setStyle("");
        }
    }

    /**
     * Initialisiert den Dialog für einen neuen Passagier.
     *
     * @param dialog Der übergeordnete Dialog
     */
    public void initNewPassenger(Dialog<Passenger> dialog) {
        this.dialog = dialog;
        this.passenger = new Passenger();

        // Standardwerte setzen
        genderCombo.setValue(Gender.MALE);
        dateOfBirthPicker.setValue(LocalDate.now().minusYears(30));

        // Deutsche Nationalität als Standard
        for (Map<String, String> country : nationalityCombo.getItems()) {
            if ("Deutsch".equals(country.get("nationality"))) {
                nationalityCombo.setValue(country);
                break;
            }
        }

        // Fehleranzeige explizit zurücksetzen
        clearError();

        // Buchungs-bezogene Elemente KOMPLETT entfernen
        if (bookingsListView != null) {
            bookingsListView.setVisible(false);
        }
        if (addBookingButton != null) {
            addBookingButton.setVisible(false);
        }
        if (removeBookingButton != null) {
            removeBookingButton.setVisible(false);
        }

        // Speichern-Button Update
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(
                dialog.getDialogPane().getButtonTypes().stream()
                        .filter(bt -> bt.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                        .findFirst().orElse(ButtonType.OK));

        if (saveButton != null) {
            formValid.addListener((obs, oldVal, newVal) -> saveButton.setDisable(!newVal));
            saveButton.setDisable(!formValid.get());
        }

        // Fokus auf das erste Feld setzen
        Platform.runLater(() -> lastNameField.requestFocus());

        // Nach Initialisierung nochmals explizit validieren mit verbesserter Thread-Synchronisation
        Platform.runLater(() -> {
            // Zuerst alle Fehleranzeigen zurücksetzen
            clearError();

            // Dann validieren
            validateForm();

            // Wenn keine Fehler aufgetreten sind (formValid=true), nochmals explizit
            // alle Fehleranzeigen zurücksetzen um Race Conditions zu vermeiden
            if (formValid.get()) {
                clearError();
            }

            logger.debug("Initialisierungsvalidierung abgeschlossen: formValid={}", formValid.get());
        });
    }

    /**
     * Initialisiert den Dialog für einen existierenden Passagier.
     *
     * @param dialog    Der übergeordnete Dialog
     * @param passenger Der zu bearbeitende Passagier
     */
    public void initEditPassenger(Dialog<Passenger> dialog, Passenger passenger) {
        this.dialog = dialog;
        this.passenger = passenger;

        // Formularfelder mit Passagierdaten füllen
        lastNameField.setText(passenger.getLastName());
        firstNameField.setText(passenger.getFirstName());
        genderCombo.setValue(passenger.getGender());
        dateOfBirthPicker.setValue(passenger.getDateOfBirth());

        // Fehleranzeige explizit zurücksetzen
        clearError();

        // Nationalität und Passnummer einfügen
        String nationalityValue = passenger.getNationality();
        String passportNumber = passenger.getPassportNumber();

        // Nationalität auswählen
        for (Map<String, String> country : nationalityCombo.getItems()) {
            if (country.get("nationality").equals(nationalityValue)) {
                nationalityCombo.setValue(country);

                // Ländercode und Passnummer trennen
                if (passportNumber != null && passportNumber.length() > 2) {
                    String countryCode = passportNumber.substring(0, 2);
                    String number = passportNumber.substring(2);
                    countryCodeField.setText(countryCode);
                    passportNumberField.setText(number);
                }
                break;
            }
        }

        // Buchungen hinzufügen
        if (passenger.getBookings() != null) {
            bookings.addAll(passenger.getBookings());
            
            // Filter direkt nach dem Hinzufügen von Buchungen anwenden
            if (showFutureFlights != null && showPastFlights != null) {
                applyBookingFilters();
            }
        }

        // Speichern-Button Update - Nutze den richtigen ButtonType
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(
                dialog.getDialogPane().getButtonTypes().stream()
                        .filter(bt -> bt.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                        .findFirst().orElse(ButtonType.OK));

        if (saveButton != null) {
            formValid.addListener((obs, oldVal, newVal) -> saveButton.setDisable(!newVal));
            saveButton.setDisable(!formValid.get());
        }

        // Fokus auf das erste Feld setzen
        Platform.runLater(() -> lastNameField.requestFocus());

        // Nach Initialisierung nochmals explizit validieren mit verbesserter Thread-Synchronisation
        Platform.runLater(() -> {
            // Zuerst alle Fehleranzeigen zurücksetzen
            clearError();

            // Dann validieren
            validateForm();

            // Wenn keine Fehler aufgetreten sind (formValid=true), nochmals explizit
            // alle Fehleranzeigen zurücksetzen um Race Conditions zu vermeiden
            if (formValid.get()) {
                clearError();
            }

            logger.debug("Initialisierungsvalidierung abgeschlossen: formValid={}", formValid.get());
        });
    }

    /**
     * Setzt alle Fehleranzeigen zurück.
     * Zentrale Methode zum Zurücksetzen aller Fehleranzeigen.
     * Diese Methode stellt sicher, dass alle UI-Elemente konsistent
     * zurückgesetzt werden, um Ghosting-Effekte zu vermeiden.
     */
    private void clearError() {
        // UI-Text-Elemente zurücksetzen
        errorLabel.setText("");
        errorLabel.setVisible(false);

        // Alle visuellen Fehler-Styles zurücksetzen
        passportNumberField.setStyle("");
        countryCodeField.setStyle("");
        lastNameField.setStyle("");
        firstNameField.setStyle("");
        dateOfBirthPicker.setStyle("");

        // Logging für Debugging-Zwecke
        logger.debug("Fehleranzeigen zurückgesetzt");
    }

    /**
     * Sammelt die Daten aus dem Formular und erstellt ein Passagier-Objekt.
     * Verwendet die zentrale Validierungslogik und stellt konsistente Passnummernnormalisierung sicher.
     *
     * @return Der erstellte oder aktualisierte Passagier
     */
    public Passenger getResult() {
        try {
            // DEBUGGING: Ausführliche Informationen ausgeben
            logger.info("==== PASSENGER DEBUG START ====");
            logger.info("Formular-Status vor Validierung: formValid={}", formValid.get());

            // Nochmal alle Felder validieren - verwendet ValidationUtils
            validateForm();

            // Wenn die Validierung fehlschlägt (formValid=false), abbrechen
            if (!formValid.get()) {
                logger.info("getResult(): Formular ist ungültig, Abbruch");
                return null;
            }

            // Persönliche Daten
            String lastName = lastNameField.getText().trim();
            String firstName = firstNameField.getText().trim();
            Gender gender = genderCombo.getValue();
            LocalDate dateOfBirth = dateOfBirthPicker.getValue();

            // Nationalität
            Map<String, String> selectedCountry = nationalityCombo.getValue();
            String nationality = selectedCountry.get("nationality");

            // DEBUGGING: Aktuelle Werte ausgeben
            logger.info("Felder zum Speichern:");
            logger.info("- lastName: '{}'", lastName);
            logger.info("- firstName: '{}'", firstName);
            logger.info("- gender: {}", gender);
            logger.info("- dateOfBirth: {}", dateOfBirth);
            logger.info("- nationality: '{}'", nationality);

            // Vollständige Passnummer zusammensetzen und GARANTIERT normalisieren
            String countryCode = selectedCountry.get("code_2").toUpperCase();
            String passportBase = passportNumberField.getText().trim().toUpperCase();
            String fullPassportNumber = countryCode + passportBase;

            // DEBUGGING: Passnummer-Details
            logger.info("Passnummer-Details:");
            logger.info("- countryCode: '{}'", countryCode);
            logger.info("- passportBase: '{}'", passportBase);
            logger.info("- zusammengesetzt: '{}'", fullPassportNumber);

            // KRITISCH: Explizit über ValidationUtils validieren, um EXAKT die gleiche
            // normalisierte Form zu erhalten wie bei der Validierung
            String normalizedPassport = skynexus.util.ValidationUtils.validatePassportNumber(fullPassportNumber);

            logger.info("- FINALE validierte Passnummer: '{}' (Original: '{}')",
                normalizedPassport, fullPassportNumber);

            // Passagier aktualisieren oder neu erstellen
            if (passenger.getId() != null) {
                // Existierenden Passagier aktualisieren
                passenger.setLastName(lastName);
                passenger.setFirstName(firstName);
                passenger.setGender(gender);
                passenger.setDateOfBirth(dateOfBirth);
                passenger.setNationality(nationality);
                passenger.setPassportNumber(normalizedPassport);

                // Buchungen zuweisen (nur im Edit-Modus mit verfügbarer ListView)
                if (bookingsListView != null) {
                    List<Booking> bookingList = new ArrayList<>(bookings);
                    passenger.setBookings(bookingList);
                }

                logger.info("Passagier aktualisiert mit ID {}, Passnummer: '{}'",
                    passenger.getId(), passenger.getPassportNumber());
            } else {
                // Neuen Passagier erstellen - direkt mit der normalisierten Passnummer
                passenger = new Passenger(lastName, firstName, gender, dateOfBirth, nationality, normalizedPassport);

                // Neue Passagiere erhalten eine leere Buchungsliste
                passenger.setBookings(new ArrayList<>());

                logger.info("Neuer Passagier erstellt mit Passnummer: '{}'",
                    passenger.getPassportNumber());
            }

            // DEBUGGING: Finaler Passagier
            logger.info("Passagier-Objekt zum Speichern: {}", passenger);
            logger.info("- Passagier.ID: {}", passenger.getId());
            logger.info("- Passagier.passportNumber: '{}'", passenger.getPassportNumber());
            logger.info("- Passagier.getBookings().size(): {}", passenger.getBookings().size());
            logger.info("==== PASSENGER DEBUG END ====");

            return passenger;

        } catch (Exception e) {
            logger.error("==== PASSENGER ERROR ====");
            logger.error("Exception beim Erstellen des Passagiers: {}", e.getMessage());
            showError("Fehler: " + e.getMessage());
            logger.error("Fehler im Detail: ", e);
            return null;
        }
    }

    /**
     * Initialisiert die Checkbox-Handler für die Flugfilter.
     */
    private void setupBookingFilters() {
        if (showFutureFlights == null || showPastFlights == null) return;

        // Standard: Nur zukünftige Flüge
        showFutureFlights.setSelected(true);
        showPastFlights.setSelected(false);

        // Listener für Filteränderungen
        showFutureFlights.selectedProperty().addListener((obs, old, val) -> applyBookingFilters());
        showPastFlights.selectedProperty().addListener((obs, old, val) -> applyBookingFilters());
        
        // Filter sofort anwenden, damit nur zukünftige Flüge zu Beginn sichtbar sind
        Platform.runLater(this::applyBookingFilters);
        
        logger.debug("Buchungsfilter initialisiert: Zukünftige Flüge={}, Vergangene Flüge={}", 
                    showFutureFlights.isSelected(), showPastFlights.isSelected());
    }

    /**
     * Wendet die gewählten Filter auf die Buchungsliste an.
     */
    private void applyBookingFilters() {
        if (bookingsListView == null || passenger == null) return;

        boolean showFuture = showFutureFlights.isSelected();
        boolean showPast = showPastFlights.isSelected();

        // Basis-Buchungen (von Passagier-Objekt)
        List<Booking> baseBookings = new ArrayList<>();
        if (passenger.getBookings() != null) {
            baseBookings.addAll(passenger.getBookings());
        }

        // Historische Buchungen laden falls benötigt
        if (showPast && passenger.getId() != null) {
            List<Booking> historicalBookings = passengerService.getHistoricalBookings(passenger.getId());
            for (Booking historical : historicalBookings) {
                if (baseBookings.stream().noneMatch(b ->
                    b.getFlight().getId().equals(historical.getFlight().getId()))) {
                    baseBookings.add(historical);
                }
            }
        }

        // Filter auf vollständige Liste anwenden
        List<Booking> filteredBookings = baseBookings.stream()
            .filter(booking -> {
                FlightStatus status = booking.getFlight().getStatus();
                boolean isPast = status == FlightStatus.COMPLETED;
                boolean isFuture = !isPast;

                return (showFuture && isFuture) || (showPast && isPast);
            })
            .sorted((b1, b2) -> {
                if (b1.getFlight().getDepartureDate() == null || b2.getFlight().getDepartureDate() == null) {
                    return 0;
                }
                return b2.getFlight().getDepartureDate().compareTo(b1.getFlight().getDepartureDate());
            })
            .toList();

        // ListView aktualisieren
        bookings.clear();
        bookings.addAll(filteredBookings);
    }

    /**
     * Fügt eine neue Buchung zum Passagier hinzu.
     * Diese Methode wird nur im Edit-Dialog verwendet.
     */
    @FXML
    private void handleAddBooking() {
        // Prüfen, ob Booking-Komponenten verfügbar sind
        if (bookingsListView == null) {
            logger.error("Buchungsliste nicht verfügbar, Buchungsfunktion wird übersprungen");
            return;
        }

        // Buchungsdialog erstellen
        Dialog<Booking> bookingDialog = new Dialog<>();
        bookingDialog.setTitle("Neue Buchung");
        bookingDialog.setHeaderText("Buchung hinzufügen");

        // Buttons definieren
        ButtonType addButtonType = new ButtonType("Hinzufügen", ButtonBar.ButtonData.OK_DONE);
        bookingDialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        // Grid für Formular erstellen
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 10, 10, 10));

        // Flugauswahl
        ComboBox<Flight> flightCombo = new ComboBox<>();
        List<Flight> scheduledFlights = flightService.getActiveFlights().stream()
                .filter(flight -> flight.getStatus() == FlightStatus.SCHEDULED)
                .collect(Collectors.toList());
        flightCombo.setItems(FXCollections.observableArrayList(scheduledFlights));

        flightCombo.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Flight item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatFlightDisplay(item));
            }
        });

        flightCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Flight item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatFlightDisplay(item));
            }
        });

        // Sitzklasse
        ComboBox<SeatClass> classCombo = new ComboBox<>();
        classCombo.getItems().addAll(SeatClass.values());
        classCombo.setValue(SeatClass.ECONOMY); // Default

        classCombo.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(SeatClass item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatSeatClass(item));
            }
        });

        classCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(SeatClass item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatSeatClass(item));
            }
        });

        // Warnungen
        Label warningLabel = new Label("");
        warningLabel.setStyle("-fx-text-fill: orange;");
        warningLabel.setWrapText(true);

        // Button-Status
        Button addButton = (Button) bookingDialog.getDialogPane().lookupButton(addButtonType);
        addButton.setDisable(true);

        // Validierung
        Runnable bookingValidator = () -> {
            warningLabel.setText("");
            addButton.setDisable(true);

            Flight selectedFlight = flightCombo.getValue();
            SeatClass selectedClass = classCombo.getValue();

            if (selectedFlight == null) {
                warningLabel.setText("Bitte einen Flug auswählen.");
                return;
            }

            if (selectedClass == null) {
                warningLabel.setText("Bitte eine Sitzklasse auswählen.");
                return;
            }

            // Prüfen, ob Flug bereits gebucht
            for (Booking booking : bookings) {
                if (booking.getFlight().getId().equals(selectedFlight.getId())) {
                    warningLabel.setText("Dieser Flug ist bereits in der Buchungsliste.");
                    return;
                }
            }

            // Prüfen, ob Flug bereits abgehoben
            if (selectedFlight.getStatus() != FlightStatus.SCHEDULED) {
                warningLabel.setText("Buchungen sind nur für Flüge im Status SCHEDULED möglich");
                return;
            }

            // Prüfen, ob noch genug Zeit bis Abflug
            if (selectedFlight.getDepartureDateTime() != null &&
                    selectedFlight.getDepartureDateTime().minusMinutes(30).isBefore(LocalDateTime.now())) {
                warningLabel.setText("Buchung nur bis 30 Min. vor Abflug möglich.");
                return;
            }

            // Prüfen, ob Plätze verfügbar
            if (!passengerService.hasFlightAvailableSeats(selectedFlight, selectedClass)) {
                warningLabel.setText("Keine freien Plätze in dieser Klasse verfügbar.");
                return;
            }

            // Alle Checks bestanden
            addButton.setDisable(false);
        };

        // Listener für Validierung
        flightCombo.valueProperty().addListener((obs, oldVal, newVal) -> bookingValidator.run());
        classCombo.valueProperty().addListener((obs, oldVal, newVal) -> bookingValidator.run());

        // Grid aufbauen
        grid.add(new Label("Flug:"), 0, 0);
        grid.add(flightCombo, 1, 0);
        grid.add(new Label("Klasse:"), 0, 1);
        grid.add(classCombo, 1, 1);
        grid.add(warningLabel, 0, 2, 2, 1);

        bookingDialog.getDialogPane().setContent(grid);
        flightCombo.requestFocus();
        bookingValidator.run();

        // ResultConverter
        bookingDialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                Flight selectedFlight = flightCombo.getValue();
                SeatClass selectedClass = classCombo.getValue();

                if (selectedFlight != null && selectedClass != null) {
                    // Temporärer Booking-Objekt für die UI-Liste erstellen (ohne Datenbank-ID)
                    // Hier verwenden wir this.passenger anstelle von null
                    return new Booking(this.passenger, selectedFlight, selectedClass);
                }
            }
            return null;
        });

        // Dialog anzeigen
        bookingDialog.showAndWait().ifPresent(booking -> {
            bookings.add(booking);
            validateForm();
        });
    }

    /**
     * Entfernt die ausgewählte Buchung aus der Liste.
     * Diese Methode wird nur im Edit-Dialog verwendet.
     */
    @FXML
    private void handleRemoveBooking() {
        // Prüfen, ob Booking-Komponenten verfügbar sind
        if (bookingsListView == null) {
            logger.error("Buchungsliste nicht verfügbar, Löschfunktion wird übersprungen");
            return;
        }

        Booking selectedBooking = bookingsListView.getSelectionModel().getSelectedItem();
        if (selectedBooking != null) {
            bookings.remove(selectedBooking);
            validateForm();
        }
    }

    /**
     * Formatiert die Anzeige einer Gender-Enum.
     */
    private String formatGender(Gender gender) {
        if (gender == null) return "";
        return switch (gender) {
            case MALE -> "Männlich";
            case FEMALE -> "Weiblich";
            case OTHER -> "Divers";
        };
    }

    /**
     * Formatiert die Anzeige einer SeatClass-Enum.
     */
    private String formatSeatClass(SeatClass seatClass) {
        if (seatClass == null) return "";
        return switch (seatClass) {
            case ECONOMY -> "Economy";
            case BUSINESS -> "Business";
            case FIRST_CLASS -> "First Class";
        };
    }

    /**
     * Formatiert die Anzeige eines Fluges mit vollständigen Routen- und Zeitinformationen.
     * Format: "FLUGHAFEN → ZIELFLUGHAFEN (FLUGNR, KLASSE) - DATUM, ZEIT"
     */
    private String formatFlightDisplay(Flight flight) {
        if (flight == null) return "Kein Flug";

        StringBuilder sb = new StringBuilder();
        
        // Flughafen-Route hinzufügen (von → nach)
        if (flight.getDepartureAirport() != null && flight.getArrivalAirport() != null) {
            String depCity = flight.getDepartureAirport().getCity();
            String arrCity = flight.getArrivalAirport().getCity();
            
            // Stadtname oder ICAO-Code verwenden
            String depName = depCity != null && !depCity.isEmpty() ? depCity : flight.getDepartureAirport().getIcaoCode();
            String arrName = arrCity != null && !arrCity.isEmpty() ? arrCity : flight.getArrivalAirport().getIcaoCode();
            
            sb.append(depName).append(" → ").append(arrName).append(" ");
        }
        
        // Flugnummer in Klammern
        sb.append("(").append(flight.getFlightNumber()).append(")");

        // Datum und Zeit hinzufügen
        if (flight.getDepartureDate() != null) {
            sb.append(" - ").append(flight.getDepartureDate().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));

            if (flight.getDepartureTime() != null) {
                sb.append(", ").append(flight.getDepartureTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
            }
        }

        return sb.toString();
    }

    /**
     * Formatiert die Anzeige einer Buchung mit Routen-, Klassen- und Zeitinformationen.
     * Format: "FLUGHAFEN → ZIELFLUGHAFEN (FLUGNR, KLASSE) - DATUM, ZEIT"
     */
    private String formatBookingDisplay(Booking booking) {
        if (booking == null || booking.getFlight() == null) return "Ungültige Buchung";

        Flight flight = booking.getFlight();
        SeatClass seatClass = booking.getSeatClass();

        StringBuilder sb = new StringBuilder();
        
        // Flughafen-Route hinzufügen (von → nach)
        if (flight.getDepartureAirport() != null && flight.getArrivalAirport() != null) {
            String depCity = flight.getDepartureAirport().getCity();
            String arrCity = flight.getArrivalAirport().getCity();
            
            // Stadtname oder ICAO-Code verwenden
            String depName = depCity != null && !depCity.isEmpty() ? depCity : flight.getDepartureAirport().getIcaoCode();
            String arrName = arrCity != null && !arrCity.isEmpty() ? arrCity : flight.getArrivalAirport().getIcaoCode();
            
            sb.append(depName).append(" → ").append(arrName).append(" ");
        }
        
        // Flugnummer und Klasse in Klammern
        sb.append("(").append(flight.getFlightNumber())
          .append(", ").append(getSeatClassShortName(seatClass))
          .append(")");

        // Datum und Zeit hinzufügen
        if (flight.getDepartureDate() != null) {
            sb.append(" - ").append(flight.getDepartureDate().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));

            if (flight.getDepartureTime() != null) {
                sb.append(", ").append(flight.getDepartureTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
            }
        }
        
        // Status hinzufügen, falls nicht SCHEDULED
        if (flight.getStatus() != null && flight.getStatus() != FlightStatus.SCHEDULED) {
            sb.append(" - Status: ").append(flight.getStatus());
        }

        return sb.toString();
    }

    /**
     * Gibt einen Kurzcode für die Sitzklasse zurück.
     */
    private String getSeatClassShortName(SeatClass seatClass) {
        if (seatClass == null) return "?";

        return switch (seatClass) {
            case ECONOMY -> "E";
            case BUSINESS -> "B";
            case FIRST_CLASS -> "FC";
        };
    }
}
