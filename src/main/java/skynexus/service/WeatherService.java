package skynexus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.util.Config;
import skynexus.util.ValidationUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service-Klasse für den Abruf und die Verarbeitung von Wetterdaten für Flughäfen.
 * Verwendet die NOAA Aviation Weather API, um METAR- und andere Wetterdaten zu erhalten.
 */
public class WeatherService {
    private static final Logger logger = LoggerFactory.getLogger(WeatherService.class);
    // Konfigurationschlüssel
    private static final String WEATHER_API_URL_KEY = "weather.api.url";
    private static final String WEATHER_API_TIMEOUT_KEY = "weather.api.timeout";
    private static final String WEATHER_API_CACHE_MINUTES_KEY = "weather.api.cache_minutes";
    // Standard-API-URL, falls keine in der Konfiguration definiert ist
    private static final String DEFAULT_API_URL = "https://aviationweather.gov/cgi-bin/data/metar.php";
    // Standard-Timeout in Millisekunden
    private static final int DEFAULT_TIMEOUT = 5000;
    // Standard-Cache-Zeit in Minuten
    private static final int DEFAULT_CACHE_MINUTES = 15;
    // Reguläre Ausdrücke für die Extrahierung von Wetterdaten aus METAR
    private static final Pattern TEMP_PATTERN = Pattern.compile("(M?\\d{2})/");
    private static final Pattern VISIBILITY_PATTERN = Pattern.compile(" (\\d+)SM ");
    private static final Pattern PRESSURE_PATTERN_A = Pattern.compile("A(\\d{4})");
    private static final Pattern PRESSURE_PATTERN_Q = Pattern.compile("Q(\\d{4})");
    private static WeatherService instance;
    // Cache für METAR-Daten
    private final Map<String, String> metarCache = new HashMap<>();
    private final Map<String, Long> cacheTimestamps = new HashMap<>();

    /**
     * Standard-Konstruktor für den WeatherService
     */
    public WeatherService() {
    }

    /**
     * Gibt die Singleton-Instanz des WeatherService zurück
     *
     * @return Die einzige Instanz des WeatherService
     */
    public static synchronized WeatherService getInstance() {
        if (instance == null) {
            instance = new WeatherService();
        }
        return instance;
    }

    /**
     * Ruft den aktuellen METAR für einen Flughafen ab.
     * Implementiert Caching, um wiederholte API-Aufrufe zu vermeiden.
     *
     * @param icaoCode ICAO-Code des Flughafens
     * @return METAR-String oder Fehlermeldung als String
     */
    public String getMetar(String icaoCode) {
        logger.debug("Rufe METAR für ICAO-Code {} ab", icaoCode);

        try {
            validateIcaoCode(icaoCode);

            // Cache-Dauer aus Konfiguration laden
            int cacheMinutes = Config.getAppPropertyInt(WEATHER_API_CACHE_MINUTES_KEY, DEFAULT_CACHE_MINUTES);
            long cacheDurationMs = TimeUnit.MINUTES.toMillis(cacheMinutes);
            long now = System.currentTimeMillis();

            // Prüfen, ob der Cache gültig ist
            if (metarCache.containsKey(icaoCode) &&
                    now - cacheTimestamps.getOrDefault(icaoCode, 0L) < cacheDurationMs) {
                logger.debug("METAR für {} aus Cache abgerufen", icaoCode);
                return metarCache.get(icaoCode);
            }

            // Frische Daten abrufen
            String apiResponse = fetchMetarByIcao(icaoCode);

            if (apiResponse == null || apiResponse.trim().isEmpty()) {
                logger.warn("Leere METAR-Antwort für ICAO: {}", icaoCode);
                return "Keine Daten verfügbar";
            }

            // Extrahiere den tatsächlichen METAR aus der Antwort
            String metar = extractMetar(apiResponse);

            // Cache aktualisieren
            metarCache.put(icaoCode, metar);
            cacheTimestamps.put(icaoCode, now);

            logger.info("METAR für {} erfolgreich abgerufen: {}", icaoCode, metar);
            return metar;
        } catch (Exception e) {
            logger.error("Fehler beim Abrufen des METAR für {}: {}", icaoCode, e.getMessage());
            return "Fehler beim Abrufen der Wetterdaten";
        }
    }

    /**
     * Extrahiert die Temperatur aus einem METAR-String.
     * Verwendet den gecachten METAR, falls verfügbar.
     *
     * @param icaoCode ICAO-Code des Flughafens
     * @return Temperatur in Celsius oder 0 als Standardwert bei Fehler
     */
    public int getTemperature(String icaoCode) {
        try {
            String metar = getMetar(icaoCode); // Nutzt bereits den Cache
            return extractTemperature(metar);
        } catch (Exception e) {
            logger.error("Fehler beim Extrahieren der Temperatur: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Extrahiert die Sichtweite aus einem METAR-String.
     * Verwendet den gecachten METAR, falls verfügbar.
     *
     * @param icaoCode ICAO-Code des Flughafens
     * @return Sichtweite in Kilometern oder 10 als Standardwert bei Fehler
     */
    public int getVisibility(String icaoCode) {
        try {
            String metar = getMetar(icaoCode); // Nutzt bereits den Cache
            return extractVisibility(metar);
        } catch (Exception e) {
            logger.error("Fehler beim Extrahieren der Sichtweite: {}", e.getMessage());
            return 10;
        }
    }

    /**
     * Extrahiert den Luftdruck aus einem METAR-String.
     * Verwendet den gecachten METAR, falls verfügbar.
     *
     * @param icaoCode ICAO-Code des Flughafens
     * @return Luftdruck in hPa oder 1013 als Standardwert bei Fehler
     */
    public int getPressure(String icaoCode) {
        try {
            String metar = getMetar(icaoCode); // Nutzt bereits den Cache
            return extractPressure(metar);
        } catch (Exception e) {
            logger.error("Fehler beim Extrahieren des Luftdrucks: {}", e.getMessage());
            return 1013;
        }
    }

    //==========================================================================
    // Extraktionsmethoden
    //==========================================================================

    /**
     * Extrahiert die Temperatur aus einem METAR-String.
     *
     * @param metar METAR-String
     * @return Temperatur in Celsius oder 0 als Standardwert bei Fehler
     */
    private int extractTemperature(String metar) {
        Matcher matcher = TEMP_PATTERN.matcher(metar);

        if (matcher.find()) {
            String tempStr = matcher.group(1);
            if (tempStr.startsWith("M")) {
                // Negative Temperatur (M steht für Minus)
                return -Integer.parseInt(tempStr.substring(1));
            } else {
                return Integer.parseInt(tempStr);
            }
        }
        logger.debug("Keine Temperaturinformation in METAR gefunden: {}", metar);
        return 0;
    }

    /**
     * Extrahiert die Sichtweite aus einem METAR-String.
     *
     * @param metar METAR-String
     * @return Sichtweite in Kilometern oder 10 als Standardwert bei Fehler
     */
    private int extractVisibility(String metar) {
        Matcher matcher = VISIBILITY_PATTERN.matcher(metar);

        if (matcher.find()) {
            // Konvertiere Statute Miles zu Kilometern (gerundet)
            int visibilityMiles = Integer.parseInt(matcher.group(1));
            return (int) Math.round(visibilityMiles * 1.60934);
        }
        logger.debug("Keine Sichtweiteninformation in METAR gefunden: {}", metar);
        return 10; // Standardwert
    }

    /**
     * Extrahiert den Luftdruck aus einem METAR-String.
     *
     * @param metar METAR-String
     * @return Luftdruck in hPa oder 1013 als Standardwert bei Fehler
     */
    private int extractPressure(String metar) {
        Matcher matcherQ = PRESSURE_PATTERN_Q.matcher(metar);
        if (matcherQ.find()) {
            // "Q" gibt den Luftdruck direkt in hPa an.
            String pressureStr = matcherQ.group(1);
            return Integer.parseInt(pressureStr);
        }

        Matcher matcherA = PRESSURE_PATTERN_A.matcher(metar);
        if (matcherA.find()) {
            String pressureStr = matcherA.group(1);
            // Umrechnung: A2992 -> 29.92 inHg; dann * 33.86389 zu hPa
            double pressureInHg = Double.parseDouble(pressureStr) / 100.0;
            return (int) Math.round(pressureInHg * 33.86389);
        }

        logger.debug("Keine Luftdruckinformation in METAR gefunden: {}", metar);
        return 1013; // Standardwert
    }

    //==========================================================================
    // Hilfsmethoden
    //==========================================================================

    /**
     * Validiert einen ICAO-Code.
     *
     * @param icaoCode Der zu prüfende ICAO-Code
     * @throws IllegalArgumentException Wenn der ICAO-Code ungültig ist
     */
    private void validateIcaoCode(String icaoCode) {
        if (icaoCode == null || icaoCode.isEmpty()) {
            throw new IllegalArgumentException("ICAO-Code darf nicht leer sein");
        }
        try {
            ValidationUtils.validateAirportICAO(icaoCode);
        } catch (IllegalArgumentException e) {
            logger.warn("Ungültiger ICAO-Code: {}", icaoCode);
            throw e;
        }
    }

    /**
     * Holt METAR-Daten von der API basierend auf dem ICAO-Code.
     *
     * @param icaoCode ICAO-Code des Flughafens
     * @return Rohantwort der API oder null bei Fehler
     * @throws IOException Bei Netzwerk- oder I/O-Fehlern
     */
    private String fetchMetarByIcao(String icaoCode) throws IOException {
        // Erstelle API-URL mit ICAO-Parameter
        String apiUrl = Config.getAppProperty(WEATHER_API_URL_KEY, DEFAULT_API_URL);
        String encodedIcao = URLEncoder.encode(icaoCode, StandardCharsets.UTF_8);
        String fullUrl = apiUrl + "?ids=" + encodedIcao + "&format=raw";

        return fetchFromApi(fullUrl);
    }

    /**
     * Führt einen HTTP-Request zur angegebenen URL aus und gibt die Antwort zurück.
     *
     * @param urlString URL-String für die Anfrage
     * @return Antworttext oder null bei Fehler
     * @throws IOException Bei Netzwerk- oder I/O-Fehlern
     */
    private String fetchFromApi(String urlString) throws IOException {
        // Timeout aus Konfiguration holen oder Standardwert verwenden
        int timeout = Config.getAppPropertyInt(WEATHER_API_TIMEOUT_KEY, DEFAULT_TIMEOUT);

        logger.debug("Rufe API auf: {}", urlString);

        URI uri = URI.create(urlString);
        URL url = uri.toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);

        try {
            int responseCode = connection.getResponseCode();
            logger.debug("API-Antwortcode: {}", responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line).append("\n");
                    }

                    return response.toString();
                }
            } else {
                logger.warn("API-Antwort nicht OK: {}", responseCode);
                return null;
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Extrahiert den METAR-String aus der API-Antwort.
     *
     * @param apiResponse Vollständige API-Antwort
     * @return Extrahierter METAR-String oder Fehlermeldung
     */
    private String extractMetar(String apiResponse) {
        if (apiResponse == null || apiResponse.trim().isEmpty()) {
            return "Keine Daten verfügbar";
        }

        // Entferne leere Zeilen und potentielle HTML-Formatierungen
        String cleanedResponse = apiResponse.replaceAll("<[^>]*>", "").trim();

        // Der tatsächliche METAR sollte in der ersten nicht-leeren Zeile sein
        String[] lines = cleanedResponse.split("\n");
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                return line.trim();
            }
        }

        return "Keine METAR-Daten gefunden";
    }

}
