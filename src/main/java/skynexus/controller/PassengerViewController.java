package skynexus.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.enums.Gender;
import skynexus.enums.SeatClass;
import skynexus.model.Booking;
import skynexus.model.Flight;
import skynexus.model.Passenger;
import skynexus.model.User;
import skynexus.service.FlightService;
import skynexus.service.PassengerService;
import skynexus.util.ExceptionHandler;
import skynexus.util.SessionManager;
import skynexus.util.TimeUtils;

import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Controller für die Passagierverwaltung
 */
public class PassengerViewController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(PassengerViewController.class);

    // Services
    private final PassengerService passengerService = PassengerService.getInstance();
    private final FlightService flightService = FlightService.getInstance();
    // Daten-Collections
    private final ObservableList<Passenger> passengers = FXCollections.observableArrayList();
    // UI-Elemente - Tabelle
    @FXML
    private TableView<Passenger> passengerTable;
    @FXML
    private TableColumn<Passenger, String> lastNameColumn;
    @FXML
    private TableColumn<Passenger, String> firstNameColumn;
    @FXML
    private TableColumn<Passenger, String> genderColumn;
    @FXML
    private TableColumn<Passenger, Integer> ageColumn;
    @FXML
    private TableColumn<Passenger, String> nationalityColumn;
    @FXML
    private TableColumn<Passenger, String> passportColumn;
    @FXML
    private TableColumn<Passenger, String> bookingsColumn;
    // UI-Elemente - Filter
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<Flight> flightFilter;
    @FXML
    private ComboBox<SeatClass> classFilter;
    // UI-Elemente - Aktionen
    @FXML
    private Button addButton;
    @FXML
    private Button refreshButton;
    // UI-Elemente - Status
    @FXML
    private Label statusLabel;
    @FXML
    private Label totalLabel;
    // Aktuell ausgewählter Passagier
    private Passenger currentPassenger;
    private FilteredList<Passenger> filteredPassengers;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // ResourceBundle für I18n
        logger.info("Initialisiere Passenger-View");

        try {
            // Tabellenspalten konfigurieren
            setupTableColumns();

            // Filter einrichten
            setupFilters();

            // Kombinierter Handler für Doppelklick + Tooltip in einer RowFactory
            passengerTable.setRowFactory(tv -> {
                TableRow<Passenger> row = new TableRow<>();

                // Tooltip für jede Zeile einrichten
                Tooltip tooltip = new Tooltip();
                tooltip.setStyle("-fx-font-size: 12px; -fx-font-family: 'System'; -fx-show-delay: 300ms;");

                // Event-Handler für Mauseingabe (Tooltip)
                row.setOnMouseEntered(event -> {
                    Passenger passenger = row.getItem();
                    if (passenger != null && !passenger.getBookings().isEmpty()) {
                        String allBookings = passenger.getAllBookingsAsString();
                        tooltip.setText(allBookings);
                        Tooltip.install(row, tooltip);
                        logger.debug("Tooltip für Passagier {} angezeigt", passenger.getFullName());
                    }
                });

                // Event-Handler für Mausaustritt (Tooltip entfernen)
                row.setOnMouseExited(event -> {
                    Tooltip.uninstall(row, tooltip);
                });

                // Doppelklick-Handler für das Öffnen der Detailansicht
                row.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && (!row.isEmpty())) {
                        Passenger selectedPassenger = row.getItem();
                        showPassengerDetails(selectedPassenger);
                    }
                });

                return row;
            });

            // Einzelklick-Handler für Statusanzeige
            passengerTable.getSelectionModel().selectedItemProperty().addListener(
                    (obs, oldSelection, newSelection) -> {
                        if (newSelection != null) {
                            // Nur Status-Update, kein Dialog öffnen
                            currentPassenger = newSelection;

                            // Status anzeigen
                            if (statusLabel != null) {
                                statusLabel.setText("Passagier " + newSelection.getFullName() + " ausgewählt");
                            }
                        }
                    });

            // Daten laden
            loadAllData();

        } catch (Exception e) {
            logger.error("Fehler beim Initialisieren der Passagier-View", e);
            ExceptionHandler.handleException(e, "bei der Initialisierung der Passagier-Ansicht");
        }
    }

    /**
     * Konfiguriert die Tabellenspalten
     */
    private void setupTableColumns() {
        lastNameColumn.setCellValueFactory(new PropertyValueFactory<>("lastName"));

        firstNameColumn.setCellValueFactory(new PropertyValueFactory<>("firstName"));

        genderColumn.setCellValueFactory(cellData -> {
            if (cellData.getValue() != null && cellData.getValue().getGender() != null) {
                Gender gender = cellData.getValue().getGender();
                String text = switch (gender) {
                    case MALE -> "Männlich";
                    case FEMALE -> "Weiblich";
                    case OTHER -> "Divers";
                };
                return new SimpleStringProperty(text);
            }
            return new SimpleStringProperty("n/a");
        });

        ageColumn.setCellValueFactory(cellData -> {
            if (cellData.getValue() != null) {
                return new SimpleIntegerProperty(cellData.getValue().getAge()).asObject();
            }
            return new SimpleIntegerProperty(0).asObject();
        });

        nationalityColumn.setCellValueFactory(new PropertyValueFactory<>("nationality"));
        passportColumn.setCellValueFactory(new PropertyValueFactory<>("passportNumber"));

        // Neue Spalte für die Buchungsübersicht
        bookingsColumn.setCellValueFactory(cellData -> {
            if (cellData.getValue() != null) {
                return new SimpleStringProperty(cellData.getValue().getBookingsAsString());
            }
            return new SimpleStringProperty("-");
        });
    }

    /**
     * Richtet die Filter für die Passagiertabelle ein
     */
    private void setupFilters() {
        filteredPassengers = new FilteredList<>(passengers, p -> true);
        passengerTable.setItems(filteredPassengers);

        // Suchfeld-Filter
        searchField.textProperty().addListener((obs, oldVal, newVal) -> updateFilters());

        // Flugfilter
        flightFilter.getItems().add(null); // Option für "Alle Flüge"
        flightFilter.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Flight item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Alle Flüge" : formatFlightDisplay(item, null));
            }
        });
        flightFilter.setCellFactory(p -> new ListCell<>() {
            @Override
            protected void updateItem(Flight item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Alle Flüge" : formatFlightDisplay(item, null));
            }
        });
        flightFilter.getSelectionModel().selectFirst();
        flightFilter.valueProperty().addListener((obs, oldVal, newVal) -> updateFilters());

        // Klassenfilter
        classFilter.getItems().addAll(null, SeatClass.ECONOMY, SeatClass.BUSINESS, SeatClass.FIRST_CLASS);
        classFilter.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(SeatClass item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("Alle Klassen");
                } else {
                    setText(switch (item) {
                        case ECONOMY -> "Economy";
                        case BUSINESS -> "Business";
                        case FIRST_CLASS -> "First Class";
                    });
                }
            }
        });
        classFilter.setCellFactory(p -> new ListCell<>() {
            @Override
            protected void updateItem(SeatClass item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("Alle Klassen");
                } else {
                    setText(switch (item) {
                        case ECONOMY -> "Economy";
                        case BUSINESS -> "Business";
                        case FIRST_CLASS -> "First Class";
                    });
                }
            }
        });
        classFilter.getSelectionModel().selectFirst();
        classFilter.valueProperty().addListener((obs, oldVal, newVal) -> updateFilters());
    }

    /**
     * Aktualisiert die Filter basierend auf den aktuellen Filtereinstellungen
     */
    private void updateFilters() {
        if (filteredPassengers == null) {
            return;
        }

        filteredPassengers.setPredicate(passenger -> {
            if (passenger == null) {
                return false;
            }

            // Suchfilter
            if (searchField.getText() != null && !searchField.getText().isEmpty()) {
                String searchText = searchField.getText().toLowerCase();

                if (!passenger.getLastName().toLowerCase().contains(searchText) &&
                        !passenger.getFirstName().toLowerCase().contains(searchText) &&
                        !(passenger.getPassportNumber() != null &&
                                passenger.getPassportNumber().toLowerCase().contains(searchText))) {
                    return false;
                }
            }

            // Flugfilter
            Flight selectedFlight = flightFilter.getValue();
            if (selectedFlight != null) {
                boolean hasBookingForFlight = passenger.getBookings().stream()
                        .anyMatch(booking -> booking.getFlight().getId().equals(selectedFlight.getId()));

                if (!hasBookingForFlight) {
                    return false;
                }
            }

            // Klassenfilter
            SeatClass selectedClass = classFilter.getValue();
            if (selectedClass != null) {
                boolean hasBookingInClass = passenger.getBookings().stream()
                        .anyMatch(booking -> booking.getSeatClass() == selectedClass);

                return hasBookingInClass;
            }

            return true;
        });

        // Status-Label aktualisieren
        updateTotalLabel();
    }

    /**
     * Aktualisiert das Label mit der Gesamtanzahl der Passagiere
     */
    private void updateTotalLabel() {
        if (totalLabel != null) {
            int total = filteredPassengers != null ? filteredPassengers.size() : 0;
            totalLabel.setText("Passagiere gesamt: " + total);
        }
    }


    /**
     * Lädt alle Daten aus den Services asynchron mit optimierter Datenbanknutzung
     */
    private void loadAllData() {
        // Status anzeigen
        if (statusLabel != null) {
            statusLabel.setText("Daten werden geladen...");
        }

        long startTime = System.currentTimeMillis();

        // Asynchrone Datenladung
        CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Lade Passagier- und Flugdaten asynchron mit optimierter DB-Nutzung...");

                // Benutzerrolle ermitteln (falls nötig)
                User currentUser = SessionManager.getInstance().getCurrentUser();

                // Parallel laden - NUR AKTIVE FLÜGE für UI-Filterung
                // Passagiere ohne Buchungen laden (werden später in einer Batch-Operation geladen)
                CompletableFuture<List<Passenger>> passengersFuture =
                        CompletableFuture.supplyAsync(passengerService::getAllPassengers);

                // Nur aktive Flüge laden, abgeschlossene ausschließen für bessere Performance
                CompletableFuture<List<Flight>> flightsFuture =
                        CompletableFuture.supplyAsync(flightService::getActiveFlights);

                // Warten auf Abschluss beider Abfragen
                CompletableFuture.allOf(passengersFuture, flightsFuture).join();

                // Ergebnisse abrufen
                List<Passenger> loadedPassengers = passengersFuture.get();
                List<Flight> loadedFlights = flightsFuture.get();

                logger.debug("Passagiere und Flüge asynchron geladen, optimiere Buchungsabfragen...");

                // Optimierung: Lade alle Buchungen in einer einzigen Datenbankabfrage (Batch-Loading)
                // Dies vermeidet das N+1 Problem und reduziert die Anzahl der Datenbankverbindungen drastisch
                if (!loadedPassengers.isEmpty()) {
                    // Extrahiere alle Passagier-IDs
                    List<Long> passengerIds = loadedPassengers.stream()
                            .map(Passenger::getId)
                            .collect(Collectors.toList()); // Corrected import reference

                    // Lade alle Buchungen für alle Passagiere in einer Abfrage
                    Map<Long, List<Booking>> bookingsMap = passengerService.getBookingsForPassengers(passengerIds);

                    // Füge Buchungen den entsprechenden Passagieren hinzu
                    for (Passenger passenger : loadedPassengers) {
                        List<Booking> bookings = bookingsMap.getOrDefault(passenger.getId(), new ArrayList<>());
                        passenger.setBookings(bookings);

                        // Setze den korrekten Passagier in den Buchungen (statt Platzhalter)
                        for (Booking booking : bookings) {
                            booking.setPassenger(passenger);
                        }
                    }
                }

                return new Object[]{loadedPassengers, loadedFlights};
            } catch (Exception e) {
                logger.error("Fehler beim asynchronen Laden der Daten: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }).thenAcceptAsync(result -> {
            try {
                @SuppressWarnings("unchecked")
                List<Passenger> loadedPassengers = (List<Passenger>) result[0];
                @SuppressWarnings("unchecked")
                List<Flight> loadedFlights = (List<Flight>) result[1];

                // UI-Aktualisierungen in JavaFX-Thread
                Platform.runLater(() -> {
                    try {
                        long duration = System.currentTimeMillis() - startTime;

                        // Passagierdaten laden
                        passengers.clear();
                        passengers.addAll(loadedPassengers);

                        // Flugdaten für Filter laden
                        flightFilter.getItems().clear();
                        flightFilter.getItems().add(null); // Option für "Alle Flüge"
                        flightFilter.getItems().addAll(loadedFlights);
                        flightFilter.getSelectionModel().selectFirst();

                        // Filter aktualisieren
                        updateFilters();

                        // Status anzeigen
                        if (statusLabel != null) {
                            statusLabel.setText("Passagiere erfolgreich geladen (" + TimeUtils.formatLoadingTime(duration) + ")");
                        }

                        logger.info("{} Passagiere und {} Flüge geladen in {} ms",
                                loadedPassengers.size(), loadedFlights.size(), duration);
                    } catch (Exception ex) {
                        logger.error("Fehler bei der UI-Aktualisierung: {}", ex.getMessage());
                        ExceptionHandler.handleException(ex, "bei der Aktualisierung der Passagier-Benutzeroberfläche");
                    }
                });
            } catch (Exception e) {
                logger.error("Fehler bei der Verarbeitung der geladenen Daten: {}", e.getMessage());
                Platform.runLater(() -> ExceptionHandler.handleException(e, "bei der Verarbeitung der Passagier-Daten"));
            }
        }, Platform::runLater).exceptionally(e -> {
            logger.error("Schwerwiegender Fehler beim Laden der Daten: {}", e.getMessage(), e);
            Platform.runLater(() -> {
                if (statusLabel != null) {
                    statusLabel.setText("Fehler beim Laden der Daten");
                }
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                ExceptionHandler.handleException(cause, "beim Laden der Passagier-Daten");
            });
            return null;
        });
    } // End of loadAllData method

    /**
     * Zeigt den Dialog zur Bearbeitung eines existierenden Passagiers mit FXML
     */
    private void showPassengerDetails(Passenger passenger) {
        try {
            // Dialog erstellen
            Dialog<Passenger> dialog = new Dialog<>();
            dialog.setTitle("Passagier bearbeiten");
            dialog.setHeaderText("Passagier-Details: " + passenger.getFullName());

            // Dialog-Buttons definieren
            ButtonType saveButtonType = new ButtonType("Speichern", ButtonBar.ButtonData.OK_DONE);
            ButtonType deleteButtonType = new ButtonType("Löschen", ButtonBar.ButtonData.OTHER);
            dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, deleteButtonType, ButtonType.CANCEL);

            // FXML-Dialog laden
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dialogs/PassengerEditDialog.fxml"));
            Parent root = loader.load();
            dialog.getDialogPane().setContent(root);

            // Controller abrufen und initialisieren
            skynexus.controller.dialogs.PassengerDialogController controller = loader.getController();
            controller.initEditPassenger(dialog, passenger);

            // Result Converter einrichten
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == saveButtonType) {
                    return controller.getResult();
                } else if (dialogButton == deleteButtonType) {
                    // Sonderwert für Löschaktion (neue Instanz mit spezieller ID)
                    Passenger passengerToDelete = new Passenger(
                            passenger.getLastName(),
                            passenger.getFirstName(),
                            passenger.getGender(),
                            passenger.getDateOfBirth(),
                            passenger.getNationality(),
                            passenger.getPassportNumber());
                    passengerToDelete.setId(Long.MIN_VALUE); // Marker für Löschung
                    return passengerToDelete;
                }
                return null;
            });

            // Dialog anzeigen und Ergebnis verarbeiten
            Optional<Passenger> result = dialog.showAndWait();
            result.ifPresent(updatedPassenger -> {
                try {
                    if (updatedPassenger.getId() == Long.MIN_VALUE) {
                        // Passagier löschen
                        boolean success = passengerService.deletePassenger(passenger);
                        if (success) {
                            statusLabel.setText("Passagier " + passenger.getFullName() + " wurde gelöscht");

                            // Direkte UI-Aktualisierung
                            passengers.remove(passenger);
                            updateFilters();
                        } else {
                            ExceptionHandler.showErrorDialog("Fehler beim Löschen",
                                    "Der Passagier konnte nicht gelöscht werden.");
                        }
                    } else {
                        // Passagier aktualisieren
                        boolean success = passengerService.savePassenger(updatedPassenger);
                        if (success) {
                            statusLabel.setText("Passagier " + updatedPassenger.getFullName() + " wurde aktualisiert");

                            // Direkte UI-Aktualisierung
                            int index = passengers.indexOf(passenger);
                            if (index >= 0) {
                                passengers.set(index, updatedPassenger);
                            } else {
                                passengers.add(updatedPassenger);
                            }
                            updateFilters();
                        } else {
                            ExceptionHandler.showErrorDialog("Fehler beim Speichern",
                                    "Der Passagier konnte nicht gespeichert werden.");
                        }
                    }
                } catch (Exception e) {
                    logger.error("Fehler beim Verarbeiten des Dialogergebnisses: {}", e.getMessage());
                    ExceptionHandler.handleException(e, "beim Verarbeiten des Dialogergebnisses");
                }
            });
        } catch (Exception e) {
            logger.error("Fehler beim Anzeigen des Passagier-Dialogs: {}", e.getMessage());
            ExceptionHandler.handleException(e, "beim Anzeigen des Passagier-Bearbeitungsdialogs");
        }
    }

    /**
     * Zeigt den Dialog zum Hinzufügen eines neuen Passagiers mit FXML
     */
    @FXML
    private void handleNewPassenger() {
        try {
            // Dialog erstellen
            Dialog<Passenger> dialog = new Dialog<>();
            dialog.setTitle("Neuer Passagier");
            dialog.setHeaderText("Passagier-Details eingeben");

            // Dialog-Buttons definieren
            ButtonType saveButtonType = new ButtonType("Speichern", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

            // FXML-Dialog laden
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dialogs/PassengerCreateDialog.fxml"));
            Parent root = loader.load();
            dialog.getDialogPane().setContent(root);

            // Controller abrufen und initialisieren
            skynexus.controller.dialogs.PassengerDialogController controller = loader.getController();
            controller.initNewPassenger(dialog);

            // Result Converter einrichten
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == saveButtonType) {
                    return controller.getResult();
                }
                return null;
            });

            // Dialog anzeigen und Ergebnis verarbeiten
            Optional<Passenger> result = dialog.showAndWait();
            result.ifPresent(newPassenger -> {
                try {
                    boolean success = passengerService.savePassenger(newPassenger);
                    if (success) {
                        statusLabel.setText("Passagier " + newPassenger.getFullName() + " wurde erstellt");

                        // UI-Aktualisierung
                        passengers.add(newPassenger);
                        updateFilters();

                        // Neuen Passagier auswählen und anzeigen
                        passengerTable.getSelectionModel().select(newPassenger);
                        passengerTable.scrollTo(newPassenger);
                    } else {
                        logger.error("Fehler beim Speichern des Passagiers - savePassenger() gibt false zurück");
                        ExceptionHandler.showErrorDialog("Fehler beim Speichern",
                                "Der Passagier konnte nicht in der Datenbank gespeichert werden.");
                    }
                } catch (Exception e) {
                    logger.error("Fehler beim Speichern des neuen Passagiers: {}", e.getMessage(), e);
                    ExceptionHandler.handleException(e, "beim Speichern des neuen Passagiers");
                }
            });
        } catch (Exception e) {
            logger.error("Fehler beim Anzeigen des Passagier-Dialogs: {}", e.getMessage());
            ExceptionHandler.handleException(e, "beim Anzeigen des Passagier-Erstellungsdialogs");
        }
    }

    /**
     * Event-Handler: Daten aktualisieren
     */
    @FXML
    private void handleRefresh() {
        loadAllData();
        if (statusLabel != null) {
            statusLabel.setText("Daten aktualisiert");
        }
    }

    /**
     * Hilfsmethode zur Anzeige der Sitzklasse (ausführliche Bezeichnung)
     */
    private String getSeatClassDisplay(SeatClass seatClass) {
        if (seatClass == null) return "";
        return switch (seatClass) {
            case ECONOMY -> "Economy";
            case BUSINESS -> "Business";
            case FIRST_CLASS -> "First Class";
        };
    }

    /**
     * Hilfsmethode zur Anzeige der Sitzklasse (Kurzform für Anzeige in Klammern)
     */
    private String getSeatClassShortDisplay(SeatClass seatClass) {
        if (seatClass == null) return "";
        return switch (seatClass) {
            case ECONOMY -> "E";
            case BUSINESS -> "B";
            case FIRST_CLASS -> "FC";
        };
    }

    /**
     * Formatiert die Anzeige eines Fluges mit relevanten Informationen:
     * Format: "Frankfurt → New York (SKX120, E) - 20.04.2025, 16:20"
     *
     * @param flight    Der anzuzeigende Flug
     * @param seatClass Die Sitzklasse (optional, kann null sein)
     * @return Formatierte Anzeige des Fluges
     */
    private String formatFlightDisplay(Flight flight, SeatClass seatClass) {
        if (flight == null) {
            return "Kein Flug";
        }

        StringBuilder sb = new StringBuilder();

        // Route im neuen Format
        sb.append(flight.getRouteDisplayName());

        // Flugnummer und Sitzklasse
        sb.append(" (").append(flight.getFlightNumber());

        if (seatClass != null) {
            sb.append(", ").append(getSeatClassShortDisplay(seatClass));
        }

        sb.append(")");

        // Datum und Uhrzeit
        if (flight.getDepartureDate() != null) {
            sb.append(" - ").append(flight.getDepartureDate().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));

            if (flight.getDepartureTime() != null) {
                sb.append(", ").append(flight.getDepartureTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
            }
        }

        return sb.toString();
    }

// showError method seems redundant as ExceptionHandler.showErrorDialog is used elsewhere.
// Keeping it in case it's used by other parts of the application not shown.

    /**
     * Zeigt einen Fehlerdialog an
     */
    private void showError(String title, String header, String content) {
        logger.error("{}: {} - {}", title, header, content);
        ExceptionHandler.showErrorDialog(title, header + ": " + content);
    }

    /**
     * Validiert alle Pflichtfelder eines Passagier-Formulars und aktiviert/deaktiviert den Speichern-Button
     *
     * @param lastNameField     Nachname-Feld
     * @param firstNameField    Vorname-Feld
     * @param genderCombo       Geschlecht-Auswahlfeld
     * @param dateOfBirthPicker Geburtsdatum-Wähler
     * @param nationalityField  Nationalität-Feld
     * @param saveButton        Speichern-Button, der aktiviert/deaktiviert wird
     * @param errorLabel        Label zur Anzeige von Fehlermeldungen
     * @return true wenn alle Felder gültig sind, sonst false
     */
    private boolean validateFormFields(TextField lastNameField, TextField firstNameField,
                                       ComboBox<Gender> genderCombo, DatePicker dateOfBirthPicker,
                                       TextField nationalityField, Button saveButton, Label errorLabel) {
        // Prüfen ob alle Pflichtfelder ausgefüllt sind
        if (lastNameField.getText() == null || lastNameField.getText().trim().isEmpty()) {
            errorLabel.setText("Nachname darf nicht leer sein");
            errorLabel.setVisible(true);
            saveButton.setDisable(true);
            return false;
        }

        if (firstNameField.getText() == null || firstNameField.getText().trim().isEmpty()) {
            errorLabel.setText("Vorname darf nicht leer sein");
            errorLabel.setVisible(true);
            saveButton.setDisable(true);
            return false;
        }

        // Validierung der Namen auf Unicode-Zeichen (nur Buchstaben, Bindestriche, Apostrophe, Leerzeichen)
        String nameRegex = "^[\\p{L}\\p{M}\\p{Pd}\\p{Zs}'']{1,100}$";
        if (!lastNameField.getText().matches(nameRegex)) {
            errorLabel.setText("Nachname enthält ungültige Zeichen");
            errorLabel.setVisible(true);
            saveButton.setDisable(true);
            return false;
        }

        if (!firstNameField.getText().matches(nameRegex)) {
            errorLabel.setText("Vorname enthält ungültige Zeichen");
            errorLabel.setVisible(true);
            saveButton.setDisable(true);
            return false;
        }

        if (genderCombo.getValue() == null) {
            errorLabel.setText("Bitte wählen Sie ein Geschlecht");
            errorLabel.setVisible(true);
            saveButton.setDisable(true);
            return false;
        }

        if (dateOfBirthPicker.getValue() == null) {
            errorLabel.setText("Geburtsdatum darf nicht leer sein");
            errorLabel.setVisible(true);
            saveButton.setDisable(true);
            return false;
        }

        if (nationalityField.getText() == null || nationalityField.getText().trim().isEmpty()) {
            errorLabel.setText("Nationalität darf nicht leer sein");
            errorLabel.setVisible(true);
            saveButton.setDisable(true);
            return false;
        }

        // Alle Felder sind gültig
        errorLabel.setVisible(false);
        saveButton.setDisable(false);
        return true;
    }


}
