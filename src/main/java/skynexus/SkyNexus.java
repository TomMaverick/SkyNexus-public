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
            checkAndCreateLogsDirectory();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/mainView.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/style.css")).toExternalForm());

            primaryStage.setTitle("SkyNexus");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(854);
            primaryStage.setMinHeight(480);

            MainViewController controller = loader.getController();

            scene.setFill(Color.TRANSPARENT);
            primaryStage.initStyle(StageStyle.TRANSPARENT);

            controller.setupWindowControls(primaryStage);

            primaryStage.setOnCloseRequest(event -> {
                controller.shutdownApplication();
                event.consume();
            });

            primaryStage.show();

            logger.info("SkyNexus wurde gestartet");
        } catch (Exception e) {
            logger.error("Fehler beim Starten der Anwendung", e);
            showFatalError("SkyNexus konnte nicht gestartet werden: " + e.getMessage());
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
                logger.debug("Logs-Verzeichnis wurde erstellt");
            } else {
                logger.warn("Logs-Verzeichnis konnte nicht erstellt werden");
            }
        }
    }

    @Override
    public void init() {
        try {
            if (Config.getDbProperty("db.url") == null) {
                logger.warn("Datenbankkonfiguration konnte nicht geladen werden, verwende Standardwerte");
            }

            DatabaseConnectionManager.getInstance();

            ApplicationReadyManager.getInstance().runWhenReady(() -> {
                try {
                    SystemSettingsService.getInstance().ensureSystemSettingsTableExists();
                    logger.debug("SystemSettingsService initialisiert und Tabelle überprüft");
                } catch (Exception e) {
                    logger.error("Fehler bei SystemSettingsService-Initialisierung: {}", e.getMessage());
                }
            });

            logger.debug("Datenbank-Manager initialisiert");
        } catch (Exception e) {
            logger.error("Fehler bei DB-Initialisierung: {}", e.getMessage());
        }
    }

    @Override
    public void stop() {
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
     */
    public static class ApplicationReadyManager {
        private static final Logger logger = LoggerFactory.getLogger(ApplicationReadyManager.class);
        private static ApplicationReadyManager instance;

        private final Map<String, CompletableFuture<Void>> componentReadyFutures = new HashMap<>();
        private final List<String> requiredComponents = new ArrayList<>();
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final CompletableFuture<Void> applicationReadyFuture;

        private ApplicationReadyManager() {
            applicationReadyFuture = new CompletableFuture<>();
            logger.debug("ApplicationReadyManager initialisiert");
        }

        public static synchronized ApplicationReadyManager getInstance() {
            if (instance == null) {
                instance = new ApplicationReadyManager();
            }
            return instance;
        }

        public synchronized CompletableFuture<Void> registerRequiredComponent(String componentName) {
            logger.debug("Komponente registriert: {}", componentName);

            if (!requiredComponents.contains(componentName)) {
                requiredComponents.add(componentName);
            }

            CompletableFuture<Void> future = new CompletableFuture<>();
            componentReadyFutures.put(componentName, future);
            future.thenRunAsync(this::checkApplicationReady, executor);

            return future;
        }

        public synchronized void setComponentReady(String componentName) {
            logger.debug("Komponente ist bereit: {}", componentName);

            CompletableFuture<Void> future = componentReadyFutures.get(componentName);
            if (future != null && !future.isDone()) {
                future.complete(null);
            } else {
                logger.warn("Nicht registrierte Komponente als bereit markiert: {}", componentName);
            }
        }

        public synchronized void setComponentFailed(String componentName, Throwable exception) {
            logger.error("Komponente ist fehlgeschlagen: {} - {}", componentName, exception.getMessage());

            CompletableFuture<Void> future = componentReadyFutures.get(componentName);
            if (future != null && !future.isDone()) {
                future.completeExceptionally(exception);

                if (requiredComponents.contains(componentName) && !applicationReadyFuture.isDone()) {
                    applicationReadyFuture.completeExceptionally(
                            new ApplicationNotReadyException("Kritische Komponente fehlgeschlagen: " + componentName, exception));
                }
            }
        }

        private synchronized void checkApplicationReady() {
            if (applicationReadyFuture.isDone()) {
                return;
            }

            boolean allReady = true;
            for (String component : requiredComponents) {
                CompletableFuture<Void> future = componentReadyFutures.get(component);
                if (future == null || !future.isDone() || future.isCompletedExceptionally()) {
                    allReady = false;
                    break;
                }
            }

            if (allReady) {
                logger.info("Anwendung ist startbereit");
                applicationReadyFuture.complete(null);
            }
        }

        public void runWhenReady(Runnable runnable) {
            applicationReadyFuture.thenRunAsync(runnable, executor);
        }

        public boolean isApplicationReady() {
            return applicationReadyFuture.isDone() && !applicationReadyFuture.isCompletedExceptionally();
        }

        public void shutdown() {
            executor.shutdownNow();
            logger.debug("ApplicationReadyManager heruntergefahren");
        }

        public static class ApplicationNotReadyException extends RuntimeException {
            public ApplicationNotReadyException(String message, Throwable cause) {
                super(message, cause);
            }
        }
    }
}
