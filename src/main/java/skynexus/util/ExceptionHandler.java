package skynexus.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Zentralisierte Fehlerbehandlung für die SkyNexus-Anwendung.
 */
public final class ExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ExceptionHandler.class);

    private ExceptionHandler() {
        throw new AssertionError("Utility-Klasse sollte nicht instanziiert werden");
    }

    /**
     * Behandelt eine Exception und zeigt einen entsprechenden Dialog an.
     *
     * @param exception Die zu behandelnde Exception
     * @param context Kontext der Exception (z.B. "beim Laden der Flüge")
     * @return true wenn die Exception behandelt wurde, false wenn die Anwendung beendet werden sollte
     */
    public static boolean handleException(Throwable exception, String context) {
        if (exception instanceof SQLException) {
            return handleDatabaseException((SQLException) exception, context);
        } else if (exception instanceof IllegalArgumentException) {
            return handleValidationException(exception, context);
        } else if (exception instanceof SecurityException) {
            return handleSecurityException((SecurityException) exception, context);
        } else {
            return handleGenericException(exception, context);
        }
    }

    /**
     * Behandelt eine Datenbankausnahme.
     */
    private static boolean handleDatabaseException(SQLException exception, String context) {
        logger.error("Datenbankfehler {}: {}", context, exception.getMessage(), exception);

        String title = "Datenbankfehler";
        String message = "Es ist ein Datenbankfehler aufgetreten " + context + ".\n\nDetails: " + exception.getMessage();

        showErrorDialog(title, message, null, exception);
        return true;
    }

    /**
     * Behandelt eine Validierungsausnahme.
     */
    private static boolean handleValidationException(Throwable exception, String context) {
        logger.warn("Validierungsfehler {}: {}", context, exception.getMessage());

        showWarningDialog("Eingabefehler", exception.getMessage());
        return true;
    }

    /**
     * Behandelt eine Sicherheitsausnahme.
     */
    private static boolean handleSecurityException(SecurityException exception, String context) {
        logger.error("Sicherheitsfehler {}: {}", context, exception.getMessage(), exception);

        String title = "Sicherheitsfehler";
        String message = "Sie haben keine Berechtigung für diese Aktion " + context + ".\n\nDetails: " + exception.getMessage();

        showErrorDialog(title, message, null, exception);
        return true;
    }

    /**
     * Behandelt eine allgemeine Ausnahme.
     */
    private static boolean handleGenericException(Throwable exception, String context) {
        logger.error("Unerwarteter Fehler {}: {}", context, exception.getMessage(), exception);

        String title = "Fehler";
        String message = "Es ist ein unerwarteter Fehler aufgetreten " + context + ".\n\nDetails: " + exception.getMessage();

        return showErrorDialog(title, message, null, exception);
    }

    /**
     * Zeigt einen Fehlerdialog an.
     */
    public static boolean showErrorDialog(String title, String message) {
        return showErrorDialog(title, message, null, null);
    }

    /**
     * Zeigt einen erweiterten Fehlerdialog mit Details an.
     */
    public static boolean showErrorDialog(String title, String message, String headerText, Throwable exception) {
        if (!Platform.isFxApplicationThread()) {
            final boolean[] result = new boolean[1];
            Platform.runLater(() -> result[0] = showErrorDialogInternal(title, message, headerText, exception));
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return result[0];
        } else {
            return showErrorDialogInternal(title, message, headerText, exception);
        }
    }

    private static boolean showErrorDialogInternal(String title, String message, String headerText, Throwable exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(headerText);
        alert.setContentText(message);

        if (exception != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            exception.printStackTrace(pw);
            String exceptionText = sw.toString();

            Label label = new Label("Exception-Details:");

            TextArea textArea = new TextArea(exceptionText);
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);
            GridPane.setVgrow(textArea, Priority.ALWAYS);
            GridPane.setHgrow(textArea, Priority.ALWAYS);

            GridPane expContent = new GridPane();
            expContent.setMaxWidth(Double.MAX_VALUE);
            expContent.add(label, 0, 0);
            expContent.add(textArea, 0, 1);

            alert.getDialogPane().setExpandableContent(expContent);
        }

        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    /**
     * Zeigt einen Warnungsdialog an.
     */
    public static void showWarningDialog(String title, String message) {
        showWarningDialog(title, message, null);
    }

    /**
     * Zeigt einen erweiterten Warnungsdialog mit optionaler Detailnachricht an.
     */
    public static void showWarningDialog(String title, String message, String details) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> showWarningDialogInternal(title, message, details));
        } else {
            showWarningDialogInternal(title, message, details);
        }
    }

    private static void showWarningDialogInternal(String title, String message, String details) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        if (details != null && !details.isEmpty()) {
            Label detailsLabel = new Label("Details:");
            TextArea detailsArea = new TextArea(details);
            detailsArea.setEditable(false);
            detailsArea.setWrapText(true);
            detailsArea.setMaxWidth(Double.MAX_VALUE);
            detailsArea.setMaxHeight(Double.MAX_VALUE);
            GridPane.setVgrow(detailsArea, Priority.ALWAYS);
            GridPane.setHgrow(detailsArea, Priority.ALWAYS);

            GridPane detailsContent = new GridPane();
            detailsContent.setMaxWidth(Double.MAX_VALUE);
            detailsContent.add(detailsLabel, 0, 0);
            detailsContent.add(detailsArea, 0, 1);

            alert.getDialogPane().setExpandableContent(detailsContent);
        }

        alert.showAndWait();
    }

    /**
     * Zeigt einen Informationsdialog an.
     */
    public static void showInfoDialog(String title, String message) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> showInfoDialogInternal(title, message));
        } else {
            showInfoDialogInternal(title, message);
        }
    }

    private static void showInfoDialogInternal(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Zeigt einen Bestätigungsdialog an.
     */
    public static boolean showConfirmDialog(String title, String message) {
        if (!Platform.isFxApplicationThread()) {
            final boolean[] result = new boolean[1];
            final Object lock = new Object();
            Platform.runLater(() -> {
                result[0] = showConfirmDialogInternal(title, message);
                synchronized (lock) {
                    lock.notify();
                }
            });
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return result[0];
        } else {
            return showConfirmDialogInternal(title, message);
        }
    }

    private static boolean showConfirmDialogInternal(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        alert.getDialogPane().applyCss();
        alert.getDialogPane().layout();

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}
