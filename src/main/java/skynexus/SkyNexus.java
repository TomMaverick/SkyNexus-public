package skynexus;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.controller.MainViewController;
import skynexus.database.DatabaseConnectionManager;
import skynexus.service.SystemSettingsService;
import skynexus.util.Config;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SkyNexus extends Application {
    private static final Logger logger = LoggerFactory.getLogger(SkyNexus.class);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            // Prüfe, ob das Logs-Verzeichnis existiert und erstelle es ggf.
            checkAndCreateLogsDirectory();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/mainView.fxml"));
            Parent root = loader.load();

            // Scene einrichten
            Scene scene = new Scene(root);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/style.css")).toExternalForm());

            // Stage konfigurieren
            primaryStage.setTitle("SkyNexus");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(854);
            primaryStage.setMinHeight(480);

            // MainViewController holen
            MainViewController controller = loader.getController();

            // Rahmen ausblenden
            scene.setFill(Color.TRANSPARENT); // Für transparenten Hintergrund
            primaryStage.initStyle(StageStyle.TRANSPARENT);

            // Fenstersteuerung mit WindowManager einrichten
            controller.setupWindowControls(primaryStage);

            // Den Close-Request auf den Controller umleiten
            primaryStage.setOnCloseRequest(event -> {
                controller.shutdownApplication();
                event.consume(); // Verhindert das automatische Schließen
            });

            primaryStage.show();

            logger.info("SkyNexus wurde gestartet");
        } catch (Exception e) {
            logger.error("Fehler beim Starten der Anwendung", e);
            showFatalError(
                    "SkyNexus konnte nicht gestartet werden: " + e.getMessage());
        }
    }

    /**
     * Prüft, ob das Logs-Verzeichnis existiert und erstellt es bei Bedarf
     */
    private void checkAndCreateLogsDirectory() {
        File logsDir = new File("logs");
        if (!logsDir.exists()) {
            boolean created = logsDir.mkdir();
            if (created) {
                logger.info("Logs-Verzeichnis wurde erstellt");
            } else {
                logger.warn("Logs-Verzeichnis konnte nicht erstellt werden");
            }
        }
    }

    @Override
    public void init() {
        // Initialisiere die Datenbankkonfiguration (ohne Verbindungsversuch)
        try {
            if (Config.getDbProperty("db.url") == null) {
                logger.warn("Datenbankkonfiguration konnte nicht geladen werden, verwende Standardwerte");
            }

            // Nur den Manager initialisieren, ohne isConnected() aufzurufen
            // Dies vermeidet blockierende Aufrufe während der init()-Phase
            DatabaseConnectionManager.getInstance();

            // SystemSettingsService initialisieren und Tabelle prüfen
            ApplicationReadyManager.getInstance().runWhenReady(() -> {
                try {
                    // Stell sicher, dass die system_settings-Tabelle existiert
                    SystemSettingsService.getInstance().ensureSystemSettingsTableExists();
                    logger.info("SystemSettingsService initialisiert und Tabelle überprüft");
                } catch (Exception e) {
                    logger.error("Fehler bei SystemSettingsService-Initialisierung: {}", e.getMessage());
                }
            });

            logger.info("Datenbank-Manager initialisiert");
        } catch (Exception e) {
            logger.error("Fehler bei DB-Initialisierung: {}", e.getMessage());
        }
    }

    @Override
    public void stop() {
        // Ressourcen freigeben
        try {
            DatabaseConnectionManager.getInstance().shutdown();
            ApplicationReadyManager.getInstance().shutdown();
            logger.info("Anwendung wird beendet");
        } catch (Exception e) {
            logger.error("Fehler beim Beenden der Anwendung: {}", e.getMessage());
        }
    }

    /**
     * Zeigt einen fatalen Fehler an und beendet die Anwendung
     */
    private void showFatalError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle("Anwendungsfehler");
        alert.setHeaderText("Ein schwerwiegender Fehler ist aufgetreten");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            Platform.exit();
        }
    }

    /**
     * Verwaltet den Bereitschaftszustand der Anwendung.
     * Diese innere Klasse implementiert ein ereignisgesteuertes System zur Initialisierung
     * von Komponenten, anstatt starre Verzögerungszeiten zu verwenden.
     */
    public static class ApplicationReadyManager {
        private static final Logger logger = LoggerFactory.getLogger(ApplicationReadyManager.class);
        private static ApplicationReadyManager instance;

        // Status der einzelnen Komponenten
        private final Map<String, CompletableFuture<Void>> componentReadyFutures = new HashMap<>();
        // Liste der erforderlichen Komponenten für die Anwendungsbereitschaft
        private final List<String> requiredComponents = new ArrayList<>();
        // Executor für asynchrone Aufgaben
        private final ExecutorService executor = Executors.newCachedThreadPool();
        // Globales Future für die Gesamtbereitschaft
        private CompletableFuture<Void> applicationReadyFuture;

        /**
         * Privater Konstruktor für Singleton-Pattern
         */
        private ApplicationReadyManager() {
            applicationReadyFuture = new CompletableFuture<>();
            logger.info("ApplicationReadyManager initialisiert");
        }

        /**
         * Gibt die einzige Instanz des ApplicationReadyManagers zurück.
         */
        public static synchronized ApplicationReadyManager getInstance() {
            if (instance == null) {
                instance = new ApplicationReadyManager();
            }
            return instance;
        }

        /**
         * Registriert eine Komponente als erforderlich für die Anwendungsbereitschaft.
         *
         * @param componentName Name der Komponente
         * @return Ein CompletableFuture, das abgeschlossen werden kann, wenn die Komponente bereit ist
         */
        public synchronized CompletableFuture<Void> registerRequiredComponent(String componentName) {
            logger.debug("Komponente als erforderlich registriert: {}", componentName);

            if (!requiredComponents.contains(componentName)) {
                requiredComponents.add(componentName);
            }

            CompletableFuture<Void> future = new CompletableFuture<>();
            componentReadyFutures.put(componentName, future);

            future.thenRunAsync(this::checkApplicationReady, executor);

            return future;
        }

        /**
         * Markiert eine Komponente als bereit.
         *
         * @param componentName Name der Komponente
         */
        public synchronized void setComponentReady(String componentName) {
            logger.info("Komponente ist bereit: {}", componentName);

            CompletableFuture<Void> future = componentReadyFutures.get(componentName);
            if (future != null && !future.isDone()) {
                future.complete(null);
            } else {
                logger.warn("Versuche, nicht registrierte Komponente als bereit zu markieren: {}", componentName);
            }
        }

        /**
         * Markiert eine Komponente als fehlgeschlagen.
         *
         * @param componentName Name der Komponente
         * @param exception     Die aufgetretene Exception
         */
        public synchronized void setComponentFailed(String componentName, Throwable exception) {
            logger.error("Komponente ist fehlgeschlagen: {} - {}", componentName, exception.getMessage());

            CompletableFuture<Void> future = componentReadyFutures.get(componentName);
            if (future != null && !future.isDone()) {
                future.completeExceptionally(exception);

                // Wenn eine erforderliche Komponente fehlschlägt, schlägt auch die Anwendungsbereitschaft fehl
                if (requiredComponents.contains(componentName) && !applicationReadyFuture.isDone()) {
                    applicationReadyFuture.completeExceptionally(
                            new ApplicationNotReadyException("Kritische Komponente fehlgeschlagen: " + componentName, exception));
                }
            }
        }

        /**
         * Prüft, ob alle erforderlichen Komponenten bereit sind.
         */
        private synchronized void checkApplicationReady() {
            if (applicationReadyFuture.isDone()) {
                return;
            }

            // Prüfen, ob alle erforderlichen Komponenten bereit sind
            boolean allReady = true;
            for (String component : requiredComponents) {
                CompletableFuture<Void> future = componentReadyFutures.get(component);
                if (future == null || !future.isDone() || future.isCompletedExceptionally()) {
                    allReady = false;
                    break;
                }
            }

            if (allReady) {
                logger.info("Alle erforderlichen Komponenten sind bereit, Anwendung ist startbereit");
                applicationReadyFuture.complete(null);
            }
        }

        /**
         * Gibt ein Future zurück, das abgeschlossen wird, wenn die Anwendung bereit ist.
         *
         * @return CompletableFuture das abgeschlossen wird, wenn die Anwendung bereit ist
         */
        public CompletableFuture<Void> getApplicationReadyFuture() {
            return applicationReadyFuture;
        }

        /**
         * Führt eine Aktion aus, sobald die Anwendung bereit ist.
         *
         * @param runnable Die auszuführende Aktion
         */
        public void runWhenReady(Runnable runnable) {
            applicationReadyFuture.thenRunAsync(runnable, executor);
        }

        /**
         * Gibt den aktuellen Bereitschaftsstatus der Anwendung zurück.
         *
         * @return true wenn die Anwendung bereit ist, false sonst
         */
        public boolean isApplicationReady() {
            return applicationReadyFuture.isDone() && !applicationReadyFuture.isCompletedExceptionally();
        }

        /**
         * Gibt eine Übersicht über den Status aller Komponenten zurück.
         *
         * @return Eine Map mit Komponentennamen und ihrem Status
         */
        public Map<String, String> getComponentStatusMap() {
            Map<String, String> statusMap = new HashMap<>();

            for (Map.Entry<String, CompletableFuture<Void>> entry : componentReadyFutures.entrySet()) {
                String componentName = entry.getKey();
                CompletableFuture<Void> future = entry.getValue();

                String status;
                if (future.isDone()) {
                    if (future.isCompletedExceptionally()) {
                        status = "FEHLGESCHLAGEN";
                    } else {
                        status = "BEREIT";
                    }
                } else {
                    status = "INITIALISIERUNG";
                }

                statusMap.put(componentName, status);
            }

            return statusMap;
        }

        /**
         * Beendet alle asynchronen Aufgaben und bereinigt Ressourcen.
         */
        public void shutdown() {
            executor.shutdownNow();
            logger.info("ApplicationReadyManager heruntergefahren");
        }

        /**
         * Exception für den Fall, dass die Anwendung nicht bereit werden kann.
         */
        public static class ApplicationNotReadyException extends RuntimeException {
            public ApplicationNotReadyException(String message, Throwable cause) {
                super(message, cause);
            }
        }
    }
}
