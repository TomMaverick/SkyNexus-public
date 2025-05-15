package skynexus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.model.Airline;
import skynexus.model.Airport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service-Klasse für die Verwaltung der Airline aus den Systemeinstellungen.
 */
public class AirlineService {
    private static final Logger logger = LoggerFactory.getLogger(AirlineService.class);
    private static AirlineService instance;
    private final SystemSettingsService settingsService;

    private AirlineService() {
        this.settingsService = SystemSettingsService.getInstance();
    }

    public static synchronized AirlineService getInstance() {
        if (instance == null) {
            instance = new AirlineService();
        }
        return instance;
    }

    /**
     * Gibt die Standard-Airline zurück
     */
    public Airline getDefaultAirline() {
        return Airline.getInstance();
    }

    /**
     * Gibt eine Liste mit der aktuellen Airline zurück.
     */
    public List<Airline> getAllAirlines() {
        Airline standardAirline = getDefaultAirline();
        if (standardAirline != null) {
            return Collections.singletonList(standardAirline);
        }
        return new ArrayList<>();
    }

    /**
     * Gibt eine Liste mit der aktuellen Airline zurück, wenn sie am angegebenen Flughafen operiert.
     * @deprecated Mit der Single-Airline-Architektur werden alle Flughäfen automatisch der Standardairline zugeordnet
     */
    @Deprecated
    public List<Airline> getAirlinesForAirport(Airport airport) {
        return getAllAirlines();
    }

    /**
     * Diese Methode ist in der Single-Airline-Architektur nicht mehr zugelassen.
     * @deprecated Die Standard-Airline darf nicht gelöscht werden
     */
    @Deprecated
    public boolean deleteAirline(Airline airline) {
        logger.warn("Versuch, die Standard-Airline zu löschen, was nicht erlaubt ist");
        return false;
    }
}
