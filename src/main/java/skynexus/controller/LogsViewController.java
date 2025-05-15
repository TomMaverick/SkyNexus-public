package skynexus.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.model.LogEntry;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controller für die Anzeige und Filterung von Log-Einträgen.
 * Ermöglicht das Laden, Filtern und Durchsuchen der Systemlogs.
 */
public class LogsViewController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(LogsViewController.class);
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\s+\\[(.*?)\\]\\s+(\\w+)\\s+([\\w.]+)\\s+-\\s+(.*)");
    private static final String ALL_LEVELS = "ALLE";

    @FXML private TableView<LogEntry> logsTable;
    @FXML private TableColumn<LogEntry, String> timestampColumn;
    @FXML private TableColumn<LogEntry, String> levelColumn;
    @FXML private TableColumn<LogEntry, String> loggerColumn;
    @FXML private TableColumn<LogEntry, String> messageColumn;
    @FXML private ComboBox<String> logFileComboBox;
    @FXML private ComboBox<String> logLevelComboBox;
    @FXML private TextField searchField;
    @FXML private Button refreshButton;
    @FXML private Label statusLabel;
    @FXML private Label entryCountLabel;

    private final ObservableList<LogEntry> logEntries = FXCollections.observableArrayList();
    private FilteredList<LogEntry> filteredLogEntries;
    private String logsDirectory;

    /**
     * Initialisiert den Controller und bereitet UI-Komponenten vor.
     * Setzt das Log-Verzeichnis, initialisiert Tabelle und Filter und lädt Log-Dateien.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logsDirectory = System.getProperty("user.dir") + File.separator + "logs";
        setupTable();
        setupFilters();
        loadLogFiles();
    }

    /**
     * Richtet die Tabelle und ihre Spalten ein.
     */
    private void setupTable() {
        timestampColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getFormattedTimestamp()));
        levelColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().level()));
        loggerColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().logger()));
        messageColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().message()));

        logsTable.setItems(filteredLogEntries = new FilteredList<>(logEntries));
    }

    /**
     * Konfiguriert Filter und Listener für Sucheingaben und Auswahlfelder.
     */
    private void setupFilters() {
        logLevelComboBox.setItems(FXCollections.observableArrayList(ALL_LEVELS, "ERROR", "WARN", "INFO", "DEBUG", "TRACE"));
        logLevelComboBox.setValue(ALL_LEVELS);

        searchField.textProperty().addListener((obs, old, val) -> applyFilters());
        logLevelComboBox.valueProperty().addListener((obs, old, val) -> applyFilters());
        logFileComboBox.valueProperty().addListener((obs, old, val) -> {
            if (val != null) loadSelectedLogFile(val);
        });
    }

    /**
     * Lädt verfügbare Log-Dateien aus dem Logs-Verzeichnis.
     * Erstellt das Verzeichnis, falls es nicht existiert.
     */
    private void loadLogFiles() {
        File logsDir = new File(logsDirectory);
        if (!logsDir.exists()) {
            try {
                Files.createDirectories(Paths.get(logsDirectory));
            } catch (IOException e) {
                logger.error("Fehler beim Erstellen des Logs-Verzeichnisses", e);
                return;
            }
        }

        File[] logFiles = logsDir.listFiles((dir, name) -> name.endsWith(".log"));
        if (logFiles != null && logFiles.length > 0) {
            Arrays.sort(logFiles, Comparator.comparing(File::lastModified).reversed());
            logFileComboBox.setItems(FXCollections.observableArrayList(
                Arrays.stream(logFiles).map(File::getName).toArray(String[]::new)));
            logFileComboBox.setValue(logFiles[0].getName());
        }
    }

    /**
     * Lädt und parsed eine ausgewählte Log-Datei.
     * Aktualisiert die Statusleiste mit dem Ergebnis.
     *
     * @param fileName Name der zu ladenden Log-Datei
     */
    private void loadSelectedLogFile(String fileName) {
        logEntries.clear();
        File logFile = new File(logsDirectory + File.separator + fileName);

        if (!logFile.exists()) {
            statusLabel.setText("Datei nicht gefunden: " + fileName);
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            int parsed = 0;

            while ((line = reader.readLine()) != null) {
                if (parseLine(line)) parsed++;
            }

            logEntries.sort(Comparator.comparing(LogEntry::timestamp).reversed());
            statusLabel.setText(parsed + " Einträge aus " + fileName + " geladen");
            updateCount();

        } catch (IOException e) {
            logger.error("Fehler beim Lesen der Log-Datei", e);
            statusLabel.setText("Fehler beim Lesen: " + e.getMessage());
        }
    }

    /**
     * Parsed eine einzelne Log-Zeile in ein LogEntry-Objekt.
     *
     * @param line Die zu parsende Log-Zeile
     * @return true wenn die Zeile erfolgreich geparst wurde, sonst false
     */
    private boolean parseLine(String line) {
        Matcher matcher = LOG_PATTERN.matcher(line);
        if (!matcher.matches()) return false;

        try {
            String timestamp = matcher.group(1);
            String thread = matcher.group(2);
            String level = matcher.group(3);
            String loggerName = matcher.group(4);
            String message = matcher.group(5);

            LocalDateTime dateTime = LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            logEntries.add(new LogEntry(dateTime, thread, level, loggerName, message));
            return true;
        } catch (Exception e) {
            logger.debug("Fehler beim Parsen der Log-Zeile: {}", line, e);
            return false;
        }
    }

    /**
     * Wendet die aktuellen Filter auf die Log-Einträge an.
     * Filtert nach Log-Level und Suchtext.
     */
    private void applyFilters() {
        String search = searchField.getText().toLowerCase();
        String level = logLevelComboBox.getValue();

        filteredLogEntries.setPredicate(entry -> {
            if (!ALL_LEVELS.equals(level) && !entry.level().equals(level)) return false;

            if (!search.isEmpty()) {
                return entry.message().toLowerCase().contains(search) ||
                       entry.logger().toLowerCase().contains(search) ||
                       entry.thread().toLowerCase().contains(search) ||
                       entry.getFormattedTimestamp().toLowerCase().contains(search);
            }
            return true;
        });

        updateCount();
    }

    /**
     * Aktualisiert die Anzeige der Anzahl gefilterter Einträge.
     */
    private void updateCount() {
        entryCountLabel.setText(filteredLogEntries.size() + " Einträge");
    }

    /**
     * Event-Handler für den Refresh-Button.
     * Aktualisiert die Liste der verfügbaren Log-Dateien und versucht,
     * die zuvor ausgewählte Datei beizubehalten.
     */
    @FXML
    private void onRefresh() {
        String selected = logFileComboBox.getValue();
        loadLogFiles();
        if (selected != null && logFileComboBox.getItems().contains(selected)) {
            logFileComboBox.setValue(selected);
        }
    }
}
