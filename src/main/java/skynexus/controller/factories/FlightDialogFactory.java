package skynexus.controller.factories;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Modality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.controller.dialogs.FlightDialogController;
import skynexus.model.Flight;
import skynexus.service.AircraftService;
import skynexus.service.AirportService;
import skynexus.service.FlightService;
import skynexus.service.RouteService;
import skynexus.util.ExceptionHandler;

import java.io.IOException;
import java.util.Optional;

/**
 * Factory-Klasse für die Erstellung von Flug-Dialogen.
 * Stellt Methoden bereit, um Flug-Dialoge mit den FXML-Komponenten zu erstellen und zu konfigurieren.
 */
public class FlightDialogFactory {
    private static final Logger logger = LoggerFactory.getLogger(FlightDialogFactory.class);

    /**
     * Erstellt einen Dialog zum Erstellen oder Bearbeiten eines Fluges.
     *
     * @param flight          Zu bearbeitender Flug (null für neuen Flug)
     * @param flightService   Service für Flugoperationen
     * @param routeService    Service für Routenoperationen
     * @param airportService  Service für Flughafenoperationen
     * @param aircraftService Service für Flugzeugoperationen
     * @param callback        Callback nach erfolgreichem Speichern
     * @return Dialog für die Flugplanung
     */
    public static Dialog<Flight> createFlightDialog(Flight flight,
                                                    FlightService flightService,
                                                    RouteService routeService,
                                                    AirportService airportService,
                                                    AircraftService aircraftService,
                                                    FlightDialogController.SaveCallback callback) {
        try {
            // FXML laden
            FXMLLoader loader = new FXMLLoader(FlightDialogFactory.class.getResource("/fxml/dialogs/FlightCreateDialog.fxml"));
            DialogPane dialogPane = loader.load();

            // Controller abrufen und Services initialisieren
            FlightDialogController controller = loader.getController();

            // Dialog erstellen
            Dialog<Flight> dialog = new Dialog<>();
            dialog.setDialogPane(dialogPane);
            dialog.setTitle(flight == null ? "Neuen Flug erstellen" : "Flug bearbeiten");
            dialog.setHeaderText(null);
            dialog.initModality(Modality.APPLICATION_MODAL);

            // Controller mit Services und Dialog initialisieren
            controller.initializeServices(flightService, routeService, airportService, aircraftService, flight, dialog, callback);

            return dialog;
        } catch (IOException e) {
            logger.error("Fehler beim Laden der FXML für FlightDialog", e);
            ExceptionHandler.handleException(e, "beim Erstellen des Flug-Dialogs");
            return null;
        }
    }

    /**
     * Zeigt den Dialog zum Erstellen oder Bearbeiten eines Fluges an.
     *
     * @param flight          Zu bearbeitender Flug (null für neuen Flug)
     * @param flightService   Service für Flugoperationen
     * @param routeService    Service für Routenoperationen
     * @param airportService  Service für Flughafenoperationen
     * @param aircraftService Service für Flugzeugoperationen
     * @param callback        Callback nach erfolgreichem Speichern
     * @return Optionales Flight-Objekt, falls gespeichert
     */
    public static Optional<Flight> showFlightDialog(Flight flight,
                                                    FlightService flightService,
                                                    RouteService routeService,
                                                    AirportService airportService,
                                                    AircraftService aircraftService,
                                                    FlightDialogController.SaveCallback callback) {
        Dialog<Flight> dialog = createFlightDialog(flight, flightService, routeService, airportService, aircraftService, callback);
        if (dialog == null) {
            return Optional.empty();
        }
        return dialog.showAndWait();
    }
}
