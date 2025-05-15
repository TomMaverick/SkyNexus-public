package skynexus.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.model.*;
import skynexus.service.*; // Importiere alle Services
import skynexus.util.*;

import java.net.URL;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * Controller für die Routenverwaltung.
 * Ermöglicht das Anzeigen, Erstellen und Bearbeiten von Flugrouten.
 */
public class AdminRoutesViewController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(AdminRoutesViewController.class);

    // Services (werden jetzt über Konstruktor injiziert)
    private final RouteService routeService;
    private final AirportService airportService;
    private final FlightService flightService;

    // Daten-Collections
    private final ObservableList<Route> routes = FXCollections.observableArrayList();
    // Aktuell ausgewählte/bearbeitete Route
    private Route currentRoute;
    // ResourceBundle für I18n
    private ResourceBundle resources;
    // UI-Elemente - Tabelle
    @FXML
    private TableView<Route> routeTable;
    @FXML
    private TableColumn<Route, String> codeColumn;
    @FXML
    private TableColumn<Route, String> departureColumn;
    @FXML
    private TableColumn<Route, String> arrivalColumn;
    @FXML
    private TableColumn<Route, Double> distanceColumn;
    @FXML
    private TableColumn<Route, String> statusColumn;
    // UI-Elemente - Filter
    @FXML
    private TextField searchField;
    @FXML
    private CheckBox activeOnlyFilter;

    // UI-Elemente - Status
    @FXML
    private Label statusLabel;
    @FXML
    private Label totalLabel;
    private FilteredList<Route> filteredRoutes;


    /**
     * Parameterloser Konstruktor für FXML-Loader.
     * Dieser Konstruktor ist notwendig, damit der FXMLLoader den Controller instanziieren kann.
     */
    public AdminRoutesViewController() {
        // Services aus den Singleton-Instanzen beziehen
        this.routeService = RouteService.getInstance();
        this.airportService = AirportService.getInstance();
        this.flightService = FlightService.getInstance();
        logger.debug("AdminRoutesViewController Instanz mit Standard-Konstruktor erstellt");
    }

    /**
     * Konstruktor für Dependency Injection.
     * Dieser Konstruktor kann alternativ verwendet werden, wenn die Controller-Instanz
     * manuell erstellt und mit dem FXML über eine ControllerFactory verbunden wird.
     *
     * @param routeService   Der RouteService.
     * @param airportService Der AirportService.
     * @param flightService  Der FlightService.
     */
    public AdminRoutesViewController(RouteService routeService, AirportService airportService,
                               FlightService flightService) {
        this.routeService = routeService != null ? routeService : RouteService.getInstance();
        this.airportService = airportService != null ? airportService : AirportService.getInstance();
        this.flightService = flightService != null ? flightService : FlightService.getInstance();
        logger.debug("AdminRoutesViewController Instanz erstellt mit injizierten Services.");
    }


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.resources = resources;
        logger.info("Initialisiere Route-View");

        // Debug-Information für Fehleranalyse
        checkInitialState();

        // Initialisiere Ressourcen-Fallback
        if (resources == null) {
            logger.warn("Route-View wurde ohne ResourceBundle initialisiert - verwende Fallback-Texte");
        }

        try {
            // Tabellenspalten konfigurieren
            setupTableColumns();

            // Filter einrichten
            setupFilters();

            // Listener für Tabellenauswahl
            if (routeTable != null && routeTable.getSelectionModel() != null) {
                // Doppelklick-Handler für die Tabelle hinzufügen -> Öffnet Edit-Dialog
                routeTable.setRowFactory(tv -> {
                    TableRow<Route> row = new TableRow<>();
                    row.setOnMouseClicked(event -> {
                        if (event.getClickCount() == 2 && (!row.isEmpty())) {
                            Route selectedRoute = row.getItem();
                            showEditDialog(selectedRoute); // Bearbeiten-Dialog bei Doppelklick
                        }
                    });
                    return row;
                });

                // Einzelklick-Handler für Statusanzeige
                routeTable.getSelectionModel().selectedItemProperty().addListener(
                        (obs, oldSelection, newSelection) -> {
                            if (newSelection != null) {
                                // Nur Status-Update, kein Dialog öffnen
                                currentRoute = newSelection;

                                // Status anzeigen
                                if (statusLabel != null) {
                                    String statusMessage = "Route " + newSelection.getRouteCode() + " ausgewählt";
                                    statusLabel.setText(statusMessage);
                                }
                                // Lade Flüge im Hintergrund, falls benötigt (z.B. für Löschprüfung)
                                // loadFlightsForRoute(newSelection); // Nicht mehr direkt anzeigen
                            } else {
                                currentRoute = null;
                                // Optional: Statuslabel zurücksetzen
                                // if (statusLabel != null) statusLabel.setText("Keine Route ausgewählt");
                            }
                        });
            } else {
                logger.error("RouteTable oder SelectionModel ist null - kann Listener nicht hinzufügen");
            }

            // Daten laden
            loadAllData();

            // Details zurücksetzen (nicht mehr nötig, da keine Detail-Pane)
            // clearDetails();

        } catch (Exception e) {
            logger.error("Fehler beim Initialisieren der Route-View", e);
            // Fallback-UI anzeigen, wenn möglich
            showFallbackUI("Fehler beim Laden der Routen-Ansicht", e.getMessage());
        }
    }

    /**
     * Zeigt eine Fallback-UI an, falls die normale Initialisierung fehlschlägt
     */
    private void showFallbackUI(String header, String details) {
        try {
            // Versuche eine minimale Fallback-UI zu erstellen
            if (statusLabel != null) {
                statusLabel.setText("FEHLER: " + header);
                statusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            }

            // Falls die Hauptkomponenten nicht verfügbar sind, versuche eine Ersatz-UI zu bauen
            if (routeTable != null && routeTable.getParent() instanceof Pane parent) {
                parent.getChildren().clear();

                VBox errorBox = new VBox(10);
                errorBox.setAlignment(javafx.geometry.Pos.CENTER);
                errorBox.setPadding(new javafx.geometry.Insets(20));

                Label errorLabel = new Label("Ein Fehler ist aufgetreten beim Laden der Routenansicht");
                errorLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: red;");

                Label detailsLabel = new Label(details);
                detailsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: darkred; -fx-wrap-text: true;");

                Button retryButton = new Button("Erneut versuchen");
                retryButton.setOnAction(e -> {
                    try {
                        parent.getChildren().clear();
                        // Füge die Tabelle wieder hinzu (oder was immer das Wurzelelement war)
                        // Dies muss ggf. angepasst werden, je nach FXML-Struktur
                        parent.getChildren().add(routeTable);
                        initialize(null, resources);
                    } catch (Exception ex) {
                        logger.error("Fehler beim erneuten Initialisieren der Route-View", ex);
                        showFallbackUI("Erneuter Fehler", ex.getMessage());
                    }
                });

                errorBox.getChildren().addAll(errorLabel, detailsLabel, retryButton);
                parent.getChildren().add(errorBox);
            }

        } catch (Exception e) {
            // Letzte Rettung: Logge den Fehler, wenn sogar die Fallback-UI fehlschlägt
            logger.error("Fehler beim Anzeigen der Fallback-UI", e);
        }
    }

    /**
     * Konfiguriert die Tabellenspalten
     */
    private void setupTableColumns() {
        // Null-Check für alle verwendeten Elemente
        if (codeColumn == null || departureColumn == null || arrivalColumn == null ||
                distanceColumn == null || statusColumn == null) {
            logger.error("Eine oder mehrere TableColumns sind null");
            return;
        }

        codeColumn.setCellValueFactory(new PropertyValueFactory<>("routeCode"));

        departureColumn.setCellValueFactory(cellData -> {
            if (cellData != null && cellData.getValue() != null &&
                    cellData.getValue().getDepartureAirport() != null) {
                Airport airport = cellData.getValue().getDepartureAirport();
                // Sicherer Zugriff auf City und Country
                String city = airport.getCity() != null ? airport.getCity() : "N/A";
                String country = airport.getCountry() != null ? airport.getCountry() : "N/A";
                return new SimpleStringProperty(airport.getIcaoCode() + " - " + city + ", " + country);
            }
            return new SimpleStringProperty("N/A");
        });

        arrivalColumn.setCellValueFactory(cellData -> {
            if (cellData != null && cellData.getValue() != null &&
                    cellData.getValue().getArrivalAirport() != null) {
                Airport airport = cellData.getValue().getArrivalAirport();
                // Sicherer Zugriff auf City und Country
                String city = airport.getCity() != null ? airport.getCity() : "N/A";
                String country = airport.getCountry() != null ? airport.getCountry() : "N/A";
                return new SimpleStringProperty(airport.getIcaoCode() + " - " + city + ", " + country);
            }
            return new SimpleStringProperty("N/A");
        });

        distanceColumn.setCellValueFactory(new PropertyValueFactory<>("distanceKm"));
        // Formatierung für Distanz (z.B. eine Nachkommastelle)
        distanceColumn.setCellFactory(column -> new TableCell<Route, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item <= 0) {
                    setText(null);
                } else {
                    setText(String.format("%.1f km", item));
                }
            }
        });


        try {
            statusColumn.setCellValueFactory(cellData -> {
                if (cellData != null && cellData.getValue() != null) {
                    String activeText = "Aktiv";
                    String inactiveText = "Inaktiv";

                    try {
                        if (resources != null) {
                            // Versuche zuerst die spezifischeren Schlüssel für Routenstatus
                            try {
                                activeText = resources.getString("route.status.active");
                                inactiveText = resources.getString("route.status.inactive");
                            } catch (MissingResourceException e) {
                                // Fallback auf allgemeine Schlüssel
                                activeText = resources.getString("active");
                                inactiveText = resources.getString("inactive");
                            }
                        }
                    } catch (MissingResourceException e) {
                        logger.warn("Fehlende Ressourcenschlüssel für Status", e);
                        // Fallback auf Standardwerte (bereits gesetzt)
                    }

                    return new SimpleStringProperty(cellData.getValue().isActive() ? activeText : inactiveText);
                }
                return new SimpleStringProperty("N/A");
            });
            // CellFactory für Styling (optional, kann auch über CSS erfolgen)
            statusColumn.setCellFactory(column -> new TableCell<Route, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().removeAll("status-active", "status-inactive"); // CSS-Klassen entfernen
                    setText(null);

                    if (!empty && item != null) {
                        setText(item);
                        if (item.equals("Aktiv") || item.equals(resources != null ? resources.getString("route.status.active") : "Aktiv")) {
                            getStyleClass().add("status-active");
                        } else {
                            getStyleClass().add("status-inactive");
                        }
                    }
                }
            });

        } catch (Exception e) {
            logger.error("Fehler beim Einrichten der Status-Spalte", e);
            // Fallback für Status-Spalte
            statusColumn.setCellValueFactory(cellData ->
                    new SimpleStringProperty(cellData.getValue() != null && cellData.getValue().isActive() ?
                            "Aktiv" : "Inaktiv"));
        }
    }



    /**
     * Richtet die Filter für die Routentabelle ein
     */
    private void setupFilters() {

        try {
            filteredRoutes = new FilteredList<>(routes, p -> true);

            // Null-Checks für UI-Elemente
            if (searchField != null) {
                // Suchfeld-Filter
                searchField.textProperty().addListener((obs, oldVal, newVal) -> updateFilters());
            } else {
                logger.warn("SearchField ist null - Filter für Sucheingabe wird nicht eingerichtet");
            }

            if (activeOnlyFilter != null) {
                // Aktiv-Filter
                activeOnlyFilter.selectedProperty().addListener((obs, oldVal, newVal) -> updateFilters());
            } else {
                logger.warn("ActiveOnlyFilter ist null - Filter für aktive Routen wird nicht eingerichtet");
            }

            // Filtrierte Liste der Tabelle zuweisen
            if (routeTable != null) {
                routeTable.setItems(filteredRoutes);
            } else {
                logger.error("RouteTable ist null - kann gefilterte Liste nicht zuweisen");
            }
        } catch (Exception e) {
            logger.error("Fehler beim Einrichten der Filter", e);
        }
    }

    /**
     * Aktualisiert die Filter basierend auf den aktuellen Filtereinstellungen
     */
    private void updateFilters() {
        // Null-Check für die filteredRoutes-Collection
        if (filteredRoutes == null) {
            logger.error("FilteredRoutes ist null - kann Filter nicht aktualisieren");
            return;
        }

        try {
            filteredRoutes.setPredicate(route -> {
                // Null-Check für die Route
                if (route == null) {
                    return false;
                }

                // Suchfilter
                if (searchField != null && searchField.getText() != null && !searchField.getText().isEmpty()) {
                    String searchText = searchField.getText().toLowerCase();

                    // Sichere Zugriffe mit Null-Checks
                    boolean matchesRouteCode = route.getRouteCode() != null &&
                            route.getRouteCode().toLowerCase().contains(searchText);

                    boolean matchesDeparture = route.getDepartureAirport() != null &&
                            route.getDepartureAirport().getIcaoCode() != null &&
                            route.getDepartureAirport().getIcaoCode().toLowerCase().contains(searchText);

                    boolean matchesArrival = route.getArrivalAirport() != null &&
                            route.getArrivalAirport().getIcaoCode() != null &&
                            route.getArrivalAirport().getIcaoCode().toLowerCase().contains(searchText);

                    // Prüfe auch Stadt und Land
                    boolean matchesDepCity = route.getDepartureAirport() != null &&
                            route.getDepartureAirport().getCity() != null &&
                            route.getDepartureAirport().getCity().toLowerCase().contains(searchText);
                    boolean matchesArrCity = route.getArrivalAirport() != null &&
                            route.getArrivalAirport().getCity() != null &&
                            route.getArrivalAirport().getCity().toLowerCase().contains(searchText);
                    boolean matchesDepCountry = route.getDepartureAirport() != null &&
                            route.getDepartureAirport().getCountry() != null &&
                            route.getDepartureAirport().getCountry().toLowerCase().contains(searchText);
                    boolean matchesArrCountry = route.getArrivalAirport() != null &&
                            route.getArrivalAirport().getCountry() != null &&
                            route.getArrivalAirport().getCountry().toLowerCase().contains(searchText);


                    if (!matchesRouteCode && !matchesDeparture && !matchesArrival &&
                            !matchesDepCity && !matchesArrCity && !matchesDepCountry && !matchesArrCountry) {
                        return false;
                    }
                }

                // Aktiv-Filter
                return activeOnlyFilter == null || !activeOnlyFilter.isSelected() || route.isActive();
            });

            // Status-Label aktualisieren (null-sicher)
            updateTotalLabel();

        } catch (Exception e) {
            logger.error("Fehler beim Aktualisieren der Filter", e);
        }
    }

    /**
     * Aktualisiert das Label mit der Gesamtanzahl der Routen
     */
    private void updateTotalLabel() {
        if (totalLabel == null) {
            logger.warn("TotalLabel ist null - kann nicht aktualisiert werden");
            return;
        }

        try {
            int filteredSize = filteredRoutes != null ? filteredRoutes.size() : 0;

            // Simpel und direkt: verwende String.format oder eine einfache Verkettung
            totalLabel.setText("Routen gesamt: " + filteredSize);
        } catch (Exception e) {
            logger.error("Fehler beim Aktualisieren des TotalLabels", e);
            totalLabel.setText("Routen: " + (filteredRoutes != null ? filteredRoutes.size() : 0));
        }
    }

    /**
     * Lädt alle Daten aus den Services
     */
    private void loadAllData() {
        statusLabel.setText("Lade Routen...");
        if (routeTable != null) {
            routeTable.setDisable(true);
        }
        long startTime = System.currentTimeMillis();

        // Asynchrones Laden
        CompletableFuture.supplyAsync(() -> {
            try {
                // Prüfen, ob der Benutzer ein Admin ist
                User currentUser = SessionManager.getInstance().getCurrentUser();
                routes.clear();
                if (currentUser != null && !currentUser.isAdmin()) {
                    // Für normale Benutzer: Nur Routen der Standard-Airline
                    Airline defaultAirline = Airline.getInstance();
                    return routeService.getRoutesByAirline(defaultAirline);
                } else {
                    // Für Admins: Alle Routen
                    return routeService.getAllRoutes();
                }
            } catch (Exception e) {
                logger.error("Fehler beim Laden der Routen", e);
                throw new RuntimeException(e);
            }
        }).thenAccept(allRoutes -> {
            // UI-Thread für Datenaktualisierung
            Platform.runLater(() -> {
                long duration = System.currentTimeMillis() - startTime;
                routes.clear();
                routes.addAll(allRoutes);

                // Filter und UI aktualisieren
                updateFilters();

                if (routeTable != null) {
                    routeTable.setDisable(false);
                }

                // Status-Label mit Zeitanzeige
                statusLabel.setText("Routen erfolgreich geladen (" + TimeUtils.formatLoadingTime(duration) + ")");

                logger.info("Routen geladen: {} Routen in {} ms", allRoutes.size(), duration);
            });
        }).exceptionally(e -> {
            Platform.runLater(() -> {
                if (routeTable != null) {
                    routeTable.setDisable(false);
                }
                statusLabel.setText("Fehler beim Laden der Routen");
                ExceptionHandler.handleException(e, "beim Laden der Routen-Daten");
            });
            return null;
        });
    }

    /**
     * Zeigt die Details einer Route an
     * (Wird beim Einzelklick in der Tabelle aufgerufen)
     */
    private void showRouteDetails(Route route) {
        if (route == null) {
            logger.warn("showRouteDetails wurde mit einer null-Route aufgerufen");
            return;
        }

        currentRoute = route;

        if (statusLabel != null) {
            String statusMessage = "Route " + route.getRouteCode() + " ausgewählt";

            statusLabel.setText(statusMessage);
        }
    }

    /**
     * Lädt die Flüge für eine bestimmte Route.
     * Diese Methode wird derzeit nicht aktiv genutzt, könnte aber für
     * zukünftige Features (z.B. Löschprüfung) verwendet werden.
     *
     * @param route Die Route, für die Flüge geladen werden sollen.
     */
    private void loadFlightsForRoute(Route route) {
        if (route == null || route.getId() == null) {
            logger.warn("loadFlightsForRoute wurde mit einer null-Route oder Route ohne ID aufgerufen");
            return;
        }

        try {
            List<Flight> flights = flightService.getFlightsByRoute(route);
            logger.debug("Geladene Flüge für Route {}: {}", route.getRouteCode(), flights.size());
            // Die Liste 'flights' wird derzeit nicht in der UI angezeigt
        } catch (Exception e) {
            logger.error("Fehler beim Laden der Flüge für Route {}", route.getRouteCode(), e);
        }
    }

    /**
     * Event-Handler: Neue Route erstellen
     */
    @FXML
    private void handleNewRoute(ActionEvent event) {
        showAddDialog();
    }

    /**
     * Zeigt den Dialog zum Hinzufügen einer neuen Route
     */
    private void showAddDialog() {
        Dialog<Route> dialog = new Dialog<>();
        dialog.setTitle("Neue Route");
        dialog.setHeaderText("Route-Details eingeben");

        ButtonType saveButtonType = new ButtonType("Speichern", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        // ComboBox für den Abflughafen
        ComboBox<Airport> departureAirportCombo = new ComboBox<>();
        departureAirportCombo.setItems(FXCollections.observableArrayList(airportService.getAllAirports()));
        departureAirportCombo.setCellFactory(p -> new ListCell<>() {
            @Override
            protected void updateItem(Airport item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getIcaoCode() + " - " + item.getName());
            }
        });
        departureAirportCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Airport item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getIcaoCode() + " - " + item.getName());
            }
        });

        // ComboBox für den Zielflughafen
        ComboBox<Airport> arrivalAirportCombo = new ComboBox<>();
        arrivalAirportCombo.setItems(FXCollections.observableArrayList(airportService.getAllAirports()));
        arrivalAirportCombo.setCellFactory(p -> new ListCell<>() {
            @Override
            protected void updateItem(Airport item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getIcaoCode() + " - " + item.getName());
            }
        });
        arrivalAirportCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Airport item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getIcaoCode() + " - " + item.getName());
            }
        });

        // Textfeld für die berechnete Distanz (nur Anzeige)
        TextField distanceField = new TextField();
        distanceField.setEditable(false);
        distanceField.setPromptText("Wird automatisch berechnet");
        distanceField.getStyleClass().add("read-only-field");

        // Textfelder für die berechneten Flugzeiten (nur Anzeige)
        TextField flightTime700Field = new TextField();
        flightTime700Field.setEditable(false);
        flightTime700Field.setPromptText("Wird automatisch berechnet");
        flightTime700Field.getStyleClass().add("read-only-field");

        TextField flightTime800Field = new TextField();
        flightTime800Field.setEditable(false);
        flightTime800Field.setPromptText("Wird automatisch berechnet");
        flightTime800Field.getStyleClass().add("read-only-field");

        TextField flightTime900Field = new TextField();
        flightTime900Field.setEditable(false);
        flightTime900Field.setPromptText("Wird automatisch berechnet");
        flightTime900Field.getStyleClass().add("read-only-field");

        // Checkbox für den aktiven Status
        CheckBox activeCheck = new CheckBox();
        activeCheck.setSelected(true);

        // Automatische Berechnung bei Auswahl der Flughäfen
        departureAirportCombo.valueProperty().addListener((obs, oldVal, newVal) -> calculateDistanceForDialog(departureAirportCombo.getValue(), arrivalAirportCombo.getValue(),
                distanceField, flightTime700Field, flightTime800Field, flightTime900Field));

        arrivalAirportCombo.valueProperty().addListener((obs, oldVal, newVal) -> calculateDistanceForDialog(departureAirportCombo.getValue(), arrivalAirportCombo.getValue(),
                distanceField, flightTime700Field, flightTime800Field, flightTime900Field));

        // Elemente zum Grid hinzufügen
        grid.add(new Label("Abflughafen:"), 0, 0);
        grid.add(departureAirportCombo, 1, 0);
        grid.add(new Label("Zielflughafen:"), 0, 1);
        grid.add(arrivalAirportCombo, 1, 1);
        grid.add(new Label("Distanz (km):"), 0, 2);
        grid.add(distanceField, 1, 2);

        grid.add(new Label("Flugzeit (min) bei..."), 0, 3);
        VBox flightTimesBox = new VBox(5);
        HBox time700Box = new HBox(5);
        time700Box.getChildren().addAll(new Label("700 km/h:"), flightTime700Field);
        HBox time800Box = new HBox(5);
        time800Box.getChildren().addAll(new Label("800 km/h:"), flightTime800Field);
        HBox time900Box = new HBox(5);
        time900Box.getChildren().addAll(new Label("900 km/h:"), flightTime900Field);
        flightTimesBox.getChildren().addAll(time700Box, time800Box, time900Box);
        grid.add(flightTimesBox, 1, 3);

        grid.add(new Label("Aktiv:"), 0, 4);
        grid.add(activeCheck, 1, 4);

        dialog.getDialogPane().setContent(grid);

        // Result Converter
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    Airport departure = departureAirportCombo.getValue();
                    Airport arrival = arrivalAirportCombo.getValue();

                    // Airline von der Standard-Airline verwenden (Single-Airline-Architektur)
                    Airline airline = Airline.getInstance();

                    if (departure == null || arrival == null) {
                        showError("Unvollständige Eingabe", "Fehlende Flughäfen",
                                "Bitte wählen Sie sowohl einen Abflug- als auch einen Zielflughafen aus.");
                        return null;
                    }

                    if (departure.equals(arrival)) {
                        showError("Ungültige Eingabe", "Identische Flughäfen",
                                "Abflug- und Zielflughafen dürfen nicht identisch sein.");
                        return null;
                    }

                    // Neue Route erstellen
                    Route newRoute = new Route(departure, arrival, airline);

                    // Distanz und Flugzeit einstellen
                    double distance = FlightUtils.calculateDistance(departure, arrival);
                    int flightTime = FlightUtils.calculateFlightTime(distance, 800.0); // Standard: 800 km/h

                    newRoute.setDistanceKm(distance);
                    newRoute.setFlightTimeMinutes(flightTime);
                    newRoute.setActive(activeCheck.isSelected());

                    return newRoute;
                } catch (Exception e) {
                    logger.error("Fehler beim Erstellen der Route", e);
                    showError("Fehler", "Eingabefehler", e.getMessage());
                    return null;
                }
            }
            return null;
        });

        Optional<Route> result = dialog.showAndWait();
        result.ifPresent(route -> {
            try {
                // Route in der Datenbank speichern
                routeService.saveRoute(route);
                // Daten neu laden
                loadAllData();
                // Status anzeigen
                if (statusLabel != null) {
                    statusLabel.setText("Route " + route.getRouteCode() + " erfolgreich erstellt");
                }
            } catch (Exception e) {
                logger.error("Fehler beim Speichern der Route", e);
                showError("Fehler", "Speicherfehler", "Die Route konnte nicht gespeichert werden: " + e.getMessage());
            }
        });
    }

    /**
     * Hilfsmethode zur Berechnung der Distanz und Flugzeiten im Dialog
     */
    private void calculateDistanceForDialog(Airport departure, Airport arrival,
                                            TextField distanceField,
                                            TextField flightTime700Field,
                                            TextField flightTime800Field,
                                            TextField flightTime900Field) {
        if (departure != null && arrival != null) {
            // Berechne die Distanz
            double distance = FlightUtils.calculateDistance(departure, arrival);
            distanceField.setText(String.format("%.1f", distance));

            // Berechne Flugzeiten für verschiedene Geschwindigkeiten
            int flightTime700 = FlightUtils.calculateFlightTime(distance, 700.0);
            int flightTime800 = FlightUtils.calculateFlightTime(distance, 800.0);
            int flightTime900 = FlightUtils.calculateFlightTime(distance, 900.0);

            // Setze die Werte in die Textfelder
            flightTime700Field.setText(String.valueOf(flightTime700));
            flightTime800Field.setText(String.valueOf(flightTime800));
            flightTime900Field.setText(String.valueOf(flightTime900));
        } else {
            // Leere die Felder, wenn ein Flughafen fehlt
            distanceField.clear();
            flightTime700Field.clear();
            flightTime800Field.clear();
            flightTime900Field.clear();
        }
    }

    /**
     * Event-Handler: Daten aktualisieren
     */
    @FXML
    private void handleRefresh(ActionEvent event) {
        loadAllData();
    }

    /**
     * Event-Handler: Eingabefelder zurücksetzen
     * (Bleibt als leere Methode für Abwärtskompatibilität)
     */
    @FXML
    private void handleClear(ActionEvent event) {
        logger.debug("handleClear aufgerufen - keine Aktion erforderlich");
    }

    /**
     * Event-Handler: Route speichern/aktualisieren
     * (Bleibt als leere Methode für Abwärtskompatibilität)
     */
    @FXML
    private void handleSave(ActionEvent event) {
        logger.debug("handleSave aufgerufen - keine Aktion erforderlich");
    }



    /**
     * Setzt alle Detailfelder zurück
     * (Angepasst für Dialoge statt Seitenleiste)
     */
    private void clearDetails() {
        currentRoute = null;

        if (statusLabel != null) {
            String readyMessage = "Bereit";

            statusLabel.setText(readyMessage);
        }
    }

    /**
     * Zeigt den Dialog zum Bearbeiten einer existierenden Route
     */
    private void showEditDialog(Route route) {
        if (route == null) {
            logger.warn("Versuch, Bearbeitungsdialog für null-Route anzuzeigen.");
            return;
        }
        Dialog<Route> dialog = new Dialog<>();
        dialog.setTitle("Route bearbeiten");
        dialog.setHeaderText("Route-Details bearbeiten: " + route.getRouteCode());

        ButtonType saveButtonType = new ButtonType("Speichern", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        // ComboBox für den Abflughafen
        ComboBox<Airport> departureAirportCombo = new ComboBox<>();
        departureAirportCombo.setItems(FXCollections.observableArrayList(airportService.getAllAirports()));
        departureAirportCombo.setCellFactory(p -> new ListCell<>() {
            @Override
            protected void updateItem(Airport item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getIcaoCode() + " - " + item.getName());
            }
        });
        departureAirportCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Airport item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getIcaoCode() + " - " + item.getName());
            }
        });

        // ComboBox für den Zielflughafen
        ComboBox<Airport> arrivalAirportCombo = new ComboBox<>();
        arrivalAirportCombo.setItems(FXCollections.observableArrayList(airportService.getAllAirports()));
        arrivalAirportCombo.setCellFactory(p -> new ListCell<>() {
            @Override
            protected void updateItem(Airport item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getIcaoCode() + " - " + item.getName());
            }
        });
        arrivalAirportCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Airport item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getIcaoCode() + " - " + item.getName());
            }
        });

        // Textfeld für die berechnete Distanz (nur Anzeige)
        TextField distanceField = new TextField();
        distanceField.setEditable(false);
        distanceField.setPromptText("Wird automatisch berechnet");
        distanceField.getStyleClass().add("read-only-field");

        // Textfelder für die berechneten Flugzeiten (nur Anzeige)
        TextField flightTime700Field = new TextField();
        flightTime700Field.setEditable(false);
        flightTime700Field.setPromptText("Wird automatisch berechnet");
        flightTime700Field.getStyleClass().add("read-only-field");

        TextField flightTime800Field = new TextField();
        flightTime800Field.setEditable(false);
        flightTime800Field.setPromptText("Wird automatisch berechnet");
        flightTime800Field.getStyleClass().add("read-only-field");

        TextField flightTime900Field = new TextField();
        flightTime900Field.setEditable(false);
        flightTime900Field.setPromptText("Wird automatisch berechnet");
        flightTime900Field.getStyleClass().add("read-only-field");

        // Checkbox für den aktiven Status
        CheckBox activeCheck = new CheckBox();

        // Bestehende Werte setzen
        departureAirportCombo.setValue(route.getDepartureAirport());
        arrivalAirportCombo.setValue(route.getArrivalAirport());
        distanceField.setText(String.format("%.1f", route.getDistanceKm()));
        flightTime800Field.setText(String.valueOf(route.getFlightTimeMinutes()));

        // Berechne Flugzeiten für verschiedene Geschwindigkeiten
        double distance = route.getDistanceKm();
        int flightTime700 = FlightUtils.calculateFlightTime(distance, 700.0);
        int flightTime900 = FlightUtils.calculateFlightTime(distance, 900.0);
        flightTime700Field.setText(String.valueOf(flightTime700));
        flightTime900Field.setText(String.valueOf(flightTime900));

        activeCheck.setSelected(route.isActive());

        // Automatische Berechnung bei Auswahl der Flughäfen
        departureAirportCombo.valueProperty().addListener((obs, oldVal, newVal) -> calculateDistanceForDialog(departureAirportCombo.getValue(), arrivalAirportCombo.getValue(),
                distanceField, flightTime700Field, flightTime800Field, flightTime900Field));

        arrivalAirportCombo.valueProperty().addListener((obs, oldVal, newVal) -> calculateDistanceForDialog(departureAirportCombo.getValue(), arrivalAirportCombo.getValue(),
                distanceField, flightTime700Field, flightTime800Field, flightTime900Field));

        // Elemente zum Grid hinzufügen
        grid.add(new Label("Abflughafen:"), 0, 0);
        grid.add(departureAirportCombo, 1, 0);
        grid.add(new Label("Zielflughafen:"), 0, 1);
        grid.add(arrivalAirportCombo, 1, 1);
        grid.add(new Label("Distanz (km):"), 0, 2);
        grid.add(distanceField, 1, 2);

        grid.add(new Label("Flugzeit (min) bei..."), 0, 3);
        VBox flightTimesBox = new VBox(5);
        HBox time700Box = new HBox(5);
        time700Box.getChildren().addAll(new Label("700 km/h:"), flightTime700Field);
        HBox time800Box = new HBox(5);
        time800Box.getChildren().addAll(new Label("800 km/h:"), flightTime800Field);
        HBox time900Box = new HBox(5);
        time900Box.getChildren().addAll(new Label("900 km/h:"), flightTime900Field);
        flightTimesBox.getChildren().addAll(time700Box, time800Box, time900Box);
        grid.add(flightTimesBox, 1, 3);

        grid.add(new Label("Aktiv:"), 0, 4);
        grid.add(activeCheck, 1, 4);

        dialog.getDialogPane().setContent(grid);

        // Result Converter
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    Airport departure = departureAirportCombo.getValue();
                    Airport arrival = arrivalAirportCombo.getValue();

                    // Airline wird beibehalten
                    Airline airline = route.getOperator();

                    // Validierung
                    if (departure == null || arrival == null) {
                        showError("Unvollständige Eingabe", "Fehlende Flughäfen",
                                "Bitte wählen Sie sowohl einen Abflug- als auch einen Zielflughafen aus.");
                        return null;
                    }

                    if (departure.equals(arrival)) {
                        showError("Ungültige Eingabe", "Identische Flughäfen",
                                "Abflug- und Zielflughafen dürfen nicht identisch sein.");
                        return null;
                    }

                    // Bestehende Route aktualisieren
                    route.setDepartureAirport(departure);
                    route.setArrivalAirport(arrival);
                    // route.setOperator(airline); // Operator wird nicht geändert

                    // Distanz und Flugzeit aktualisieren
                    double newDistance = FlightUtils.calculateDistance(departure, arrival);
                    int flightTime = FlightUtils.calculateFlightTime(newDistance, 800.0); // Standard: 800 km/h

                    route.setDistanceKm(newDistance);
                    route.setFlightTimeMinutes(flightTime);
                    route.setActive(activeCheck.isSelected());

                    return route;
                } catch (Exception e) {
                    logger.error("Fehler beim Aktualisieren der Route", e);
                    showError("Fehler", "Eingabefehler", e.getMessage());
                    return null;
                }
            }
            return null;
        });

        Optional<Route> result = dialog.showAndWait();
        result.ifPresent(updatedRoute -> {
            try {
                // Route in der Datenbank speichern
                routeService.saveRoute(updatedRoute);
                // Daten neu laden
                loadAllData();
                // Status anzeigen
                if (statusLabel != null) {
                    statusLabel.setText("Route " + updatedRoute.getRouteCode() + " erfolgreich aktualisiert");
                }
            } catch (Exception e) {
                logger.error("Fehler beim Speichern der Route", e);
                showError("Fehler", "Speicherfehler", "Die Route konnte nicht gespeichert werden: " + e.getMessage());
            }
        });
    }

    /**
     * Zeigt einen Fehlerdialog an
     */
    private void showError(String title, String header, String content) {
        logger.error("{}: {} - {}", title, header, content);
        ExceptionHandler.showErrorDialog(title, header + ": " + content);
    }

    /**
     * Zeigt einen Bestätigungsdialog an
     */
    private boolean showConfirmation(String title, String header, String content) {
        logger.info("Bestätigungsdialog: {} - {}", header, content);
        return ExceptionHandler.showConfirmDialog(title, header + "\n\n" + content);
    }

    /**
     * Hilfsklasse für Debug-Zwecke zur Prüfung des initialen Status der Methoden und Felder
     * Kann bei Bedarf entfernt werden
     */
    private void checkInitialState() {
        logger.debug("AdminRoutesViewController Initialstatus:");
        logger.debug("ResourceBundle null: {}", resources == null);
        logger.debug("RouteTable null: {}", routeTable == null);
        logger.debug("SearchField null: {}", searchField == null);
        logger.debug("StatusLabel null: {}", statusLabel == null);
        logger.debug("TotalLabel null: {}", totalLabel == null);
    }
}
