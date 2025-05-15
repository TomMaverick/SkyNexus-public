package skynexus.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.model.Airline;
import skynexus.model.Airport;
import skynexus.model.User;
import skynexus.service.AirlineService;
import skynexus.service.AirportService;
import skynexus.service.UserService;
import skynexus.util.SessionManager;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller für den Login- und Registrierungsbildschirm.
 */
public class LoginViewController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(LoginViewController.class);

    private final UserService userService;
    private final AirportService airportService;
    private final AirlineService airlineService;

    @FXML private TextField loginUsernameField;
    @FXML private PasswordField loginPasswordField;
    @FXML private Button loginButton;
    @FXML private Label loginErrorLabel;

    @FXML private TextField registerUsernameField;
    @FXML private PasswordField registerPasswordField;
    @FXML private PasswordField registerPasswordConfirmField;
    @FXML private Button registerButton;
    @FXML private Label registerErrorLabel;

    private AuthenticationListener authenticationListener;

    public LoginViewController() {
        this.userService = UserService.getInstance();
        this.airportService = AirportService.getInstance();
        this.airlineService = AirlineService.getInstance();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("LoginViewController initialisiert");
        hideMessages();
        setupUIComponents();
        Platform.runLater(this::focusFirstEmptyField);
    }

    /**
     * Richtet die UI-Komponenten ein
     */
    private void setupUIComponents() {
        loginUsernameField.setOnKeyPressed(this::handleEnterKey);
        loginPasswordField.setOnKeyPressed(this::handleEnterKey);
        registerUsernameField.setOnKeyPressed(this::handleEnterKey);
        registerPasswordField.setOnKeyPressed(this::handleEnterKey);
        registerPasswordConfirmField.setOnKeyPressed(this::handleEnterKey);

        loginButton.setGraphic(new FontIcon("mdi-login"));
    }

    public void setAuthenticationListener(AuthenticationListener listener) {
        this.authenticationListener = listener;
    }

    /**
     * Fokussiert das erste leere Feld
     */
    private void focusFirstEmptyField() {
        if (loginUsernameField.getText().isEmpty()) {
            loginUsernameField.requestFocus();
        } else {
            loginPasswordField.requestFocus();
        }
    }

    /**
     * Event-Handler für Enter-Taste in Eingabefeldern
     */
    private void handleEnterKey(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            if (loginUsernameField.isFocused() || loginPasswordField.isFocused()) {
                handleLogin();
            } else if (registerUsernameField.isFocused() || registerPasswordField.isFocused() ||
                    registerPasswordConfirmField.isFocused()) {
                handleRegister();
            }
        }
    }

    /**
     * Event-Handler für Login-Button
     */
    @FXML
    private void handleLogin() {
        String username = loginUsernameField.getText().trim();
        String password = loginPasswordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError(loginErrorLabel, "Bitte geben Sie Benutzername und Passwort ein.");
            return;
        }

        loginButton.setDisable(true);
        hideMessages();

        new Thread(() -> {
            try {
                var userOptional = userService.authenticateUser(username, password);

                Platform.runLater(() -> {
                    if (userOptional.isPresent()) {
                        handleSuccessfulLogin(userOptional.get());
                    } else {
                        showError(loginErrorLabel, "Ungültiger Benutzername oder Passwort.");
                        resetLoginUI();
                    }
                });
            } catch (Exception e) {
                logger.error("Fehler bei der Anmeldung", e);
                Platform.runLater(() -> {
                    showError(loginErrorLabel, "Fehler bei der Anmeldung: " + e.getMessage());
                    resetLoginUI();
                });
            }
        }).start();
    }

    /**
     * Event-Handler für Registrieren-Button
     */
    @FXML
    private void handleRegister() {
        String username = registerUsernameField.getText().trim();
        String password = registerPasswordField.getText();
        String passwordConfirm = registerPasswordConfirmField.getText();

        Airline defaultAirline = airlineService.getDefaultAirline();
        Airport defaultAirport = airportService.getDefaultAirport();

        if (!validateRegistrationInput(username, password, passwordConfirm)) {
            return;
        }

        registerButton.setDisable(true);
        hideMessages();

        new Thread(() -> {
            try {
                User newUser = userService.createUser(username, password);

                Platform.runLater(() -> {
                    showSuccess(registerErrorLabel, "Registrierung erfolgreich.");
                    clearRegistrationFields();
                    registerButton.setDisable(false);
                });

            } catch (IllegalArgumentException e) {
                logger.warn("Validierungsfehler: {}", e.getMessage());
                Platform.runLater(() -> {
                    showError(registerErrorLabel, e.getMessage());
                    registerButton.setDisable(false);
                });

            } catch (Exception e) {
                logger.error("Fehler bei der Registrierung", e);
                Platform.runLater(() -> {
                    showError(registerErrorLabel, "Fehler bei der Registrierung: " + e.getMessage());
                    registerButton.setDisable(false);
                });
            }
        }).start();
    }

    /**
     * Validiert die Eingaben für die Registrierung
     */
    private boolean validateRegistrationInput(String username, String password, String passwordConfirm) {
        if (username.isEmpty() || password.isEmpty() || passwordConfirm.isEmpty()) {
            showError(registerErrorLabel, "Bitte füllen Sie alle Felder aus.");
            return false;
        }

        if (!password.equals(passwordConfirm)) {
            showError(registerErrorLabel, "Die Passwörter stimmen nicht überein.");
            return false;
        }

        if (!userService.getSecurityService().isPasswordValid(password)) {
            showError(registerErrorLabel, "Das Passwort entspricht nicht den Richtlinien.");
            return false;
        }

        return true;
    }

    private void resetLoginUI() {
        loginButton.setDisable(false);
    }

    /**
     * Verarbeitet eine erfolgreiche Anmeldung
     */
    private void handleSuccessfulLogin(User user) {
        SessionManager.getInstance().setCurrentUser(user);

        if (authenticationListener != null) {
            authenticationListener.onLoginSuccessful(user);
        }

        logger.info("Benutzer '{}' hat sich erfolgreich angemeldet", user.getUsername());
    }

    /**
     * Blendet alle Meldungen aus
     */
    private void hideMessages() {
        loginErrorLabel.setVisible(false);
        registerErrorLabel.setVisible(false);
    }

    /**
     * Zeigt eine Fehlermeldung an
     */
    private void showError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.getStyleClass().setAll("error-label");
        createAutoHideTimeline(errorLabel).play();
    }

    /**
     * Zeigt eine Erfolgsmeldung an
     */
    private void showSuccess(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.getStyleClass().setAll("success-label");
        createAutoHideTimeline(label).play();
    }

    /**
     * Erstellt eine Timeline zum automatischen Ausblenden eines Labels
     */
    private Timeline createAutoHideTimeline(Label label) {
        return new Timeline(new KeyFrame(Duration.seconds(5), e -> label.setVisible(false)));
    }

    /**
     * Löscht die Eingabefelder für die Registrierung
     */
    private void clearRegistrationFields() {
        registerUsernameField.clear();
        registerPasswordField.clear();
        registerPasswordConfirmField.clear();
    }
}
