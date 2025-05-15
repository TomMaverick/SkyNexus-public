package skynexus.controller.helpers;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
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
import skynexus.util.ExceptionHandler;
import skynexus.util.FlightUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Hilfsklasse zur Anzeige eines Dialogs zum Erstellen oder Bearbeiten eines Fluges.
 * Implementiert Validierungen und Geschäftslogik zur Flugplanung.
 * (Helper class for displaying a dialog to create or edit a flight.
 * Implements validation and business logic for flight planning.)
 */
public class FlightDialogHelper {
    private static final Logger logger = LoggerFactory.getLogger(FlightDialogHelper.class);

    // Preiskonstanten (Price constants)
    private static final double BASE_PRICE_PER_KM = 0.10;
    private static final double ECONOMY_FACTOR = 1.0;
    private static final double BUSINESS_FACTOR = 3.5;
    private static final double FIRST_CLASS_FACTOR = 6.0;
    private static final double DOMESTIC_FEE = 25.0;
    private static final double INTERNATIONAL_FEE = 75.0;

    // Services
    private final FlightService flightService;
    private final RouteService routeService;
    private final AirportService airportService;
    private final AircraftService aircraftService;

    // UI-Elemente (UI Elements)
    private Dialog<Flight> dialog;

    private TextField flightNumberField;
    private ComboBox<Aircraft> aircraftComboBox;
    private ComboBox<Airport> departureAirportComboBox;
    private ComboBox<Airport> arrivalAirportComboBox;
    private DatePicker departureDatePicker;
    private Spinner<Integer> departureHourSpinner;
    private Spinner<Integer> departureMinuteSpinner;
    private Label arrivalDateTimeLabel;
    private CheckBox createReturnFlightCheck;
    private Spinner<Integer> turnaroundHoursSpinner;
    private Spinner<Integer> turnaroundMinutesSpinner;
    private TextField priceEconomyField;
    private TextField priceBusinessField;
    private TextField priceFirstField;

    // Status- und Info-Labels (Status and Info Labels)
    private Label flightNumberErrorLabel;
    private Label aircraftErrorLabel;
    private Label routeInfoLabel;
    private Label routeDistanceLabel;
    private Label routeTimeLabel;
    private Label returnFlightInfoLabel;

    // Validierungsstatus (Validation Status)
    private final BooleanProperty isFlightNumberValid = new SimpleBooleanProperty(false);
    private final BooleanProperty isAircraftValid = new SimpleBooleanProperty(false);
    private final BooleanProperty isRouteValid = new SimpleBooleanProperty(false);
    private final BooleanProperty isDepartureDateTimeValid = new SimpleBooleanProperty(false);

    // Cache des aktuellen Flugs (für Bearbeitung) (Cache of the current flight (for editing))
    private Flight currentFlight;

    /**
     * Funktionales Interface für Callback nach erfolgreichem Speichern
     * (Functional interface for callback after successful save)
     */
    @FunctionalInterface
    public interface SaveCallback {
        void onSaved(Flight flight);
    }

    /**
     * Konstruktor für FlightDialogHelper
     * (Constructor for FlightDialogHelper)
     *
     * @param flightService   Service für Flugoperationen (Service for flight operations)
     * @param routeService    Service für Routenoperationen (Service for route operations)
     * @param airportService  Service für Flughafenoperationen (Service for airport operations)
     * @param aircraftService Service für Flugzeugoperationen (Service for aircraft operations)
     */
    public FlightDialogHelper(FlightService flightService, RouteService routeService,
                              AirportService airportService, AircraftService aircraftService) {
        // Services should ideally be checked for null here if not using dependency injection framework
        this.flightService = Objects.requireNonNull(flightService, "FlightService darf nicht null sein");
        this.routeService = Objects.requireNonNull(routeService, "RouteService darf nicht null sein");
        this.airportService = Objects.requireNonNull(airportService, "AirportService darf nicht null sein");
        this.aircraftService = Objects.requireNonNull(aircraftService, "AircraftService darf nicht null sein");
    }

    /**
     * Zeigt den Dialog zum Erstellen oder Bearbeiten eines Fluges an.
     * (Displays the dialog for creating or editing a flight.)
     *
     * @param flight       Existierender Flug (null für neuen Flug) (Existing flight (null for new flight))
     * @param saveCallback Callback nach erfolgreichem Speichern (Callback after successful save)
     * @return Optionales Flight-Objekt, falls gespeichert (Optional Flight object if saved)
     */
    public Optional<Flight> showDialog(Flight flight, SaveCallback saveCallback) {
        this.currentFlight = flight;

        // Dialog initialisieren (Initialize dialog)
        dialog = new Dialog<>();
        dialog.setTitle(flight == null ? "Neuen Flug erstellen" : "Flug bearbeiten");
        String headerText = flight == null ? "Neuen Flug planen" :
                (flight.getFlightNumber() != null ? "Flug " + flight.getFlightNumber() + " bearbeiten" : "Flug bearbeiten");
        dialog.setHeaderText(headerText);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Dialog breiter machen, damit kein Text abgeschnitten wird
        dialog.getDialogPane().setMinWidth(600);
        dialog.getDialogPane().setPrefWidth(600);

        // UI-Elemente erstellen und konfigurieren (Create and configure UI elements)
        createControls();
        layoutControls();
        setupEventHandlers();
        setupValidation();

        // Daten vorausfüllen, falls ein Flug bearbeitet wird (Pre-fill data if editing a flight)
        if (flight != null) {
            populateFields(flight);
        } else {
            // Bei neuem Flug automatisch Flugnummer vorschlagen (Suggest flight number automatically for new flight)
            generateNextFlightNumber();

            // Standard Turnaround-Zeit einrichten (1 Stunde)
            turnaroundHoursSpinner.getValueFactory().setValue(1);
            turnaroundMinutesSpinner.getValueFactory().setValue(0);
        }

        // OK-Button nur aktivieren, wenn Validierung erfolgreich (Enable OK button only if validation is successful)
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.disableProperty().bind(
                isFlightNumberValid.not()
                        .or(isAircraftValid.not())
                        .or(isRouteValid.not())
                        .or(isDepartureDateTimeValid.not())
        );

        // Aktion für OK-Button (Flug speichern) (Action for OK button (save flight))
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                Flight result = saveFlight(saveCallback);
                return result; // saveFlight returns null on failure
            }
            return null; // Return null if Cancel or closed
        });

        // Dialog anzeigen und Ergebnis zurückgeben (Show dialog and return result)
        return dialog.showAndWait().filter(Objects::nonNull); // Filter null results from converter
    }

    /**
     * Erstellt alle UI-Steuerelemente für den Dialog
     * (Creates all UI controls for the dialog)
     */
    private void createControls() {
        // Flugnummer (Flight number)
        flightNumberField = new TextField();
        flightNumberField.setPromptText("z.B. SKX100");
        flightNumberErrorLabel = new Label();
        flightNumberErrorLabel.setTextFill(Color.RED);
        flightNumberErrorLabel.setVisible(false);

        // Flugzeugauswahl (Aircraft selection)
        aircraftComboBox = new ComboBox<>();
        aircraftComboBox.setMaxWidth(Double.MAX_VALUE);
        aircraftComboBox.setPromptText("Verfügbares Flugzeug auswählen");
        aircraftErrorLabel = new Label();
        aircraftErrorLabel.setTextFill(Color.RED);
        aircraftErrorLabel.setVisible(false);

        // Flughafenauswahl (Airport selection)
        departureAirportComboBox = new ComboBox<>();
        departureAirportComboBox.setMaxWidth(Double.MAX_VALUE);
        departureAirportComboBox.setPromptText("Abflughafen auswählen");

        arrivalAirportComboBox = new ComboBox<>();
        arrivalAirportComboBox.setMaxWidth(Double.MAX_VALUE);
        arrivalAirportComboBox.setPromptText("Zielflughafen auswählen");

        // Routeninformationen (Route information)
        routeInfoLabel = new Label();
        routeInfoLabel.setVisible(false);

        routeDistanceLabel = new Label();
        routeTimeLabel = new Label();

        // Datums- und Zeitauswahl (Date and time selection)
        departureDatePicker = new DatePicker(LocalDate.now());

        SpinnerValueFactory<Integer> hourFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, LocalTime.now().getHour());
        departureHourSpinner = new Spinner<>(hourFactory);
        departureHourSpinner.setEditable(true);
        departureHourSpinner.setPrefWidth(70);

        SpinnerValueFactory<Integer> minuteFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, LocalTime.now().getMinute());
        departureMinuteSpinner = new Spinner<>(minuteFactory);
        departureMinuteSpinner.setEditable(true);
        departureMinuteSpinner.setPrefWidth(70);

        // Ankunftszeit (berechnet) (Arrival time (calculated))
        arrivalDateTimeLabel = new Label("Wird berechnet...");

        // Rückflug-Option (Return flight option)
        createReturnFlightCheck = new CheckBox("Automatisch Rückflug erstellen");
        createReturnFlightCheck.setSelected(true);
        returnFlightInfoLabel = new Label();
        returnFlightInfoLabel.setVisible(false);

        // Turnaround-Zeit Spinner (Zeit zwischen Ankunft und Rückflug)
        SpinnerValueFactory<Integer> turnaroundHoursFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 0);
        turnaroundHoursSpinner = new Spinner<>(turnaroundHoursFactory);
        turnaroundHoursSpinner.setEditable(true);
        turnaroundHoursSpinner.setPrefWidth(70);

        SpinnerValueFactory<Integer> turnaroundMinutesFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 45);
        turnaroundMinutesSpinner = new Spinner<>(turnaroundMinutesFactory);
        turnaroundMinutesSpinner.setEditable(true);
        turnaroundMinutesSpinner.setPrefWidth(70);

        // Preisfelder (Price fields)
        priceEconomyField = new TextField();
        priceEconomyField.setPromptText("Economy-Preis");

        priceBusinessField = new TextField();
        priceBusinessField.setPromptText("Business-Preis");

        priceFirstField = new TextField();
        priceFirstField.setPromptText("First-Class-Preis");

        // ComboBox-Converter für bessere Anzeige (ComboBox converters for better display)
        setupComboBoxConverters();

        // Initialisieren der Daten für ComboBoxen (Initialize data for ComboBoxes)
        loadAirports();
    }

    /**
     * Ordnet die UI-Elemente im Dialog-Layout an
     * (Arranges the UI elements in the dialog layout)
     */
    // Neue UI-Elemente für Flugzeugdetails
    private Label aircraftPaxCapacityLabel;
    private Label aircraftCargoCapacityLabel;
    private Label aircraftRangeLabel;
    private Label aircraftSpeedLabel;
    private Label aircraftCostPerHourLabel;

    private void layoutControls() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // Überschriften und Gruppen (Headings and groups)
        Label flightInfoHeader = new Label("Flugdaten");
        flightInfoHeader.setFont(Font.font("System", FontWeight.BOLD, 14));

        Label routeInfoHeader = new Label("Routeninformationen");
        routeInfoHeader.setFont(Font.font("System", FontWeight.BOLD, 14));

        Label aircraftInfoHeader = new Label("Flugzeugdetails");
        aircraftInfoHeader.setFont(Font.font("System", FontWeight.BOLD, 14));

        Label priceInfoHeader = new Label("Preise");
        priceInfoHeader.setFont(Font.font("System", FontWeight.BOLD, 14));

        // Flugnummer (Flight number)
        grid.add(flightInfoHeader, 0, 0, 2, 1);
        grid.add(new Label("Flugnummer:"), 0, 1);
        grid.add(flightNumberField, 1, 1);
        grid.add(flightNumberErrorLabel, 2, 1);

        // Route - jetzt zuerst, VOR der Flugzeugauswahl
        grid.add(routeInfoHeader, 0, 2, 2, 1);
        grid.add(new Label("Abflughafen:"), 0, 3);
        grid.add(departureAirportComboBox, 1, 3);

        grid.add(new Label("Zielflughafen:"), 0, 4);
        grid.add(arrivalAirportComboBox, 1, 4);

        // Routeninformationen (Route information)
        grid.add(routeInfoLabel, 0, 5, 2, 1);

        HBox routeDetailsBox = new HBox(10);
        routeDetailsBox.getChildren().addAll(routeDistanceLabel, routeTimeLabel);
        grid.add(routeDetailsBox, 0, 6, 2, 1);

        // Flugzeug - jetzt NACH der Routenauswahl
        grid.add(aircraftInfoHeader, 0, 7, 2, 1);
        grid.add(new Label("Flugzeug:"), 0, 8);
        grid.add(aircraftComboBox, 1, 8);
        grid.add(aircraftErrorLabel, 2, 8);

        // Neue Flugzeugdetails
        // Initialisiere die Labels für Flugzeugdetails
        aircraftPaxCapacityLabel = new Label("Passagierkapazität: -");
        aircraftCargoCapacityLabel = new Label("Frachtkapazität: -");
        aircraftRangeLabel = new Label("Reichweite: -");
        aircraftSpeedLabel = new Label("Geschwindigkeit: -");
        aircraftCostPerHourLabel = new Label("Betriebskosten/h: -");

        // Flugzeugdetails in Container für besseres Layout
        VBox aircraftDetailsBox = new VBox(5);
        aircraftDetailsBox.getChildren().addAll(
            aircraftPaxCapacityLabel,
            aircraftCargoCapacityLabel,
            aircraftRangeLabel,
            aircraftSpeedLabel,
            aircraftCostPerHourLabel
        );
        grid.add(aircraftDetailsBox, 0, 9, 2, 1);

        // Zeitplanung (Scheduling)
        grid.add(new Label("Abflugdatum:"), 0, 10);
        grid.add(departureDatePicker, 1, 10);

        grid.add(new Label("Abflugzeit:"), 0, 11);
        HBox timeBox = new HBox(5);
        timeBox.getChildren().addAll(departureHourSpinner, new Label(":"), departureMinuteSpinner);
        grid.add(timeBox, 1, 11);

        grid.add(new Label("Ankunft:"), 0, 12);
        grid.add(arrivalDateTimeLabel, 1, 12);

        // Rückflug (Return flight)
        grid.add(createReturnFlightCheck, 0, 13, 2, 1);

        // Turnaround Zeit (Zeit zwischen Ankunft und Rückflug)
        HBox turnaroundBox = new HBox(5);
        turnaroundBox.getChildren().addAll(
            new Label("Turnaround-Zeit:"),
            turnaroundHoursSpinner,
            new Label("h"),
            turnaroundMinutesSpinner,
            new Label("min")
        );
        grid.add(turnaroundBox, 0, 14, 2, 1);

        // Rückfluginformationslabel für mehrzeiligen Text konfigurieren
        returnFlightInfoLabel.setWrapText(true);
        returnFlightInfoLabel.setMinHeight(40); // Genug Platz für zwei Zeilen
        grid.add(returnFlightInfoLabel, 0, 15, 2, 1);

        // Preise (Prices)
        grid.add(priceInfoHeader, 0, 16, 2, 1);
        grid.add(new Label("Economy:"), 0, 17);
        grid.add(priceEconomyField, 1, 17);

        grid.add(new Label("Business:"), 0, 18);
        grid.add(priceBusinessField, 1, 18);

        grid.add(new Label("First Class:"), 0, 19);
        grid.add(priceFirstField, 1, 19);

        // In Dialog einfügen (Insert into dialog)
        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        dialog.getDialogPane().setContent(scrollPane);
    }

    /**
     * Richtet die ComboBox-Converter für formatierte Anzeige ein
     * (Sets up the ComboBox converters for formatted display)
     */
    private void setupComboBoxConverters() {
        // Airport-Converter: ICAO - Stadt, Land (Airport converter: ICAO - City, Country)
        StringConverter<Airport> airportConverter = new StringConverter<Airport>() {
            @Override
            public String toString(Airport airport) {
                if (airport == null) return null;
                // Attributes like city, country, icaoCode are validated non-null/non-empty in Airport model
                return String.format("%s - %s, %s", airport.getIcaoCode(), airport.getCity(), airport.getCountry());
            }

            @Override
            public Airport fromString(String string) { return null; }
        };

        departureAirportComboBox.setConverter(airportConverter);
        arrivalAirportComboBox.setConverter(airportConverter);

        // Aircraft-Converter: Registration - Type (Aircraft converter: Registration - Type)
        StringConverter<Aircraft> aircraftConverter = new StringConverter<Aircraft>() {
            @Override
            public String toString(Aircraft aircraft) {
                if (aircraft == null) return null;
                // Aircraft model ensures type and registrationNo are non-null/non-empty
                // AircraftType ensures getFullName() works (uses manufacturer + model)
                return String.format("%s - %s", aircraft.getRegistrationNo(), aircraft.getType().getFullName());
            }

            @Override
            public Aircraft fromString(String string) { return null; }
        };

        aircraftComboBox.setConverter(aircraftConverter);
    }

    /**
     * Lädt alle Flughäfen für die ComboBoxen
     * (Loads all airports for the ComboBoxes)
     */
    private void loadAirports() {
        try {
            List<Airport> airports = airportService.getAllAirports(); // Assumes non-null list, elements validated by service/model
            airports.sort(Comparator.comparing(Airport::getIcaoCode)); // IcaoCode guaranteed non-null by model
            ObservableList<Airport> observableAirports = FXCollections.observableArrayList(airports);
            departureAirportComboBox.setItems(observableAirports);
            arrivalAirportComboBox.setItems(observableAirports);
        } catch (Exception e) {
            // ExceptionHandler is called by the service layer if needed
            logger.error("Fehler beim Laden der Flughäfen für ComboBoxen.", e);
            // Show specific error in UI?
            ExceptionHandler.showErrorDialog("Fehler", "Flughäfen konnten nicht geladen werden.", null, e);
        }
    }

    /**
     * Richtet Event-Handler für UI-Elemente ein
     * (Sets up event handlers for UI elements)
     */
    private void setupEventHandlers() {
        // Flugnummer validieren bei Änderung (Validate flight number on change)
        flightNumberField.textProperty().addListener((obs, oldVal, newVal) -> {
            validateFlightNumber(newVal);
            updateReturnFlightInfo();
        });

        // Flughafenauswahl aktualisiert verfügbare Flugzeuge (Airport selection updates available aircraft)
        departureAirportComboBox.valueProperty().addListener((obs, oldVal, newVal) -> handleAirportChange());
        arrivalAirportComboBox.valueProperty().addListener((obs, oldVal, newVal) -> handleAirportChange());

        // Flugzeugauswahl validieren (Validate aircraft selection)
        aircraftComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            validateAircraftSelection();
            // Flugzeugdetails aktualisieren
            updateAircraftDetails(newVal);
            // Recalculate route time, arrival time, and prices if aircraft changes
            checkExistingRoute();
            updatePrices();
            updateArrivalTime();
            updateReturnFlightInfo();
        });

        // Zeit- und Datumsänderungen (Time and date changes)
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

        // Rückflug-Option (Return flight option)
        createReturnFlightCheck.selectedProperty().addListener((obs, oldVal, newVal) -> updateReturnFlightInfo());
    }

    /**
     * Handles changes in either departure or arrival airport selection.
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
     * Helper to invalidate route selection and clear related fields.
     */
    private void invalidateRouteSelection(String message) {
        isRouteValid.set(false);
        routeInfoLabel.setText(message);
        routeInfoLabel.setTextFill(Color.RED);
        routeInfoLabel.setVisible(true);
        routeDistanceLabel.setText("");
        routeTimeLabel.setText("");
        arrivalDateTimeLabel.setText("Route ungültig");
        isDepartureDateTimeValid.set(false);
        priceEconomyField.setText("");
        priceBusinessField.setText("");
        priceFirstField.setText("");
        updateReturnFlightInfo();
    }


    /**
     * Richtet Validierungslogik für den Dialog ein
     * (Sets up validation logic for the dialog)
     */
    private void setupValidation() {
        isFlightNumberValid.set(false);
        isAircraftValid.set(false);
        isRouteValid.set(false);
        isDepartureDateTimeValid.set(false); // Set true only when arrival time is calculated
    }

    /**
     * Validiert die eingegebene Turnaround-Zeit (muss mindestens 1 Stunde betragen)
     */
    private void validateTurnaroundTime() {
        int hours = turnaroundHoursSpinner.getValue();
        int minutes = turnaroundMinutesSpinner.getValue();
        int totalMinutes = (hours * 60) + minutes;

        // Mindestens 60 Minuten (1 Stunde)
        if (totalMinutes < 60) {
            // Wenn die Gesamtzeit zu niedrig ist, setze auf 1h 0min
            turnaroundHoursSpinner.getValueFactory().setValue(1);
            turnaroundMinutesSpinner.getValueFactory().setValue(0);
        }

        // Maximal 24h
        if (hours > 23) {
            turnaroundHoursSpinner.getValueFactory().setValue(23);
            turnaroundMinutesSpinner.getValueFactory().setValue(59);
        } else if (hours == 24 && minutes > 0) {
            turnaroundHoursSpinner.getValueFactory().setValue(24);
            turnaroundMinutesSpinner.getValueFactory().setValue(0);
        }
    }

    /**
     * Füllt alle Felder mit Daten aus einem existierenden Flug
     * (Fills all fields with data from an existing flight)
     *
     * @param flight Der zu bearbeitende Flug (The flight to be edited)
     */
    private void populateFields(Flight flight) {
        // Assume flight object and its core attributes are valid if not null
        if (flight == null || flight.getDepartureAirport() == null || flight.getArrivalAirport() == null) {
            logger.error("Versuch, Felder mit ungültigem Flugobjekt zu füllen: {}", flight);
            // Optionally show error and close dialog?
            return;
        }

        flightNumberField.setText(flight.getFlightNumber()); // Validated non-null in Flight

        // Flughäfen auswählen (Select airports)
        // Find the matching object in the ComboBox list to ensure correct selection
        final Long depAirportId = flight.getDepartureAirport().getId();
        departureAirportComboBox.getItems().stream()
                .filter(a -> a.getId().equals(depAirportId))
                .findFirst()
                .ifPresent(departureAirportComboBox::setValue);

        final Long arrAirportId = flight.getArrivalAirport().getId();
        arrivalAirportComboBox.getItems().stream()
                .filter(a -> a.getId().equals(arrAirportId))
                .findFirst()
                .ifPresent(arrivalAirportComboBox::setValue);

        // Flugzeug auswählen (Select aircraft)
        if (flight.getAircraft() != null) {
            try {
                List<Aircraft> allAircraft = aircraftService.getAllAircraft();
                final Long aircraftId = flight.getAircraft().getId();
                Optional<Aircraft> aircraftToSelect = allAircraft.stream()
                        .filter(a -> a.getId().equals(aircraftId))
                        .findFirst();

                if(aircraftToSelect.isPresent()) {
                    updateAvailableAircraft(); // Update list, potentially adding this aircraft
                    aircraftComboBox.setValue(aircraftToSelect.get()); // Set the value
                    // Flugzeugdetails aktualisieren
                    updateAircraftDetails(aircraftToSelect.get());
                } else {
                    logger.warn("Flugzeug mit ID {} für Flug {} nicht im Service gefunden.", aircraftId, flight.getFlightNumber());
                    updateAvailableAircraft(); // Update with standard available aircraft
                }
            } catch (Exception e) {
                logger.error("Fehler beim Laden der Flugzeugliste während populateFields.", e);
                ExceptionHandler.handleException(e, "beim Laden der Flugzeuge für die Bearbeitung");
                updateAvailableAircraft();
            }
        } else {
            updateAvailableAircraft(); // Ensure list is populated if flight had no aircraft
        }

        // Datum und Zeit setzen (Set date and time) - Assumed non-null from Flight constructor/DB
        departureDatePicker.setValue(flight.getDepartureDate());
        departureHourSpinner.getValueFactory().setValue(flight.getDepartureTime().getHour());
        departureMinuteSpinner.getValueFactory().setValue(flight.getDepartureTime().getMinute());

        // Preise setzen (Set prices) - Use Locale for consistency
        priceEconomyField.setText(String.format(Locale.US, "%.2f", flight.getPriceEconomy()));
        priceBusinessField.setText(String.format(Locale.US, "%.2f", flight.getPriceBusiness()));
        priceFirstField.setText(String.format(Locale.US, "%.2f", flight.getPriceFirst()));

        // Rückflug-Option deaktivieren (Disable return flight option)
        createReturnFlightCheck.setSelected(false);
        createReturnFlightCheck.setDisable(true);
        returnFlightInfoLabel.setVisible(false);

        // Initial validation after populating
        validateFlightNumber(flight.getFlightNumber());
        validateAircraftSelection();
        checkExistingRoute();
        updateArrivalTime(); // Calculate and display arrival time
    }

    /**
     * Generiert automatisch die nächste verfügbare Flugnummer
     * (Automatically generates the next available flight number)
     */
    private void generateNextFlightNumber() {
        try {
            String airlineCode = Airline.getInstance().getIcaoCode();
            // Airline code check removed - assuming Airline singleton is correctly initialized

            List<Flight> existingFlights = flightService.getAllFlights(); // Assumes non-null list

            Set<Integer> usedNumbers = new HashSet<>();
            for (Flight flight : existingFlights) {
                String flightNumber = flight.getFlightNumber(); // Assumed non-null from Flight model
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
            // Validation happens via listener

        } catch (Exception e) {
            // Catch potential errors from Airline.getInstance() or flightService
            logger.error("Fehler beim Generieren der nächsten Flugnummer.", e);
            ExceptionHandler.handleException(e, "beim Generieren der Flugnummer");
            flightNumberErrorLabel.setText("Fehler bei Flugnummer-Generierung!");
            flightNumberErrorLabel.setVisible(true);
            isFlightNumberValid.set(false);
        }
    }

    /**
     * Validiert die eingegebene Flugnummer
     * (Validates the entered flight number)
     *
     * @param flightNumber Die zu validierende Flugnummer (The flight number to validate)
     */
    private void validateFlightNumber(String flightNumber) {
        flightNumberErrorLabel.setVisible(false);
        isFlightNumberValid.set(false);

        // Keep check for empty input
        if (flightNumber == null || flightNumber.trim().isEmpty()) {
            flightNumberErrorLabel.setText("Flugnummer darf nicht leer sein!");
            flightNumberErrorLabel.setVisible(true);
            return;
        }

        String airlineCode = Airline.getInstance().getIcaoCode(); // Assume non-null/empty

        // Keep format checks
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

        // Keep uniqueness check
        try {
            List<Flight> existingFlights = flightService.getAllFlights(); // Assumes non-null list
            for (Flight existingFlight : existingFlights) {
                // Check if existingFlight or its ID is null before comparing
                if (currentFlight != null && existingFlight != null && existingFlight.getId() != null
                        && currentFlight.getId() != null && existingFlight.getId().equals(currentFlight.getId())) {
                    continue;
                }
                // Check if existingFlight flight number is null before comparing
                if (existingFlight != null && flightNumber.equals(existingFlight.getFlightNumber())) {
                    flightNumberErrorLabel.setText("Flugnummer wird bereits verwendet!");
                    flightNumberErrorLabel.setVisible(true);
                    return;
                }
            }
        } catch (Exception e) {
            logger.error("Fehler beim Prüfen der Flugnummer-Eindeutigkeit.", e);
            ExceptionHandler.handleException(e, "beim Prüfen der Flugnummer");
            flightNumberErrorLabel.setText("Fehler bei Eindeutigkeitsprüfung!");
            flightNumberErrorLabel.setVisible(true);
            return;
        }

        isFlightNumberValid.set(true);
    }

    /**
     * Aktualisiert die Anzeige der Flugzeugdetails basierend auf dem ausgewählten Flugzeug
     * (Updates the display of aircraft details based on the selected aircraft)
     *
     * @param aircraft Das ausgewählte Flugzeug (The selected aircraft)
     */
    private void updateAircraftDetails(Aircraft aircraft) {
        if (aircraft == null || aircraft.getType() == null) {
            // Keine Flugzeug ausgewählt oder kein Typ verfügbar - leere Details anzeigen
            aircraftPaxCapacityLabel.setText("Passagierkapazität: -");
            aircraftCargoCapacityLabel.setText("Frachtkapazität: -");
            aircraftRangeLabel.setText("Reichweite: -");
            aircraftSpeedLabel.setText("Geschwindigkeit: -");
            aircraftCostPerHourLabel.setText("Betriebskosten/h: -");
        } else {
            // Flugzeugdetails anzeigen
            AircraftType type = aircraft.getType();
            aircraftPaxCapacityLabel.setText(String.format("Passagierkapazität: %d Personen", type.getPaxCapacity()));
            aircraftCargoCapacityLabel.setText(String.format("Frachtkapazität: %.1f Tonnen", type.getCargoCapacity() / 1000)); // Von kg zu Tonnen umrechnen
            aircraftRangeLabel.setText(String.format("Reichweite: %.0f km", type.getMaxRangeKm()));
            aircraftSpeedLabel.setText(String.format("Geschwindigkeit: %.0f km/h", type.getSpeedKmh()));
            aircraftCostPerHourLabel.setText(String.format("Betriebskosten/h: %.2f €", type.getCostPerHour()));
        }
    }

    private void updateAvailableAircraft() {
        Airport departureAirport = departureAirportComboBox.getValue(); // Can be null
        Airport arrivalAirport = arrivalAirportComboBox.getValue();     // Can be null
        Aircraft previouslySelectedAircraft = aircraftComboBox.getValue();

        aircraftComboBox.setItems(FXCollections.observableArrayList());

        // Keep check: Need departure airport to filter
        if (departureAirport == null) {
            validateAircraftSelection();
            // Auch Flugzeugdetails leeren
            updateAircraftDetails(null);
            return;
        }

        List<Aircraft> availableAircraft = new ArrayList<>();
        try {
            List<Aircraft> allAircraft = aircraftService.getAllAircraft(); // Assumes non-null list

            // Filter: Available and at departure location
            // Assumes Aircraft elements and their status/location/id are non-null based on service/model
            availableAircraft = allAircraft.stream()
                    .filter(a -> a.getStatus() == AircraftStatus.AVAILABLE)
                    .filter(a -> a.getCurrentLocation().getId().equals(departureAirport.getId()))
                    .collect(Collectors.toList());

            // Filter by range if arrival airport is selected
            if (arrivalAirport != null) {
                double distance = calculateRouteDistance(departureAirport, arrivalAirport);
                if (distance > 0) {
                    // Assumes type and range are valid based on model
                    availableAircraft = availableAircraft.stream()
                            .filter(a -> a.getType().getMaxRangeKm() >= distance * 1.1)
                            .collect(Collectors.toList());
                }
            }

            // Add current aircraft if editing
            if (currentFlight != null && currentFlight.getAircraft() != null) {
                final long currentFlightAircraftId = currentFlight.getAircraft().getId();
                boolean alreadyInList = availableAircraft.stream().anyMatch(a -> a.getId().equals(currentFlightAircraftId));
                if (!alreadyInList) {
                    // Find and add the original aircraft from the full list
                    allAircraft.stream()
                            .filter(a -> a.getId().equals(currentFlightAircraftId))
                            .findFirst()
                            .ifPresent(availableAircraft::add);
                }
            }

            // Sort (Assumes registrationNo is non-null)
            availableAircraft.sort(Comparator.comparing(Aircraft::getRegistrationNo));

            // Wenn keine Flugzeuge am Abflughafen verfügbar sind, zeige Warnung
            if (availableAircraft.isEmpty()) {
                aircraftErrorLabel.setText("Keine Flugzeuge am Abflughafen " + departureAirport.getIcaoCode() + " verfügbar!");
                aircraftErrorLabel.setVisible(true);
            } else {
                aircraftErrorLabel.setVisible(false);
            }

        } catch (Exception e) {
            logger.error("Fehler beim Aktualisieren der Flugzeugliste.", e);
            ExceptionHandler.handleException(e, "beim Aktualisieren der Flugzeugliste");
        }

        // Set items and restore selection
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
            aircraftComboBox.setValue(null); // Clear if selection is no longer valid
        }

        validateAircraftSelection();
        // Flugzeugdetails für das aktuelle Flugzeug aktualisieren
        updateAircraftDetails(aircraftComboBox.getValue());
    }


    /**
     * Validiert das ausgewählte Flugzeug (Verfügbarkeit, Reichweite)
     * (Validates the selected aircraft (availability, range))
     */
    private void validateAircraftSelection() {
        Aircraft aircraft = aircraftComboBox.getValue(); // Can be null
        Airport departureAirport = departureAirportComboBox.getValue(); // Can be null
        Airport arrivalAirport = arrivalAirportComboBox.getValue();     // Can be null

        aircraftErrorLabel.setVisible(false);
        isAircraftValid.set(false);

        // Keep check: Must select an aircraft
        if (aircraft == null) {
            return;
        }

        // Assume aircraft attributes (type, location, status, id) are non-null based on model/service guarantees
        // if loaded correctly.

        boolean isEditingThisAircraft = currentFlight != null && currentFlight.getAircraft() != null && aircraft.getId().equals(currentFlight.getAircraft().getId());

        // Check status (unless editing this specific one)
        if (aircraft.getStatus() != AircraftStatus.AVAILABLE && !isEditingThisAircraft) {
            aircraftErrorLabel.setText("Flugzeug ist nicht verfügbar!");
            aircraftErrorLabel.setVisible(true);
            return;
        }

        // Check location (unless editing this specific one)
        // Keep check for departureAirport null
        if (departureAirport != null && !aircraft.getCurrentLocation().getId().equals(departureAirport.getId()) && !isEditingThisAircraft) {
            aircraftErrorLabel.setText("Flugzeug befindet sich nicht am Abflughafen!");
            aircraftErrorLabel.setVisible(true);
            return;
        }

        // Check range
        // Keep checks for departure/arrival airport null
        if (departureAirport != null && arrivalAirport != null) {
            double distance = calculateRouteDistance(departureAirport, arrivalAirport);
            if (distance > 0) {
                double range = aircraft.getType().getMaxRangeKm(); // Assumes type non-null
                if (range < distance * 1.1) {
                    aircraftErrorLabel.setText("Unzureichende Reichweite! " +
                            String.format("%.0f km benötigt, %.0f km verfügbar", distance * 1.1, range));
                    aircraftErrorLabel.setVisible(true);
                    return;
                }
            }
        }

        isAircraftValid.set(true);
    }

    /**
     * Prüft, ob für die gewählte Flughafen-Kombination bereits eine Route existiert
     * (Checks if a route already exists for the selected airport combination)
     */
    private void checkExistingRoute() {
        Airport departure = departureAirportComboBox.getValue(); // Can be null
        Airport arrival = arrivalAirportComboBox.getValue();     // Can be null

        routeInfoLabel.setVisible(false);
        routeDistanceLabel.setText("");
        routeTimeLabel.setText("");
        isRouteValid.set(false);

        // Keep checks: Need both airports
        if (departure == null || arrival == null) {
            return;
        }

        // Keep check: Airports must be different (handled by handleAirportChange, but good safety check)
        if (departure.equals(arrival)) {
            routeInfoLabel.setText("Start- und Zielflughafen müssen unterschiedlich sein!");
            routeInfoLabel.setTextFill(Color.RED);
            routeInfoLabel.setVisible(true);
            return;
        }

        Route existingRoute;
        try {
            // Service method can return null
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
            distance = existingRoute.getDistanceKm(); // Primitive, validated > 0 in Route
        } else {
            routeInfoLabel.setText("Neue Route wird bei Speicherung erstellt.");
            routeInfoLabel.setTextFill(Color.BLUE);

            distance = calculateRouteDistance(departure, arrival);
            if (distance <= 0) {
                routeDistanceLabel.setText("Distanz: Fehler");
                routeTimeLabel.setText("Flugzeit: Wird nach Flugzeugauswahl berechnet");
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
            routeTimeLabel.setText("Flugzeit: Wird nach Flugzeugauswahl berechnet");
        }

        routeInfoLabel.setVisible(true);
        isRouteValid.set(true);
    }


    /**
     * Berechnet die Distanz zwischen zwei Flughäfen
     * (Calculates the distance between two airports)
     */
    private double calculateRouteDistance(Airport departure, Airport arrival) {
        // Keep checks for null airports and null lat/lon
        if (departure == null || arrival == null ||
                departure.getLatitude() == 0.0 || departure.getLongitude() == 0.0 || // Primitives can't be null, check for default/unset value
                arrival.getLatitude() == 0.0 || arrival.getLongitude() == 0.0) {
            // Latitude/Longitude are primitive doubles, validated in Airport, should not be 0.0 unless default/unset.
            // Log if this happens, but maybe allow calculation? Or return 0.0 as before.
            logger.warn("Ungültige Koordinaten für Distanzberechnung: Dep ({}, {}), Arr ({}, {})",
                    departure != null ? departure.getLatitude() : "null", departure != null ? departure.getLongitude() : "null",
                    arrival != null ? arrival.getLatitude() : "null", arrival != null ? arrival.getLongitude() : "null");
            return 0.0; // Return 0.0 if coordinates seem invalid
        }
        // Assume FlightUtils handles potential calculation errors
        return FlightUtils.calculateDistance(
                departure.getLatitude(), departure.getLongitude(),
                arrival.getLatitude(), arrival.getLongitude()
        );
    }

    /**
     * Berechnet die Flugzeit basierend auf Distanz und Geschwindigkeit
     * (Calculates flight time based on distance and speed)
     */
    private int calculateFlightTime(double distance, double speedKmh) {
        // Keep checks for invalid inputs
        if (speedKmh <= 0 || distance <= 0) {
            return 0;
        }
        // Assume FlightUtils handles potential calculation errors
        return FlightUtils.calculateFlightTime(distance, speedKmh);
    }

    /**
     * Aktualisiert die Ankunftszeit basierend auf Abflugzeit und Flugzeit
     * (Updates the arrival time based on departure time and flight time)
     */
    private void updateArrivalTime() {
        arrivalDateTimeLabel.setText("Wird berechnet...");
        isDepartureDateTimeValid.set(false);

        // Keep checks: Need valid route, departure date, and time values
        if (!isRouteValid.get()) {
            arrivalDateTimeLabel.setText("Unvollständige Route");
            return;
        }
        Airport departure = departureAirportComboBox.getValue(); // Should be non-null if route is valid
        Airport arrival = arrivalAirportComboBox.getValue();     // Should be non-null if route is valid
        LocalDate departureDate = departureDatePicker.getValue(); // Can be null
        Integer hourVal = departureHourSpinner.getValue();       // Should not be null
        Integer minuteVal = departureMinuteSpinner.getValue();   // Should not be null
        Aircraft selectedAircraft = aircraftComboBox.getValue(); // Kann null sein

        if (departureDate == null || hourVal == null || minuteVal == null || departure == null || arrival == null || selectedAircraft == null) {
            arrivalDateTimeLabel.setText("Unvollständige Eingabe");
            return;
        }
        LocalTime departureTime = LocalTime.of(hourVal, minuteVal);

        // Berechnung der Flugzeit basierend auf dem gewählten Flugzeug und der Distanz
        double distance = calculateRouteDistance(departure, arrival);
        if (distance <= 0) {
            arrivalDateTimeLabel.setText("Distanzberechnung fehlgeschlagen");
            return;
        }

        // Flugzeit mit der Geschwindigkeit des gewählten Flugzeugs berechnen
        int flightTimeMinutes = calculateFlightTime(distance, selectedAircraft.getType().getSpeedKmh());

        // Keep check: Flight time must be valid
        if (flightTimeMinutes <= 0) {
            arrivalDateTimeLabel.setText("Flugzeit ungültig");
            return;
        }

        // Ankunftszeit berechnen (Calculate arrival time)
        try {
            // Assume TimeUtils handles internal logic correctly
            Object[] arrivalDateTime = FlightUtils.calculateArrivalTime(departureDate, departureTime, flightTimeMinutes);

            // Keep check on result format
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
            // No need to call ExceptionHandler here, just show error in label
        }
    }

    /**
     * Aktualisiert die Informationen zum automatisch erstellten Rückflug
     * (Updates the information about the automatically created return flight)
     */
    private void updateReturnFlightInfo() {
        returnFlightInfoLabel.setVisible(false);

        // Keep checks: Checkbox status and validity of base flight data
        if (!createReturnFlightCheck.isSelected() || createReturnFlightCheck.isDisabled() ||
                !isFlightNumberValid.get() || !isRouteValid.get() || !isDepartureDateTimeValid.get()) {
            return;
        }

        Airport departure = departureAirportComboBox.getValue(); // Non-null if route valid
        Airport arrival = arrivalAirportComboBox.getValue();     // Non-null if route valid
        String flightNumber = flightNumberField.getText();       // Non-null/empty if valid
        String airlineCode = Airline.getInstance().getIcaoCode(); // Assumed non-null

        try {
            String numericPart = flightNumber.substring(airlineCode.length());
            int number = Integer.parseInt(numericPart);
            String returnFlightNumber = airlineCode + (number + 1);

            boolean returnNumberTaken;
            try {
                // Keep check for existing return flight number
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
                // Assumes departure/arrival ICAO codes are non-null
                int turnaroundHours = turnaroundHoursSpinner.getValue();
                int turnaroundMinutes = turnaroundMinutesSpinner.getValue();
                String turnaroundText = (turnaroundMinutes == 0)
                    ? String.format("%dh Turnaround", turnaroundHours)
                    : String.format("%dh %dmin Turnaround", turnaroundHours, turnaroundMinutes);

                // Formatierung mit Zeilenumbruch bei längeren Texten
                String formattedText = String.format(
                        "Rückflug %s von %s nach %s\nwird erstellt (%s)",
                        returnFlightNumber, arrival.getIcaoCode(), departure.getIcaoCode(), turnaroundText);

                returnFlightInfoLabel.setText(formattedText);
                returnFlightInfoLabel.setTextFill(Color.GREEN);
            }
            returnFlightInfoLabel.setVisible(true);

        } catch (NumberFormatException | IndexOutOfBoundsException | NullPointerException e) {
            // Should not happen if flightNumber is valid, but catch anyway
            logger.error("Fehler beim Verarbeiten der Flugnummer '{}' für Rückfluginfo.", flightNumber, e);
            returnFlightInfoLabel.setText("Fehler bei Verarbeitung der Flugnummer.");
            returnFlightInfoLabel.setTextFill(Color.RED);
            returnFlightInfoLabel.setVisible(true);
        }
    }

    /**
     * Aktualisiert die Preisfelder basierend auf Route, Flugzeug usw.
     * (Updates the price fields based on route, aircraft, etc.)
     */
    private void updatePrices() {
        priceEconomyField.setText("");
        priceBusinessField.setText("");
        priceFirstField.setText("");

        // Keep checks: Need valid route and aircraft selection
        if (!isRouteValid.get() || !isAircraftValid.get()) {
            return;
        }

        Flight tempFlight = collectInputDataForPricing(); // Gets current selections

        // Selections are guaranteed non-null if validation flags are true
        Airport departure = tempFlight.getDepartureAirport();
        Airport arrival = tempFlight.getArrivalAirport();
        Aircraft aircraft = tempFlight.getAircraft();

        double distance;
        int flightTimeMinutes;

        try {
            // Service method can return null
            Route existingRoute = routeService.findRouteByAirportsAndAirline(departure, arrival, Airline.getInstance());
            if (existingRoute != null) {
                distance = existingRoute.getDistanceKm();
                flightTimeMinutes = existingRoute.getFlightTimeMinutes();
            } else {
                distance = calculateRouteDistance(departure, arrival);
                // Assumes aircraft type non-null
                flightTimeMinutes = calculateFlightTime(distance, aircraft.getType().getSpeedKmh());
            }
        } catch (Exception e) {
            logger.error("Fehler beim Ermitteln der Routendaten für Preisberechnung.", e);
            // Don't call ExceptionHandler, just log and abort price update
            return;
        }

        // Keep checks for valid distance/time
        if (distance < 0 || flightTimeMinutes < 0) {
            logger.warn("Ungültige Distanz ({}) oder Flugzeit ({}) für Preisberechnung.", distance, flightTimeMinutes);
            return;
        }

        tempFlight.setDistanceKm(distance);
        tempFlight.setFlightTimeMinutes(flightTimeMinutes);

        // Preise berechnen (Calculate prices)
        calculatePrices(tempFlight); // Internal checks handle nulls/invalid values

        // In UI übertragen (Transfer to UI)
        priceEconomyField.setText(String.format(Locale.US, "%.2f", tempFlight.getPriceEconomy()));
        priceBusinessField.setText(String.format(Locale.US, "%.2f", tempFlight.getPriceBusiness()));
        priceFirstField.setText(String.format(Locale.US, "%.2f", tempFlight.getPriceFirst()));
    }

    /**
     * Sammelt aktuelle Eingabedaten für die Preisberechnung
     * (Collects current input data for price calculation)
     */
    private Flight collectInputDataForPricing() {
        Flight tempFlight = new Flight();
        // Values can be null if not selected, but updatePrices checks validation flags first
        tempFlight.setDepartureAirport(departureAirportComboBox.getValue());
        tempFlight.setArrivalAirport(arrivalAirportComboBox.getValue());
        tempFlight.setAircraft(aircraftComboBox.getValue());
        return tempFlight;
    }

    /**
     * Berechnet Preise für einen Flug nach der Preisformel
     * (Calculates prices for a flight according to the price formula)
     */
    private void calculatePrices(Flight flight) {
        // Keep checks for required data
        if (flight == null || flight.getAircraft() == null || flight.getAircraft().getType() == null ||
                flight.getDepartureAirport() == null || flight.getArrivalAirport() == null ||
                flight.getDistanceKm() < 0 || flight.getFlightTimeMinutes() < 0) {
            logger.warn("Kann Preise nicht berechnen - fehlende Daten.");
            if (flight != null) { // Set prices to 0 if calculation fails
                flight.setPriceEconomy(0.0);
                flight.setPriceBusiness(0.0);
                flight.setPriceFirst(0.0);
            }
            return;
        }

        double distance = flight.getDistanceKm();
        double flightHours = flight.getFlightTimeMinutes() / 60.0;
        AircraftType type = flight.getAircraft().getType(); // Assumed non-null from check above
        int paxCapacity = type.getPaxCapacity(); // Assumed > 0 from model validation
        double costPerHour = type.getCostPerHour(); // Assumed >= 0 from model validation

        // Keep check for valid pax capacity
        if (paxCapacity <= 0) {
            logger.warn("Ungültige Pax-Kapazität ({}) für Typ {}", paxCapacity, type.getFullName());
            flight.setPriceEconomy(0.0);
            flight.setPriceBusiness(0.0);
            flight.setPriceFirst(0.0);
            return;
        }

        boolean isDomestic = isSameCountry(flight.getDepartureAirport(), flight.getArrivalAirport()); // Handles internal nulls
        double airportFee = isDomestic ? DOMESTIC_FEE : INTERNATIONAL_FEE;

        double basePrice = (BASE_PRICE_PER_KM * distance) +
                (flightHours * (costPerHour / paxCapacity)) +
                airportFee;

        // Keep check for negative base price
        if (basePrice < 0) {
            logger.warn("Negativer Basispreis ({}) berechnet für Flug {}. Setze auf 0.", basePrice, flight.getFlightNumber());
            basePrice = 0.0;
        }

        flight.setPriceEconomy(Math.round(basePrice * ECONOMY_FACTOR * 100.0) / 100.0);
        flight.setPriceBusiness(Math.round(basePrice * BUSINESS_FACTOR * 100.0) / 100.0);
        flight.setPriceFirst(Math.round(basePrice * FIRST_CLASS_FACTOR * 100.0) / 100.0);
    }

    /**
     * Prüft, ob zwei Flughäfen im selben Land liegen
     * (Checks if two airports are in the same country)
     */
    private boolean isSameCountry(Airport airport1, Airport airport2) {
        // Keep checks for null airports or countries
        if (airport1 == null || airport2 == null ||
                airport1.getCountry() == null || airport2.getCountry() == null) {
            logger.warn("Kann Land nicht bestimmen für: {} / {}",
                    airport1 != null ? airport1.getIcaoCode() : "null",
                    airport2 != null ? airport2.getIcaoCode() : "null");
            return false; // Assume international if country unknown
        }
        // Country assumed non-null/non-empty from model
        return airport1.getCountry().equalsIgnoreCase(airport2.getCountry());
    }

    /**
     * Erstellt einen Rückflug basierend auf dem Hinflug
     * (Creates a return flight based on the outbound flight)
     */
    private Flight createReturnFlight(Flight outboundFlight) {
        // Keep checks for essential outbound flight data
        if (outboundFlight == null || outboundFlight.getDepartureAirport() == null ||
                outboundFlight.getArrivalAirport() == null || outboundFlight.getAircraft() == null ||
                outboundFlight.getArrivalDateTime() == null || outboundFlight.getFlightNumber() == null ||
                outboundFlight.getRoute() == null) {
            logger.error("Kann Rückflug nicht erstellen - unvollständige Hinflugdaten: {}", outboundFlight);
            return null;
        }

        Flight returnFlight = new Flight();

        // Reverse airports (assumed non-null from check above)
        returnFlight.setDepartureAirport(outboundFlight.getArrivalAirport());
        returnFlight.setArrivalAirport(outboundFlight.getDepartureAirport());

        // Generate return flight number
        String outboundNumber = outboundFlight.getFlightNumber(); // Assumed non-null
        String airlineCode = Airline.getInstance().getIcaoCode(); // Assumed non-null
        String returnFlightNumber;

        // Keep check for airline code format
        if (outboundNumber.startsWith(airlineCode)) {
            try {
                String numericPart = outboundNumber.substring(airlineCode.length());
                int number = Integer.parseInt(numericPart);
                returnFlightNumber = airlineCode + (number + 1);

                // Keep check for existing number
                if (flightService.getAllFlights().stream().anyMatch(f -> returnFlightNumber.equals(f.getFlightNumber()))) {
                    logger.warn("Rückflugnummer {} ist bereits vergeben.", returnFlightNumber);
                    return null;
                }
                returnFlight.setFlightNumber(returnFlightNumber);

            } catch (NumberFormatException | IndexOutOfBoundsException | NullPointerException e) { // Added NPE catch
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

        // Set aircraft (assumed non-null)
        returnFlight.setAircraft(outboundFlight.getAircraft());

        // Find or create return route
        Route returnRoute = null;
        try {
            // Service method can return null
            returnRoute = routeService.findRouteByAirportsAndAirline(
                    returnFlight.getDepartureAirport(), returnFlight.getArrivalAirport(), Airline.getInstance());
        } catch (Exception e) {
            logger.error("Fehler beim Suchen der Rückflugroute für {}", returnFlightNumber, e);
            // Proceed to create new route
        }

        // Keep check if route needs creation
        if (returnRoute == null) {
            logger.info("Erstelle neue Route für Rückflug {}", returnFlightNumber);
            returnRoute = new Route();
            // Airports assumed non-null
            returnRoute.setDepartureAirport(returnFlight.getDepartureAirport());
            returnRoute.setArrivalAirport(returnFlight.getArrivalAirport());
            returnRoute.setOperator(Airline.getInstance()); // Assumed non-null

            double distance = calculateRouteDistance(returnRoute.getDepartureAirport(), returnRoute.getArrivalAirport());
            returnRoute.setDistanceKm(distance); // Validated >= 0 in Route

            // Aircraft and type assumed non-null
            double speed = returnFlight.getAircraft().getType().getSpeedKmh(); // Validated > 0 in Type
            int flightTime = calculateFlightTime(distance, speed);
            returnRoute.setFlightTimeMinutes(flightTime); // Validated > 0 in Route

            try {
                boolean routeSaved = routeService.saveRoute(returnRoute); // Returns boolean
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
            // Assumes TimeUtils handles internal logic
            Object[] returnArrivalDateTime = FlightUtils.calculateArrivalTime(
                    returnFlight.getDepartureDate(), returnFlight.getDepartureTime(), returnFlight.getFlightTimeMinutes());

            // Keep check on result format
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
        calculatePrices(returnFlight); // Handles internal checks

        // Set status
        returnFlight.setStatus(FlightStatus.SCHEDULED);

        logger.info("Rückflugobjekt erfolgreich vorbereitet: {}", returnFlight.getFlightNumber());
        return returnFlight;
    }


    /**
     * Sammelt alle Eingabedaten und erstellt oder aktualisiert ein Flight-Objekt
     * (Collects all input data and creates or updates a Flight object)
     *
     * @return Flight-Objekt mit allen Eingabedaten (Flight object with all input data), or null if data is invalid
     */
    private Flight collectInputData() {
        // Keep basic validation checks
        if (!isFlightNumberValid.get() || !isAircraftValid.get() || !isRouteValid.get() || !isDepartureDateTimeValid.get()) {
            logger.warn("Kann Eingabedaten nicht sammeln - Validierungsfehler.");
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

        // Collect data - UI elements are guaranteed non-null if validation passed
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
            // Service method can return null
            route = routeService.findRouteByAirportsAndAirline(
                    flight.getDepartureAirport(), flight.getArrivalAirport(), Airline.getInstance());
        } catch (Exception e) {
            logger.error("Fehler beim Suchen der Route während Datensammlung.", e);
            ExceptionHandler.handleException(e, "beim Suchen der Route");
            return null;
        }

        // Keep check if route needs creation
        if (route == null) {
            logger.info("Route nicht gefunden für {} -> {}. Erstelle neue Route.",
                    flight.getDepartureAirport().getIcaoCode(), flight.getArrivalAirport().getIcaoCode());
            route = new Route();
            // Airports/Aircraft assumed non-null from validation checks
            route.setDepartureAirport(flight.getDepartureAirport());
            route.setArrivalAirport(flight.getArrivalAirport());
            route.setOperator(Airline.getInstance()); // Assumed non-null

            double distance = calculateRouteDistance(route.getDepartureAirport(), route.getArrivalAirport());
            route.setDistanceKm(distance); // Validated >= 0

            // Aircraft type assumed non-null
            double speed = flight.getAircraft().getType().getSpeedKmh(); // Validated > 0
            int flightTime = calculateFlightTime(distance, speed);
            route.setFlightTimeMinutes(flightTime); // Validated > 0

            try {
                boolean routeSaved = routeService.saveRoute(route); // Returns boolean
                if (!routeSaved) {
                    logger.error("Speichern der neuen Route fehlgeschlagen während Datensammlung.");
                    ExceptionHandler.handleException(new RuntimeException("RouteService.saveRoute returned false"), "beim Speichern der neuen Route");
                    return null;
                }
                logger.info("Neue Route gespeichert (ID: {})", route.getId());
            } catch (Exception e) {
                logger.error("Exception beim Speichern der neuen Route während Datensammlung.", e);
                ExceptionHandler.handleException(e, "beim Speichern der neuen Route");
                return null;
            }
        }
        flight.setRoute(route); // Route is now guaranteed non-null
        flight.setDistanceKm(route.getDistanceKm());
        flight.setFlightTimeMinutes(route.getFlightTimeMinutes());

        // Price Handling - Keep try-catch for parsing
        try {
            flight.setPriceEconomy(Double.parseDouble(priceEconomyField.getText().replace(',', '.')));
            flight.setPriceBusiness(Double.parseDouble(priceBusinessField.getText().replace(',', '.')));
            flight.setPriceFirst(Double.parseDouble(priceFirstField.getText().replace(',', '.')));
        } catch (NumberFormatException | NullPointerException e) {
            logger.error("Fehler beim Parsen der Preise. Neuberechnung.", e);
            calculatePrices(flight); // Recalculate if parsing fails
            if (flight.getPriceEconomy() < 0 || flight.getPriceBusiness() < 0 || flight.getPriceFirst() < 0) {
                logger.error("Preisberechnung fehlgeschlagen (negative Preise).");
                return null;
            }
        }

        // Arrival Time Recalculation - Keep try-catch
        try {
            // Assumes TimeUtils handles internal logic
            Object[] arrivalDateTime = FlightUtils.calculateArrivalTime(
                    flight.getDepartureDate(), flight.getDepartureTime(), flight.getFlightTimeMinutes());
            // Keep check on result format
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
     * Speichert den Flug und erstellt optional einen Rückflug
     * (Saves the flight and optionally creates a return flight)
     *
     * @param saveCallback Callback nach erfolgreichem Speichern (Callback after successful save)
     * @return Der gespeicherte Flug (The saved flight), or null if saving failed
     */
    private Flight saveFlight(SaveCallback saveCallback) {
        Flight flightToSave = collectInputData(); // Handles internal validation

        // Keep check: collectInputData returns null on validation failure
        if (flightToSave == null) {
            logger.error("Flugdaten ungültig. Speichern abgebrochen.");
            // Show error only if collectInputData didn't already
            // Alert alert = new Alert(Alert.AlertType.ERROR); ...
            return null;
        }

        try {
            // --- Transaction Start (Conceptual) ---

            boolean outboundSaved = flightService.saveFlight(flightToSave); // Returns boolean

            // Keep check on save result
            if (!outboundSaved) {
                logger.error("Speichern des Hinflugs {} fehlgeschlagen (Service-Fehler).", flightToSave.getFlightNumber());
                // ExceptionHandler called by service? If not, call here.
                // ExceptionHandler.handleException(new RuntimeException("FlightService.saveFlight returned false"), "beim Speichern des Hinflugs");
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Speichern fehlgeschlagen");
                alert.setHeaderText("Hinflug konnte nicht gespeichert werden.");
                alert.setContentText("Der Flugdaten-Service meldete einen Fehler.");
                alert.showAndWait();
                return null;
            }

            // Use the object that was passed to the service (assuming it's updated by reference or reloaded by service)
            Flight successfullySavedOutboundFlight = flightToSave;
            logger.info("Hinflug gespeichert: {} (ID: {})", successfullySavedOutboundFlight.getFlightNumber(), successfullySavedOutboundFlight.getId());

            // Erstelle immer die Rückflugroute, wenn es ein neuer Flug ist, unabhängig vom Checkbox-Status
            if (currentFlight == null) {
                // Rückflugroute erstellen
                createReturnRoute(successfullySavedOutboundFlight.getDepartureAirport(),
                                 successfullySavedOutboundFlight.getArrivalAirport());

                // Optional: Create return flight - nur wenn die Checkbox angewählt ist
                if (createReturnFlightCheck.isSelected()) {
                    logger.info("Versuche, Rückflug für {} zu erstellen.", successfullySavedOutboundFlight.getFlightNumber());
                    Flight returnFlight = createReturnFlight(successfullySavedOutboundFlight); // Handles internal checks

                    // Keep check: Return flight creation might fail
                    if (returnFlight != null) {
                        try {
                            boolean returnSaved = flightService.saveFlight(returnFlight); // Returns boolean
                            // Keep check on save result
                            if (!returnSaved) {
                                logger.error("Speichern des Rückflugs {} fehlgeschlagen (Service-Fehler).", returnFlight.getFlightNumber());
                                // ExceptionHandler called by service?
                                Alert alert = new Alert(Alert.AlertType.WARNING);
                                alert.setTitle("Rückflugfehler");
                                alert.setHeaderText("Hinflug gespeichert, aber Rückflug konnte nicht gespeichert werden.");
                                alert.setContentText("Service-Fehler beim Speichern des Rückflugs.");
                                alert.showAndWait();
                            } else {
                                logger.info("Rückflug gespeichert: {} (ID: {})", returnFlight.getFlightNumber(), returnFlight.getId());
                            }
                        } catch (Exception returnSaveEx) {
                            logger.error("Exception beim Speichern des Rückflugs {}: {}", returnFlight.getFlightNumber(), returnSaveEx.getMessage(), returnSaveEx);
                            ExceptionHandler.handleException(returnSaveEx, "beim Speichern des Rückflugs");
                            // Alert already shown by ExceptionHandler? If not, show here.
                        }
                    } else {
                        logger.warn("Erstellung des Rückflugs für {} fehlgeschlagen.", successfullySavedOutboundFlight.getFlightNumber());
                        // Show info that return flight creation failed
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Rückflug Info");
                        alert.setHeaderText("Hinflug gespeichert.");
                        alert.setContentText("Automatischer Rückflug konnte nicht erstellt werden (z.B. Nummer vergeben).");
                        alert.showAndWait();
                    }
                }
            }

            // --- Transaction End (Conceptual) ---

            // Callback (assume saveCallback is non-null or checked before calling)
            if (saveCallback != null) {
                saveCallback.onSaved(successfullySavedOutboundFlight);
            }

            return successfullySavedOutboundFlight;

        } catch (Exception e) {
            // Catch unexpected errors during the save process
            logger.error("Unerwarteter Fehler beim Speichern des Flugs {}: {}", flightToSave.getFlightNumber(), e.getMessage(), e);
            ExceptionHandler.handleException(e, "beim Speichern des Flugs " + flightToSave.getFlightNumber());
            // Alert shown by ExceptionHandler?
            return null;
        }
    }

    /**
     * Erstellt eine Route vom Zielflughafen zum Abflughafen (Rückflugroute)
     * Wird immer bei Erstellung eines neuen Fluges aufgerufen, unabhängig davon, ob ein Rückflug erstellt wird.
     *
     * @param originAirport Ursprünglicher Abflughafen (wird zum Ziel der Rückflugroute)
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
            int flightTime = calculateFlightTime(distance, 800.0); // 800 km/h Standardgeschwindigkeit
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
}
