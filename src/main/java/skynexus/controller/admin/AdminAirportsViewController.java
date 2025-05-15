package skynexus.controller.admin;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.model.Airport;
import skynexus.service.AirportService;
import skynexus.util.TimeUtils;
import skynexus.database.DatabaseConnectionManager;

import java.net.URL;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.ArrayList;

public class AdminAirportsViewController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(AdminAirportsViewController.class);

    @FXML
    private TableView<Airport> airportTable;
    @FXML
    private TableColumn<Airport, String> icaoCodeColumn;
    @FXML
    private TableColumn<Airport, String> nameColumn;
    @FXML
    private TableColumn<Airport, String> locationColumn;
    @FXML
    private TableColumn<Airport, String> countryColumn;
    @FXML
    private TableColumn<Airport, String> latitudeColumn;
    @FXML
    private TableColumn<Airport, String> longitudeColumn;

    @FXML
    private TextField searchField;
    @FXML
    private Button addAirportButton;
    @FXML
    private Label statusLabel;
    @FXML
    private Label totalCountLabel;

    private ObservableList<Airport> airportList;
    private FilteredList<Airport> filteredAirports;
    private AirportService airportService;

    /**
     * Lädt alle verfügbaren Länder aus der Datenbank in die ComboBox
     *
     * @param comboBox Die ComboBox, die mit Ländernamen gefüllt werden soll
     */
    private void loadCountriesIntoComboBox(ComboBox<String> comboBox) {
        CompletableFuture.supplyAsync(() -> {
            try (DatabaseConnectionManager.ConnectionScope scope = DatabaseConnectionManager.getInstance().createConnectionScope()) {
                return scope.execute(conn -> {
                    String sql = "SELECT DISTINCT country FROM countries ORDER BY country";
                    try (PreparedStatement stmt = conn.prepareStatement(sql);
                         ResultSet rs = stmt.executeQuery()) {

                        List<String> countries = new ArrayList<>();
                        while (rs.next()) {
                            countries.add(rs.getString("country"));
                        }
                        return countries;
                    }
                });
            } catch (Exception e) {
                logger.error("Fehler beim Laden der Länder: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }).thenAccept(countries -> {
            Platform.runLater(() -> {
                comboBox.getItems().addAll(countries);
                logger.debug("Länder erfolgreich geladen: {} Einträge", countries.size());
            });
        }).exceptionally(e -> {
            Platform.runLater(() -> {
                logger.error("Fehler beim Laden der Länder für ComboBox: {}", e.getMessage());
                showErrorAlert("Fehler", "Länder konnten nicht geladen werden: " + e.getMessage());
            });
            return null;
        });
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        logger.debug("AdminAirportsViewController wurde initialisiert");

        airportService = new AirportService();

        // Initialize table columns
        icaoCodeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getIcaoCode()));
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        locationColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCity()));
        countryColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCountry()));
        latitudeColumn.setCellValueFactory(data -> new SimpleStringProperty(String.format("%.4f", data.getValue().getLatitude())));
        longitudeColumn.setCellValueFactory(data -> new SimpleStringProperty(String.format("%.4f", data.getValue().getLongitude())));

        setupTableClickHandler();
        loadAirports();
        setupSearch();

        // Setup add button action
        addAirportButton.setOnAction(e -> showAddEditDialog(null));
    }

    private void loadAirports() {
        statusLabel.setText("Flughäfen werden geladen...");
        airportTable.setDisable(true);
        long startTime = System.currentTimeMillis();

        // Asynchrones Laden
        CompletableFuture.supplyAsync(() -> {
            try {
                return airportService.getAllAirports();
            } catch (Exception e) {
                logger.error("Fehler beim Laden der Flughäfen", e);
                throw new RuntimeException(e);
            }
        }).thenAccept(airports -> {
            // UI-Thread für Datenaktualisierung
            Platform.runLater(() -> {
                long duration = System.currentTimeMillis() - startTime;
                airportList = FXCollections.observableArrayList(airports);
                filteredAirports = new FilteredList<>(airportList, p -> true);
                airportTable.setItems(filteredAirports);
                airportTable.setDisable(false);

                // Statuszeile mit Zeitanzeige
                statusLabel.setText("Flughäfen erfolgreich geladen (" + TimeUtils.formatLoadingTime(duration) + ")");
                updateTotalCount();
            });
        }).exceptionally(e -> {
            Platform.runLater(() -> {
                airportTable.setDisable(false);
                statusLabel.setText("Fehler beim Laden der Flughäfen");
                showErrorAlert("Fehler beim Laden der Flughäfen", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            });
            return null;
        });
    }

    private void setupSearch() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredAirports.setPredicate(airport -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }

                String lowerCaseFilter = newValue.toLowerCase();

                return airport.getName().toLowerCase().contains(lowerCaseFilter) ||
                        airport.getIcaoCode().toLowerCase().contains(lowerCaseFilter) ||
                        airport.getCity().toLowerCase().contains(lowerCaseFilter) ||
                        airport.getCountry().toLowerCase().contains(lowerCaseFilter) ||
                        String.valueOf(airport.getLatitude()).contains(lowerCaseFilter) ||
                        String.valueOf(airport.getLongitude()).contains(lowerCaseFilter);
            });

            updateTotalCount();
        });
    }

    private void setupTableClickHandler() {
        // Doppelklick-Handler für Bearbeitung
        airportTable.setRowFactory(tv -> {
            TableRow<Airport> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    Airport airport = row.getItem();
                    showAddEditDialog(airport);
                }
            });
            return row;
        });
    }

    private void showAddEditDialog(Airport airport) {
        Dialog<Airport> dialog = new Dialog<>();
        dialog.setTitle(airport == null ? "Neuen Flughafen hinzufügen" : "Flughafen bearbeiten");
        dialog.setHeaderText(null);

        // Set the button types
        ButtonType saveButtonType = new ButtonType("Speichern", ButtonBar.ButtonData.OK_DONE);
        ButtonType deleteButtonType = new ButtonType("Löschen", ButtonBar.ButtonData.OTHER);

        if (airport != null) {
            // Only show delete button when editing an existing airport
            dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, deleteButtonType, ButtonType.CANCEL);
        } else {
            // For new airports, only show save and cancel
            dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        }

        // Create the form grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 25, 20));

        // Style für die Labels angeben
        String labelStyle = "-fx-font-size: 12px; -fx-text-fill: #333333; -fx-font-weight: normal; -fx-min-width: 100px;";

        // Create fields
        TextField icaoField = new TextField();
        icaoField.setPromptText("ICAO-Code (z.B. EDDF)");

        TextField nameField = new TextField();
        nameField.setPromptText("Name des Flughafens");

        TextField cityField = new TextField();
        cityField.setPromptText("Stadt");

        ComboBox<String> countryCombo = new ComboBox<>();
        countryCombo.setEditable(false);
        countryCombo.setPromptText("Land auswählen");
        countryCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Land auswählen" : item);
            }
        });

        // Lade verfügbare Länder aus der Datenbank
        loadCountriesIntoComboBox(countryCombo);

        TextField latitudeField = new TextField();
        latitudeField.setPromptText("Breitengrad (z.B. 50.0379) *");

        TextField longitudeField = new TextField();
        longitudeField.setPromptText("Längengrad (z.B. 8.5622) *");

        // Pre-fill fields if editing
        if (airport != null) {
            icaoField.setText(airport.getIcaoCode());
            nameField.setText(airport.getName());
            cityField.setText(airport.getCity());

            // Setze den aktuellen Länderwert in der ComboBox
            if (airport.getCountry() != null) {
                countryCombo.setValue(airport.getCountry());
            }

            latitudeField.setText(String.valueOf(airport.getLatitude()));
            longitudeField.setText(String.valueOf(airport.getLongitude()));

            // ICAO-Code sollte nicht verändert werden, wenn wir einen bestehenden Flughafen bearbeiten
            icaoField.setDisable(true);
        }

        // Add fields to the grid with proper alignment
        Label icaoLabel = new Label("ICAO-Code:*");
        icaoLabel.setStyle(labelStyle);
        GridPane.setHalignment(icaoLabel, javafx.geometry.HPos.RIGHT);
        grid.add(icaoLabel, 0, 0);
        grid.add(icaoField, 1, 0);

        Label nameLabel = new Label("Name:*");
        nameLabel.setStyle(labelStyle);
        GridPane.setHalignment(nameLabel, javafx.geometry.HPos.RIGHT);
        grid.add(nameLabel, 0, 1);
        grid.add(nameField, 1, 1);

        Label cityLabel = new Label("Stadt:*");
        cityLabel.setStyle(labelStyle);
        GridPane.setHalignment(cityLabel, javafx.geometry.HPos.RIGHT);
        grid.add(cityLabel, 0, 2);
        grid.add(cityField, 1, 2);

        Label countryLabel = new Label("Land:*");
        countryLabel.setStyle(labelStyle);
        GridPane.setHalignment(countryLabel, javafx.geometry.HPos.RIGHT);
        grid.add(countryLabel, 0, 3);
        grid.add(countryCombo, 1, 3);

        Label latitudeLabel = new Label("Breitengrad:*");
        latitudeLabel.setStyle(labelStyle);
        GridPane.setHalignment(latitudeLabel, javafx.geometry.HPos.RIGHT);
        grid.add(latitudeLabel, 0, 4);
        grid.add(latitudeField, 1, 4);

        Label longitudeLabel = new Label("Längengrad:*");
        longitudeLabel.setStyle(labelStyle);
        GridPane.setHalignment(longitudeLabel, javafx.geometry.HPos.RIGHT);
        grid.add(longitudeLabel, 0, 5);
        grid.add(longitudeField, 1, 5);

        dialog.getDialogPane().setContent(grid);

        // Enable/disable save button depending on whether fields are filled
        dialog.getDialogPane().lookupButton(saveButtonType).setDisable(true);

        // Add listeners for required fields
        icaoField.textProperty().addListener((observable, oldValue, newValue) -> validateInput(dialog, saveButtonType, icaoField, nameField, cityField, countryCombo, latitudeField, longitudeField));
        nameField.textProperty().addListener((observable, oldValue, newValue) -> validateInput(dialog, saveButtonType, icaoField, nameField, cityField, countryCombo, latitudeField, longitudeField));
        cityField.textProperty().addListener((observable, oldValue, newValue) -> validateInput(dialog, saveButtonType, icaoField, nameField, cityField, countryCombo, latitudeField, longitudeField));
        countryCombo.valueProperty().addListener((observable, oldValue, newValue) -> validateInput(dialog, saveButtonType, icaoField, nameField, cityField, countryCombo, latitudeField, longitudeField));
        latitudeField.textProperty().addListener((observable, oldValue, newValue) -> validateInput(dialog, saveButtonType, icaoField, nameField, cityField, countryCombo, latitudeField, longitudeField));
        longitudeField.textProperty().addListener((observable, oldValue, newValue) -> validateInput(dialog, saveButtonType, icaoField, nameField, cityField, countryCombo, latitudeField, longitudeField));

        // Convert the result
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == deleteButtonType) {
                // Show confirmation dialog for deletion
                showDeleteConfirmation(airport);
                dialog.close();
                return null; // We don't return the airport, just handle deletion
            } else if (dialogButton == saveButtonType) {
                try {
                    Airport result = airport == null ? new Airport() : airport;
                    result.setIcaoCode(icaoField.getText().toUpperCase());
                    result.setName(nameField.getText());
                    result.setCity(cityField.getText());

                    // Land aus ComboBox abrufen
                    String selectedCountry = countryCombo.getValue();
                    if (selectedCountry == null || selectedCountry.trim().isEmpty()) {
                        showErrorAlert("Eingabefehler", "Bitte wählen Sie ein Land aus.");
                        return null;
                    }
                    result.setCountry(selectedCountry);

                    // Parse mandatory latitude
                    try {
                        result.setLatitude(Double.parseDouble(latitudeField.getText()));
                    } catch (NumberFormatException e) {
                        logger.error("Ungültiger Breitengrad: {}", latitudeField.getText());
                        showErrorAlert("Eingabefehler", "Bitte geben Sie einen gültigen Breitengrad ein (z.B. 50.0379)");
                        return null;
                    }

                    // Parse mandatory longitude
                    try {
                        result.setLongitude(Double.parseDouble(longitudeField.getText()));
                    } catch (NumberFormatException e) {
                        logger.error("Ungültiger Längengrad: {}", longitudeField.getText());
                        showErrorAlert("Eingabefehler", "Bitte geben Sie einen gültigen Längengrad ein (z.B. 8.5622)");
                        return null;
                    }

                    return result;
                } catch (Exception e) {
                    logger.error("Fehler beim Erstellen des Flughafen-Objekts", e);
                    showErrorAlert("Eingabefehler", "Bitte überprüfen Sie Ihre Eingaben");
                    return null;
                }
            }
            return null;
        });

// Show the dialog and process the result
        Optional<Airport> result = dialog.showAndWait();
        result.ifPresent(newAirport -> {
            try {
                boolean success;
                if (airport == null) {
                    // Neuen Flughafen hinzufügen
                    success = airportService.saveAirport(newAirport);
                    if (success) {
                        airportList.add(newAirport);
                    }
                } else {
                    // Bestehenden Flughafen aktualisieren
                    success = airportService.updateAirport(newAirport);
                    if (success) {
                        // Aktualisiere die Ansicht
                        loadAirports();
                    }
                }

                if (!success) {
                    showErrorAlert("Fehler beim Speichern",
                            "Der Flughafen konnte nicht gespeichert werden. Möglicherweise existiert er bereits mit diesem ICAO-Code.");
                }
            } catch (Exception e) {
                logger.error("Fehler beim Speichern des Flughafens", e);
                showErrorAlert("Fehler beim Speichern", e.getMessage());
            }
        });
    }

    private void validateInput(Dialog<?> dialog, ButtonType saveButtonType,
                               TextField icaoField, TextField nameField,
                               TextField cityField, ComboBox<String> countryCombo,
                               TextField latitudeField, TextField longitudeField) {
        boolean disableButton = icaoField.getText().trim().isEmpty() ||
                nameField.getText().trim().isEmpty() ||
                cityField.getText().trim().isEmpty() ||
                countryCombo.getValue() == null ||
                latitudeField.getText().trim().isEmpty() ||
                longitudeField.getText().trim().isEmpty() ||
                !icaoField.getText().matches("[A-Za-z]{4}");

        // Zusätzliche Prüfung für gültige Koordinatenformate
        if (!disableButton) {
            try {
                Double.parseDouble(latitudeField.getText());
            } catch (NumberFormatException e) {
                disableButton = true;
            }

            try {
                Double.parseDouble(longitudeField.getText());
            } catch (NumberFormatException e) {
                disableButton = true;
            }
        }

        dialog.getDialogPane().lookupButton(saveButtonType).setDisable(disableButton);
    }

    private void showDeleteConfirmation(Airport airport) {
        // Prüfe zunächst auf aktive Flüge
        boolean hasActiveFlights = airportService.hasActiveFlights(airport.getId());

        if (hasActiveFlights) {
            // Detaillierte Abhängigkeiten ermitteln
            String dependencySummary = airportService.getDependencySummary(airport.getId());

            // Informiere den Benutzer, dass erst Flüge gelöscht werden müssen
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Flughafen kann nicht gelöscht werden");
            alert.setHeaderText("Aktive Flüge vorhanden");
            alert.setContentText(String.format("Der Flughafen %s (%s) kann nicht gelöscht werden.\n\n" +
                    "Folgende Abhängigkeiten verhindern das Löschen:\n\n%s\n" +
                    "Bitte löschen Sie zuerst alle aktiven Flüge oder ändern Sie deren Status.",
                    airport.getName(),
                    airport.getIcaoCode(),
                    dependencySummary));
            alert.showAndWait();
            return;
        }

        // Prüfe auf bestehende Routen
        boolean hasRoutes = airportService.isAirportInUse(airport.getId());

        // Erstelle passende Warnung basierend auf Verwendung
        Alert alert;

        if (hasRoutes) {
            // Detaillierte Abhängigkeiten ermitteln
            String dependencySummary = airportService.getDependencySummary(airport.getId());

            alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Flughafen kann nicht gelöscht werden");
            alert.setHeaderText("Flughafen " + airport.getName() + " wird in Routen verwendet");
            alert.setContentText(String.format("Der Flughafen %s (%s) kann nicht gelöscht werden.\n\n" +
                    "Folgende Abhängigkeiten verhindern das Löschen:\n\n%s\n" +
                    "Bitte löschen Sie zuerst alle abhängigen Routen.",
                    airport.getName(),
                    airport.getIcaoCode(),
                    dependencySummary));

            // Nur OK-Button zeigen, da Löschen nicht möglich ist
            ButtonType okButton = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
            alert.getButtonTypes().setAll(okButton);
            alert.showAndWait();
        } else {
            // Keine Abhängigkeiten - normaler Löschvorgang
            alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Flughafen löschen");
            alert.setHeaderText("Flughafen " + airport.getName() + " löschen?");
            alert.setContentText(String.format("Möchten Sie den Flughafen %s (%s) wirklich löschen?\n\n" +
                    "Dieser Vorgang kann nicht rückgängig gemacht werden.",
                    airport.getName(),
                    airport.getIcaoCode()));

            ButtonType deleteButton = new ButtonType("Löschen", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancelButton = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(deleteButton, cancelButton);

            alert.showAndWait().ifPresent(response -> {
                if (response == deleteButton) {
                    try {
                        boolean success = airportService.deleteAirport(airport.getId());
                        if (success) {
                            airportList.remove(airport);
                            updateTotalCount();

                            // Erfolgreiche Löschung anzeigen
                            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                            successAlert.setTitle("Erfolgreich gelöscht");
                            successAlert.setHeaderText(null);
                            successAlert.setContentText(String.format("Flughafen %s wurde erfolgreich gelöscht.",
                                    airport.getName()));
                            successAlert.showAndWait();
                        } else {
                            // Falls doch noch Fehler auftreten
                            showErrorAlert("Fehler beim Löschen",
                                    "Der Flughafen konnte nicht gelöscht werden. Bitte versuchen Sie es erneut.");
                        }
                    } catch (Exception e) {
                        logger.error("Fehler beim Löschen des Flughafens", e);
                        showErrorAlert("Fehler beim Löschen", e.getMessage());
                    }
                }
            });
        }
    }

    private void updateTotalCount() {
        int count = filteredAirports.size();
        totalCountLabel.setText("Flughäfen gesamt: " + count);
    }

    private void showErrorAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
