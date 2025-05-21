package skynexus.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.enums.AircraftStatus;
import skynexus.model.*;
import skynexus.service.AircraftService;
import skynexus.service.AirportService;
import skynexus.service.FlightService;
import skynexus.service.ManufacturerService;
import skynexus.util.ExceptionHandler;
import skynexus.util.SessionManager;
import skynexus.util.TimeUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Controller für die Flugzeug-Verwaltungsansicht.
 * Zeigt eine Übersicht über alle Flugzeuge der Airline und deren Status.
 */
public class AircraftViewController {
    private static final Logger logger = LoggerFactory.getLogger(AircraftViewController.class);

    // Allgemeine UI-Elemente
    @FXML
    private AnchorPane root;

    // Tabellen-Elemente
    @FXML
    private TableView<Aircraft> aircraftTable;
    @FXML
    private TableColumn<Aircraft, String> registrationColumn;
    @FXML
    private TableColumn<Aircraft, String> typeColumn;
    @FXML
    private TableColumn<Aircraft, Integer> capacityColumn;
    @FXML
    private TableColumn<Aircraft, String> locationColumn;
    @FXML
    private TableColumn<Aircraft, Double> speedColumn;
    @FXML
    private TableColumn<Aircraft, Double> costPerHourColumn;
    @FXML
    private TableColumn<Aircraft, String> buildDateColumn;
    @FXML
    private TableColumn<Aircraft, String> statusColumn;
    @FXML
    private TextField searchField;
    @FXML
    private Button addButton;
    @FXML
    private Button addTypeButton;
    @FXML
    private Label statusLabel;
    @FXML
    private Label totalLabel;

    // Services
    private AircraftService aircraftService;
    private ManufacturerService manufacturerService;
    private AirportService airportService;
    private ObservableList<Aircraft> aircraftList;

    /**
     * Initialisiert den Controller und das Layout.
     */
    @FXML
    public void initialize() {
        logger.debug("AircraftView wird initialisiert");

        // Services initialisieren
        aircraftService = AircraftService.getInstance();
        manufacturerService = ManufacturerService.getInstance();
        airportService = AirportService.getInstance();

        // UI-Komponenten konfigurieren
        setupTableColumns();
        setupActions();
        setupSearch();

        // Daten laden
        loadAircraft();
    }

    /**
     * Konfiguriert die Tabellenspalten mit den korrekten CellValueFactories
     */
    private void setupTableColumns() {
        registrationColumn.setCellValueFactory(new PropertyValueFactory<>("registrationNo"));

        // Doppelklick-Handler für Tabellenzeilen zur Bearbeitung
        aircraftTable.setRowFactory(tv -> {
            TableRow<Aircraft> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    showEditDialog(row.getItem());
                }
            });
            return row;
        });

        typeColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getType().getFullName()));

        capacityColumn.setCellValueFactory(cellData ->
                new SimpleIntegerProperty(cellData.getValue().getType().getPaxCapacity()).asObject());

        // Standort-Spalte konfigurieren
        locationColumn.setCellValueFactory(cellData -> {
            Aircraft aircraft = cellData.getValue();
            Airport location = aircraft.getCurrentLocation();
            if (location != null) {
                String locationText = location.getIcaoCode();
                // Wenn Stadt verfügbar ist, füge sie hinzu
                if (location.getCity() != null && !location.getCity().isEmpty()) {
                    locationText += " - " + location.getCity();
                }
                return new SimpleStringProperty(locationText);
            }
            return new SimpleStringProperty("Unbekannt");
        });

        // Geschwindigkeit-Spalte konfigurieren
        speedColumn.setCellValueFactory(cellData -> {
            AircraftType type = cellData.getValue().getType();
            if (type != null) {
                return new javafx.beans.property.SimpleDoubleProperty(type.getSpeedKmh()).asObject();
            }
            return new javafx.beans.property.SimpleDoubleProperty(0).asObject();
        });

        // Kosten pro Stunde Spalte konfigurieren
        costPerHourColumn.setCellValueFactory(cellData -> {
            AircraftType type = cellData.getValue().getType();
            if (type != null) {
                return new javafx.beans.property.SimpleDoubleProperty(type.getCostPerHour()).asObject();
            }
            return new javafx.beans.property.SimpleDoubleProperty(0).asObject();
        });

        // Format für Kosten pro Stunde mit Währungssymbol
        costPerHourColumn.setCellFactory(column -> new TableCell<Aircraft, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f €", item));
                }
            }
        });

        buildDateColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getBuildDate().toString()));

        statusColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getStatus().toString()));

        // Status-Spalte mit CSS-Klassen für farbliche Hervorhebung
        statusColumn.setCellFactory(column -> new TableCell<Aircraft, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    getStyleClass().removeAll(
                        "status-available", "status-scheduled", "status-flying", 
                        "status-unknown", "status-maintenance", "status-grounded"
                    );
                } else {
                    setText(item);

                    // Alle vorherigen Status-Klassen entfernen
                    getStyleClass().removeAll(
                        "status-available", "status-scheduled", "status-flying", 
                        "status-unknown", "status-maintenance", "status-grounded"
                    );

                    // Passende Status-Klasse basierend auf Enum-Wert hinzufügen
                    getStyleClass().add("status-" + item.toLowerCase());
                }
            }
        });
    }

    /**
     * Konfiguriert die Aktionen der Buttons
     */
    private void setupActions() {
        addButton.setOnAction(e -> showAddDialog());
        addTypeButton.setOnAction(e -> showAddTypeDialog());
    }

    /**
     * Lädt die Flugzeugdaten
     */
    private void loadAircraft() {
        statusLabel.setText("Lade Flugzeugdaten...");
        aircraftTable.setDisable(true);
        long startTime = System.currentTimeMillis();

        // Asynchrones Laden
        CompletableFuture.supplyAsync(() -> {
            try {
                // Zuerst Status-Synchronisierung durchführen
                FlightService.getInstance().synchronizeAircraftStatus();
                
                List<Aircraft> allAircraft;
                allAircraft = aircraftService.getAllAircraft();
                logger.info("Lade alle Flugzeuge");
                return allAircraft;
            } catch (Exception e) {
                logger.error("Fehler beim Laden der Flugzeuge", e);
                throw new RuntimeException(e);
            }
        }).thenAccept(allAircraft -> {
            // UI-Thread für Datenaktualisierung
            Platform.runLater(() -> {
                long duration = System.currentTimeMillis() - startTime;
                aircraftList = FXCollections.observableArrayList(allAircraft);
                aircraftTable.setItems(aircraftList);
                aircraftTable.setDisable(false);

                // Statuszeile mit Zeitanzeige
                statusLabel.setText("Flugzeuge erfolgreich geladen (" + TimeUtils.formatLoadingTime(duration) + ")");
                updateStatusLine();
            });
        }).exceptionally(e -> {
            Platform.runLater(() -> {
                aircraftTable.setDisable(false);
                statusLabel.setText("Fehler beim Laden der Flugzeuge");
                ExceptionHandler.handleException(e, "beim Laden der Flugzeuge");
            });
            return null;
        });
    }

    /**
     * Aktualisiert die Statuszeile am unteren Rand der Ansicht
     */
    private void updateStatusLine() {
        if (aircraftList != null) {
            totalLabel.setText("Flugzeuge gesamt: " + aircraftList.size());
            // Don't reset statusLabel here, it might be showing load time
        } else {
            totalLabel.setText("Flugzeuge gesamt: 0");
            statusLabel.setText("Keine Daten verfügbar");
        }
    }

    /**
     * Konfiguriert das Suchfeld für die Filterung der Flugzeuge
     */
    private void setupSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterAircraft(newVal));
    }

    /**
     * Filtert die angezeigten Flugzeuge basierend auf dem Suchtext
     *
     * @param searchText Der Suchtext für die Filterung
     */
    private void filterAircraft(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            aircraftTable.setItems(aircraftList);
            statusLabel.setText("Bereit");
            totalLabel.setText("Flugzeuge gesamt: " + aircraftList.size());
            return;
        }

        String lowerCaseFilter = searchText.toLowerCase();
        ObservableList<Aircraft> filteredList = FXCollections.observableArrayList();

        for (Aircraft aircraft : aircraftList) {
            if (aircraft.getRegistrationNo().toLowerCase().contains(lowerCaseFilter) ||
                    aircraft.getType().getFullName().toLowerCase().contains(lowerCaseFilter) ||
                    (aircraft.getCurrentLocation() != null && aircraft.getCurrentLocation().getIcaoCode() != null &&
                            aircraft.getCurrentLocation().getIcaoCode().toLowerCase().contains(lowerCaseFilter)) ||
                    (aircraft.getCurrentLocation() != null && aircraft.getCurrentLocation().getCity() != null &&
                            aircraft.getCurrentLocation().getCity().toLowerCase().contains(lowerCaseFilter))) {
                filteredList.add(aircraft);
            }
        }

        aircraftTable.setItems(filteredList);

        // Status-Zeile aktualisieren
        statusLabel.setText("Filter aktiv: " + searchText);
        totalLabel.setText("Flugzeuge gefiltert: " + filteredList.size() + " / " + aircraftList.size());
    }

    /**
     * Zeigt den Dialog zum Hinzufügen eines neuen Flugzeugs
     */
    private void showAddDialog() {
        Dialog<Aircraft> dialog = new Dialog<>();
        dialog.setTitle("Neues Flugzeug");
        dialog.setHeaderText("Flugzeugdetails eingeben");

        ButtonType saveButtonType = new ButtonType("Speichern", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField registrationNoField = new TextField();
        registrationNoField.setPromptText("z.B. D-AIAB");

        // Hersteller-ComboBox
        ComboBox<Manufacturer> manufacturerComboBox = new ComboBox<>();
        List<Manufacturer> manufacturers = manufacturerService.getAllManufacturers();
        manufacturerComboBox.setItems(FXCollections.observableArrayList(manufacturers));
        manufacturerComboBox.setPromptText("Hersteller auswählen");

        // Typ-ComboBox, die abhängig vom gewählten Hersteller ist
        ComboBox<AircraftType> typeComboBox = new ComboBox<>();
        typeComboBox.setPromptText("Erst Hersteller wählen");
        typeComboBox.setDisable(true); // Deaktiviert, bis ein Hersteller gewählt wird

        // Listener für Hersteller-Wechsel
        manufacturerComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // Lade Flugzeugtypen für den ausgewählten Hersteller
                List<AircraftType> types = aircraftService.getAircraftTypesByManufacturer(newVal);

                typeComboBox.setItems(FXCollections.observableArrayList(types));
                typeComboBox.setDisable(false);
                typeComboBox.setPromptText("Typ auswählen");
            } else {
                typeComboBox.getItems().clear();
                typeComboBox.setDisable(true);
                typeComboBox.setPromptText("Erst Hersteller wählen");
            }
        });

        // DatePicker konfigurieren, der nur per Kalender auswählbar ist
        DatePicker buildDatePicker = new DatePicker();
        buildDatePicker.setEditable(false); // Deaktiviert direkte Texteingabe

        // Flughafen-ComboBox für initialen Standort
        ComboBox<Airport> locationComboBox = new ComboBox<>();
        try {
            List<Airport> airports = airportService.getAllAirports();
            locationComboBox.setItems(FXCollections.observableArrayList(airports));
            locationComboBox.setPromptText("Initialen Standort wählen");

            // Anzeigen von ICAO-Code + Stadtname
            locationComboBox.setCellFactory(param -> new ListCell<Airport>() {
                @Override
                protected void updateItem(Airport item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        String displayText = item.getIcaoCode();
                        if (item.getCity() != null && !item.getCity().isEmpty()) {
                            displayText += " - " + item.getCity();
                            if (item.getCountry() != null && !item.getCountry().isEmpty()) {
                                displayText += ", " + item.getCountry();
                            }
                        }
                        setText(displayText);
                    }
                }
            });

            // Auch für den ausgewählten Wert
            locationComboBox.setButtonCell(new ListCell<Airport>() {
                @Override
                protected void updateItem(Airport item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        String displayText = item.getIcaoCode();
                        if (item.getCity() != null && !item.getCity().isEmpty()) {
                            displayText += " - " + item.getCity();
                            if (item.getCountry() != null && !item.getCountry().isEmpty()) {
                                displayText += ", " + item.getCountry();
                            }
                        }
                        setText(displayText);
                    }
                }
            });
        } catch (Exception e) {
            logger.error("Fehler beim Laden der Flughäfen", e);
            ExceptionHandler.handleException(e, "beim Laden der Flughäfen");
        }

        ComboBox<AircraftStatus> statusComboBox = new ComboBox<>();
        statusComboBox.setItems(FXCollections.observableArrayList(AircraftStatus.values()));
        statusComboBox.setValue(AircraftStatus.AVAILABLE);

        // Ermittle, ob der aktuelle Benutzer ein Admin ist
        final boolean isAdmin;

        // Sicherer Zugriff auf den angemeldeten Benutzer und seine Berechtigungen
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser != null) {
            isAdmin = currentUser.isAdmin();
        } else {
            isAdmin = false;
        }

        try {
            if (isAdmin) {
                logger.info("Airline wird automatisch vom System bestimmt");
            }
        } catch (Exception e) {
            logger.error("Fehler beim Laden der Benutzerinformationen: {}", e.getMessage());
            ExceptionHandler.handleException(e, "beim Laden der Benutzerinformationen");
        }

        grid.add(new Label("Registrierung:"), 0, 0);
        grid.add(registrationNoField, 1, 0);
        grid.add(new Label("Hersteller:"), 0, 1);
        grid.add(manufacturerComboBox, 1, 1);
        grid.add(new Label("Flugzeugtyp:"), 0, 2);
        grid.add(typeComboBox, 1, 2);
        grid.add(new Label("Baujahr:"), 0, 3);
        grid.add(buildDatePicker, 1, 3);
        grid.add(new Label("Initialer Standort:"), 0, 4);
        grid.add(locationComboBox, 1, 4);
        grid.add(new Label("Status:"), 0, 5);
        grid.add(statusComboBox, 1, 5);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    String regNo = registrationNoField.getText();
                    AircraftType type = typeComboBox.getValue();
                    java.time.LocalDate buildLocalDate = buildDatePicker.getValue();
                    Airport initialLocation = locationComboBox.getValue();
                    AircraftStatus status = statusComboBox.getValue();

                    // Airline wird automatisch vom System übernommen (Single-Airline-Architektur)
                    final Airline airlineToUse = Airline.getInstance();

                    if (regNo.isEmpty() || type == null || buildLocalDate == null || airlineToUse == null) {
                        ExceptionHandler.showWarningDialog("Unvollständige Eingabe", "Bitte füllen Sie alle Pflichtfelder aus");
                        return null;
                    }

                    // Neuer Konstruktor mit Airline- und Airport-Parameter
                    Aircraft aircraft = new Aircraft(type, regNo, buildLocalDate, airlineToUse, initialLocation);
                    aircraft.setStatus(status);

                    return aircraft;
                } catch (Exception e) {
                    ExceptionHandler.handleException(e, "bei der Eingabevalidierung");
                    return null;
                }
            }
            return null;
        });

        Optional<Aircraft> result = dialog.showAndWait();
        result.ifPresent(aircraft -> {
            try {
                boolean success = aircraftService.saveAircraft(aircraft);
                if (success) {
                    loadAircraft();
                } else {
                    ExceptionHandler.showErrorDialog("Fehler", "Flugzeug konnte nicht gespeichert werden");
                }
            } catch (Exception e) {
                ExceptionHandler.handleException(e, "beim Speichern des Flugzeugs");
            }
        });
    }

    /**
     * Zeigt den Dialog zum Hinzufügen eines neuen Flugzeugtyps
     */
    private void showAddTypeDialog() {
        Dialog<AircraftType> dialog = new Dialog<>();
        dialog.setTitle("Neuer Flugzeugtyp");
        dialog.setHeaderText("Flugzeugtypdetails eingeben");

        ButtonType saveButtonType = new ButtonType("Speichern", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        // Bestehenden Hersteller auswählen oder neuen hinzufügen
        ComboBox<Manufacturer> manufacturerComboBox = new ComboBox<>();
        List<Manufacturer> manufacturers = manufacturerService.getAllManufacturers();
        manufacturerComboBox.setItems(FXCollections.observableArrayList(manufacturers));
        manufacturerComboBox.setPromptText("Hersteller auswählen");

        // Option zum Hinzufügen eines neuen Herstellers
        Button addManufacturerButton = new Button("Neuer Hersteller");
        addManufacturerButton.setOnAction(e -> {
            TextInputDialog manufacturerDialog = new TextInputDialog();
            manufacturerDialog.setTitle("Neuer Hersteller");
            manufacturerDialog.setHeaderText("Herstellernamen eingeben");
            manufacturerDialog.setContentText("Name:");

            Optional<String> result = manufacturerDialog.showAndWait();
            result.ifPresent(name -> {
                if (!name.trim().isEmpty()) {
                    Manufacturer newManufacturer = new Manufacturer(name.trim());
                    boolean success = manufacturerService.saveManufacturer(newManufacturer);
                    if (success) {
                        // Aktualisiere die Herstellerliste
                        List<Manufacturer> updatedManufacturers = manufacturerService.getAllManufacturers();
                        manufacturerComboBox.setItems(FXCollections.observableArrayList(updatedManufacturers));
                        // Wähle den neuen Hersteller
                        manufacturerComboBox.getSelectionModel().select(
                                updatedManufacturers.stream()
                                        .filter(m -> m.getName().equals(name.trim()))
                                        .findFirst()
                                        .orElse(null)
                        );
                        ExceptionHandler.showInfoDialog("Erfolg", "Hersteller wurde erfolgreich hinzugefügt");
                    } else {
                        ExceptionHandler.showErrorDialog("Fehler", "Hersteller konnte nicht gespeichert werden");
                    }
                }
            });
        });

        TextField modelField = new TextField();
        modelField.setPromptText("z.B. A320-200");

        TextField paxCapacityField = new TextField();
        paxCapacityField.setPromptText("z.B. 180");

        TextField cargoCapacityField = new TextField();
        cargoCapacityField.setPromptText("z.B. 2000");

        TextField rangeField = new TextField();
        rangeField.setPromptText("z.B. 5700");

        TextField speedField = new TextField();
        speedField.setPromptText("z.B. 840");

        TextField costPerHourField = new TextField();
        costPerHourField.setPromptText("z.B. 5000");

        // Layout anpassen für den zusätzlichen Button
        grid.add(new Label("Hersteller:"), 0, 0);
        grid.add(manufacturerComboBox, 1, 0);
        grid.add(addManufacturerButton, 2, 0);
        grid.add(new Label("Modell:"), 0, 1);
        grid.add(modelField, 1, 1);
        grid.add(new Label("Sitzkapazität:"), 0, 2);
        grid.add(paxCapacityField, 1, 2);
        grid.add(new Label("Frachtkapazität (kg):"), 0, 3);
        grid.add(cargoCapacityField, 1, 3);
        grid.add(new Label("Reichweite (km):"), 0, 4);
        grid.add(rangeField, 1, 4);
        grid.add(new Label("Geschwindigkeit (km/h):"), 0, 5);
        grid.add(speedField, 1, 5);
        grid.add(new Label("Kosten pro Std. (€):"), 0, 6);
        grid.add(costPerHourField, 1, 6);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    Manufacturer manufacturer = manufacturerComboBox.getValue();
                    String model = modelField.getText();
                    int paxCapacity = Integer.parseInt(paxCapacityField.getText());
                    double cargoCapacity = Double.parseDouble(cargoCapacityField.getText());
                    double range = Double.parseDouble(rangeField.getText());
                    double speed = Double.parseDouble(speedField.getText());
                    double costPerHour = Double.parseDouble(costPerHourField.getText());

                    if (manufacturer == null || model.isEmpty()) {
                        ExceptionHandler.showWarningDialog("Unvollständige Eingabe", "Bitte füllen Sie alle Pflichtfelder aus");
                        return null;
                    }

                    AircraftType type = new AircraftType(manufacturer, model, paxCapacity, cargoCapacity, range, speed, costPerHour);
                    return type;
                } catch (NumberFormatException e) {
                    ExceptionHandler.showWarningDialog("Eingabefehler", "Bitte geben Sie gültige Zahlen ein");
                    return null;
                } catch (Exception e) {
                    ExceptionHandler.handleException(e, "bei der Eingabevalidierung");
                    return null;
                }
            }
            return null;
        });

        Optional<AircraftType> result = dialog.showAndWait();
        result.ifPresent(type -> {
            try {
                boolean success = aircraftService.saveAircraftType(type);
                if (success) {
                    ExceptionHandler.showInfoDialog("Erfolg", "Flugzeugtyp wurde erfolgreich gespeichert");
                } else {
                    ExceptionHandler.showErrorDialog("Fehler", "Flugzeugtyp konnte nicht gespeichert werden");
                }
            } catch (Exception e) {
                ExceptionHandler.handleException(e, "beim Speichern des Flugzeugtyps");
            }
        });
    }

    /**
     * Zeigt den Dialog zum Bearbeiten eines bestehenden Flugzeugs
     *
     * @param aircraft Das zu bearbeitende Flugzeug
     */
    private void showEditDialog(Aircraft aircraft) {
        if (aircraft == null) {
            return;
        }

        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Flugzeug bearbeiten");
        dialog.setHeaderText("Flugzeugdetails bearbeiten: " + aircraft.getRegistrationNo());

        ButtonType saveButtonType = new ButtonType("Speichern", ButtonBar.ButtonData.OK_DONE);
        ButtonType deleteButtonType = new ButtonType("Löschen", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, deleteButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        // Registrierung (nicht editierbar)
        TextField registrationNoField = new TextField(aircraft.getRegistrationNo());
        registrationNoField.setDisable(true); // Registrierung nicht editierbar

        // Flugzeugtyp (nicht editierbar)
        TextField typeField = new TextField(aircraft.getType().getFullName());
        typeField.setDisable(true);

        // Baujahr (nur Kalender, nicht editierbar per Text)
        DatePicker buildDatePicker = new DatePicker(aircraft.getBuildDate());
        buildDatePicker.setEditable(false);

        // Status ComboBox
        ComboBox<AircraftStatus> statusComboBox = new ComboBox<>();
        statusComboBox.setItems(FXCollections.observableArrayList(AircraftStatus.values()));
        statusComboBox.setValue(aircraft.getStatus());

        // Aktueller Standort
        ComboBox<Airport> locationComboBox = new ComboBox<>();
        try {
            List<Airport> airports = airportService.getAllAirports();
            locationComboBox.setItems(FXCollections.observableArrayList(airports));

            // Aktuellen Standort vorauswählen
            if (aircraft.getCurrentLocation() != null) {
                // Suche nach dem Airport mit derselben ID
                airports.stream()
                        .filter(a -> a.getId().equals(aircraft.getCurrentLocation().getId()))
                        .findFirst()
                        .ifPresent(locationComboBox::setValue);
            }

            // Anzeigen von ICAO-Code + Stadtname
            locationComboBox.setCellFactory(param -> new ListCell<Airport>() {
                @Override
                protected void updateItem(Airport item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        String displayText = item.getIcaoCode();
                        if (item.getCity() != null && !item.getCity().isEmpty()) {
                            displayText += " - " + item.getCity();
                            if (item.getCountry() != null && !item.getCountry().isEmpty()) {
                                displayText += ", " + item.getCountry();
                            }
                        }
                        setText(displayText);
                    }
                }
            });

            // Auch für den ausgewählten Wert
            locationComboBox.setButtonCell(new ListCell<Airport>() {
                @Override
                protected void updateItem(Airport item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        String displayText = item.getIcaoCode();
                        if (item.getCity() != null && !item.getCity().isEmpty()) {
                            displayText += " - " + item.getCity();
                            if (item.getCountry() != null && !item.getCountry().isEmpty()) {
                                displayText += ", " + item.getCountry();
                            }
                        }
                        setText(displayText);
                    }
                }
            });
        } catch (Exception e) {
            logger.error("Fehler beim Laden der Flughäfen", e);
            ExceptionHandler.handleException(e, "beim Laden der Flughäfen");
        }

        grid.add(new Label("Registrierung:"), 0, 0);
        grid.add(registrationNoField, 1, 0);
        grid.add(new Label("Flugzeugtyp:"), 0, 1);
        grid.add(typeField, 1, 1);
        grid.add(new Label("Baujahr:"), 0, 2);
        grid.add(buildDatePicker, 1, 2);
        grid.add(new Label("Standort:"), 0, 3);
        grid.add(locationComboBox, 1, 3);
        grid.add(new Label("Status:"), 0, 4);
        grid.add(statusComboBox, 1, 4);

        dialog.getDialogPane().setContent(grid);

        // Delete-Button rot einfärben
        Button deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteButtonType);
        deleteButton.getStyleClass().add("btn-danger");

        // Handler für den Löschen-Button
        deleteButton.setOnAction(e -> {
            e.consume(); // Verhindere standard Dialog-Verhalten
            showDeleteConfirmation(aircraft);
            dialog.close(); // Schließe Edit-Dialog, Bestätigungsdialog wird asynchron behandelt
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    // Aktualisiere das Flugzeug-Objekt mit den neuen Werten
                    aircraft.setBuildDate(buildDatePicker.getValue());
                    aircraft.setCurrentLocation(locationComboBox.getValue());
                    aircraft.setStatus(statusComboBox.getValue());

                    boolean success = aircraftService.updateAircraft(aircraft);
                    if (success) {
                        return true;
                    } else {
                        ExceptionHandler.showErrorDialog("Fehler", "Flugzeug konnte nicht aktualisiert werden");
                        return false;
                    }
                } catch (Exception e) {
                    ExceptionHandler.handleException(e, "beim Aktualisieren des Flugzeugs");
                    return false;
                }
            }
            return false;
        });

        Optional<Boolean> result = dialog.showAndWait();
        result.ifPresent(success -> {
            if (success) {
                loadAircraft();
                statusLabel.setText("Flugzeug wurde aktualisiert oder gelöscht");
            }
        });
    }

    /**
     * Zeigt einen Bestätigungsdialog für das Löschen eines Flugzeugs an
     *
     * @param aircraft Das zu löschende Flugzeug
     */
    private void showDeleteConfirmation(Aircraft aircraft) {
        // Debug-Logging für Fehleranalyse
        logger.debug("Zeige Löschdialog für Flugzeug: {}", aircraft != null ? aircraft.getRegistrationNo() : "null");

        if (aircraft == null) {
            logger.error("Aircraft ist null beim Löschversuch");
            ExceptionHandler.showErrorDialog("Fehler", "Flugzeug konnte nicht gelöscht werden: keine Daten verfügbar");
            return;
        }

        try {
            // Sichere String-Darstellung für Dialog-Text
            String registrationNo = aircraft.getRegistrationNo() != null ? aircraft.getRegistrationNo() : "Unbekannt";
            String message = "Möchten Sie das Flugzeug mit der Registrierung " + registrationNo + " wirklich löschen?\n\nDiese Aktion kann nicht rückgängig gemacht werden.";

            logger.debug("Erstelle Bestätigungsdialog für: {}", registrationNo);

            // Verwendung des zentralen Bestätigungsdialogs
            boolean confirmed = ExceptionHandler.showConfirmDialog(
                "Flugzeug löschen",
                message
            );

            logger.debug("Bestätigung für Löschung von {}: {}", registrationNo, confirmed);

            if (confirmed) {
                try {
                    logger.info("Lösche Flugzeug: {}", registrationNo);
                    boolean success = aircraftService.deleteAircraft(aircraft);

                    if (success) {
                        logger.info("Flugzeug {} erfolgreich gelöscht", registrationNo);
                        Platform.runLater(() -> {
                            ExceptionHandler.showInfoDialog("Erfolg", "Flugzeug wurde erfolgreich gelöscht");
                            loadAircraft(); // Liste neu laden
                        });
                    } else {
                        logger.warn("Flugzeug {} konnte nicht gelöscht werden", registrationNo);
                        ExceptionHandler.showErrorDialog("Fehler", "Flugzeug konnte nicht gelöscht werden");
                    }
                } catch (Exception e) {
                    logger.error("Fehler beim Löschen von Flugzeug {}: {}", registrationNo, e.getMessage(), e);
                    ExceptionHandler.handleException(e, "beim Löschen des Flugzeugs");
                }
            }
        } catch (Exception e) {
            logger.error("Unerwarteter Fehler beim Löschdialog: {}", e.getMessage(), e);
            ExceptionHandler.handleException(e, "beim Anzeigen des Löschdialogs");
        }
    }

    // Die showAlert-Methode wurde durch die zentrale Fehlerbehandlung mit ExceptionHandler ersetzt
}
