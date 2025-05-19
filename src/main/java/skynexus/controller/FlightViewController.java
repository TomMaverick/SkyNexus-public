package skynexus.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import org.controlsfx.control.CheckComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.enums.FlightStatus;
import skynexus.util.ExceptionHandler;
import skynexus.util.TimeUtils;
import skynexus.model.Aircraft;
import skynexus.model.Airport;
import skynexus.model.Flight;
import skynexus.service.AirportService;
import skynexus.service.FlightService;
import skynexus.service.PassengerService;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

public class FlightViewController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(FlightViewController.class);

    // Services
    private final FlightService flightService = FlightService.getInstance();
    private final PassengerService passengerService = PassengerService.getInstance();
    private final AirportService airportService = AirportService.getInstance();
    // DateFormatter
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    // UI-Elemente
    @FXML
    private TableView<Flight> flightTable;
    @FXML
    private TableColumn<Flight, String> numberColumn;
    @FXML
    private TableColumn<Flight, String> departureColumn;
    @FXML
    private TableColumn<Flight, String> departureDateColumn;
    @FXML
    private TableColumn<Flight, String> departureTimeColumn;
    @FXML
    private TableColumn<Flight, Void> arrowColumn;
    @FXML
    private TableColumn<Flight, String> arrivalColumn;
    @FXML
    private TableColumn<Flight, String> arrivalDateColumn;
    @FXML
    private TableColumn<Flight, String> arrivalTimeColumn;
    @FXML
    private TableColumn<Flight, String> aircraftColumn;
    @FXML
    private TableColumn<Flight, String> statusColumn;
    @FXML
    private TableColumn<Flight, String> paxColumn;
    @FXML
    private TextField searchField;
    @FXML
    private DatePicker dateFilter;
    private CheckComboBox<FlightStatus> statusFilterCheck;
    @FXML
    private Label statusLabel;
    @FXML
    private Label totalLabel;
    @FXML
    private HBox statusFilterContainer;

    // ResourceBundle für I18n
    // private ResourceBundle resources;

    // Daten
    private final ObservableList<Flight> flights = FXCollections.observableArrayList();
    private FilteredList<Flight> filteredFlights;

    // Cache für Passagierzahlen und Flughafeninformationen
    private Map<Long, Integer> passengerCounts;
    private final Map<Long, Airport> airportCache = new java.util.concurrent.ConcurrentHashMap<>();

    // Aktuell ausgewählter Flug
    private Flight currentFlight;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // I18n-ResourceBundle initialisieren, falls benötigt
        // this.resources = resources;

        logger.info("Initialisiere Flight-View");

        // CheckComboBox initialisieren:
        statusFilterCheck = new CheckComboBox<>(FXCollections.observableArrayList(FlightStatus.values()));
        statusFilterCheck.setTitle("Filter");

        // Standardauswahl setzen:
        statusFilterCheck.getCheckModel().check(FlightStatus.SCHEDULED);
        statusFilterCheck.getCheckModel().check(FlightStatus.BOARDING);
        statusFilterCheck.getCheckModel().check(FlightStatus.DEPARTED);
        statusFilterCheck.getCheckModel().check(FlightStatus.FLYING);
        statusFilterCheck.getCheckModel().check(FlightStatus.LANDED);
        statusFilterCheck.getCheckModel().check(FlightStatus.DEPLANING);

        // Listener für Filteränderung:
        statusFilterCheck.getCheckModel().getCheckedItems().addListener((ListChangeListener<FlightStatus>) change -> updateFilters());

        // In Layout einfügen:
        statusFilterContainer.getChildren().add(statusFilterCheck);

        // Filter einrichten
        setupFilters();

        // Listener für Tabellen-Auswahl
        flightTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                showFlightDetails(newVal);
            }
        });

        // Daten laden
        loadDataAsync();
    }

    /**
     * Lädt die Daten asynchron, um die UI nicht zu blockieren.
     * Optimiert durch parallele Ausführung und verbesserte Fehlerbehandlung.
     */
    private void loadDataAsync() {
        statusLabel.setText("Daten werden geladen...");
        flightTable.setDisable(true);
        long startTime = System.currentTimeMillis();

        // Starte beide Datenbankabfragen parallel
        CompletableFuture<Map<Long, Integer>> passengerCountsFuture = CompletableFuture.supplyAsync(() -> {
            logger.debug("Lade Passagierzahlen für alle Flüge...");
            return passengerService.getPassengerCountsForAllFlights();
        });

        CompletableFuture<List<Flight>> flightsFuture = CompletableFuture.supplyAsync(() -> {
            logger.debug("Lade alle Flüge mit optimierter JOIN-Abfrage...");
            return flightService.getAllFlights();
        });

        // Warte auf beide Abfragen und verarbeite die Ergebnisse
        CompletableFuture.allOf(passengerCountsFuture, flightsFuture)
                .thenAccept(v -> {
                    try {
                        this.passengerCounts = passengerCountsFuture.get();
                        List<Flight> allFlights = flightsFuture.get();
                        long duration = System.currentTimeMillis() - startTime;

                        Platform.runLater(() -> {
                            try {
                                // Daten in die UI laden
                                flights.clear();
                                flights.addAll(allFlights);

                                // Tabelle konfigurieren
                                setupTableColumns();
                                updateFilters();

                                // UI freigeben
                                flightTable.setDisable(false);
                                statusLabel.setText("Daten erfolgreich geladen (" + TimeUtils.formatLoadingTime(duration) + ")");

                                logger.info("{} Flüge und Passagierzahlen für {} Flüge geladen in {} ms",
                                        allFlights.size(), passengerCounts.size(), duration);
                            } catch (Exception ex) {
                                logger.error("Fehler bei der UI-Aktualisierung: {}", ex.getMessage());
                                statusLabel.setText("Fehler bei der Darstellung der Daten");
                            }
                        });
                    } catch (Exception e) {
                        logger.error("Fehler beim Laden der Daten: {}", e.getMessage(), e);
                        Platform.runLater(() -> {
                            flightTable.setDisable(false);
                            statusLabel.setText("Fehler beim Laden der Daten");
                            ExceptionHandler.handleException(e, "beim Laden der Flugdaten");
                        });
                    }
                })
                .exceptionally(e -> {
                    logger.error("Schwerwiegender Fehler beim asynchronen Laden: {}", e.getMessage(), e);
                    Platform.runLater(() -> {
                        flightTable.setDisable(false);
                        statusLabel.setText("Fehler beim Laden der Daten");
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        ExceptionHandler.handleException(cause, "beim asynchronen Laden der Flugdaten");
                    });
                    return null;
                });
    }

    private void setupTableColumns() {
        numberColumn.setCellValueFactory(new PropertyValueFactory<>("flightNumber"));
        
        // Doppelklick-Handler für Tabellenzeilen zur Bearbeitung
        flightTable.setRowFactory(tv -> {
            TableRow<Flight> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    Flight flight = row.getItem();
                    if (flight.getStatus() == FlightStatus.SCHEDULED) {
                        handleEditFlight(new ActionEvent());
                    } else {
                        showWarning("Hinweis", "Flug nicht bearbeitbar", 
                                "Nur Flüge mit dem Status SCHEDULED können bearbeitet werden.");
                    }
                }
            });
            return row;
        });

        // Abflughafen
        departureColumn.setCellValueFactory(cellData -> {
            Flight flight = cellData.getValue();
            if (flight != null && flight.getDepartureAirport() != null) {
                Airport departure = getAirportWithDetails(flight.getDepartureAirport());
                String depCity = departure.getCity() != null ? departure.getCity() : "N/A";
                String depCountry = departure.getCountry() != null ? departure.getCountry() : "N/A";
                return new SimpleStringProperty(departure.getIcaoCode() + " - " + depCity + ", " + depCountry);
            }
            return new SimpleStringProperty("N/A");
        });

        // Abflugdatum
        departureDateColumn.setCellValueFactory(cellData -> {
            Flight flight = cellData.getValue();
            if (flight != null && flight.getDepartureDate() != null) {
                return new SimpleStringProperty(dateFormatter.format(flight.getDepartureDate()));
            }
            return new SimpleStringProperty("N/A");
        });

        // Abflugzeit
        departureTimeColumn.setCellValueFactory(cellData -> {
            Flight flight = cellData.getValue();
            if (flight != null && flight.getDepartureTime() != null) {
                return new SimpleStringProperty(timeFormatter.format(flight.getDepartureTime()));
            }
            return new SimpleStringProperty("N/A");
        });

        // Pfeil-Spalte
        arrowColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : "→");
            }
        });

        // Zielflughafen
        arrivalColumn.setCellValueFactory(cellData -> {
            Flight flight = cellData.getValue();
            if (flight != null && flight.getArrivalAirport() != null) {
                Airport arrival = getAirportWithDetails(flight.getArrivalAirport());
                String arrCity = arrival.getCity() != null ? arrival.getCity() : "N/A";
                String arrCountry = arrival.getCountry() != null ? arrival.getCountry() : "N/A";
                return new SimpleStringProperty(arrival.getIcaoCode() + " - " + arrCity + ", " + arrCountry);
            }
            return new SimpleStringProperty("N/A");
        });

        // Landedatum
        arrivalDateColumn.setCellValueFactory(cellData -> {
            Flight flight = cellData.getValue();
            if (flight != null && flight.getArrivalDate() != null) {
                return new SimpleStringProperty(dateFormatter.format(flight.getArrivalDate()));
            }
            return new SimpleStringProperty("N/A");
        });

        // Landezeit
        arrivalTimeColumn.setCellValueFactory(cellData -> {
            Flight flight = cellData.getValue();
            if (flight != null && flight.getArrivalTime() != null) {
                return new SimpleStringProperty(timeFormatter.format(flight.getArrivalTime()));
            }
            return new SimpleStringProperty("N/A");
        });

        aircraftColumn.setCellValueFactory(cellData -> {
            Aircraft aircraft = cellData.getValue().getAircraft();
            return new SimpleStringProperty(aircraft.getRegistrationNo());
        });

        statusColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getStatus().toString()));
        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll(
                        "status-scheduled", "status-boarding", "status-departed", "status-flying",
                        "status-landed", "status-deplaning", "status-completed", "status-unknown",
                        "status-cancelled", "status-delayed"
                    );
                } else {
                    setText(item);
                    // Alle vorherigen Status-Klassen entfernen
                    getStyleClass().removeAll(
                        "status-scheduled", "status-boarding", "status-departed", "status-flying",
                        "status-landed", "status-deplaning", "status-completed", "status-unknown",
                        "status-cancelled", "status-delayed"
                    );
                    
                    // CSS-Klasse basierend auf Status-Wert hinzufügen
                    getStyleClass().add("status-" + item.toLowerCase());
                }
            }
        });

        paxColumn.setCellValueFactory(cellData -> {
            Flight flight = cellData.getValue();
            Integer actualPax = passengerCounts.getOrDefault(flight.getId(), 0);
            int maxCapacity = flight.getAircraft().getType().getPaxCapacity();
            return new SimpleStringProperty(actualPax + "/" + maxCapacity);
        });
    }

    /**
     * Lädt einen Flughafen mit Details aus dem Cache oder der Datenbank.
     * Nutzt das optimierte Caching des AirportService.
     */
    private Airport getAirportWithDetails(Airport basicAirport) {
        if (basicAirport == null || basicAirport.getId() == null) {
            return new Airport();
        }

        // Der AirportService hat nun internes Caching, daher können wir direkt abfragen
        // und müssen nicht mehr manuell cachen
        Airport fullAirport = airportService.getAirportById(basicAirport.getId());

        // Fallback zum alten Verhalten, falls der AirportService null zurückgibt
        if (fullAirport == null) {
            // Aus lokalem Cache laden oder zurückfallen auf den Basis-Flughafen
            return airportCache.getOrDefault(basicAirport.getId(), basicAirport);
        }

        // Für den Fall der Fälle, auch lokal cachen
        airportCache.put(fullAirport.getId(), fullAirport);

        return fullAirport;
    }

    private void setupFilters() {
        filteredFlights = new FilteredList<>(flights, p -> true);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> updateFilters());
        dateFilter.valueProperty().addListener((obs, oldVal, newVal) -> updateFilters());
        flightTable.setItems(filteredFlights);
    }

    private void updateFilters() {
        filteredFlights.setPredicate(flight -> {
            // Suchfilter
            if (searchField.getText() != null && !searchField.getText().isEmpty()) {
                String searchText = searchField.getText().toLowerCase();
                if (!flight.getFlightNumber().toLowerCase().contains(searchText) &&
                        !flight.getDepartureAirport().getIcaoCode().toLowerCase().contains(searchText) &&
                        !flight.getArrivalAirport().getIcaoCode().toLowerCase().contains(searchText)) {
                    return false;
                }
            }

            // Datumsfilter
            if (dateFilter.getValue() != null && !dateFilter.getValue().equals(flight.getDepartureDate())) {
                return false;
            }

            // Statusfilter anhand der CheckComboBox
            ObservableList<FlightStatus> selectedStatuses = statusFilterCheck.getCheckModel().getCheckedItems();
            if (!selectedStatuses.isEmpty()) {
                return selectedStatuses.contains(flight.getStatus());
            } else {
                // Standard: Zeige alle Flüge außer denen mit COMPLETED-Status
                return flight.getStatus() != FlightStatus.COMPLETED;
            }
        });
        totalLabel.setText("Flüge gesamt: " + filteredFlights.size());
    }

    private void showFlightDetails(Flight flight) {
        currentFlight = flight;
        statusLabel.setText("Flug " + flight.getFlightNumber() + " ausgewählt");
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        airportCache.clear();
        loadDataAsync();
    }

    @FXML
    private void handleNewFlight(ActionEvent event) {
        logger.info("Neuen Flug erstellen");

        // FXML-basierter Dialog über die Factory
        Optional<Flight> result = skynexus.controller.factories.FlightDialogFactory.showFlightDialog(
            null,
            flightService,
            skynexus.service.RouteService.getInstance(),
            airportService,
            skynexus.service.AircraftService.getInstance(),
            flight -> {
                // Nach dem erfolgreichen Speichern eines neuen Flugs
                loadDataAsync();
                statusLabel.setText("Neuer Flug erstellt: " + flight.getFlightNumber());
            }
        );
    }

    @FXML
    private void handleEditFlight(ActionEvent event) {
        if (currentFlight == null) {
            showWarning("Hinweis", "Kein Flug ausgewählt", "Bitte wählen Sie einen Flug aus der Tabelle aus.");
            return;
        }
        
        // Prüfen, ob der Flug noch im Status SCHEDULED ist
        if (currentFlight.getStatus() != FlightStatus.SCHEDULED) {
            showWarning("Hinweis", "Flug nicht bearbeitbar", 
                    "Nur Flüge mit dem Status SCHEDULED können bearbeitet werden.");
            return;
        }
        
        logger.info("Flug bearbeiten: {}", currentFlight.getFlightNumber());

        // FXML-basierter Dialog über die Factory
        Optional<Flight> result = skynexus.controller.factories.FlightDialogFactory.showFlightDialog(
            currentFlight,
            flightService,
            skynexus.service.RouteService.getInstance(),
            airportService,
            skynexus.service.AircraftService.getInstance(),
            flight -> {
                // Nach dem erfolgreichen Speichern eines vorhandenen Flugs
                loadDataAsync();
                statusLabel.setText("Flug aktualisiert: " + flight.getFlightNumber());
            }
        );
    }

    @FXML
    private void handleDeleteFlight(ActionEvent event) {
        if (currentFlight == null) {
            showWarning("Hinweis", "Kein Flug ausgewählt", "Bitte wählen Sie einen Flug aus der Tabelle aus.");
            return;
        }
        
        // Prüfen, ob der Flug noch im Status SCHEDULED ist
        if (currentFlight.getStatus() != FlightStatus.SCHEDULED) {
            showWarning("Hinweis", "Flug nicht löschbar", 
                    "Nur Flüge mit dem Status SCHEDULED können gelöscht werden.");
            return;
        }
        
        boolean confirmed = showConfirmation("Flug löschen",
                "Flug " + currentFlight.getFlightNumber() + " löschen?",
                "Möchten Sie diesen Flug wirklich löschen?");
        if (confirmed) {
            try {
                boolean success = flightService.deleteFlight(currentFlight.getId());
                if (success) {
                    passengerService.clearPassengerCountCache();
                    loadDataAsync();
                    statusLabel.setText("Flug gelöscht: " + currentFlight.getFlightNumber());
                    logger.info("Flug gelöscht: {}", currentFlight.getFlightNumber());
                    currentFlight = null;
                } else {
                    ExceptionHandler.showErrorDialog("Fehler", "Der Flug konnte nicht gelöscht werden.");
                }
            } catch (Exception e) {
                logger.error("Fehler beim Löschen des Flugs", e);
                ExceptionHandler.handleException(e, "beim Löschen des Flugs");
            }
        }
    }

    private void showError(String title, String header, String content) {
        logger.error("{}: {} - {}", title, header, content);
        ExceptionHandler.showErrorDialog(title, header + ": " + content);
    }

    private void showWarning(String title, String header, String content) {
        logger.warn("{}: {} - {}", title, header, content);
        ExceptionHandler.showWarningDialog(title, header + ": " + content);
    }

    private boolean showConfirmation(String title, String header, String content) {
        logger.info("Bestätigungsdialog: {} - {}", header, content);
        return ExceptionHandler.showConfirmDialog(title, header + "\n\n" + content);
    }
}
