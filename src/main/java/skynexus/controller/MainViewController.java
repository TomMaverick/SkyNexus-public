package skynexus.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign.MaterialDesign;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.SkyNexus;
import skynexus.database.DatabaseConnectionManager;
import skynexus.model.User;
import skynexus.service.SystemSettingsService;
import skynexus.util.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Controller für die Hauptansicht der SkyNexus-Anwendung.
 * Verwaltet Navigation, UI-Status und Benutzerinteraktionen.
 */
public class MainViewController implements AuthenticationListener {
    private static final Logger logger = LoggerFactory.getLogger(MainViewController.class);
    private static final String COMPONENT_NAME = "MainViewController";

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss - dd.MM.yyyy");
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final long sessionStartTime = System.currentTimeMillis();

    private FontIcon dbSyncIcon;
    private FontIcon dbCheckIcon;
    private FontIcon dbAlertIcon;

    // FXML UI-Komponenten
    @FXML private BorderPane root;
    @FXML private BorderPane header;
    @FXML private AnchorPane contentArea;
    @FXML private VBox sidebar;
    @FXML private VBox adminSection;

    @FXML private Label timeLabel;
    @FXML private Label sessionTime;
    @FXML private Label dbStatusLabel;
    @FXML private Label dbStatusIcon;

    @FXML private Button dashboardButton;
    @FXML private Button planeButton;
    @FXML private Button flightButton;
    @FXML private Button passengersButton;
    @FXML private Button settingsButton;
    @FXML private Button logsButton;
    @FXML private Button logoutButton;

    @FXML private Button adminAirports;
    @FXML private Button adminRoutes;
    @FXML private Button adminUsers;

    @FXML private Button minimizeButton;
    @FXML private Button maximizeButton;
    @FXML private Button closeButton;

    private boolean dbConnectionActive = false;
    private String currentViewPath;
    private Stage logsViewerStage;

    /**
     * Initialisiert den Controller.
     * Wird automatisch vom FXML-Loader aufgerufen.
     */
    public void initialize() {
        logger.debug("MainViewController wird initialisiert");

        // Bei ApplicationReadyManager als erforderliche Komponente registrieren
        SkyNexus.ApplicationReadyManager.getInstance().registerRequiredComponent(COMPONENT_NAME);

        // Icons initialisieren
        initializeStatusIcons();

        startTimers();
        setupDatabase();
        setupUI();
        checkUserSession();

        logger.info("MainViewController initialisiert");

        // Controller als bereit markieren
        SkyNexus.ApplicationReadyManager.getInstance().setComponentReady(COMPONENT_NAME);
    }

    /**
     * Initialisiert die FontIcon-Objekte für den DB-Status.
     */
    private void initializeStatusIcons() {
        try {
            dbSyncIcon = new FontIcon(MaterialDesign.MDI_SYNC);
            dbSyncIcon.setIconSize(24);

            dbCheckIcon = new FontIcon(MaterialDesign.MDI_CHECK_CIRCLE);
            dbCheckIcon.setIconSize(24);

            dbAlertIcon = new FontIcon(MaterialDesign.MDI_ALERT_CIRCLE);
            dbAlertIcon.setIconSize(24);
            logger.debug("Status-Icons initialisiert.");
        } catch (NoClassDefFoundError e) {
            logger.error("Fehler beim Initialisieren der Ikonli Icons: Klasse nicht gefunden. Prüfe Abhängigkeiten!", e);
            dbSyncIcon = new FontIcon();
            dbCheckIcon = new FontIcon();
            dbAlertIcon = new FontIcon();
        } catch (Exception e) {
            logger.error("Unerwarteter Fehler beim Initialisieren der Status-Icons", e);
            dbSyncIcon = new FontIcon();
            dbCheckIcon = new FontIcon();
            dbAlertIcon = new FontIcon();
        }
    }


    /**
     * Startet alle Timer-basierten Funktionen
     */
    private void startTimers() {
        startClock();
        startSessionTimer();
    }

    /**
     * Richtet die Datenbanküberwachung ein
     */
    private void setupDatabase() {
        setInitialDbStatus();
        setupDbStatusCheckWithEvents();
    }

    /**
     * Richtet die UI-Komponenten ein
     */
    private void setupUI() {
        updateButtonTexts();
        configureSidebar();
        setupButtonHandlers();
    }

    /**
     * Prüft, ob ein Benutzer angemeldet ist und lädt die entsprechende View
     */
    private void checkUserSession() {
        if (SessionManager.getInstance().getCurrentUser() == null) {
            Platform.runLater(this::showLoginScreen);
        } else {
            Platform.runLater(() -> {
                loadDashboardView();
                updateAdminVisibility();
            });
        }
    }

    /**
     * Startet die Uhr im Footer
     */
    private void startClock() {
        scheduler.scheduleAtFixedRate(() -> {
            String currentTime = dateFormat.format(new Date());
            // Prüfen ob timeLabel null ist, bevor darauf zugegriffen wird
            if (timeLabel != null) {
                Platform.runLater(() -> timeLabel.setText(currentTime));
            }
        }, 0, 1, TimeUnit.SECONDS);
    }


    /**
     * Startet den Timer für die Sitzungsdauer
     */
    private void startSessionTimer() {
        scheduler.scheduleAtFixedRate(() -> {
            long elapsedTime = System.currentTimeMillis() - sessionStartTime;
            long hours = TimeUnit.MILLISECONDS.toHours(elapsedTime);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTime) % 60;
            long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime) % 60;

            String sessionDuration = String.format("%02d:%02d:%02d", hours, minutes, seconds);

            // Prüfen ob sessionTime null ist
            if (sessionTime != null) {
                Platform.runLater(() -> sessionTime.setText("Sitzungszeit: " + sessionDuration));
            }
        }, 0, 1, TimeUnit.SECONDS);
    }


    /**
     * Setzt den initialen Datenbankstatus
     */
    private void setInitialDbStatus() {
        // Sicherstellen, dass UI-Updates auf dem JavaFX Application Thread laufen
        Platform.runLater(() -> {
            if (dbStatusLabel != null) {
                dbStatusLabel.setText("Verbinde...");
            } else {
                logger.warn("dbStatusLabel ist null in setInitialDbStatus");
            }

            if (dbStatusIcon != null) {
                // Sicherstellen, dass Icon initialisiert ist
                if (dbSyncIcon != null) {
                    dbStatusIcon.setGraphic(dbSyncIcon);
                    dbStatusIcon.getStyleClass().add("db-status-connecting");
                    logger.trace("Setze DB Status Icon auf SYNC (connecting)");
                } else {
                    logger.error("dbSyncIcon wurde nicht initialisiert!");
                    dbStatusIcon.setGraphic(null);
                }
            } else {
                logger.warn("dbStatusIcon Label ist null in setInitialDbStatus");
            }
        });
    }


    /**
     * Richtet die Datenbankstatusüberwachung mit ereignisgesteuerter Initialisierung ein
     */
    private void setupDbStatusCheckWithEvents() {
        int checkInterval = Config.getDbPropertyInt("db.connection.check.interval", 10);

        // Bei Anwendungsbereitschaft DB-Status prüfen
        SkyNexus.ApplicationReadyManager.getInstance().runWhenReady(() -> {
            logger.debug("Anwendung ist bereit, prüfe Datenbankstatus");

            // Erste Status-Prüfung sofort durchführen
            boolean isConnected = checkDatabaseConnection();
            Platform.runLater(() -> updateDbStatus(isConnected));

            // Regelmäßige Prüfungen starten
            scheduler.scheduleAtFixedRate(() -> {
                boolean connected = checkDatabaseConnection();
                Platform.runLater(() -> updateDbStatus(connected));
            }, checkInterval, checkInterval, TimeUnit.SECONDS);

            logger.info("Datenbankstatusüberwachung gestartet mit Intervall: {} Sekunden", checkInterval);
        });
    }


    /**
     * Prüft die Datenbankverbindung
     */
    private boolean checkDatabaseConnection() {
        try {
            boolean connected = DatabaseConnectionManager.getInstance().isConnected();
            // Nur loggen, wenn sich der Status ändert, um Log-Spam zu vermeiden
            if (connected != dbConnectionActive) {
                logger.info("Datenbankverbindungsstatus geändert zu: {}", connected);
            }
            dbConnectionActive = connected;
            return connected;
        } catch (Exception e) {
            // Nur loggen, wenn sich der Status ändert oder es der erste Fehler ist
            if (dbConnectionActive) { // Wenn vorher verbunden war
                logger.error("Datenbankverbindungsfehler: {}", e.getMessage());
            }
            dbConnectionActive = false;
            return false;
        }
    }


    /**
     * Aktualisiert die Anzeige des Datenbankstatus
     */
    private void updateDbStatus(boolean isConnected) {
        // Sicherstellen, dass UI-Updates auf dem JavaFX Application Thread laufen
        Platform.runLater(() -> {
            if (dbStatusLabel != null) {
                dbStatusLabel.setText(isConnected ? "Verbunden" : "Getrennt");
            } else {
                logger.warn("dbStatusLabel ist null in updateDbStatus");
            }

            if (dbStatusIcon != null) {
                dbStatusIcon.getStyleClass().removeAll("db-status-connected", "db-status-disconnected", "db-status-connecting");
                if (isConnected) {
                    if (dbCheckIcon != null) {
                        dbStatusIcon.setGraphic(dbCheckIcon);
                        dbStatusIcon.getStyleClass().add("db-status-connected");
                        logger.trace("Setze DB Status Icon auf CHECK_CIRCLE (connected)");
                    } else {
                        logger.error("dbCheckIcon wurde nicht initialisiert!");
                        dbStatusIcon.setGraphic(null);
                    }
                } else {
                    if (dbAlertIcon != null) {
                        dbStatusIcon.setGraphic(dbAlertIcon);
                        dbStatusIcon.getStyleClass().add("db-status-disconnected");
                        logger.trace("Setze DB Status Icon auf ALERT_CIRCLE (disconnected)");
                    } else {
                        logger.error("dbAlertIcon wurde nicht initialisiert!");
                        dbStatusIcon.setGraphic(null);
                    }
                }

                // Datenbank-Statistiken im Tooltip anzeigen
                String stats = isConnected ? DatabaseConnectionManager.getInstance().getConnectionStats() : "Nicht verbunden";
                Tooltip tooltip = new Tooltip(stats);
                Tooltip.install(dbStatusIcon, tooltip); // Tooltip immer neu setzen oder aktualisieren

            } else {
                logger.warn("dbStatusIcon Label ist null in updateDbStatus");
            }
        });
    }


    /**
     * Konfiguriert die Sidebar für responsive Anzeige
     */
    private void configureSidebar() {
        // Null-Check für root
        if (root == null) {
            logger.error("Root-Element ist null in configureSidebar. FXML wurde möglicherweise nicht korrekt geladen.");
            return;
        }
        root.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            double screenWidth = newWidth.doubleValue();

            // Sidebar-Breite: Fix 200px bis 720p, dann 15% bis max 400px
            double sidebarWidth = (screenWidth <= 1280) ? 200 : Math.min(screenWidth * 0.15, 400);
            // Null-Check für sidebar
            if (sidebar != null) {
                sidebar.setPrefWidth(sidebarWidth);
            } else {
                logger.warn("Sidebar ist null in configureSidebar Listener.");
            }


            // Button-Größe anpassen
            double buttonWidth = sidebarWidth * 0.9;
            buttonWidth = Math.max(180, Math.min(buttonWidth, 380));

            // Schriftgröße anpassen
            double fontSize = Math.max(14, Math.min(18, buttonWidth * 0.08));

            // Auf alle Buttons anwenden (mit Null-Check für sidebar)
            if (sidebar != null) {
                applyStyleToButtons(sidebar, buttonWidth, fontSize);
            }
        });
        // Initiales Styling nach dem Laden der Szene erzwingen
        Platform.runLater(() -> {
            if (root != null && root.getWidth() > 0) {
                double initialScreenWidth = root.getWidth();
                double initialSidebarWidth = (initialScreenWidth <= 1280) ? 200 : Math.min(initialScreenWidth * 0.15, 400);
                if (sidebar != null) {
                    sidebar.setPrefWidth(initialSidebarWidth);
                }
                double initialButtonWidth = initialSidebarWidth * 0.9;
                initialButtonWidth = Math.max(180, Math.min(initialButtonWidth, 380));
                double initialFontSize = Math.max(14, Math.min(18, initialButtonWidth * 0.08));
                if (sidebar != null) {
                    applyStyleToButtons(sidebar, initialButtonWidth, initialFontSize);
                }
            } else if (root != null) {
                // Listener hinzufügen, falls Breite anfangs 0 ist
                root.widthProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal.doubleValue() > 0) {
                        // Listener entfernen, nachdem Breite gesetzt wurde? Optional.
                        configureSidebar(); // Erneut aufrufen, um Styles anzuwenden
                    }
                });
            }
        });
    }

    /**
     * Wendet Stile auf alle Buttons in einem Container an
     */
    private void applyStyleToButtons(Node container, double buttonWidth, double fontSize) {
        if (container instanceof VBox vbox) {
            vbox.getChildren().forEach(node -> {
                if (node instanceof Button button) {
                    button.setPrefWidth(buttonWidth);
                } else if (node instanceof VBox childVBox) {
                    // Rekursiv für verschachtelte VBoxen
                    applyStyleToButtons(childVBox, buttonWidth, fontSize);
                }
            });
        } else {
            logger.trace("applyStyleToButtons: Container ist keine VBox oder null.");
        }
    }

    /**
     * Richtet die Event-Handler für Navigation-Buttons ein
     */
    private void setupButtonHandlers() {
        // Füge Null-Checks hinzu, falls FXML-Elemente nicht injiziert wurden
        if (dashboardButton != null) dashboardButton.setOnAction(e -> handleDashboardButtonClick()); else logger.warn("dashboardButton ist null.");
        if (planeButton != null) planeButton.setOnAction(e -> handlePlaneButtonClick()); else logger.warn("planeButton ist null.");
        if (flightButton != null) flightButton.setOnAction(e -> handleFlightButtonClick()); else logger.warn("flightButton ist null.");
        if (passengersButton != null) passengersButton.setOnAction(e -> handlePassengersButtonClick()); else logger.warn("passengersButton ist null.");
        if (settingsButton != null) settingsButton.setOnAction(e -> handleSettingsButtonClick()); else logger.warn("settingsButton ist null.");
        if (logsButton != null) logsButton.setOnAction(e -> handleLogsButtonClick()); else logger.warn("logsButton ist null.");
        if (logoutButton != null) logoutButton.setOnAction(e -> handleLogoutButtonClick()); else logger.warn("logoutButton ist null.");

        // Admin-Buttons
        if (adminAirports != null) adminAirports.setOnAction(e -> handleAdminAirportsClick()); else logger.warn("adminAirports ist null.");
        if (adminRoutes != null) adminRoutes.setOnAction(e -> handleRouteButtonClick()); else logger.warn("adminRoutes ist null.");
        if (adminUsers != null) adminUsers.setOnAction(e -> handleAdminUsersClick()); else logger.warn("adminUsers ist null.");

        // Fenstersteuerung-Buttons
        if (minimizeButton != null) minimizeButton.setOnAction(e -> minimizeWindow()); else logger.warn("minimizeButton ist null.");
        if (maximizeButton != null) maximizeButton.setOnAction(e -> maximizeWindow()); else logger.warn("maximizeButton ist null.");
        if (closeButton != null) closeButton.setOnAction(e -> closeWindow()); else logger.warn("closeButton ist null.");
    }

    /**
     * Aktualisiert die Texte aller Buttons
     */
    public void updateButtonTexts() {
        try {
            // Füge Null-Checks hinzu
            if (dashboardButton != null) dashboardButton.setText("Dashboard");
            if (planeButton != null) planeButton.setText("Flugzeuge");
            if (flightButton != null) flightButton.setText("Flugplanung");
            if (passengersButton != null) passengersButton.setText("Passagiere");
            if (settingsButton != null) settingsButton.setText("Einstellungen");
            if (logsButton != null) logsButton.setText("Logs");
            if (logoutButton != null) logoutButton.setText("Abmelden");

            // Admin-Buttons
            if (adminAirports != null) adminAirports.setText("Flughäfen");
            if (adminRoutes != null) adminRoutes.setText("Routen");
            if (adminUsers != null) adminUsers.setText("Benutzer");
        } catch (Exception e) {
            logger.error("Fehler beim Aktualisieren der Button-Texte: {}", e.getMessage());
        }
    }

    /**
     * Aktualisiert die Sichtbarkeit der Admin-Sektion basierend auf Benutzerrechten
     */
    private void updateAdminVisibility() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        boolean isAdmin = currentUser != null && currentUser.isAdmin();

        // Null-Check für adminSection
        if (adminSection != null) {
            adminSection.setVisible(isAdmin);
            adminSection.setManaged(isAdmin); // Wichtig für Layout
            logger.debug("Admin-Bereich Sichtbarkeit gesetzt auf: {}", isAdmin);
        } else {
            logger.warn("adminSection ist null in updateAdminVisibility.");
        }
    }

    /**
     * Aktualisiert alle UI-Texte
     */
    public void updateUITexts() {
        // Fenstertitel setzen (mit Null-Checks)
        if (root != null && root.getScene() != null && root.getScene().getWindow() instanceof Stage stage) {
            stage.setTitle("SkyNexus");
        } else {
            logger.warn("Fenster oder Szene nicht verfügbar zum Setzen des Titels.");
        }

        // UI-Elemente aktualisieren
        updateDbStatus(dbConnectionActive); // Aktualisiert DB-Status und Label-Text
        updateButtonTexts(); // Aktualisiert Button-Texte

        // Session-Time Label Text auch aktualisieren
        if (sessionTime != null) {
            long elapsedTime = System.currentTimeMillis() - sessionStartTime;
            long hours = TimeUnit.MILLISECONDS.toHours(elapsedTime);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTime) % 60;
            long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime) % 60;
            String sessionDuration = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            sessionTime.setText("Sitzungszeit: " + sessionDuration);
        }
    }

    /**
     * Setzt den aktiven Button und entfernt das Styling von allen anderen
     */
    private void setActiveButton(Button activeButton) {
        // Null-Check für sidebar und activeButton
        if (sidebar == null || activeButton == null) {
            logger.warn("Sidebar oder activeButton ist null in setActiveButton.");
            return;
        }

        // Alle Buttons zurücksetzen
        sidebar.getChildren().forEach(node -> {
            if (node instanceof Button button) {
                button.getStyleClass().remove("active");
            } else if (node instanceof VBox vbox) {
                vbox.getChildren().forEach(child -> {
                    if (child instanceof Button button) {
                        button.getStyleClass().remove("active");
                    }
                });
            }
        });

        // Aktiven Button setzen
        if (!activeButton.getStyleClass().contains("active")) {
            activeButton.getStyleClass().add("active");
        }
    }

    /**
     * Zeigt den Login-Screen an und blendet die Navigation aus
     */
    public void showLoginScreen() {
        // Null-Checks
        if (sidebar == null || contentArea == null) {
            logger.error("Sidebar oder ContentArea ist null in showLoginScreen.");
            return;
        }
        sidebar.setVisible(false);
        sidebar.setManaged(false);

        loadView("/fxml/loginView.fxml", true);
        logger.info("Login-Bildschirm angezeigt");
    }

    /**
     * Lädt das Dashboard
     */
    private void loadDashboardView() {
        loadView("/fxml/dashboardView.fxml");
        if (dashboardButton != null) {
            setActiveButton(dashboardButton);
        } else {
            logger.warn("dashboardButton ist null beim Versuch, ihn als aktiv zu setzen.");
        }
    }

    /**
     * Lädt eine View mit Authentifizierungs-Listener
     */
    private void loadView(String fxmlPath, boolean isLoginView) {
        // Null-Check
        if (contentArea == null) {
            logger.error("ContentArea ist null in loadView. Kann View nicht laden: {}", fxmlPath);
            showError("Interner Fehler", "UI kann nicht aktualisiert werden (contentArea fehlt).");
            return;
        }
        try {
            // UINavigationHelper nutzen, um die View zu laden
            Object controller = UINavigationHelper.loadView(contentArea, fxmlPath);

            if (isLoginView && controller instanceof LoginViewController loginController) {
                loginController.setAuthenticationListener(this);
            }

            currentViewPath = fxmlPath;
            logger.debug("View geladen: {}", fxmlPath);

        } catch (Exception e) {
            logger.error("Fehler beim Laden der View {}: {}", fxmlPath, e.getMessage(), e); // Logge den Stacktrace
            showError("Fehler beim Laden", "Die Ansicht '" + fxmlPath + "' konnte nicht geladen werden: " + e.getMessage());
        }
    }

    /**
     * Überladene Methode für Standardviews ohne Login-Funktionalität
     */
    private void loadView(String fxmlPath) {
        loadView(fxmlPath, false);
    }

    /**
     * Zeigt eine Fehlermeldung an
     */
    private void showError(String title, String message) {
        logger.error("{}: {}", title, message);

        // Sicherstellen, dass der Alert auf dem FX-Thread läuft
        if (Platform.isFxApplicationThread()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            if (root != null && root.getScene() != null) {
                alert.initOwner(root.getScene().getWindow());
            }
            alert.showAndWait();
        } else {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle(title);
                alert.setHeaderText(null);
                alert.setContentText(message);
                if (root != null && root.getScene() != null) {
                    alert.initOwner(root.getScene().getWindow());
                }
                alert.showAndWait();
            });
        }
    }

    /**
     * Navigiert zum Dashboard
     */
    private void handleDashboardButtonClick() {
        loadDashboardView();
    }

    /**
     * Lädt die Passagierübersicht
     */
    private void handlePassengersButtonClick() {
        if (passengersButton != null) {
            loadView("/fxml/passengerView.fxml");
            setActiveButton(passengersButton);
        } else {
            logger.warn("passengersButton ist null.");
        }
    }

    /**
     * Lädt die Flugzeugübersicht
     */
    private void handlePlaneButtonClick() {
        if (planeButton != null) {
            loadView("/fxml/aircraftView.fxml");
            setActiveButton(planeButton);
        } else {
            logger.warn("planeButton ist null.");
        }
    }

    /**
     * Lädt die Routenverwaltung
     */
    private void handleRouteButtonClick() {
        if (adminRoutes != null) {
            loadView("/fxml/admin/adminRoutesView.fxml");
            setActiveButton(adminRoutes);
        } else {
            logger.warn("adminRoutes ist null.");
        }
    }

    /**
     * Lädt die Flugplanung
     */
    private void handleFlightButtonClick() {
        if (flightButton != null) {
            loadView("/fxml/flightView.fxml");
            setActiveButton(flightButton);
        } else {
            logger.warn("flightButton ist null.");
        }
    }

    /**
     * Lädt die Einstellungen und verbindet den SettingsViewController mit dem MainViewController
     */
    private void handleSettingsButtonClick() {
        if (settingsButton == null) {
            logger.warn("settingsButton ist null.");
            return;
        }
        try {
            SettingsViewController controller = UINavigationHelper.loadView(contentArea, "/fxml/settingsView.fxml");
            if (controller != null) {
                controller.setMainController(this);
                currentViewPath = "/fxml/settingsView.fxml";
                setActiveButton(settingsButton);
            } else {
                logger.error("SettingsViewController konnte nicht geladen werden.");
                showError("Fehler", "Einstellungen konnten nicht korrekt initialisiert werden.");
            }
        } catch (Exception e) {
            logger.error("Fehler beim Laden der Einstellungen: {}", e.getMessage(), e);
            showError("Fehler", "Einstellungen konnten nicht geladen werden: " + e.getMessage());
        }
    }

    /**
     * Öffnet den Log-Viewer in einem separaten Fenster
     */
    private void handleLogsButtonClick() {
        if (logsButton != null) {
            setActiveButton(logsButton);
            openLogsViewer();
        } else {
            logger.warn("logsButton ist null.");
        }
    }

    /**
     * Zeigt einen Bestätigungsdialog und meldet den Benutzer bei Bestätigung ab
     */
    private void handleLogoutButtonClick() {
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Abmelden");
        confirmDialog.setHeaderText("Abmelden bestätigen");
        confirmDialog.setContentText("Möchten Sie sich wirklich abmelden?");
        if (root != null && root.getScene() != null) {
            confirmDialog.initOwner(root.getScene().getWindow());
        }

        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            SessionManager.getInstance().clearSession();
            onLogout();
            logger.info("Benutzer abgemeldet");
        }
    }

    /**
     * Lädt die Flughafenverwaltung (nur für Administratoren)
     */
    private void handleAdminAirportsClick() {
        if (adminAirports != null) {
            loadView("/fxml/admin/adminAirportsView.fxml");
            setActiveButton(adminAirports);
        } else {
            logger.warn("adminAirports ist null.");
        }
    }

    /**
     * Lädt die Benutzerverwaltung (nur für Administratoren)
     */
    private void handleAdminUsersClick() {
        if (adminUsers != null) {
            loadView("/fxml/admin/adminUsersView.fxml");
            setActiveButton(adminUsers);
        } else {
            logger.warn("adminUsers ist null.");
        }
    }


    /**
     * Öffnet den Log-Viewer in einem separaten Fenster
     */
    private void openLogsViewer() {
        if (logsViewerStage != null && logsViewerStage.isShowing()) {
            logsViewerStage.toFront();
            return;
        }

        try {
            // UINavigationHelper verwenden, um ein neues Fenster zu öffnen
            Stage stage = new Stage();
            // Besitzerfenster setzen, falls möglich
            if (root != null && root.getScene() != null) {
                stage.initOwner(root.getScene().getWindow());
            }

            LogsViewController controller = UINavigationHelper.openInNewWindow(
                    "/fxml/logsView.fxml",
                    "SkyNexus - System-Logs",
                    stage // Stage übergeben
            );

            // Prüfen ob Controller erfolgreich geladen wurde
            if (controller == null) {
                logger.error("LogsViewController konnte nicht initialisiert werden.");
                showError("Fehler", "Log-Viewer konnte nicht korrekt initialisiert werden.");
                return; // Verhindert NullPointerException bei stage.setOnCloseRequest
            }

            stage.setMinWidth(720);
            stage.setMinHeight(480);

            stage.setOnCloseRequest(e -> {
                logsViewerStage = null;
                // Logs-Button Status zurücksetzen
                if (logsButton != null) {
                    logsButton.getStyleClass().remove("active");
                }
                logger.info("Log-Viewer geschlossen");
            });

            // Kein zusätzliches Event für Close-Button mehr benötigt
            // Da LogView-Dialog Standard-Fensterdekorationen verwendet

            logsViewerStage = stage;
            logsViewerStage.show();
            logger.info("Log-Viewer geöffnet");

        } catch (Exception e) {
            logger.error("Fehler beim Öffnen des Log-Viewers: {}", e.getMessage(), e);
            showError("Fehler", "Log-Viewer konnte nicht geöffnet werden: " + e.getMessage());
        }
    }

    /**
     * Richtet die Fenstersteuerung ein (Ziehen und Größenänderung)
     */
    public void setupWindowControls(Stage stage) {
        // Null-Check für Stage und Header
        if (stage == null) {
            logger.error("Stage ist null in setupWindowControls.");
            return;
        }
        if (header == null) {
            logger.error("Header ist null in setupWindowControls.");
            return;
        }

        // UINavigationHelper für Ziehen und Größenänderung verwenden
        UINavigationHelper uiNavigation = UINavigationHelper.getInstance();
        uiNavigation.makeDraggable(stage, header);
        uiNavigation.enableResize(stage);

        // UI-Texte aktualisieren, wenn das Fenster bereit ist
        Platform.runLater(this::updateUITexts);
    }

    /**
     * Minimiert das Anwendungsfenster
     */
    @FXML
    private void minimizeWindow() {
        if (root != null && root.getScene() != null && root.getScene().getWindow() instanceof Stage stage) {
            stage.setIconified(true);
        } else {
            logger.warn("Fenster konnte nicht minimiert werden (Stage nicht gefunden).");
        }
    }

    /**
     * Maximiert das Anwendungsfenster oder stellt es wieder her
     */
    @FXML
    private void maximizeWindow() {
        if (root != null && root.getScene() != null && root.getScene().getWindow() instanceof Stage stage) {
            // Umschalten zwischen maximiert und normalem Fenster
            boolean currentMaximized = stage.isMaximized();
            stage.setMaximized(!currentMaximized);

            // Text des Buttons je nach Status ändern
            if (maximizeButton != null) {
                if (stage.isMaximized()) {
                    maximizeButton.setText("❒"); // Unicode-Symbol für "Fenster wiederherstellen"
                } else {
                    maximizeButton.setText("☐"); // Unicode-Symbol für "Fenster maximieren"
                }
            } else {
                logger.warn("maximizeButton ist null.");
            }

            logger.debug("Fenster maximiert: {}", !currentMaximized);
        } else {
            logger.warn("Fenster konnte nicht maximiert/wiederhergestellt werden (Stage nicht gefunden).");
        }
    }

    /**
     * Schließt das Anwendungsfenster und fährt die Anwendung herunter
     */
    @FXML
    private void closeWindow() {
        if (root != null && root.getScene() != null && root.getScene().getWindow() instanceof Stage stage) {
            stage.close(); // Schließt das Fenster
            shutdownApplication(); // Fährt die Anwendung herunter
        } else {
            logger.warn("Fenster konnte nicht geschlossen werden (Stage nicht gefunden).");
            // Fallback, um zumindest die Anwendung zu beenden
            shutdownApplication();
        }
    }

    @Override
    public void onLoginSuccessful(User user) {
        // Null-Check für User
        if (user == null) {
            logger.error("onLoginSuccessful aufgerufen mit null User.");
            // Fallback: Zeige Login-Screen erneut oder Fehler
            showLoginScreen();
            return;
        }

        // Prüfen, ob es ein Admin-Login ist und das System noch nicht initialisiert wurde
        if (user.isAdmin() && !SystemSettingsService.getInstance().isSystemInitialized()) {
            logger.info("Admin-Benutzer '{}' loggt sich zum ersten Mal ein - Ersteinrichtung wird angezeigt",
                    user.getUsername());
            showFirstRunSetup();
        } else {
            // Standardablauf: Sidebar anzeigen und Dashboard laden (mit Null-Checks)
            if (sidebar != null) {
                sidebar.setVisible(true);
                sidebar.setManaged(true);
            } else {
                logger.warn("Sidebar ist null in onLoginSuccessful.");
            }
            loadDashboardView();
            updateAdminVisibility();
        }
    }

    /**
     * Zeigt den Ersteinrichtungs-Dialog
     */
    private void showFirstRunSetup() {
        try {
            // Sidebar verstecken während der Ersteinrichtung (mit Null-Checks)
            if (sidebar != null) {
                sidebar.setVisible(false);
                sidebar.setManaged(false);
            } else {
                logger.warn("Sidebar ist null in showFirstRunSetup.");
            }


            // FirstRunSetupView laden und Controller konfigurieren
            FirstRunSetupController controller = UINavigationHelper.loadView(contentArea,
                    "/fxml/firstRunSetupView.fxml");

            // Prüfen ob Controller erfolgreich geladen wurde
            if (controller == null) {
                logger.error("FirstRunSetupController konnte nicht geladen werden.");
                throw new IllegalStateException("Ersteinrichtung konnte nicht geladen werden.");
            }


            // SetupCompletedListener setzen
            controller.setSetupCompletedListener(() -> {
                // Nach erfolgreicher Einrichtung zum Dashboard wechseln (mit Null-Checks)
                if (sidebar != null) {
                    sidebar.setVisible(true);
                    sidebar.setManaged(true);
                }
                loadDashboardView();
                updateAdminVisibility();
            });

        } catch (Exception e) {
            logger.error("Fehler beim Laden der Ersteinrichtung: {}", e.getMessage(), e);
            showError("Fehler", "Die Ersteinrichtung konnte nicht geladen werden: " + e.getMessage());

            // Fallback: direkt zum Dashboard (mit Null-Checks)
            if (sidebar != null) {
                sidebar.setVisible(true);
                sidebar.setManaged(true);
            }
            loadDashboardView();
            updateAdminVisibility();
        }
    }

    @Override
    public void onLogout() {
        showLoginScreen();
    }


    /**
     * Fährt die Anwendung sicher und kontrolliert herunter.
     *
     * Prozess:
     * 1. Beendet den Scheduler mit erweitertem Timeout
     * 2. Gibt Ressourcen asynchron mit Timeout frei
     * 3. Schließt zusätzliche Fenster wie Log-Viewer
     * 4. Führt einen sicheren Plattform-Exit durch
     */
    public void shutdownApplication() {
        logger.info("Anwendung wird heruntergefahren");

        // Scheduler mit erweitertem Timeout beenden
        if (!scheduler.isShutdown()) { // Null-Check hinzugefügt
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    logger.warn("Scheduler konnte nicht ordnungsgemäß beendet werden, erzwinge Beendigung.");
                    scheduler.shutdownNow();
                } else {
                    logger.info("Scheduler erfolgreich beendet.");
                }
            } catch (InterruptedException e) {
                logger.error("Warten auf Scheduler-Beendigung unterbrochen.", e);
                Thread.currentThread().interrupt();
                scheduler.shutdownNow();
            }
        } else {
            logger.warn("Scheduler war bereits null oder heruntergefahren.");
        }


        // Weitere Ressourcen mit Timeout freigeben
        CompletableFuture.runAsync(() -> {
                    try {
                        DatabaseConnectionManager.getInstance().shutdown();
                        SessionManager.getInstance().clearSession();
                        logger.info("Datenbankverbindung und Session erfolgreich geschlossen/bereinigt.");
                    } catch (Exception e) {
                        logger.error("Fehler beim Herunterfahren der Datenbank/Session", e);
                    }
                }).orTimeout(2, TimeUnit.SECONDS) // Timeout für die Operation
                .exceptionally(ex -> { // Behandelt Timeout oder andere Fehler
                    logger.error("Timeout oder Fehler bei Ressourcenfreigabe (DB/Session)", ex);
                    return null; // Muss null zurückgeben für exceptionally
                });

        // Log-Viewer schließen, falls vorhanden und angezeigt
        if (logsViewerStage != null && logsViewerStage.isShowing()) {
            Platform.runLater(() -> {
                try {
                    logsViewerStage.close();
                    logger.info("Log-Viewer geschlossen");
                } catch (Exception e) {
                    logger.error("Fehler beim Schließen des Log-Viewers", e);
                }
            });
        }

        // Sicheres Platform Exit
        logger.info("Führe Platform.exit() aus...");
        // Kurze Verzögerung, um anderen Threads Zeit zum Beenden zu geben (optional)
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Platform.exit(); // Beendet die JavaFX-Anwendungsthread
        logger.info("Führe System.exit(0) aus...");
        System.exit(0); // Beendet die JVM
    }
}
