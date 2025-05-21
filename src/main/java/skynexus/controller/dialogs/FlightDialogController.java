package skynexus.controller.dialogs;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.util.Pair;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.enums.AircraftStatus;
import skynexus.enums.FlightStatus;
import skynexus.model.*;
import skynexus.service.AircraftService;
import skynexus.service.AirportService;
import skynexus.service.FlightService;
import skynexus.service.RouteService;
import skynexus.util.Config;
import skynexus.util.ExceptionHandler;
import skynexus.util.FlightUtils;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller für den Flug-Dialog.
 * Verwaltet die Benutzeroberfläche und Geschäftslogik zur Erstellung und Bearbeitung von Flügen.
 */
public class FlightDialogController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(FlightDialogController.class);

    // Services
    private FlightService flightService;
    private RouteService routeService;
    private AirportService airportService;
    private AircraftService aircraftService;

    // UI-Elemente
    @FXML
    private TextField flightNumberField;
    @FXML
    private Label flightNumberErrorLabel;

    @FXML
    private ComboBox<Airport> departureAirportComboBox;
    @FXML
    private ComboBox<Airport> arrivalAirportComboBox;
    @FXML
    private Label routeInfoLabel;
    @FXML
    private Label routeDistanceLabel;
    @FXML
    private Label routeTimeLabel;

    @FXML
    private ComboBox<Aircraft> aircraftComboBox;
    @FXML
    private Label aircraftErrorLabel;
    @FXML
    private Label aircraftPaxCapacityLabel;
    @FXML
    private Label aircraftCargoCapacityLabel;
    @FXML
    private Label aircraftRangeLabel;
    @FXML
    private Label aircraftSpeedLabel;
    @FXML
    private Label aircraftCostPerHourLabel;

    @FXML
    private DatePicker departureDatePicker;
    @FXML
    private Spinner<Integer> departureHourSpinner;
    @FXML
    private Spinner<Integer> departureMinuteSpinner;
    @FXML
    private Label arrivalDateTimeLabel;

    @FXML
    private CheckBox createReturnFlightCheck;
    @FXML
    private Spinner<Integer> turnaroundHoursSpinner;
    @FXML
    private Spinner<Integer> turnaroundMinutesSpinner;
    @FXML
    private Label returnFlightInfoLabel;

    @FXML
    private TextField priceEconomyField;
    @FXML
    private TextField priceBusinessField;
    @FXML
    private TextField priceFirstField;

    // Validierungsstatus
    private final BooleanProperty isFlightNumberValid = new SimpleBooleanProperty(false);
    private final BooleanProperty isAircraftValid = new SimpleBooleanProperty(false);
    private final BooleanProperty isRouteValid = new SimpleBooleanProperty(false);
    private final BooleanProperty isDepartureDateTimeValid = new SimpleBooleanProperty(false);

    private Flight currentFlight;
    private SaveCallback saveCallback;

    /**
     * Funktionales Interface für Callback nach erfolgreichem Speichern
     */
    @FunctionalInterface
    public interface SaveCallback {
        void onSaved(Flight flight);
    }

    /**
     * Initialisiert den Controller mit FXML.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupSpinners();
        departureDatePicker.setValue(LocalDate.now());
    }

    /**
     * Initialisiert den Controller mit den notwendigen Services
     * und dem zu bearbeitenden Flug.
     *
     * @param flightService   Service für Flugoperationen
     * @param routeService    Service für Routenoperationen
     * @param airportService  Service für Flughafenoperationen
     * @param aircraftService Service für Flugzeugoperationen
     * @param flight          Zu bearbeitender Flug (null für neuen Flug)
     * @param dialog          Der Dialog, in dem der Controller verwendet wird
     * @param callback        Callback nach erfolgreichem Speichern
     */
    public void initializeServices(FlightService flightService, RouteService routeService,
                                   AirportService airportService, AircraftService aircraftService,
                                   Flight flight, Dialog<Flight> dialog, SaveCallback callback) {
        this.flightService = Objects.requireNonNull(flightService, "FlightService darf nicht null sein");
        this.routeService = Objects.requireNonNull(routeService, "RouteService darf nicht null sein");
        this.airportService = Objects.requireNonNull(airportService, "AirportService darf nicht null sein");
        this.aircraftService = Objects.requireNonNull(aircraftService, "AircraftService darf nicht null sein");
        this.currentFlight = flight;
        // Dialog und aktueller Flug
        this.saveCallback = callback;

        setupComboBoxConverters();
        loadAirports();
        setupEventHandlers();
        setupValidation();

        // Daten vorausfüllen, falls ein Flug bearbeitet wird
        if (flight != null) {
            populateFields(flight);
        } else {
            // Bei neuem Flug automatisch Flugnummer vorschlagen
            generateNextFlightNumber();

            // Standard Turnaround-Zeit einrichten
            turnaroundHoursSpinner.getValueFactory().setValue(Config.getDefaultTurnaroundHours());
            turnaroundMinutesSpinner.getValueFactory().setValue(Config.getDefaultTurnaroundMinutes());
        }

        // Dialog für Löschfunktion konfigurieren (nur bei Bearbeitung und SCHEDULED-Status)
        Button deleteButton = null;
        if (flight != null && flight.getStatus() == FlightStatus.SCHEDULED) {
            // Füge DELETE-Button hinzu
            ButtonType deleteButtonType = new ButtonType("Löschen", ButtonBar.ButtonData.LEFT);
            dialog.getDialogPane().getButtonTypes().add(0, deleteButtonType);
            
            // Referenz auf den Button holen und Stil setzen
            deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteButtonType);
            deleteButton.getStyleClass().add("btn-danger");
            
            // Löschen-Handler setzen
            deleteButton.setOnAction(e -> {
                e.consume(); // Verhindere standard Dialog-Verhalten
                boolean confirmed = ExceptionHandler.showConfirmDialog(
                    "Flug löschen", 
                    "Möchten Sie den Flug " + flight.getFlightNumber() + " wirklich löschen?\n\nDiese Aktion kann nicht rückgängig gemacht werden."
                );
                
                if (confirmed) {
                    try {
                        boolean success = flightService.deleteFlight(flight.getId());
                        if (success) {
                            logger.info("Flug {} erfolgreich gelöscht", flight.getFlightNumber());
                            
                            // Callback informieren und Dialog schließen
                            if (saveCallback != null) {
                                saveCallback.onSaved(flight); // Callback über Löschung informieren
                            }
                            dialog.close();
                        } else {
                            ExceptionHandler.showErrorDialog("Fehler", "Der Flug konnte nicht gelöscht werden");
                        }
                    } catch (Exception ex) {
                        logger.error("Fehler beim Löschen des Flugs", ex);
                        ExceptionHandler.handleException(ex, "beim Löschen des Flugs");
                    }
                }
            });
        }

        // OK-Button nur aktivieren, wenn Validierung erfolgreich
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.disableProperty().bind(
                isFlightNumberValid.not()
                        .or(isAircraftValid.not())
                        .or(isRouteValid.not())
                        .or(isDepartureDateTimeValid.not())
        );

        // ResultConverter einrichten
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return saveFlight();
            }
            return null;
        });
    }

    /**
     * Initialisiert und konfiguriert alle Spinner im Dialog
     */
    private void setupSpinners() {
        // Stunden-Spinner (0-23)
        SpinnerValueFactory<Integer> hourFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, LocalTime.now().getHour());
        departureHourSpinner.setValueFactory(hourFactory);

        // Minuten-Spinner (0-59)
        SpinnerValueFactory<Integer> minuteFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, LocalTime.now().getMinute());
        departureMinuteSpinner.setValueFactory(minuteFactory);

        // Turnaround-Zeit Spinner
        int minTurnaroundHours = Config.getMinTurnaroundMinutes() / 60;
        int maxTurnaroundHours = Config.getMaxTurnaroundHours();

        SpinnerValueFactory<Integer> turnaroundHoursFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(minTurnaroundHours, maxTurnaroundHours,
                        Config.getDefaultTurnaroundHours());
        turnaroundHoursSpinner.setValueFactory(turnaroundHoursFactory);

        SpinnerValueFactory<Integer> turnaroundMinutesFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59,
                        Config.getDefaultTurnaroundMinutes());
        turnaroundMinutesSpinner.setValueFactory(turnaroundMinutesFactory);
    }

    /**
     * Richtet die ComboBox-Converter für formatierte Anzeige ein
     */
    private void setupComboBoxConverters() {
        // Airport-Converter: ICAO - Stadt, Land
        StringConverter<Airport> airportConverter = new StringConverter<>() {
            @Override
            public String toString(Airport airport) {
                if (airport == null) return null;
                return String.format("%s - %s, %s", airport.getIcaoCode(), airport.getCity(), airport.getCountry());
            }

            @Override
            public Airport fromString(String string) {
                return null;
            }
        };

        departureAirportComboBox.setConverter(airportConverter);
        arrivalAirportComboBox.setConverter(airportConverter);

        // Aircraft-Converter: Registration - Type
        StringConverter<Aircraft> aircraftConverter = new StringConverter<>() {
            @Override
            public String toString(Aircraft aircraft) {
                if (aircraft == null) return null;
                return String.format("%s - %s", aircraft.getRegistrationNo(), aircraft.getType().getFullName());
            }

            @Override
            public Aircraft fromString(String string) {
                return null;
            }
        };

        aircraftComboBox.setConverter(aircraftConverter);
    }

    /**
     * Lädt alle Flughäfen für die ComboBoxen
     */
    private void loadAirports() {
        try {
            List<Airport> airports = airportService.getAllAirports();
            airports.sort(Comparator.comparing(Airport::getIcaoCode));
            ObservableList<Airport> observableAirports = FXCollections.observableArrayList(airports);
            departureAirportComboBox.setItems(observableAirports);
            arrivalAirportComboBox.setItems(observableAirports);
        } catch (Exception e) {
            logger.error("Fehler beim Laden der Flughäfen für ComboBoxen.", e);
                    ExceptionHandler.showErrorDialog("Fehler", "Flughäfen konnten nicht geladen werden.", null, e);
        }
    }

    /**
     * Richtet Event-Handler für UI-Elemente ein
     */
    private void setupEventHandlers() {
        // Flugnummer validieren bei Änderung
        flightNumberField.textProperty().addListener((obs, oldVal, newVal) -> {
            validateFlightNumber(newVal);
            updateReturnFlightInfo();
        });

        // Flughafenauswahl aktualisiert verfügbare Flugzeuge
        departureAirportComboBox.valueProperty().addListener((obs, oldVal, newVal) -> handleAirportChange());
        arrivalAirportComboBox.valueProperty().addListener((obs, oldVal, newVal) -> handleAirportChange());

        // Flugzeugauswahl validieren
        aircraftComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            validateAircraftSelection();
            updateAircraftDetails(newVal);
            checkExistingRoute();
            updatePrices();
            updateArrivalTime();
            updateReturnFlightInfo();
        });

        // Zeit- und Datumsänderungen
        departureDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateArrivalTime();
            updateReturnFlightInfo();
        });
        departureHourSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateArrivalTime();
            updateReturnFlightInfo();
        });
        departureMinuteSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateArrivalTime();
            updateReturnFlightInfo();
        });

        // Turnaround-Zeit Änderungen
        turnaroundHoursSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            validateTurnaroundTime();
            updateReturnFlightInfo();
        });
        turnaroundMinutesSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            validateTurnaroundTime();
            updateReturnFlightInfo();
        });

        // Rückflug-Option
        createReturnFlightCheck.selectedProperty().addListener((obs, oldVal, newVal) -> updateReturnFlightInfo());
    }

    /**
     * Richtet Validierungslogik für den Dialog ein
     */
    private void setupValidation() {
        isFlightNumberValid.set(false);
        isAircraftValid.set(false);
        isRouteValid.set(false);
        isDepartureDateTimeValid.set(false);
    }

    /**
     * Verarbeitet Änderungen an der Flughafenauswahl
     */
    private void handleAirportChange() {
        Airport departure = departureAirportComboBox.getValue();
        Airport arrival = arrivalAirportComboBox.getValue();

        // Check if airports are the same
        if (departure != null && departure.equals(arrival)) {
            invalidateRouteSelection("Abflug- und Zielflughafen müssen unterschiedlich sein!");
        } else {
            // Airports are different or one is null, proceed with updates
            updateAvailableAircraft(); // Filter aircraft based on departure and range
            checkExistingRoute();      // Check route validity and update info
            updatePrices();            // Update prices based on route/aircraft
            updateArrivalTime();       // Update arrival time
            updateReturnFlightInfo();  // Update return flight info
        }
    }

    /**
     * Ungültige Routenauswahl behandeln und UI aktualisieren
     */
    private void invalidateRouteSelection(String message) {
        isRouteValid.set(false);
        routeInfoLabel.setText(message);
        routeInfoLabel.setTextFill(Color.RED);
        routeInfoLabel.setVisible(true);
        routeDistanceLabel.setText("Distanz: -");
                routeTimeLabel.setText("Flugzeit: -");
                        arrivalDateTimeLabel.setText("Route ungültig");
                                isDepartureDateTimeValid.set(false);
        priceEconomyField.setText("");
                priceBusinessField.setText("");
                        priceFirstField.setText("");
                                updateReturnFlightInfo();
    }

    /**
     * Füllt alle Felder mit Daten aus einem existierenden Flug
     *
     * @param flight Der zu bearbeitende Flug
     */
    private void populateFields(Flight flight) {
        if (flight == null || flight.getDepartureAirport() == null || flight.getArrivalAirport() == null) {
            logger.error("Versuch, Felder mit ungültigem Flugobjekt zu füllen: {}", flight);
            return;
        }

        flightNumberField.setText(flight.getFlightNumber());
        flightNumberField.setDisable(true); // Flugnummer im Bearbeitungsmodus nicht änderbar

        // Flughäfen auswählen
        final Long depAirportId = flight.getDepartureAirport().getId();
        departureAirportComboBox.getItems().stream()
                .filter(a -> a.getId().equals(depAirportId))
                .findFirst()
                .ifPresent(departureAirportComboBox::setValue);
        departureAirportComboBox.setDisable(true); // Abflughafen nicht änderbar

        final Long arrAirportId = flight.getArrivalAirport().getId();
        arrivalAirportComboBox.getItems().stream()
                .filter(a -> a.getId().equals(arrAirportId))
                .findFirst()
                .ifPresent(arrivalAirportComboBox::setValue);
        arrivalAirportComboBox.setDisable(true); // Zielflughafen nicht änderbar

        // Flugzeug auswählen
        if (flight.getAircraft() != null) {
            try {
                List<Aircraft> allAircraft = aircraftService.getAllAircraft();
                final Long aircraftId = flight.getAircraft().getId();
                Optional<Aircraft> aircraftToSelect = allAircraft.stream()
                        .filter(a -> a.getId().equals(aircraftId))
                        .findFirst();

                if (aircraftToSelect.isPresent()) {
                    updateAvailableAircraft();
                    aircraftComboBox.setValue(aircraftToSelect.get());
                    updateAircraftDetails(aircraftToSelect.get());
                } else {
                    logger.warn("Flugzeug mit ID {} für Flug {} nicht im Service gefunden.", aircraftId, flight.getFlightNumber());
                            updateAvailableAircraft();
                }
            } catch (Exception e) {
                logger.error("Fehler beim Laden der Flugzeugliste während populateFields.", e);
                        ExceptionHandler.handleException(e, "beim Laden der Flugzeuge für die Bearbeitung");
                                updateAvailableAircraft();
            }
        } else {
            updateAvailableAircraft();
        }

        // Datum und Zeit setzen
        departureDatePicker.setValue(flight.getDepartureDate());
        departureHourSpinner.getValueFactory().setValue(flight.getDepartureTime().getHour());
        departureMinuteSpinner.getValueFactory().setValue(flight.getDepartureTime().getMinute());

        // Preise setzen
        priceEconomyField.setText(String.format(Locale.US, "%.2f", flight.getPriceEconomy()));
                priceBusinessField.setText(String.format(Locale.US, "%.2f", flight.getPriceBusiness()));
                        priceFirstField.setText(String.format(Locale.US, "%.2f", flight.getPriceFirst()));

                                // Rückflug-Option deaktivieren
                                createReturnFlightCheck.setSelected(false);
        createReturnFlightCheck.setDisable(true);
        returnFlightInfoLabel.setVisible(false);

        // Initial validation after populating
        validateFlightNumber(flight.getFlightNumber());
        validateAircraftSelection();
        checkExistingRoute();
        updateArrivalTime();
    }

    /**
     * Generiert automatisch die nächste verfügbare Flugnummer
     */
    private void generateNextFlightNumber() {
        try {
            String airlineCode = Airline.getInstance().getIcaoCode();

            List<Flight> existingFlights = flightService.getAllFlights();

            Set<Integer> usedNumbers = new HashSet<>();
            for (Flight flight : existingFlights) {
                String flightNumber = flight.getFlightNumber();
                if (flightNumber.startsWith(airlineCode)) {
                    try {
                        String numericPart = flightNumber.substring(airlineCode.length());
                        int number = Integer.parseInt(numericPart);
                        usedNumbers.add(number);
                    } catch (NumberFormatException | IndexOutOfBoundsException e) {
                        logger.debug("Ignoriere ungültige Flugnummer beim Generieren: {}", flightNumber);
                    }
                }
            }

            int nextNumber = 100;
            while (usedNumbers.contains(nextNumber) || usedNumbers.contains(nextNumber + 1)) {
                nextNumber += 10;
                if (nextNumber > 9999) {
                    logger.error("Konnte keine freie Flugnummer finden.");
                            flightNumberErrorLabel.setText("Keine freie Flugnummer gefunden!");
                                    flightNumberErrorLabel.setVisible(true);
                    isFlightNumberValid.set(false);
                    return;
                }
            }

            String newFlightNumber = airlineCode + nextNumber;
            flightNumberField.setText(newFlightNumber);

        } catch (Exception e) {
            logger.error("Fehler beim Generieren der nächsten Flugnummer.", e);
                    ExceptionHandler.handleException(e, "beim Generieren der Flugnummer");
                            flightNumberErrorLabel.setText("Fehler bei Flugnummer-Generierung!");
                                    flightNumberErrorLabel.setVisible(true);
            isFlightNumberValid.set(false);
        }
    }

    /**
     * Validiert die eingegebene Flugnummer
     *
     * @param flightNumber Die zu validierende Flugnummer
     */
    private void validateFlightNumber(String flightNumber) {
        flightNumberErrorLabel.setVisible(false);
        isFlightNumberValid.set(false);

        if (flightNumber == null || flightNumber.trim().isEmpty()) {
            flightNumberErrorLabel.setText("Flugnummer darf nicht leer sein!");
            flightNumberErrorLabel.setVisible(true);
            return;
        }

        String airlineCode = Airline.getInstance().getIcaoCode();

        if (!flightNumber.startsWith(airlineCode)) {
            flightNumberErrorLabel.setText("Flugnummer muss mit '" + airlineCode + "' beginnen!");
            flightNumberErrorLabel.setVisible(true);
            return;
        }
        String numericPart = flightNumber.substring(airlineCode.length());
        if (!numericPart.matches("\\d{3,4}")) {
            flightNumberErrorLabel.setText("Flugnummer muss 3-4 Ziffern nach '" + airlineCode + "' enthalten!");
            flightNumberErrorLabel.setVisible(true);
            return;
        }

        try {
            List<Flight> existingFlights = flightService.getAllFlights();
            for (Flight existingFlight : existingFlights) {
                if (currentFlight != null && existingFlight != null && existingFlight.getId() != null
                        && currentFlight.getId() != null && existingFlight.getId().equals(currentFlight.getId())) {
                    continue; // Skip check if it's the same flight being edited
                }
                if (existingFlight != null && flightNumber.equals(existingFlight.getFlightNumber())) {
                    flightNumberErrorLabel.setText("Flugnummer wird bereits verwendet!");
                    flightNumberErrorLabel.setVisible(true);
                    return; // Number is taken
                }
            }
        } catch (Exception e) {
            logger.error("Fehler beim Prüfen der Flugnummer-Eindeutigkeit.", e);
            ExceptionHandler.handleException(e, "beim Prüfen der Flugnummer");
            flightNumberErrorLabel.setText("Fehler bei Eindeutigkeitsprüfung!");
            flightNumberErrorLabel.setVisible(true);
            return; // Error during check
        }
        isFlightNumberValid.set(true); // All checks passed
    }

    /**
     * Aktualisiert die Liste der verfügbaren Flugzeuge basierend auf Abflughafen, Route und Zeitraum
     */
    private void updateAvailableAircraft() {
        Airport departureAirport = departureAirportComboBox.getValue();
        Airport arrivalAirport = arrivalAirportComboBox.getValue();
        Aircraft previouslySelectedAircraft = aircraftComboBox.getValue();

        aircraftComboBox.setItems(FXCollections.observableArrayList());

        if (departureAirport == null) {
            validateAircraftSelection();
            updateAircraftDetails(null);
            return;
        }

        List<Aircraft> availableAircraft = new ArrayList<>();
        try {
            List<Aircraft> allAircraft = aircraftService.getAllAircraft();

            // 1. Grundlegende Filterung: Status und Standort
            availableAircraft = allAircraft.stream()
                    .filter(a -> a.getStatus() == AircraftStatus.AVAILABLE)
                    .filter(a -> a.getCurrentLocation().getId().equals(departureAirport.getId()))
                    .collect(Collectors.toList());

            // 2. Filterung nach Reichweite, falls Zielflughafen bekannt
            if (arrivalAirport != null) {
                double distance = calculateRouteDistance(departureAirport, arrivalAirport);
                if (distance > 0) {
                    availableAircraft = availableAircraft.stream()
                            .filter(a -> a.getType().getMaxRangeKm() >= distance * Config.getRouteDistanceBuffer())
                            .collect(Collectors.toList());
                }
            }

            // 3. Filterung nach zeitlicher Verfügbarkeit
            if (departureDatePicker.getValue() != null && departureHourSpinner.getValue() != null && departureMinuteSpinner.getValue() != null && arrivalAirport != null) {

                LocalDateTime departureDateTime = LocalDateTime.of(
                        departureDatePicker.getValue(),
                        LocalTime.of(departureHourSpinner.getValue(), departureMinuteSpinner.getValue())
                );

                // Zunächst eine Route finden/berechnen
                Route route = routeService.findRouteByAirportsAndAirline(
                        departureAirport, arrivalAirport, Airline.getInstance());

                if (route == null) {
                    // Temporäre Route für Berechnungen erstellen
                    route = new Route();
                    route.setDepartureAirport(departureAirport);
                    route.setArrivalAirport(arrivalAirport);

                    // Distanz berechnen
                    double distance = calculateRouteDistance(departureAirport, arrivalAirport);
                    route.setDistanceKm(distance);

                    // Flugzeit mit Standardgeschwindigkeit berechnen (wird später präzisiert)
                    route.setFlightTimeMinutes(calculateFlightTime(distance, Config.getDefaultAircraftSpeed()));
                }

                // Blockzeit berechnen
                Pair<LocalDateTime, LocalDateTime> blockTime = flightService.calculateBlockTimeForRoute(
                        departureDateTime, route);

                if (blockTime != null) {
                    LocalDateTime blockStartTime = blockTime.getKey();
                    LocalDateTime blockEndTime = blockTime.getValue();

                    logger.debug("Prüfe Flugzeugverfügbarkeit für Zeitraum {} bis {}",
                             blockStartTime, blockEndTime);

                    // Bei Bearbeitung eines bestehenden Flugs den aktuellen Flug ausschließen
                    Long excludeFlightId = (currentFlight != null) ? currentFlight.getId() : null;

                    // Zeitliche Verfügbarkeit prüfen
                    final List<Aircraft> timeFilteredAircraft = new ArrayList<>();
                    for (Aircraft aircraft : availableAircraft) {
                        if (flightService.isAircraftAvailableForTimeRange(aircraft, blockStartTime, blockEndTime, excludeFlightId)) {
                            timeFilteredAircraft.add(aircraft);
                        }
                    }

                    // Ergebnisse anzeigen
                    int filteredOut = availableAircraft.size() - timeFilteredAircraft.size();
                    if (filteredOut > 0) {
                        logger.debug("{} Flugzeuge aufgrund zeitlicher Überschneidungen ausgefiltert", filteredOut);
                    }

                    availableAircraft = timeFilteredAircraft;
                } else {
                    logger.warn("Konnte Blockzeit für Route nicht berechnen - Zeitfilter wird ignoriert");
                }
            }

            // 4. Hinzufügen des aktuellen Flugzeugs bei Bearbeitung eines Fluges
            if (currentFlight != null && currentFlight.getAircraft() != null) {
                final long currentFlightAircraftId = currentFlight.getAircraft().getId();
                boolean alreadyInList = availableAircraft.stream().anyMatch(a -> a.getId().equals(currentFlightAircraftId));
                if (!alreadyInList) {
                    allAircraft.stream()
                            .filter(a -> a.getId().equals(currentFlightAircraftId))
                            .findFirst()
                            .ifPresent(availableAircraft::add);
                }
            }

            availableAircraft.sort(Comparator.comparing(Aircraft::getRegistrationNo));

            // 5. Feedback in der UI
            if (availableAircraft.isEmpty()) {
                if (departureDatePicker.getValue() != null &&
                    departureHourSpinner.getValue() != null &&
                    departureMinuteSpinner.getValue() != null) {
                    // Falls Zeitfilterung aktiv war
                    aircraftErrorLabel.setText("Keine verfügbaren Flugzeuge für den gewählten Zeitraum gefunden!");
                } else {
                    aircraftErrorLabel.setText("Keine Flugzeuge am Abflughafen " + departureAirport.getIcaoCode() + " verfügbar!");
                }
                aircraftErrorLabel.setVisible(true);
            } else {
                aircraftErrorLabel.setVisible(false);
            }

        } catch (Exception e) {
            logger.error("Fehler beim Aktualisieren der Flugzeugliste.", e);
            ExceptionHandler.handleException(e, "beim Aktualisieren der Flugzeugliste");
        }

        aircraftComboBox.setItems(FXCollections.observableArrayList(availableAircraft));
        if (previouslySelectedAircraft != null && availableAircraft.contains(previouslySelectedAircraft)) {
            aircraftComboBox.setValue(previouslySelectedAircraft);
        } else if (currentFlight != null && currentFlight.getAircraft() != null) {
            final long currentFlightAircraftId = currentFlight.getAircraft().getId();
            availableAircraft.stream()
                    .filter(a -> a.getId().equals(currentFlightAircraftId))
                    .findFirst()
                    .ifPresent(aircraftComboBox::setValue);
        } else if (!availableAircraft.contains(aircraftComboBox.getValue())) {
            aircraftComboBox.setValue(null);
        }

        validateAircraftSelection();
        updateAircraftDetails(aircraftComboBox.getValue());
    }

    /**
     * Aktualisiert die Anzeige der Flugzeugdetails basierend auf dem ausgewählten Flugzeug
     *
     * @param aircraft Das ausgewählte Flugzeug
     */
    private void updateAircraftDetails(Aircraft aircraft) {
        if (aircraft == null || aircraft.getType() == null) {
            aircraftPaxCapacityLabel.setText(" -");
                    aircraftCargoCapacityLabel.setText(" -");
                            aircraftRangeLabel.setText(" -");
                                    aircraftSpeedLabel.setText(" -");
                                            aircraftCostPerHourLabel.setText(" -");
        } else {
            AircraftType type = aircraft.getType();
            aircraftPaxCapacityLabel.setText(String.format(" %d Personen", type.getPaxCapacity()));
                    aircraftCargoCapacityLabel.setText(String.format(" %.1f Tonnen", type.getCargoCapacity() / 1000));
                            aircraftRangeLabel.setText(String.format(" %.0f km", type.getMaxRangeKm()));
                                    aircraftSpeedLabel.setText(String.format(" %.0f km/h", type.getSpeedKmh()));
                                            aircraftCostPerHourLabel.setText(String.format(" %.2f €", type.getCostPerHour()));
        }
    }

    /**
     * Aktualisiert die Informationen zum automatisch erstellten Rückflug
     */
    private void updateReturnFlightInfo() {
        returnFlightInfoLabel.setVisible(false);

        if (!createReturnFlightCheck.isSelected() || createReturnFlightCheck.isDisabled() ||
                !isFlightNumberValid.get() || !isRouteValid.get() || !isDepartureDateTimeValid.get()) {
            return;
        }

        Airport departure = departureAirportComboBox.getValue();
        Airport arrival = arrivalAirportComboBox.getValue();
        String flightNumber = flightNumberField.getText();
        String airlineCode = Airline.getInstance().getIcaoCode();

        try {
            String numericPart = flightNumber.substring(airlineCode.length());
            int number = Integer.parseInt(numericPart);
            String returnFlightNumber = airlineCode + (number + 1);

            boolean returnNumberTaken;
            try {
                returnNumberTaken = flightService.getAllFlights().stream()
                        .anyMatch(f -> returnFlightNumber.equals(f.getFlightNumber()));
            } catch (Exception e) {
                logger.error("Fehler beim Prüfen der Rückflugnummer-Verfügbarkeit.", e);
                returnFlightInfoLabel.setText("Fehler bei Prüfung der Rückflugnummer!");
                returnFlightInfoLabel.setTextFill(Color.RED);
                returnFlightInfoLabel.setVisible(true);
                return;
            }

            if (returnNumberTaken) {
                returnFlightInfoLabel.setText(String.format(
                        "Rückflugnummer %s ist bereits vergeben.", returnFlightNumber));
                returnFlightInfoLabel.setTextFill(Color.ORANGERED);
            } else {
                String formattedText = String.format(
                        "Rückflug %s von %s nach %s wird erstellt.",
                        returnFlightNumber, arrival.getIcaoCode(), departure.getIcaoCode());

                returnFlightInfoLabel.setText(formattedText);
                returnFlightInfoLabel.setTextFill(Color.GREEN);
            }
            returnFlightInfoLabel.setVisible(true);

        } catch (NumberFormatException | IndexOutOfBoundsException | NullPointerException e) {
            logger.error("Fehler beim Verarbeiten der Flugnummer '{}' für Rückfluginfo.", flightNumber, e);
            returnFlightInfoLabel.setText("Fehler bei Verarbeitung der Flugnummer.");
            returnFlightInfoLabel.setTextFill(Color.RED);
            returnFlightInfoLabel.setVisible(true);
        }
    }

    /**
     * Aktualisiert die Preisfelder basierend auf Route, Flugzeug usw.
     */
    private void updatePrices() {
        priceEconomyField.setText("");
        priceBusinessField.setText("");
        priceFirstField.setText("");

        if (!isRouteValid.get() || !isAircraftValid.get()) {
            return;
        }

        Flight tempFlight = collectInputDataForPricing();

        Airport departure = tempFlight.getDepartureAirport();
        Airport arrival = tempFlight.getArrivalAirport();
        Aircraft aircraft = tempFlight.getAircraft();

        double distance;
        int flightTimeMinutes;

        try {
            Route existingRoute = routeService.findRouteByAirportsAndAirline(departure, arrival, Airline.getInstance());
            if (existingRoute != null) {
                distance = existingRoute.getDistanceKm();
                flightTimeMinutes = existingRoute.getFlightTimeMinutes();
            } else {
                distance = calculateRouteDistance(departure, arrival);
                flightTimeMinutes = calculateFlightTime(distance, aircraft.getType().getSpeedKmh());
            }
        } catch (Exception e) {
            logger.error("Fehler beim Ermitteln der Routendaten für Preisberechnung.", e);
            return;
        }

        if (distance < 0 || flightTimeMinutes < 0) {
            logger.warn("Ungültige Distanz ({}) oder Flugzeit ({}) für Preisberechnung.", distance, flightTimeMinutes);
            return;
        }

        tempFlight.setDistanceKm(distance);
        tempFlight.setFlightTimeMinutes(flightTimeMinutes);

        calculatePrices(tempFlight);

        priceEconomyField.setText(String.format(Locale.US, "%.2f", tempFlight.getPriceEconomy()));
        priceBusinessField.setText(String.format(Locale.US, "%.2f", tempFlight.getPriceBusiness()));
        priceFirstField.setText(String.format(Locale.US, "%.2f", tempFlight.getPriceFirst()));
    }

    /**
     * Sammelt aktuelle Eingabedaten für die Preisberechnung
     */
    private Flight collectInputDataForPricing() {
        Flight tempFlight = new Flight();
        tempFlight.setDepartureAirport(departureAirportComboBox.getValue());
        tempFlight.setArrivalAirport(arrivalAirportComboBox.getValue());
        tempFlight.setAircraft(aircraftComboBox.getValue());
        return tempFlight;
    }

    /**
     * Berechnet Preise für einen Flug nach der Preisformel
     */
    private void calculatePrices(Flight flight) {
        if (flight == null || flight.getAircraft() == null || flight.getAircraft().getType() == null ||
                flight.getDepartureAirport() == null || flight.getArrivalAirport() == null ||
                flight.getDistanceKm() < 0 || flight.getFlightTimeMinutes() < 0) {
            logger.warn("Kann Preise nicht berechnen - fehlende Daten.");
            if (flight != null) {
                flight.setPriceEconomy(0.0);
                flight.setPriceBusiness(0.0);
                flight.setPriceFirst(0.0);
            }
            return;
        }

        double distance = flight.getDistanceKm();
        double flightHours = flight.getFlightTimeMinutes() / 60.0;
        AircraftType type = flight.getAircraft().getType();
        int paxCapacity = type.getPaxCapacity();
        double costPerHour = type.getCostPerHour();

        if (paxCapacity <= 0) {
            logger.warn("Ungültige Pax-Kapazität ({}) für Typ {}", paxCapacity, type.getFullName());
            flight.setPriceEconomy(0.0);
            flight.setPriceBusiness(0.0);
            flight.setPriceFirst(0.0);
            return;
        }

        boolean isDomestic = isSameCountry(flight.getDepartureAirport(), flight.getArrivalAirport());
        double airportFee = isDomestic ? Config.getDomesticFee() : Config.getInternationalFee();

        double basePrice = (Config.getBasePricePerKm() * distance) +
                (flightHours * (costPerHour / paxCapacity)) +
                airportFee;

        if (basePrice < 0) {
            logger.warn("Negativer Basispreis ({}) berechnet für Flug {}. Setze auf 0.", basePrice, flight.getFlightNumber());
            basePrice = 0.0;
        }

        flight.setPriceEconomy(Math.round(basePrice * Config.getEconomyFactor() * 100.0) / 100.0);
        flight.setPriceBusiness(Math.round(basePrice * Config.getBusinessFactor() * 100.0) / 100.0);
        flight.setPriceFirst(Math.round(basePrice * Config.getFirstClassFactor() * 100.0) / 100.0);
    }

    /**
     * Prüft, ob zwei Flughäfen im selben Land liegen
     */
    private boolean isSameCountry(Airport airport1, Airport airport2) {
        if (airport1 == null || airport2 == null ||
                airport1.getCountry() == null || airport2.getCountry() == null) {
            logger.warn("Kann Land nicht bestimmen für: {} / {}",
                    airport1 != null ? airport1.getIcaoCode() : "null",
                    airport2 != null ? airport2.getIcaoCode() : "null");
            return false; // Assume international if country unknown
        }
        return airport1.getCountry().equalsIgnoreCase(airport2.getCountry());
    }

    /**
     * Erstellt einen Rückflug basierend auf dem Hinflug
     */
    private Flight createReturnFlight(Flight outboundFlight) {
        if (outboundFlight == null || outboundFlight.getDepartureAirport() == null ||
                outboundFlight.getArrivalAirport() == null || outboundFlight.getAircraft() == null ||
                outboundFlight.getArrivalDateTime() == null || outboundFlight.getFlightNumber() == null ||
                outboundFlight.getRoute() == null) {
            logger.error("Kann Rückflug nicht erstellen - unvollständige Hinflugdaten: {}", outboundFlight);
            return null;
        }

        Flight returnFlight = new Flight();

        // Reverse airports
        returnFlight.setDepartureAirport(outboundFlight.getArrivalAirport());
        returnFlight.setArrivalAirport(outboundFlight.getDepartureAirport());

        // Generate return flight number
        String outboundNumber = outboundFlight.getFlightNumber();
        String airlineCode = Airline.getInstance().getIcaoCode();
        String returnFlightNumber;

        if (outboundNumber.startsWith(airlineCode)) {
            try {
                String numericPart = outboundNumber.substring(airlineCode.length());
                int number = Integer.parseInt(numericPart);
                returnFlightNumber = airlineCode + (number + 1);

                if (flightService.getAllFlights().stream().anyMatch(f -> returnFlightNumber.equals(f.getFlightNumber()))) {
                    logger.warn("Rückflugnummer {} ist bereits vergeben.", returnFlightNumber);
                    return null;
                }
                returnFlight.setFlightNumber(returnFlightNumber);

            } catch (NumberFormatException | IndexOutOfBoundsException | NullPointerException e) {
                logger.error("Konnte Hinflugnummer {} nicht parsen.", outboundNumber, e);
                return null;
            }
        } else {
            logger.error("Hinflugnummer {} beginnt nicht mit Airline-Code {}.", outboundNumber, airlineCode);
            return null;
        }

        // Calculate return departure time based on configured turnaround time
        int turnaroundMinutes = turnaroundMinutesSpinner.getValue();
        int turnaroundHours = turnaroundHoursSpinner.getValue();
        int totalTurnaroundMinutes = (turnaroundHours * 60) + turnaroundMinutes;

        LocalDateTime returnDepartureTime = outboundFlight.getArrivalDateTime().plusMinutes(totalTurnaroundMinutes);
        returnFlight.setDepartureDate(returnDepartureTime.toLocalDate());
        returnFlight.setDepartureTime(returnDepartureTime.toLocalTime());

        // Set aircraft
        returnFlight.setAircraft(outboundFlight.getAircraft());

        // Find or create return route
        Route returnRoute = null;
        try {
            returnRoute = routeService.findRouteByAirportsAndAirline(
                    returnFlight.getDepartureAirport(), returnFlight.getArrivalAirport(), Airline.getInstance());
        } catch (Exception e) {
            logger.error("Fehler beim Suchen der Rückflugroute für {}", returnFlightNumber, e);
        }

        if (returnRoute == null) {
            logger.info("Erstelle neue Route für Rückflug {}", returnFlightNumber);
            returnRoute = new Route();
            returnRoute.setDepartureAirport(returnFlight.getDepartureAirport());
            returnRoute.setArrivalAirport(returnFlight.getArrivalAirport());
            returnRoute.setOperator(Airline.getInstance());

            double distance = calculateRouteDistance(returnRoute.getDepartureAirport(), returnRoute.getArrivalAirport());
            returnRoute.setDistanceKm(distance);

            double speed = returnFlight.getAircraft().getType().getSpeedKmh();
            int flightTime = calculateFlightTime(distance, speed);
            returnRoute.setFlightTimeMinutes(flightTime);

            try {
                boolean routeSaved = routeService.saveRoute(returnRoute);
                if (!routeSaved) {
                    logger.error("Speichern der neuen Rückflugroute fehlgeschlagen für {}", returnFlightNumber);
                    return null;
                }
                logger.info("Neue Rückflugroute gespeichert (ID: {})", returnRoute.getId());
            } catch (Exception e) {
                logger.error("Exception beim Speichern der neuen Rückflugroute für {}", returnFlightNumber, e);
                ExceptionHandler.handleException(e, "beim Speichern der Rückflugroute");
                return null;
            }
        }

        returnFlight.setRoute(returnRoute);
        returnFlight.setDistanceKm(returnRoute.getDistanceKm());
        returnFlight.setFlightTimeMinutes(returnRoute.getFlightTimeMinutes());

        // Calculate arrival time for return flight
        try {
            Object[] returnArrivalDateTime = FlightUtils.calculateArrivalTime(
                    returnFlight.getDepartureDate(), returnFlight.getDepartureTime(), returnFlight.getFlightTimeMinutes());

            if (returnArrivalDateTime != null && returnArrivalDateTime.length > 1 &&
                    returnArrivalDateTime[0] instanceof LocalDate && returnArrivalDateTime[1] instanceof LocalTime) {
                returnFlight.setArrivalDateTime(LocalDateTime.of((LocalDate) returnArrivalDateTime[0], (LocalTime) returnArrivalDateTime[1]));
            } else {
                logger.error("Ankunftszeit für Rückflug {} konnte nicht berechnet werden.", returnFlightNumber);
                return null;
            }
        } catch (Exception e) {
            logger.error("Exception bei Berechnung der Ankunftszeit für Rückflug {}.", returnFlightNumber, e);
            return null;
        }

        // Calculate prices
        calculatePrices(returnFlight);

        // Set status
        returnFlight.setStatus(FlightStatus.SCHEDULED);

        logger.info("Rückflugobjekt erfolgreich vorbereitet: {}", returnFlight.getFlightNumber());
        return returnFlight;
    }

    /**
     * Sammelt alle Eingabedaten und erstellt oder aktualisiert ein Flight-Objekt
     *
     * @return Der gespeicherte Flug oder null bei Fehler
     */
    private Flight saveFlight() {
        // Sammeln der Eingabedaten
        Flight flightToSave = collectInputData();
        if (flightToSave == null) {
            logger.error("Flugdaten ungültig. Speichern abgebrochen.");
            return null;
        }

        try {
            // --- Transaction Start (Conceptual) ---
            boolean outboundSaved = flightService.saveFlight(flightToSave);

            if (!outboundSaved) {
                logger.error("Speichern des Flugs {} fehlgeschlagen (Service-Fehler).", flightToSave.getFlightNumber());
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Speichern fehlgeschlagen");
                alert.setHeaderText("Flug konnte nicht gespeichert werden.");
                alert.setContentText("Der Flugdaten-Service meldete einen Fehler.");
                alert.showAndWait();
                return null;
            }

            // Use the object that was passed to the service
            Flight successfullySavedOutboundFlight = flightToSave;
            logger.info("Flug gespeichert: {} (ID: {})", successfullySavedOutboundFlight.getFlightNumber(),
                    successfullySavedOutboundFlight.getId());

            // Erstelle immer die Rückflugroute, wenn es ein neuer Flug ist
            if (currentFlight == null) {
                // Rückflugroute erstellen
                createReturnRoute(successfullySavedOutboundFlight.getDepartureAirport(),
                        successfullySavedOutboundFlight.getArrivalAirport());

                // Optional: Create return flight
                if (createReturnFlightCheck.isSelected()) {
                    logger.info("Versuche, Rückflug für {} zu erstellen.", successfullySavedOutboundFlight.getFlightNumber());
                    Flight returnFlight = createReturnFlight(successfullySavedOutboundFlight);

                    if (returnFlight != null) {
                        try {
                            boolean returnSaved = flightService.saveFlight(returnFlight);
                            if (!returnSaved) {
                                logger.error("Speichern des Rückflugs {} fehlgeschlagen (Service-Fehler).",
                                        returnFlight.getFlightNumber());
                                Alert alert = new Alert(Alert.AlertType.WARNING);
                                alert.setTitle("Rückflugfehler");
                                alert.setHeaderText("Flug gespeichert, aber Rückflug konnte nicht gespeichert werden.");
                                alert.setContentText("Service-Fehler beim Speichern des Rückflugs.");
                                alert.showAndWait();
                            } else {
                                logger.info("Rückflug gespeichert: {} (ID: {})", returnFlight.getFlightNumber(),
                                        returnFlight.getId());
                            }
                        } catch (Exception returnSaveEx) {
                            logger.error("Exception beim Speichern des Rückflugs {}: {}", returnFlight.getFlightNumber(),
                                    returnSaveEx.getMessage(), returnSaveEx);
                            ExceptionHandler.handleException(returnSaveEx, "beim Speichern des Rückflugs");
                        }
                    } else {
                        logger.warn("Erstellung des Rückflugs für {} fehlgeschlagen.",
                                successfullySavedOutboundFlight.getFlightNumber());
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Rückflug Info");
                        alert.setHeaderText("Flug gespeichert.");
                        alert.setContentText("Automatischer Rückflug konnte nicht erstellt werden (z.B. Nummer vergeben).");
                        alert.showAndWait();
                    }
                }
            }

            // Callback aufrufen
            if (saveCallback != null) {
                saveCallback.onSaved(successfullySavedOutboundFlight);
            }

            return successfullySavedOutboundFlight;

        } catch (Exception e) {
            logger.error("Unerwarteter Fehler beim Speichern des Flugs {}: {}",
                    flightToSave.getFlightNumber(), e.getMessage(), e);
            ExceptionHandler.handleException(e, "beim Speichern des Flugs " + flightToSave.getFlightNumber());
            return null;
        }
    }

    /**
     * Sammelt alle Eingabedaten und erstellt oder aktualisiert ein Flight-Objekt
     *
     * @return Flight-Objekt mit allen Eingabedaten, oder null, wenn Daten ungültig sind
     */
    private Flight collectInputData() {
        if (!isFlightNumberValid.get() || !isAircraftValid.get() || !isRouteValid.get() || !isDepartureDateTimeValid.get()) {
            logger.warn("Kann Eingabedaten nicht sammeln - Validierungsfehler.");
            return null;
        }
        
        // Finale Prüfung der 45-Minuten-Regel vor dem Speichern
        LocalDateTime plannedDepartureDateTime = LocalDateTime.of(
                departureDatePicker.getValue(),
                LocalTime.of(departureHourSpinner.getValue(), departureMinuteSpinner.getValue())
        );
        LocalDateTime minimumDepartureTime = LocalDateTime.now().plusMinutes(45);
        
        if (plannedDepartureDateTime.isBefore(minimumDepartureTime)) {
            logger.warn("Flug muss mindestens 45 Minuten in der Zukunft liegen - Validierungsfehler bei finalem Check.");
            ExceptionHandler.showWarningDialog("Ungültige Abflugzeit", 
                    "Der Abflug muss mindestens 45 Minuten in der Zukunft liegen.",
                    "Bitte wählen Sie ein späteres Datum oder eine spätere Uhrzeit.");
            return null;
        }

        Flight flight;
        boolean isNewFlight = (currentFlight == null);

        if (isNewFlight) {
            flight = new Flight();
            flight.setStatus(FlightStatus.SCHEDULED);
        } else {
            flight = currentFlight;
        }

        // Collect data
        flight.setFlightNumber(flightNumberField.getText());
        flight.setDepartureAirport(departureAirportComboBox.getValue());
        flight.setArrivalAirport(arrivalAirportComboBox.getValue());
        flight.setAircraft(aircraftComboBox.getValue());
        flight.setDepartureDate(departureDatePicker.getValue());
        flight.setDepartureTime(LocalTime.of(
                departureHourSpinner.getValue(), departureMinuteSpinner.getValue()));

        // Find or create route
        Route route;
        try {
            route = routeService.findRouteByAirportsAndAirline(
                    flight.getDepartureAirport(), flight.getArrivalAirport(), Airline.getInstance());
        } catch (Exception e) {
            logger.error("Fehler beim Suchen der Route während Datensammlung.", e);
            ExceptionHandler.handleException(e, "beim Suchen der Route");
            return null;
        }

        if (route == null) {
            logger.info("Route nicht gefunden für {} -> {}. Erstelle neue Route.",
                    flight.getDepartureAirport().getIcaoCode(), flight.getArrivalAirport().getIcaoCode());
            route = new Route();
            route.setDepartureAirport(flight.getDepartureAirport());
            route.setArrivalAirport(flight.getArrivalAirport());
            route.setOperator(Airline.getInstance());

            double distance = calculateRouteDistance(route.getDepartureAirport(), route.getArrivalAirport());
            route.setDistanceKm(distance);

            double speed = flight.getAircraft().getType().getSpeedKmh();
            int flightTime = calculateFlightTime(distance, speed);
            route.setFlightTimeMinutes(flightTime);

            try {
                boolean routeSaved = routeService.saveRoute(route);
                if (!routeSaved) {
                    logger.error("Speichern der neuen Route fehlgeschlagen während Datensammlung.");
                    ExceptionHandler.handleException(new RuntimeException("RouteService.saveRoute returned false"),
                            "beim Speichern der neuen Route");
                    return null;
                }
                logger.info("Neue Route gespeichert (ID: {})", route.getId());
            } catch (Exception e) {
                logger.error("Exception beim Speichern der neuen Route während Datensammlung.", e);
                ExceptionHandler.handleException(e, "beim Speichern der neuen Route");
                return null;
            }
        }
        flight.setRoute(route);
        flight.setDistanceKm(route.getDistanceKm());
        flight.setFlightTimeMinutes(route.getFlightTimeMinutes());

        // Price Handling
        try {
            flight.setPriceEconomy(Double.parseDouble(priceEconomyField.getText().replace(',', '.')));
            flight.setPriceBusiness(Double.parseDouble(priceBusinessField.getText().replace(',', '.')));
            flight.setPriceFirst(Double.parseDouble(priceFirstField.getText().replace(',', '.')));
        } catch (NumberFormatException | NullPointerException e) {
            logger.error("Fehler beim Parsen der Preise. Neuberechnung.", e);
            calculatePrices(flight);
            if (flight.getPriceEconomy() < 0 || flight.getPriceBusiness() < 0 || flight.getPriceFirst() < 0) {
                logger.error("Preisberechnung fehlgeschlagen (negative Preise).");
                return null;
            }
        }

        // Arrival Time Recalculation
        try {
            Object[] arrivalDateTime = FlightUtils.calculateArrivalTime(
                    flight.getDepartureDate(), flight.getDepartureTime(), flight.getFlightTimeMinutes());
            if (arrivalDateTime != null && arrivalDateTime.length > 1 &&
                    arrivalDateTime[0] instanceof LocalDate && arrivalDateTime[1] instanceof LocalTime) {
                flight.setArrivalDateTime(LocalDateTime.of((LocalDate) arrivalDateTime[0], (LocalTime) arrivalDateTime[1]));
            } else {
                logger.error("Ankunftszeit konnte nicht neu berechnet werden. Ergebnis: {}", Arrays.toString(arrivalDateTime));
                return null;
            }
        } catch (Exception e) {
            logger.error("Exception bei Neuberechnung der Ankunftszeit.", e);
            return null;
        }

        return flight;
    }

    /**
     * Erstellt eine Route vom Zielflughafen zum Abflughafen (Rückflugroute).
     * Wird immer bei Erstellung eines neuen Fluges aufgerufen, unabhängig davon, ob ein Rückflug erstellt wird.
     *
     * @param originAirport      Ursprünglicher Abflughafen (wird zum Ziel der Rückflugroute)
     * @param destinationAirport Ursprünglicher Zielflughafen (wird zum Abflug der Rückflugroute)
     */
    private void createReturnRoute(Airport originAirport, Airport destinationAirport) {
        // Prüfen, ob die Rückflugroute bereits existiert
        try {
            Route existingReturnRoute = routeService.findRouteByAirportsAndAirline(
                    destinationAirport, originAirport, Airline.getInstance());

            if (existingReturnRoute != null) {
                logger.info("Rückflugroute von {} nach {} existiert bereits. ID: {}",
                        destinationAirport.getIcaoCode(), originAirport.getIcaoCode(), existingReturnRoute.getId());
                return; // Route existiert bereits, nichts zu tun
            }

            // Route erstellen
            Route returnRoute = new Route();
            returnRoute.setDepartureAirport(destinationAirport);
            returnRoute.setArrivalAirport(originAirport);
            returnRoute.setOperator(Airline.getInstance());

            double distance = calculateRouteDistance(destinationAirport, originAirport);
            returnRoute.setDistanceKm(distance);

            // Für die Flugzeit verwenden wir die Standardgeschwindigkeit, da kein Flugzeug vorgegeben ist
            int flightTime = calculateFlightTime(distance, Config.getDefaultAircraftSpeed());
            returnRoute.setFlightTimeMinutes(flightTime);

            // Route speichern
            boolean saved = routeService.saveRoute(returnRoute);
            if (saved) {
                logger.info("Rückflugroute von {} nach {} erfolgreich erstellt. ID: {}",
                        destinationAirport.getIcaoCode(), originAirport.getIcaoCode(), returnRoute.getId());
            } else {
                logger.error("Fehler beim Speichern der Rückflugroute von {} nach {}",
                        destinationAirport.getIcaoCode(), originAirport.getIcaoCode());
            }

        } catch (Exception e) {
            logger.error("Fehler beim Erstellen der Rückflugroute: {}", e.getMessage(), e);
        }
    }

    /**
     * Validiert das ausgewählte Flugzeug (Verfügbarkeit, Reichweite, zeitliche Verfügbarkeit)
     */
    private void validateAircraftSelection() {
        Aircraft aircraft = aircraftComboBox.getValue();
        Airport departureAirport = departureAirportComboBox.getValue();
        Airport arrivalAirport = arrivalAirportComboBox.getValue();

        aircraftErrorLabel.setVisible(false);
        isAircraftValid.set(false);

        if (aircraft == null) {
            return;
        }

        boolean isEditingThisAircraft = currentFlight != null && currentFlight.getAircraft() != null
                && aircraft.getId().equals(currentFlight.getAircraft().getId());

        // 1. Prüfe Grundstatus des Flugzeugs
        if (aircraft.getStatus() != AircraftStatus.AVAILABLE && !isEditingThisAircraft) {
            aircraftErrorLabel.setText("Flugzeug ist nicht verfügbar!");
            aircraftErrorLabel.setVisible(true);
            return;
        }

        // 2. Prüfe Standort des Flugzeugs
        if (departureAirport != null && !aircraft.getCurrentLocation().getId().equals(departureAirport.getId())
                && !isEditingThisAircraft) {
            aircraftErrorLabel.setText("Flugzeug befindet sich nicht am Abflughafen!");
            aircraftErrorLabel.setVisible(true);
            return;
        }

        // 3. Prüfe Reichweite des Flugzeugs
        if (departureAirport != null && arrivalAirport != null) {
            double distance = calculateRouteDistance(departureAirport, arrivalAirport);
            if (distance > 0) {
                double range = aircraft.getType().getMaxRangeKm();
                double requiredRange = distance * Config.getRouteDistanceBuffer();
                if (range < requiredRange) {
                    aircraftErrorLabel.setText("Unzureichende Reichweite! " +
                            String.format("%.0f km benötigt, %.0f km verfügbar", requiredRange, range));
                    aircraftErrorLabel.setVisible(true);
                    return;
                }
            }
        }

        // 4. Prüfe zeitliche Verfügbarkeit des Flugzeugs
        if (departureDatePicker.getValue() != null &&
            departureHourSpinner.getValue() != null &&
            departureMinuteSpinner.getValue() != null &&
            departureAirport != null &&
            arrivalAirport != null) {

            LocalDateTime departureDateTime = LocalDateTime.of(
                    departureDatePicker.getValue(),
                    LocalTime.of(departureHourSpinner.getValue(), departureMinuteSpinner.getValue())
            );

            // Zunächst eine Route finden/berechnen
            Route route = routeService.findRouteByAirportsAndAirline(
                    departureAirport, arrivalAirport, Airline.getInstance());

            if (route == null) {
                // Temporäre Route für Berechnungen erstellen
                route = new Route();
                route.setDepartureAirport(departureAirport);
                route.setArrivalAirport(arrivalAirport);

                // Distanz berechnen
                double distance = calculateRouteDistance(departureAirport, arrivalAirport);
                route.setDistanceKm(distance);

                // Flugzeit mit spezifischer Geschwindigkeit des Flugzeugs berechnen
                double speed = aircraft.getType().getSpeedKmh();
                if (speed <= 0) {
                    speed = Config.getDefaultAircraftSpeed(); // Fallback
                }
                route.setFlightTimeMinutes(calculateFlightTime(distance, speed));
            }

            // Blockzeit berechnen
            Pair<LocalDateTime, LocalDateTime> blockTime = flightService.calculateBlockTimeForRoute(
                    departureDateTime, route);

            if (blockTime != null) {
                LocalDateTime blockStartTime = blockTime.getKey();
                LocalDateTime blockEndTime = blockTime.getValue();

                // Bei Bearbeitung eines bestehenden Flugs den aktuellen Flug ausschließen
                Long excludeFlightId = (currentFlight != null) ? currentFlight.getId() : null;

                // Zeitliche Verfügbarkeit prüfen
                if (!flightService.isAircraftAvailableForTimeRange(aircraft, blockStartTime, blockEndTime, excludeFlightId)) {
                    aircraftErrorLabel.setText("Flugzeug ist für diesen Zeitraum bereits verplant!");
                    aircraftErrorLabel.setVisible(true);
                    return;
                }
            }
        }

        isAircraftValid.set(true);
    }

    /**
     * Validiert die eingegebene Turnaround-Zeit (muss mindestens 1 Stunde betragen)
     */
    private void validateTurnaroundTime() {
        int hours = turnaroundHoursSpinner.getValue();
        int minutes = turnaroundMinutesSpinner.getValue();
        int totalMinutes = (hours * 60) + minutes;

        // Mindestens die konfigurierte Mindestwartungszeit
        int minTurnaroundMinutes = Config.getMinTurnaroundMinutes();
        if (totalMinutes < minTurnaroundMinutes) {
            // Wenn die Gesamtzeit zu niedrig ist, setze auf konfigurierten Standardwert
            turnaroundHoursSpinner.getValueFactory().setValue(Config.getDefaultTurnaroundHours());
            turnaroundMinutesSpinner.getValueFactory().setValue(Config.getDefaultTurnaroundMinutes());
        }

        // Maximal die konfigurierte Höchstzeit
        int maxTurnaroundHours = Config.getMaxTurnaroundHours();
        if (hours > maxTurnaroundHours) {
            turnaroundHoursSpinner.getValueFactory().setValue(maxTurnaroundHours);
            turnaroundMinutesSpinner.getValueFactory().setValue(0);
        } else if (hours == maxTurnaroundHours && minutes > 0) {
            turnaroundHoursSpinner.getValueFactory().setValue(maxTurnaroundHours);
            turnaroundMinutesSpinner.getValueFactory().setValue(0);
        }
    }

    /**
     * Berechnet die Distanz zwischen zwei Flughäfen
     */
    private double calculateRouteDistance(Airport departure, Airport arrival) {
        if (departure == null || arrival == null ||
                departure.getLatitude() == 0.0 || departure.getLongitude() == 0.0 ||
                arrival.getLatitude() == 0.0 || arrival.getLongitude() == 0.0) {
            logger.warn("Ungültige Koordinaten für Distanzberechnung: Dep ({}, {}), Arr ({}, {})",
                    departure != null ? departure.getLatitude() : "null", departure != null ? departure.getLongitude() : "null",
                    arrival != null ? arrival.getLatitude() : "null", arrival != null ? arrival.getLongitude() : "null");
            return 0.0;
        }
        return FlightUtils.calculateDistance(
                departure.getLatitude(), departure.getLongitude(),
                arrival.getLatitude(), arrival.getLongitude()
        );
    }

    /**
     * Berechnet die Flugzeit basierend auf Distanz und Geschwindigkeit
     */
    private int calculateFlightTime(double distance, double speedKmh) {
        if (speedKmh <= 0 || distance <= 0) {
            return 0;
        }
        return FlightUtils.calculateFlightTime(distance, speedKmh);
    }

    /**
     * Aktualisiert die Ankunftszeit basierend auf Abflugzeit und Flugzeit
     */
    private void updateArrivalTime() {
        arrivalDateTimeLabel.setText("Wird berechnet...");
        isDepartureDateTimeValid.set(false);

        if (!isRouteValid.get()) {
            arrivalDateTimeLabel.setText("Unvollständige Route");
            return;
        }
        Airport departure = departureAirportComboBox.getValue();
        Airport arrival = arrivalAirportComboBox.getValue();
        LocalDate departureDate = departureDatePicker.getValue();
        Integer hourVal = departureHourSpinner.getValue();
        Integer minuteVal = departureMinuteSpinner.getValue();
        Aircraft selectedAircraft = aircraftComboBox.getValue();

        if (departureDate == null || hourVal == null || minuteVal == null || departure == null || arrival == null || selectedAircraft == null) {
            arrivalDateTimeLabel.setText("Unvollständige Eingabe");
            return;
        }
        LocalTime departureTime = LocalTime.of(hourVal, minuteVal);
        
        // Prüfe, ob der Flug mindestens 45 Minuten in der Zukunft liegt
        LocalDateTime plannedDepartureDateTime = LocalDateTime.of(departureDate, departureTime);
        LocalDateTime minimumDepartureTime = LocalDateTime.now().plusMinutes(45);
        
        if (plannedDepartureDateTime.isBefore(minimumDepartureTime)) {
            arrivalDateTimeLabel.setText("Abflug muss mind. 45 Min. in der Zukunft liegen");
            isDepartureDateTimeValid.set(false);
            return;
        }

        // Berechnung der Flugzeit basierend auf dem gewählten Flugzeug und der Distanz
        double distance = calculateRouteDistance(departure, arrival);
        if (distance <= 0) {
            arrivalDateTimeLabel.setText("Distanzberechnung fehlgeschlagen");
            return;
        }

        // Flugzeit mit der Geschwindigkeit des gewählten Flugzeugs berechnen
        int flightTimeMinutes = calculateFlightTime(distance, selectedAircraft.getType().getSpeedKmh());

        if (flightTimeMinutes <= 0) {
            arrivalDateTimeLabel.setText("Flugzeit ungültig");
            return;
        }

        // Ankunftszeit berechnen
        try {
            Object[] arrivalDateTime = FlightUtils.calculateArrivalTime(departureDate, departureTime,
                    flightTimeMinutes);

            if (arrivalDateTime != null && arrivalDateTime.length > 1 &&
                    arrivalDateTime[0] instanceof LocalDate arrivalDate && arrivalDateTime[1] instanceof LocalTime arrivalTime) {

                DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

                arrivalDateTimeLabel.setText(arrivalDate.format(dateFormatter) + " " + arrivalTime.format(timeFormatter));
                isDepartureDateTimeValid.set(true); // Calculation successful
            } else {
                arrivalDateTimeLabel.setText("Fehler bei Berechnung");
                logger.error("Ungültiges Ergebnis von TimeUtils.calculateArrivalTime: {}", Arrays.toString(arrivalDateTime));
            }
        } catch (Exception e) {
            arrivalDateTimeLabel.setText("Fehler bei Zeitberechnung");
            logger.error("Fehler bei Berechnung der Ankunftszeit mit TimeUtils", e);
        }
    }

    /**
     * Prüft, ob für die gewählte Flughafen-Kombination bereits eine Route existiert
     */
    private void checkExistingRoute() {
        Airport departure = departureAirportComboBox.getValue();
        Airport arrival = arrivalAirportComboBox.getValue();

        routeInfoLabel.setVisible(false);
        routeDistanceLabel.setText("Distanz: -");
        routeTimeLabel.setText("Flugzeit: -");
        isRouteValid.set(false);

        if (departure == null || arrival == null) {
            return;
        }

        if (departure.equals(arrival)) {
            routeInfoLabel.setText("Start- und Zielflughafen müssen unterschiedlich sein!");
            routeInfoLabel.setTextFill(Color.RED);
            routeInfoLabel.setVisible(true);
            return;
        }

        Route existingRoute;
        try {
            existingRoute = routeService.findRouteByAirportsAndAirline(departure, arrival, Airline.getInstance());
        } catch (Exception e) {
            logger.error("Fehler beim Suchen der Route.", e);
            ExceptionHandler.handleException(e, "beim Suchen der Route");
            routeInfoLabel.setText("Fehler bei Routensuche!");
            routeInfoLabel.setTextFill(Color.RED);
            routeInfoLabel.setVisible(true);
            return;
        }

        double distance;

        // Distanz ermitteln
        if (existingRoute != null) {
            routeInfoLabel.setText("Route existiert bereits und wird verwendet.");
            routeInfoLabel.setTextFill(Color.GREEN);
            distance = existingRoute.getDistanceKm();
        } else {
            routeInfoLabel.setText("Neue Route wird beim Speichern erstellt.");
            routeInfoLabel.setTextFill(Color.BLUE);

            distance = calculateRouteDistance(departure, arrival);
            if (distance <= 0) {
                routeDistanceLabel.setText("Distanz: Fehler");
                routeTimeLabel.setText("Flugzeit: -");
                routeInfoLabel.setVisible(true);
                return; // Cannot calculate time without distance
            }
        }

        // Display distance info
        routeDistanceLabel.setText(String.format("Distanz: %.1f km", distance));

        // Flugzeit nur berechnen, wenn ein Flugzeug ausgewählt ist
        Aircraft selectedAircraft = aircraftComboBox.getValue();
        if (selectedAircraft != null && selectedAircraft.getType() != null) {
            double speed = selectedAircraft.getType().getSpeedKmh();
            int flightTimeMinutes = calculateFlightTime(distance, speed);

            if (flightTimeMinutes <= 0) {
                routeTimeLabel.setText("Flugzeit: Fehler");
                isDepartureDateTimeValid.set(false); // Invalidate dependent time
            } else {
                routeTimeLabel.setText(String.format("Flugzeit: %s", FlightUtils.formatFlightTime(flightTimeMinutes)));
            }
        } else {
            // Kein Flugzeug ausgewählt - keine Flugzeit berechnen
            routeTimeLabel.setText("Flugzeit: -");
        }

        routeInfoLabel.setVisible(true);
        isRouteValid.set(true);
    }
}
