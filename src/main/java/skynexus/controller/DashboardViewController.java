package skynexus.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.enums.AircraftStatus;
import skynexus.enums.FlightStatus;
import skynexus.model.*;
import skynexus.service.*;
import skynexus.util.SessionManager;
import skynexus.util.TimeUtils;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Controller für die Dashboard-Ansicht.
 * Zeigt die Startseite nach erfolgreicher Anmeldung an mit konsolidierten Informationen
 * zu Flughafen und Airline.
 */
public class DashboardViewController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(DashboardViewController.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final ObservableList<Flight> departures = FXCollections.observableArrayList();
    private final ObservableList<Flight> arrivals = FXCollections.observableArrayList();
    // Allgemeine UI-Elemente
    @FXML
    private ImageView dashboardImage;
    @FXML
    private AnchorPane root;
    // Flughafen-Info
    @FXML
    private Label airportNameLabel;
    @FXML
    private Label airportIcaoLabel;
    @FXML
    private Label localTimeLabel;
    @FXML
    private VBox airportInfoContainer;
    // Wetter-Info
    @FXML
    private Label temperatureLabel;
    @FXML
    private Label visibilityLabel;
    @FXML
    private Label pressureLabel;
    // Abflug-Tabelle
    @FXML
    private TableView<Flight> departuresTableView;
    @FXML
    private TableColumn<Flight, String> depFlightColumn;
    @FXML
    private TableColumn<Flight, String> depDestinationColumn;
    @FXML
    private TableColumn<Flight, Object> depTimeColumn; // Geändert zu Object für gemischte Inhalte
    @FXML
    private TableColumn<Flight, String> depStatusColumn;
    // Ankunft-Tabelle
    @FXML
    private TableView<Flight> arrivalsTableView;
    @FXML
    private TableColumn<Flight, String> arrFlightColumn;
    @FXML
    private TableColumn<Flight, String> arrOriginColumn;
    @FXML
    private TableColumn<Flight, Object> arrTimeColumn; // Geändert zu Object für gemischte Inhalte
    @FXML
    private TableColumn<Flight, String> arrStatusColumn;
    // Airline-Info
    @FXML
    private Label airlineNameLabel;
    @FXML
    private Label airlineIcaoLabel;
    @FXML
    private Label airlineCountryLabel;
    // Flotten-Status
    @FXML
    private Label totalAircraftLabel;
    @FXML
    private Label activeAircraftLabel;
    @FXML
    private Label groundedAircraftLabel;
    // Passagier-Statistiken
    @FXML
    private Label todayPaxLabel;
    @FXML
    private Label monthlyPaxLabel;
    @FXML
    private Label loadFactorLabel;

    // Services (werden jetzt über Konstruktor injiziert)
    private final AirportService airportService;
    private final AirlineService airlineService;
    private final FlightService flightService;
    private final AircraftService aircraftService;
    private final WeatherService weatherService;

    // Daten für das Dashboard
    private Airport currentAirport;
    private Airline currentAirline;
    // Timer für regelmäßige Aktualisierungen
    private ScheduledExecutorService scheduler;

    /**
     * Standardkonstruktor für FXML-Loader.
     * HINWEIS: Dieser Konstruktor wird vom FXML-Loader benötigt.
     * Die injizierten Services werden hier durch Singleton-Instanzen ersetzt.
     */
    public DashboardViewController() {
        this(
                AirportService.getInstance(),
                AirlineService.getInstance(),
                FlightService.getInstance(),
                AircraftService.getInstance(),
                WeatherService.getInstance()
        );
        logger.debug("DashboardViewController mit Standard-Constructor erstellt (für FXML-Loader).");
    }

    /**
     * Konstruktor für Dependency Injection.
     * HINWEIS: Dieser Konstruktor muss von außen aufgerufen werden
     * (z.B. über eine Controller Factory beim FXML Laden) und die Service-Instanzen
     * müssen übergeben werden.
     *
     * @param airportService  Der AirportService.
     * @param airlineService  Der AirlineService.
     * @param flightService   Der FlightService.
     * @param aircraftService Der AircraftService.
     * @param weatherService  Der WeatherService.
     */
    public DashboardViewController(AirportService airportService, AirlineService airlineService,
                                   FlightService flightService, AircraftService aircraftService,
                                   WeatherService weatherService) {
        this.airportService = airportService;
        this.airlineService = airlineService;
        this.flightService = flightService;
        this.aircraftService = aircraftService;
        this.weatherService = weatherService;
        logger.debug("DashboardViewController Instanz erstellt mit injizierten Services.");
    }


    /**
     * Initialisiert den Controller und richtet responsives Verhalten ein.
     * Verwendet asynchrones Laden für bessere UI-Responsivität
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("DashboardView wird initialisiert");

        // Services wurden bereits im Konstruktor injiziert

        // UI-Elemente konfigurieren
        setupResponsiveImage();
        setupTableColumns();

        // UI mit Platzhaltern initialisieren
        initializeUIWithPlaceholders();

        // Daten asynchron laden
        loadDataInBackground();

        // Periodische Aktualisierung starten
        startPeriodicUpdates();
    }

    /**
     * Initialisiert die UI mit Platzhaltern, um sofort etwas anzuzeigen
     */
    private void initializeUIWithPlaceholders() {
        // Flughafen-Platzhalter
        if (airportNameLabel != null) airportNameLabel.setText("Wird geladen...");
        if (airportIcaoLabel != null) airportIcaoLabel.setText("ICAO: ---");
        if (localTimeLabel != null) localTimeLabel.setText("Lokalzeit: --:--");

        // Wetter-Platzhalter
        if (temperatureLabel != null) temperatureLabel.setText("Temperatur: -- °C");
        if (visibilityLabel != null) visibilityLabel.setText("Sichtweite: -- km");
        if (pressureLabel != null) pressureLabel.setText("Luftdruck: ---- hPa");

        // Airline-Platzhalter
        if (airlineNameLabel != null) airlineNameLabel.setText("Wird geladen...");
        if (airlineIcaoLabel != null) airlineIcaoLabel.setText("ICAO: ---");
        if (airlineCountryLabel != null) airlineCountryLabel.setText("Land: ---");

        // Flotten-Platzhalter
        if (totalAircraftLabel != null) totalAircraftLabel.setText("Gesamt: -- Flugzeuge");
        if (activeAircraftLabel != null) activeAircraftLabel.setText("Im Einsatz: -- Flugzeuge");
        if (groundedAircraftLabel != null) groundedAircraftLabel.setText("Am Boden: -- Flugzeuge");
    }

    /**
     * Lädt die Daten asynchron im Hintergrund
     */
    private void loadDataInBackground() {
        Thread loadingThread = new Thread(() -> {
            try {
                // Benutzerkontext laden (Flughafen/Airline)
                loadUserContext();

                // UI-Aktualisierung auf dem JavaFX-Thread
                Platform.runLater(this::refreshDashboard);
            } catch (Exception e) {
                logger.error("Fehler beim Laden der Dashboard-Daten im Hintergrund", e);
                Platform.runLater(() -> showErrorAlert("Fehler beim Laden der Daten",
                        "Die Dashboard-Daten konnten nicht geladen werden: " + e.getMessage()));
            }
        });

        loadingThread.setDaemon(true); // Damit der Thread die Anwendung nicht am Beenden hindert
        loadingThread.setName("Dashboard-Loader"); // Gib dem Thread einen Namen für Debugging
        loadingThread.start();
    }

    /**
     * Richtet das responsive Verhalten des Dashboard-Bildes ein
     */
    private void setupResponsiveImage() {
        if (dashboardImage != null && root != null) {
            // Bildbreite automatisch anpassen mit Abstand links/rechts
            root.widthProperty().addListener((obs, oldWidth, newWidth) -> {
                double padding = 100; // 50px links + 50px rechts
                double availableWidth = Math.max(0, newWidth.doubleValue() - padding);
                dashboardImage.setFitWidth(availableWidth);
            });
            // Initiales Setzen der Breite
            Platform.runLater(() -> {
                double initialWidth = root.getWidth();
                double padding = 100;
                double availableWidth = Math.max(0, initialWidth - padding);
                dashboardImage.setFitWidth(availableWidth);
            });
        }
    }

    /**
     * Konfiguriert die Tabellenspalten mit den korrekten CellValueFactories
     */
    private void setupTableColumns() {
        // Abflug-Tabelle konfigurieren
        if (departuresTableView != null && depFlightColumn != null && depDestinationColumn != null &&
                depTimeColumn != null && depStatusColumn != null) {

            depFlightColumn.setCellValueFactory(data ->
                    new SimpleStringProperty(data.getValue().getFlightNumber()));
            depFlightColumn.setCellFactory(column -> createStyledTableCell()); // Verwende Hilfsmethode

            depDestinationColumn.setCellValueFactory(data -> {
                Airport arrival = data.getValue().getArrivalAirport();
                return new SimpleStringProperty(arrival != null ? arrival.getName() : "N/A");
            });
            depDestinationColumn.setCellFactory(column -> createStyledTableCell());

            // Formatierte Anzeige der Abflugszeit (mit möglicher Datumsanzeige wenn nicht heute)
            depTimeColumn.setCellValueFactory(data -> {
                Flight flight = data.getValue();
                LocalTime time = flight.getDepartureTime();
                LocalDate date = flight.getDepartureDate();
                LocalDate today = LocalDate.now();

                if (date != null && time != null) {
                    if (date.equals(today)) {
                        // Nur Zeit anzeigen, wenn das Datum heute ist
                        return new SimpleObjectProperty<>(time);
                    } else {
                        // Datum und Zeit kombinieren für bessere Anzeige bei 24h-Ansicht
                        // Format: "HH:MM (DD.M.)" für übersichtliche Darstellung
                        String formattedDateTime = String.format("%02d:%02d (%d.%d.)",
                                time.getHour(),
                                time.getMinute(),
                                date.getDayOfMonth(),
                                date.getMonthValue());
                        // String direkt als Objekt zurückgeben
                        return new SimpleObjectProperty<>(formattedDateTime);
                    }
                }
                return new SimpleObjectProperty<>(time); // Gibt null zurück, wenn Zeit null ist
            });
            // Spezielle Formatierung für Zeitanzeige
            depTimeColumn.setCellFactory(column -> createFormattedTimeTableCell()); // Verwende Hilfsmethode

            depStatusColumn.setCellValueFactory(data -> {
                FlightStatus status = data.getValue().getStatus();
                return new SimpleStringProperty(status != null ? status.toString() : "N/A");
            });
            depStatusColumn.setCellFactory(column -> createStatusTableCell()); // Verwende Hilfsmethode für Status-Styling

            departuresTableView.setItems(departures); // Datenquelle setzen
        } else {
            logger.error("Einige Spalten der Abflugtabelle sind null.");
        }


        // Ankunft-Tabelle konfigurieren
        if (arrivalsTableView != null && arrFlightColumn != null && arrOriginColumn != null &&
                arrTimeColumn != null && arrStatusColumn != null) {

            arrFlightColumn.setCellValueFactory(data ->
                    new SimpleStringProperty(data.getValue().getFlightNumber()));
            arrFlightColumn.setCellFactory(column -> createStyledTableCell());

            arrOriginColumn.setCellValueFactory(data -> {
                Airport departure = data.getValue().getDepartureAirport();
                return new SimpleStringProperty(departure != null ? departure.getName() : "N/A");
            });
            arrOriginColumn.setCellFactory(column -> createStyledTableCell());

            // Formatierte Anzeige der Ankunftszeit (mit möglicher Datumsanzeige wenn nicht heute)
            arrTimeColumn.setCellValueFactory(data -> {
                Flight flight = data.getValue();
                LocalTime time = flight.getArrivalTime(); // Dynamisch berechnet
                LocalDate date = flight.getArrivalDate(); // Dynamisch berechnet
                LocalDate today = LocalDate.now();

                if (date != null && time != null) {
                    if (date.equals(today)) {
                        // Nur Zeit anzeigen, wenn das Datum heute ist
                        return new SimpleObjectProperty<>(time);
                    } else {
                        // Datum und Zeit kombinieren für bessere Anzeige bei 24h-Ansicht
                        // Format: "HH:MM (DD.M.)" für übersichtliche Darstellung
                        String formattedDateTime = String.format("%02d:%02d (%d.%d.)",
                                time.getHour(),
                                time.getMinute(),
                                date.getDayOfMonth(),
                                date.getMonthValue());
                        // String direkt als Objekt zurückgeben
                        return new SimpleObjectProperty<>(formattedDateTime);
                    }
                }
                return new SimpleObjectProperty<>(time); // Gibt null zurück, wenn Zeit null ist
            });
            // Spezielle Formatierung für Zeitanzeige
            arrTimeColumn.setCellFactory(column -> createFormattedTimeTableCell()); // Verwende Hilfsmethode

            arrStatusColumn.setCellValueFactory(data -> {
                FlightStatus status = data.getValue().getStatus();
                return new SimpleStringProperty(status != null ? status.toString() : "N/A");
            });
            arrStatusColumn.setCellFactory(column -> createStatusTableCell()); // Verwende Hilfsmethode für Status-Styling

            arrivalsTableView.setItems(arrivals); // Datenquelle setzen
        } else {
            logger.error("Einige Spalten der Ankunftstabelle sind null.");
        }

    }

    /**
     * Hilfsmethode zur Erstellung einer Standard-TableCell für Text.
     *
     * @return Eine TableCell-Instanz.
     */
    private <T> TableCell<Flight, T> createStyledTableCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                // Hier könnten allgemeine Styles hinzugefügt werden, wenn nötig
            }
        };
    }

    /**
     * Hilfsmethode zur Erstellung einer TableCell für formatierte Zeit/Datum-Objekte.
     *
     * @return Eine TableCell-Instanz.
     */
    private TableCell<Flight, Object> createFormattedTimeTableCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else if (item instanceof LocalTime time) {
                    // Nur Zeit formatieren (HH:MM Format)
                    setText(String.format("%02d:%02d", time.getHour(), time.getMinute()));
                } else {
                    // Kombiniertes Datum/Zeit oder anderer String
                    setText(item.toString());
                }
            }
        };
    }

    /**
     * Hilfsmethode zur Erstellung einer TableCell für den Flugstatus mit CSS-Styling.
     *
     * @return Eine TableCell-Instanz.
     */
    private TableCell<Flight, String> createStatusTableCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                // Vorherige Style-Klassen entfernen
                getStyleClass().removeAll("status-scheduled", "status-boarding", "status-departed",
                        "status-flying", "status-landed", "status-deplaning",
                        "status-completed", "status-unknown");
                setText(null);

                if (!empty && item != null) {
                    setText(item);
                    // Füge CSS-Klasse basierend auf dem Status hinzu
                    // Die Farben werden in der CSS-Datei definiert (z.B. application.css)
                    String styleClass = "status-" + item.toLowerCase();
                    getStyleClass().add(styleClass);
                }
            }
        };
    }


    /**
     * Lädt die Benutzerkontext-bezogenen Daten mit der Standard-Airline und einem Default-Flughafen
     */
    private void loadUserContext() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser != null) {
            logger.info("Lade Dashboard für Benutzer: {}", currentUser.getUsername());

            try {
                // Standard-Airline verwenden (Single-Airline-Architektur)
                currentAirline = Airline.getInstance();
                logger.info("Standard-Airline geladen: {}", currentAirline.getName());

                // Default-Flughafen aus den Systemeinstellungen laden
                currentAirport = airportService.getDefaultAirport();

                if (currentAirport == null) {
                    logger.warn("Kein Default-Flughafen gefunden.");
                } else {
                    logger.info("Default-Flughafen geladen: {}", currentAirport.getName());
                }

            } catch (Exception e) {
                logger.error("Fehler beim Laden der Benutzer-Kontextdaten", e);
                showErrorAlert("Fehler beim Laden der Daten",
                        "Die Dashboard-Daten konnten nicht vollständig geladen werden: " + e.getMessage());
                currentAirport = null;
                currentAirline = null;
            }
        } else {
            logger.warn("Dashboard ohne angemeldeten Benutzer aufgerufen");
            currentAirport = null;
            currentAirline = null;
        }
    }

    /**
     * Startet periodische Aktualisierungen für dynamische Daten
     * mit optimierter Ressourcennutzung
     */
    private void startPeriodicUpdates() {
        // Verhindere erneutes Starten, falls schon aktiv
        if (scheduler != null && !scheduler.isShutdown()) {
            logger.warn("Scheduler läuft bereits.");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true); // Erlaube Beenden der Anwendung
            t.setName("Dashboard-Updater");
            return t;
        });


        // Lokalzeit jede Sekunde aktualisieren (leichtgewichtige Operation)
        scheduler.scheduleAtFixedRate(() -> Platform.runLater(this::updateLocalTime), 0, 1, TimeUnit.SECONDS);

        // Flugdaten und andere Daten alle 60 Sekunden aktualisieren
        scheduler.scheduleAtFixedRate(() -> Platform.runLater(this::refreshDashboard), 5, 60, TimeUnit.SECONDS);

        logger.info("Periodische Dashboard-Aktualisierungen gestartet.");
    }

    /**
     * Aktualisiert die lokale Uhrzeit des Flughafens mit Zeitzonenangabe
     */
    private void updateLocalTime() {
        if (currentAirport != null && localTimeLabel != null) {
            try {
                // Erweiterte Zeitinformationen mit Zeitzonenangabe abrufen
                TimeUtils.AirportTimeInfo timeInfo = TimeUtils.getAirportTimeInfo(currentAirport);

                // Formatierte Zeitanzeige mit Zeitzone
                String formattedTime = timeInfo.getLocalTime().format(TIME_FORMATTER);
                String timezoneString = timeInfo.getTimezoneString();

                // Aktualisiere Label mit Zeit und Zeitzone
                localTimeLabel.setText(String.format("Lokalzeit: %s (%s)", formattedTime, timezoneString));
            } catch (Exception e) {
                logger.error("Fehler beim Aktualisieren der Lokalzeit", e);
                localTimeLabel.setText("Lokalzeit: Fehler");
            }
        } else if (localTimeLabel != null) {
            // Setze zurück, wenn kein Flughafen ausgewählt
            localTimeLabel.setText("Lokalzeit: --:--");
        }
    }

    /**
     * Aktualisiert alle Dashboard-Elemente mit den neuesten Daten
     */
    public void refreshDashboard() {
        logger.debug("Aktualisiere Dashboard...");
        // Stelle sicher, dass Services verfügbar sind
        if (flightService == null || airportService == null || airlineService == null ||
                aircraftService == null || weatherService == null) {
            logger.error("Dashboard kann nicht aktualisiert werden: Ein oder mehrere Services sind null.");
            showErrorAlert("Interner Fehler", "Dashboard konnte nicht aktualisiert werden (fehlende Services).");
            return;
        }

        // Benutzerkontext neu laden, falls er sich geändert haben könnte (optional)
        // loadUserContext(); // Nur wenn nötig

        if (currentAirport != null) {
            updateAirportInfo();
            updateWeatherInfo();
            updateFlightTables();
        } else {
            logger.warn("Kein Flughafen mit dem Benutzer verknüpft, Flughafen-Infos nicht aktualisiert.");
            // Optional: UI-Elemente für Flughafen leeren/deaktivieren
            clearAirportInfo();
            departures.clear();
            arrivals.clear();
        }

        if (currentAirline != null) {
            updateAirlineInfo();
            updateFleetStatus();
            updatePassengerStats();
        } else {
            logger.warn("Keine Airline mit dem Benutzer verknüpft, Airline-Infos nicht aktualisiert.");
            // Optional: UI-Elemente für Airline leeren/deaktivieren
            clearAirlineInfo();
        }
        logger.debug("Dashboard-Aktualisierung abgeschlossen.");
    }

    /**
     * Leert die Flughafen-Info-Labels
     */
    private void clearAirportInfo() {
        if (airportNameLabel != null) airportNameLabel.setText("Kein Flughafen");
        if (airportIcaoLabel != null) airportIcaoLabel.setText("ICAO: ---");
        if (localTimeLabel != null) localTimeLabel.setText("Lokalzeit: --:--");
        if (temperatureLabel != null) temperatureLabel.setText("Temperatur: -- °C");
        if (visibilityLabel != null) visibilityLabel.setText("Sichtweite: -- km");
        if (pressureLabel != null) pressureLabel.setText("Luftdruck: ---- hPa");
    }

    /**
     * Leert die Airline-Info-Labels und Flottenstatus
     */
    private void clearAirlineInfo() {
        if (airlineNameLabel != null) airlineNameLabel.setText("Keine Airline");
        if (airlineIcaoLabel != null) airlineIcaoLabel.setText("ICAO: ---");
        if (airlineCountryLabel != null) airlineCountryLabel.setText("Land: ---");
        if (totalAircraftLabel != null) totalAircraftLabel.setText("Gesamt: -- Flugzeuge");
        if (activeAircraftLabel != null) activeAircraftLabel.setText("Im Einsatz: -- Flugzeuge");
        if (groundedAircraftLabel != null) groundedAircraftLabel.setText("Am Boden: -- Flugzeuge");
    }


    /**
     * Aktualisiert die Flughafen-Basisinformationen
     */
    private void updateAirportInfo() {
        if (currentAirport != null && airportNameLabel != null && airportIcaoLabel != null) {
            airportNameLabel.setText(currentAirport.getName() != null ? currentAirport.getName() : "Unbekannt");
            airportIcaoLabel.setText("ICAO: " + (currentAirport.getIcaoCode() != null ? currentAirport.getIcaoCode() : "---"));
            updateLocalTime();
        }
    }

    /**
     * Aktualisiert die Wetterdaten für den Flughafen
     * Lädt aktuelle Daten aus dem WeatherService
     */
    private void updateWeatherInfo() {
        // Stelle sicher, dass UI-Elemente und Flughafen-Info vorhanden sind
        if (temperatureLabel == null || visibilityLabel == null ||
                pressureLabel == null || currentAirport == null || currentAirport.getIcaoCode() == null) {
            logger.warn("UpdateWeatherInfo: UI-Elemente oder Flughafeninformationen fehlen.");
            return;
        }

        try {
            String icaoCode = currentAirport.getIcaoCode();

            // Echte Wetterdaten über den WeatherService abrufen
            Integer temperature = weatherService.getTemperature(icaoCode); // Annahme: Gibt Integer zurück
            Integer visibility = weatherService.getVisibility(icaoCode); // Annahme: Gibt Integer zurück
            Integer pressure = weatherService.getPressure(icaoCode); // Annahme: Gibt Integer zurück

            // UI-Elemente aktualisieren (mit Null-Prüfung für Wetterdaten)
            temperatureLabel.setText("Temperatur: " + temperature + " °C");
            visibilityLabel.setText("Sichtweite: " + visibility + " km");
            pressureLabel.setText("Luftdruck: " + pressure + " hPa");

            logger.debug("Wetterdaten für {} aktualisiert", icaoCode);

        } catch (Exception e) {
            logger.error("Fehler beim Laden der Wetterdaten für {}: {}",
                    (currentAirport != null ? currentAirport.getIcaoCode() : "N/A"), e.getMessage(), e);
            // Fehlerbehandlung - Platzhalter anzeigen
            temperatureLabel.setText("Temperatur: -- °C");
            visibilityLabel.setText("Sichtweite: -- km");
            pressureLabel.setText("Luftdruck: ---- hPa");
        }
    }

    /**
     * Aktualisiert die Abflug- und Ankunftstabellen
     * Zeigt nun Flüge der nächsten 24 Stunden (statt nur des aktuellen Tages)
     * Verwendet die optimierten Service-Methoden mit Limit-Parameter
     */
    private void updateFlightTables() {
        // Stelle sicher, dass flightService nicht null ist
        if (flightService == null) {
            logger.error("FlightService ist null in updateFlightTables.");
            return;
        }
        try {
            // Flugstatus basierend auf aktueller Zeit aktualisieren
            flightService.updateFlightStatuses();

            // Heutiges Datum und Folgetag für 24-Stunden-Ansicht
            LocalDate today = LocalDate.now();
            LocalDate tomorrow = today.plusDays(1);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nowPlus24Hours = now.plusHours(24);

            // Tabellen aktualisieren, wenn sie vorhanden sind und ein Flughafen ausgewählt ist
            if (departuresTableView != null && currentAirport != null && currentAirport.getId() != null) {
                List<Flight> todayDepartures = flightService.getDeparturesForAirport(
                        currentAirport.getId(), today, 15); // Mehr Flüge laden, um zu filtern
                List<Flight> tomorrowDepartures = flightService.getDeparturesForAirport(
                        currentAirport.getId(), tomorrow, 15); // Auch Flüge für morgen laden

                // Kombiniere die Listen
                List<Flight> allDepartures = new ArrayList<>();
                allDepartures.addAll(todayDepartures);
                allDepartures.addAll(tomorrowDepartures);

                // Filtere Flüge der nächsten 24 Stunden
                List<Flight> next24HoursDepartures = allDepartures.stream()
                        .filter(flight -> {
                            if (flight == null || flight.getStatus() == FlightStatus.COMPLETED) {
                                return false;
                            }

                            LocalDateTime departureDateTime;
                            if (flight.getDepartureDate() != null && flight.getDepartureTime() != null) {
                                departureDateTime = LocalDateTime.of(flight.getDepartureDate(), flight.getDepartureTime());
                                // Im 24-Stunden-Fenster und nicht in der Vergangenheit
                                return departureDateTime.isAfter(now) && departureDateTime.isBefore(nowPlus24Hours);
                            }
                            return false;
                        })
                        .sorted(Comparator.comparing(flight ->
                                LocalDateTime.of(flight.getDepartureDate(), flight.getDepartureTime())))
                        .limit(8) // Begrenze auf 8 Abflüge für Übersichtlichkeit
                        .toList();

                departures.clear();
                departures.addAll(next24HoursDepartures);
                logger.debug("Nächste 24h Abflüge geladen: {} Flüge", next24HoursDepartures.size());
            } else {
                if (departuresTableView != null) departures.clear(); // Tabelle leeren, wenn kein Flughafen
                logger.debug("Keine Abflüge geladen: currentAirport ist null oder hat keine ID");
            }

            if (arrivalsTableView != null && currentAirport != null && currentAirport.getId() != null) {
                List<Flight> todayArrivals = flightService.getArrivalsForAirport(
                        currentAirport.getId(), today, 15); // Mehr Flüge laden, um zu filtern
                List<Flight> tomorrowArrivals = flightService.getArrivalsForAirport(
                        currentAirport.getId(), tomorrow, 15); // Auch Flüge für morgen laden

                // Kombiniere die Listen
                List<Flight> allArrivals = new ArrayList<>();
                allArrivals.addAll(todayArrivals);
                allArrivals.addAll(tomorrowArrivals);

                // Filtere Flüge der nächsten 24 Stunden
                List<Flight> next24HoursArrivals = allArrivals.stream()
                        .filter(flight -> {
                            if (flight == null || flight.getStatus() == FlightStatus.COMPLETED) {
                                return false;
                            }

                            LocalDateTime arrivalDateTime;
                            if (flight.getArrivalDate() != null && flight.getArrivalTime() != null) {
                                arrivalDateTime = LocalDateTime.of(flight.getArrivalDate(), flight.getArrivalTime());
                                // Im 24-Stunden-Fenster und nicht in der Vergangenheit
                                return arrivalDateTime.isAfter(now) && arrivalDateTime.isBefore(nowPlus24Hours);
                            }
                            return false;
                        })
                        .sorted(Comparator.comparing(flight ->
                                LocalDateTime.of(flight.getArrivalDate(), flight.getArrivalTime())))
                        .limit(8) // Begrenze auf 8 Ankünfte für Übersichtlichkeit
                        .toList();

                arrivals.clear();
                arrivals.addAll(next24HoursArrivals);
                logger.debug("Nächste 24h Ankünfte geladen: {} Flüge", next24HoursArrivals.size());
            } else {
                if (arrivalsTableView != null) arrivals.clear(); // Tabelle leeren, wenn kein Flughafen
                logger.debug("Keine Ankünfte geladen: currentAirport ist null oder hat keine ID");
            }

        } catch (Exception e) {
            logger.error("Fehler beim Laden der Flugdaten für Tabellen: {}", e.getMessage(), e);
            // Optional: Tabellen leeren bei Fehler
            departures.clear();
            arrivals.clear();
        }
    }

    /**
     * Aktualisiert die Airline-Basisinformationen
     */
    private void updateAirlineInfo() {
        if (currentAirline != null && airlineNameLabel != null && airlineIcaoLabel != null &&
                airlineCountryLabel != null) {

            airlineNameLabel.setText(currentAirline.getName() != null ? currentAirline.getName() : "Unbekannt");
            airlineIcaoLabel.setText("ICAO: " + (currentAirline.getIcaoCode() != null ? currentAirline.getIcaoCode() : "---"));
            airlineCountryLabel.setText("Land: " + (currentAirline.getCountry() != null ? currentAirline.getCountry() : "---"));
        }
    }

    /**
     * Aktualisiert die Flottenstatus-Informationen
     */
    private void updateFleetStatus() {
        // Stelle sicher, dass UI-Elemente und Airline-Info vorhanden sind
        if (totalAircraftLabel == null || activeAircraftLabel == null || groundedAircraftLabel == null ||
                currentAirline == null || currentAirline.getId() == null || aircraftService == null) {
            logger.warn("UpdateFleetStatus: UI-Elemente, Airline-Informationen oder AircraftService fehlen.");
            return;
        }

        try {
            // Flotten-Informationen aus der Datenbank laden
            List<Aircraft> allAircraft = aircraftService.getAllAircraft();

            // Nach Status filtern
            long totalCount = allAircraft.size();
            long activeCount = allAircraft.stream()
                    .filter(aircraft -> aircraft != null && aircraft.getStatus() == AircraftStatus.FLYING)
                    .count();
            // Grounded sind alle, die nicht FLYING sind (vereinfacht)
            long groundedCount = totalCount - activeCount;

            // UI aktualisieren
            totalAircraftLabel.setText("Gesamt: " + totalCount + " Flugzeuge");
            activeAircraftLabel.setText("Im Einsatz: " + activeCount + " Flugzeuge");
            groundedAircraftLabel.setText("Am Boden: " + groundedCount + " Flugzeuge");

        } catch (Exception e) {
            logger.error("Fehler beim Laden der Flottendaten für Airline ID {}: {}", currentAirline.getId(), e.getMessage(), e);
            // Fallback UI
            totalAircraftLabel.setText("Gesamt: Fehler");
            activeAircraftLabel.setText("Im Einsatz: --");
            groundedAircraftLabel.setText("Am Boden: --");
        }
    }

    /**
     * Leere Methode für updatePassengerStats, da Passagierstatistiken entfernt wurden
     * Diese Methode wird für die vollständige Entfernung der Passagierstatistiken beibehalten.
     */
    private void updatePassengerStats() {
        // Diese Methode ist absichtlich leer, da Passagierstatistiken aus der Anwendung entfernt wurden
        logger.debug("Passagierstatistiken wurden aus der Anwendung entfernt");
    }

    /**
     * Zeigt einen Fehlerdialog an
     *
     * @param title   Der Titel des Dialogs
     * @param content Der Inhalt des Dialogs
     */
    private void showErrorAlert(String title, String content) {
        // Stelle sicher, dass dies auf dem JavaFX Application Thread ausgeführt wird
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    /**
     * Gibt Ressourcen frei, wenn der Controller entladen wird
     */
    public void cleanup() {
        if (scheduler != null && !scheduler.isShutdown()) {
            logger.info("Beende Dashboard-Updater Scheduler...");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                    logger.warn("Scheduler wurde zum Beenden gezwungen.");
                }
                logger.info("Dashboard-Updater Scheduler erfolgreich beendet.");
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
                logger.error("Warten auf Scheduler-Beendigung unterbrochen.", e);
            }
        }
    }
}
