package skynexus.enums;

// Enum des Betriebsstatus eines Flugzeugs.
public enum AircraftStatus {
    AVAILABLE,     // Einsatzbereit und nicht für Flüge eingeplant
    SCHEDULED,     // Flugzeug ist für zukünftige Einsätze eingeplant
    FLYING,        // Befindet sich aktuell im Flug (umfasst alle aktiven Flugphasen)
    UNKNOWN        // Unbekannt (Nullwert oder nicht spezifiziert)
}
