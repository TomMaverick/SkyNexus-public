package skynexus.controller.admin;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
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
import skynexus.model.User;
import skynexus.service.SecurityService;
import skynexus.service.UserService;
import skynexus.util.TimeUtils;

import java.net.URL;
import java.sql.SQLException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * Controller für die Benutzerverwaltung im Administrationsbereich.
 * Ermöglicht das Anzeigen, Erstellen, Bearbeiten und Verwalten von Benutzern.
 */
public class AdminUsersViewController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(AdminUsersViewController.class);

    @FXML
    private TableView<User> userTable;
    @FXML
    private TableColumn<User, String> usernameColumn;

    @FXML
    private TableColumn<User, String> lastLoginColumn;
    @FXML
    private TableColumn<User, String> createdAtColumn;
    @FXML
    private TableColumn<User, Boolean> adminColumn;
    @FXML
    private TableColumn<User, Boolean> activeColumn;

    @FXML
    private TextField searchField;
    @FXML
    private Button addButton;
    @FXML
    private Label statusLabel;
    @FXML
    private Label totalCountLabel;

    private ObservableList<User> userList;
    private FilteredList<User> filteredUsers;
    private UserService userService;

    private SecurityService securityService;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        logger.debug("AdminUsersViewController wird initialisiert");

        userService = UserService.getInstance();

        securityService = userService.getSecurityService();

        // Tabellenspalten initialisieren
        setupTableColumns();

        // Doppelklick-Handler für Benutzerbearbeitung
        userTable.setRowFactory(tv -> {
            TableRow<User> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    showEditDialog(row.getItem());
                }
            });
            return row;
        });

        // Leere Listen initialisieren für UI
        userList = FXCollections.observableArrayList();
        filteredUsers = new FilteredList<>(userList, p -> true);
        userTable.setItems(filteredUsers);

        // Asynchrones Laden der Daten
        loadUsersAsync();

        // Hinzufügen-Button-Aktion
        addButton.setOnAction(e -> showAddDialog());
    }

    /**
     * Konfiguriert die Tabellenspalten mit entsprechenden CellValueFactories und CellFactories
     */
    private void setupTableColumns() {
        // Benutzername mit Admin-Badge und verbesserte Farbgebung
        usernameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getUsername()));
        usernameColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String username, boolean empty) {
                super.updateItem(username, empty);
                getStyleClass().removeAll("admin-user-cell"); // Erst Klasse entfernen

                if (empty || username == null) {
                    setText(null);
                } else {
                    TableRow<User> row = getTableRow();
                    if (row != null && row.getItem() != null && row.getItem().isAdmin()) {
                        setText(username + " 👑");
                        getStyleClass().add("admin-user-cell");
                    } else {
                        setText(username);
                    }
                }
            }
        });



        // Airline-Spalte wurde im Rahmen der Single-Airline-Migration entfernt

        // Letzter Login
        lastLoginColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedLastLogin()));

        // Erstellungsdatum
        createdAtColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedCreationDate()));

        // Admin-Status mit Checkbox
        adminColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().isAdmin()));
        adminColumn.setCellFactory(column -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();

            {
                checkBox.setDisable(true); // Nur anzeigen, nicht direkt bearbeiten
            }

            @Override
            protected void updateItem(Boolean isAdmin, boolean empty) {
                super.updateItem(isAdmin, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    checkBox.setSelected(isAdmin != null && isAdmin);
                    setGraphic(checkBox);
                }
            }
        });

        // Aktiv-Status mit neuer CSS-Klassen-Formatierung
        activeColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().isActive()));
        activeColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean active, boolean empty) {
                super.updateItem(active, empty);
                getStyleClass().removeAll("user-status-active", "user-status-inactive"); // Erst entfernen

                if (empty || active == null) {
                    setText(null);
                } else {
                    setText(active ? "Aktiv" : "Inaktiv");
                    getStyleClass().add(active ? "user-status-active" : "user-status-inactive");
                }
            }
        });
    }



    /**
     * Lädt die Benutzerdaten asynchron im Hintergrund, um die UI nicht zu blockieren
     */
    private void loadUsersAsync() {
        // Statusmeldung anzeigen und Tabelle deaktivieren während des Ladens
        totalCountLabel.setText("Lade Benutzerdaten...");
        userTable.setDisable(true);

        long startTime = System.currentTimeMillis();

        // Benutzer asynchron laden
        CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Starte asynchrones Laden der Benutzerdaten");
                return userService.getAllUsers();
            } catch (SQLException e) {
                logger.error("Fehler beim asynchronen Laden der Benutzer", e);
                throw new RuntimeException(e);
            }
        }).thenAccept(users -> {
            // Berechnungen im UI-Thread ausführen
            Platform.runLater(() -> {
                try {
                    long duration = System.currentTimeMillis() - startTime;

                    // Daten in die ObservableList laden
                    userList.clear();
                    userList.addAll(users);

                    // Filter aktualisieren
                    filteredUsers.setPredicate(p -> true);  // Reset filter
                    updateFilters();

                    // UI aktualisieren
                    userTable.setDisable(false);

                    // Statuszeile mit Zeitanzeige
                    statusLabel.setText("Benutzer erfolgreich geladen (" + TimeUtils.formatLoadingTime(duration) + ")");
                    totalCountLabel.setText("Benutzer gesamt: " + users.size());

                    logger.info("{} Benutzer geladen in {} ms", users.size(), duration);
                } catch (Exception e) {
                    logger.error("Fehler bei der UI-Aktualisierung nach Benutzerladen", e);
                    totalCountLabel.setText("Fehler beim Laden der Benutzer");
                    showErrorAlert("Fehler", "Fehler beim Laden der Benutzer: " + e.getMessage());
                }
            });
        }).exceptionally(e -> {
            // Fehlerbehandlung im UI-Thread
            Platform.runLater(() -> {
                userTable.setDisable(false);
                totalCountLabel.setText("Fehler beim Laden der Benutzer");
                showErrorAlert("Fehler", "Daten konnten nicht geladen werden: " +
                        (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
            });
            return null;
        });
    }

    /**
     * Aktualisiert die Filter basierend auf den aktuellen Filtereinstellungen
     */
    private void updateFilters() {
        if (filteredUsers == null) return;

        filteredUsers.setPredicate(user -> {
            // Wenn der Suchtext leer ist, zeige alle Benutzer
            if (searchField.getText() == null || searchField.getText().isEmpty()) {
                return true;
            }

            String lowerCaseFilter = searchField.getText().toLowerCase();

            // Filtern nach Benutzername
            return user.getUsername().toLowerCase().contains(lowerCaseFilter);
        });

        updateTotalCount();
    }

    /**
     * Aktualisiert den Zähler für die Gesamtanzahl der angezeigten Benutzer
     */
    private void updateTotalCount() {
        int count = filteredUsers.size();
        totalCountLabel.setText("Benutzer gesamt: " + count);
    }

    /**
     * Zeigt den Dialog zum Bearbeiten eines Benutzers an (aufgerufen durch Doppelklick)
     *
     * @param user Der zu bearbeitende Benutzer
     */
    private void showEditDialog(User user) {
        if (user == null) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Benutzer bearbeiten");
        dialog.setHeaderText("Benutzer: " + user.getUsername());

        ButtonType saveButtonType = new ButtonType("Speichern", ButtonBar.ButtonData.OK_DONE);
        ButtonType resetPasswordButtonType = new ButtonType("Passwort zurücksetzen", ButtonBar.ButtonData.LEFT);
        ButtonType deleteButtonType = new ButtonType("Benutzer löschen", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, resetPasswordButtonType, deleteButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        // Benutzername (nicht editierbar)
        TextField usernameField = new TextField(user.getUsername());
        usernameField.setDisable(true);

        CheckBox adminCheckBox = new CheckBox("Administrator-Rechte");
        CheckBox activeCheckBox = new CheckBox("Aktiv");

        // Werte setzen
        adminCheckBox.setSelected(user.isAdmin());
        activeCheckBox.setSelected(user.isActive());

        // Layout für Bearbeitung
        grid.add(new Label("Benutzername:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(adminCheckBox, 1, 1);
        grid.add(activeCheckBox, 1, 2);

        dialog.getDialogPane().setContent(grid);

        // Button Styling
        Button deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteButtonType);
        deleteButton.getStyleClass().add("btn-danger");

        Button resetPasswordButton = (Button) dialog.getDialogPane().lookupButton(resetPasswordButtonType);
        resetPasswordButton.getStyleClass().add("btn-warning");

        // Result Handler
        dialog.showAndWait().ifPresent(buttonType -> {
            if (buttonType == saveButtonType) {
                saveUserChanges(user, adminCheckBox.isSelected(), activeCheckBox.isSelected());
            } else if (buttonType == resetPasswordButtonType) {
                showResetPasswordDialog(user);
            } else if (buttonType == deleteButtonType) {
                showDeleteUserDialog(user);
            }
        });
    }

    /**
     * Zeigt den Dialog zum Hinzufügen eines neuen Benutzers an
     */
    private void showAddDialog() {
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Neuen Benutzer erstellen");
        dialog.setHeaderText(null);

        // Button-Typen festlegen
        ButtonType saveButtonType = new ButtonType("Speichern", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // Formular-Grid erstellen
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        // Formularfelder erstellen
        TextField usernameField = new TextField();
        usernameField.setPromptText("Benutzername");



        // ComboBox für Airline wurde im Rahmen der Single-Airline-Migration entfernt
        // Standardairline wird automatisch zugewiesen

        CheckBox adminCheckBox = new CheckBox("Administrator-Rechte");
        CheckBox activeCheckBox = new CheckBox("Aktiv");
        activeCheckBox.setSelected(true); // Standard: aktiv

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Passwort");

        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Passwort bestätigen");



        // Felder zum Grid hinzufügen
        grid.add(new Label("Benutzername:*"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Passwort:*"), 0, 1);
        grid.add(passwordField, 1, 1);
        grid.add(new Label("Passwort bestätigen:*"), 0, 2);
        grid.add(confirmPasswordField, 1, 2);
        grid.add(adminCheckBox, 1, 3);
        grid.add(activeCheckBox, 1, 4);

        dialog.getDialogPane().setContent(grid);

        // Eingabefelder validieren und Save-Button entsprechend aktivieren/deaktivieren
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(true);

        // Validierungslistener für die Felder
        usernameField.textProperty().addListener((observable, oldValue, newValue) -> {
            boolean valid = validateUserInputs(usernameField, passwordField, confirmPasswordField, false);
            saveButton.setDisable(!valid);
        });

        passwordField.textProperty().addListener((observable, oldValue, newValue) -> {
            boolean valid = validateUserInputs(usernameField, passwordField, confirmPasswordField, false);
            saveButton.setDisable(!valid);
        });

        confirmPasswordField.textProperty().addListener((observable, oldValue, newValue) -> {
            boolean valid = validateUserInputs(usernameField, passwordField, confirmPasswordField, false);
            saveButton.setDisable(!valid);
        });

        // Listener für airlineComboBox wurde im Rahmen der Single-Airline-Migration entfernt

        // Dialog anzeigen und verarbeiten
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    // Neuen Benutzer erstellen
                    return userService.createUser(
                            usernameField.getText(),
                            passwordField.getText()
                    );
                } catch (Exception e) {
                    logger.error("Fehler beim Speichern des Benutzers", e);
                    showErrorAlert("Fehler beim Speichern", e.getMessage());
                    return null;
                }
            }
            return null;
        });

        // Ergebnis verarbeiten
        Optional<User> result = dialog.showAndWait();
        result.ifPresent(resultUser -> {
            // Neuer Benutzer wurde hinzugefügt
            userList.add(resultUser);
            // Tabelle aktualisieren
            userTable.refresh();
            updateTotalCount();

            // Nach 200ms neu laden, um auch DB-Aktualisierungen zu erfassen
            Platform.runLater(() -> {
                try {
                    Thread.sleep(200);
                    loadUsersAsync();
                } catch (InterruptedException e) {
                    logger.warn("Verzögertes Neuladen wurde unterbrochen", e);
                }
            });
        });
    }

    /**
     * Validiert die Benutzereingaben für das Formular
     */
    private boolean validateUserInputs(TextField usernameField, PasswordField passwordField,
                                       PasswordField confirmPasswordField, boolean isEdit) {
        // Allgemeine Validierung
        if (usernameField.getText().trim().isEmpty()) {
            return false;
        }

        // Benutzername mindestens 3 Zeichen
        if (usernameField.getText().trim().length() < 3) {
            return false;
        }

        // Zusätzliche Validierung nur für neue Benutzer (Passwort)
        if (!isEdit) {
            // Passwort-Felder dürfen nicht leer sein
            if (passwordField.getText().isEmpty() || confirmPasswordField.getText().isEmpty()) {
                return false;
            }

            // Passwörter müssen übereinstimmen
            if (!passwordField.getText().equals(confirmPasswordField.getText())) {
                return false;
            }

            // Passwort-Richtlinien prüfen
            return securityService.isPasswordValid(passwordField.getText());
        }

        return true;
    }

    /**
     * Speichert die Änderungen am Benutzer
     */
    private void saveUserChanges(User user, boolean isAdmin, boolean isActive) {
        try {
            user.setAdmin(isAdmin);
            user.setActive(isActive);

            boolean adminSuccess = userService.setUserAdminStatus(user.getId(), isAdmin);
            boolean activeSuccess = userService.setUserActiveStatus(user.getId(), isActive);

            if (adminSuccess && activeSuccess) {
                showInfoAlert("Erfolg", "Benutzer wurde erfolgreich aktualisiert.");
                loadUsersAsync();
            } else {
                showErrorAlert("Fehler", "Einige Einstellungen konnten nicht gespeichert werden.");
            }
        } catch (Exception e) {
            logger.error("Fehler beim Speichern der Benutzereinstellungen", e);
            showErrorAlert("Fehler", "Benutzer konnte nicht aktualisiert werden: " + e.getMessage());
        }
    }

    /**
     * Zeigt Dialog zum Löschen des Benutzers mit speziellen Warnungen
     */
    private void showDeleteUserDialog(User user) {
        try {
            // Erst Validierung durchführen
            userService.validateUserDeletion(user.getId());

            // Spezielle Warnung für Admins
            String warningMessage = "Möchten Sie diesen Benutzer wirklich löschen?";
            if (user.isAdmin()) {
                warningMessage = "⚠️ WARNUNG: Dies ist ein Administrator!\n\n" +
                               "Das Löschen dieses Benutzers entfernt alle Admin-Rechte.\n" +
                               "Möchten Sie fortfahren?";
            }

            Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
            confirmDialog.setTitle("Benutzer löschen");
            confirmDialog.setHeaderText("Benutzer löschen: " + user.getUsername());
            confirmDialog.setContentText(warningMessage);

            confirmDialog.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    try {
                        boolean success = userService.deleteUser(user.getId());
                        if (success) {
                            showInfoAlert("Erfolg", "Benutzer wurde erfolgreich gelöscht.");
                            loadUsersAsync();
                        } else {
                            showErrorAlert("Fehler", "Benutzer konnte nicht gelöscht werden.");
                        }
                    } catch (Exception e) {
                        logger.error("Fehler beim Löschen des Benutzers", e);
                        showErrorAlert("Fehler", "Benutzer konnte nicht gelöscht werden: " + e.getMessage());
                    }
                }
            });
        } catch (IllegalStateException e) {
            // Validierungsfehler anzeigen
            showErrorAlert("Löschung nicht erlaubt", e.getMessage());
        } catch (Exception e) {
            logger.error("Fehler bei der Lösch-Validierung", e);
            showErrorAlert("Fehler", "Validierung fehlgeschlagen: " + e.getMessage());
        }
    }

    /**
     * Zeigt einen Dialog zum Zurücksetzen des Passworts eines Benutzers an
     */
    private void showResetPasswordDialog(User user) {
        if (user == null) return;

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Passwort zurücksetzen für " + user.getUsername());
        dialog.setHeaderText(null);

        // Button-Typen festlegen
        ButtonType resetButtonType = new ButtonType("Zurücksetzen", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(resetButtonType, ButtonType.CANCEL);

        // Formular-Grid erstellen
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        // Formularfelder für das neue Passwort
        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("Neues Passwort");

        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Passwort bestätigen");

        grid.add(new Label("Neues Passwort:*"), 0, 0);
        grid.add(newPasswordField, 1, 0);
        grid.add(new Label("Passwort bestätigen:*"), 0, 1);
        grid.add(confirmPasswordField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        // Eingabevalidierung
        Button resetButton = (Button) dialog.getDialogPane().lookupButton(resetButtonType);
        resetButton.setDisable(true);

        // Validierungslistener
        newPasswordField.textProperty().addListener((observable, oldValue, newValue) ->
                resetButton.setDisable(!validatePasswordReset(newPasswordField, confirmPasswordField)));

        confirmPasswordField.textProperty().addListener((observable, oldValue, newValue) ->
                resetButton.setDisable(!validatePasswordReset(newPasswordField, confirmPasswordField)));

        // Dialog anzeigen und verarbeiten
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == resetButtonType) {
                return newPasswordField.getText();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newPassword -> {
            try {
                // Hier setzen wir das neue Passwort direkt, ohne das aktuelle zu verlangen,
                // da dies ein Administrator-Reset ist.
                String salt = securityService.generateSalt();
                String passwordHash = securityService.hashPassword(newPassword, salt);

                boolean success;

                // In einer realen Anwendung würde hier ein spezieller Admin-Reset-Endpunkt verwendet werden.
                // Für dieses Beispiel simulieren wir das.
                try (java.sql.Connection conn = skynexus.database.DatabaseConnectionManager.getInstance().getConnection()) {
                    String sql = "UPDATE users SET password_hash = ?, salt = ? WHERE id = ?";
                    try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, passwordHash);
                        stmt.setString(2, salt);
                        stmt.setLong(3, user.getId());
                        int updated = stmt.executeUpdate();
                        success = updated > 0;
                    }
                }

                if (success) {
                    showInfoAlert("Passwort zurückgesetzt",
                            "Das Passwort für Benutzer " + user.getUsername() + " wurde erfolgreich zurückgesetzt.");

                    // Daten nach kurzer Verzögerung neu laden
                    Platform.runLater(() -> {
                        try {
                            Thread.sleep(200);
                            loadUsersAsync();
                        } catch (InterruptedException e) {
                            logger.warn("Verzögertes Neuladen wurde unterbrochen", e);
                        }
                    });
                } else {
                    showErrorAlert("Fehler beim Zurücksetzen",
                            "Das Passwort konnte nicht zurückgesetzt werden.");
                }
            } catch (Exception e) {
                logger.error("Fehler beim Zurücksetzen des Passworts", e);
                showErrorAlert("Fehler beim Zurücksetzen", e.getMessage());
            }
        });
    }

    /**
     * Validiert die Passwortfelder für das Zurücksetzen eines Passworts
     */
    private boolean validatePasswordReset(PasswordField newPasswordField, PasswordField confirmPasswordField) {
        // Felder dürfen nicht leer sein
        if (newPasswordField.getText().isEmpty() || confirmPasswordField.getText().isEmpty()) {
            return false;
        }

        // Passwörter müssen übereinstimmen
        if (!newPasswordField.getText().equals(confirmPasswordField.getText())) {
            return false;
        }

        // Passwort-Richtlinien prüfen
        return securityService.isPasswordValid(newPasswordField.getText());
    }



    /**
     * Zeigt einen Informationsdialog an
     */
    private void showInfoAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Zeigt einen Fehlerdialog an
     */
    private void showErrorAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
