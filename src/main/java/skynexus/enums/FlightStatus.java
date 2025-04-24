package skynexus.enums;

// Enum zur Darstellung des Flugstatus.
public enum FlightStatus {
    SCHEDULED,   // Geplant (bis 30 Minuten vor Abflug)
    BOARDING,    // Boarding (30 Minuten vor Abflug bis Abflug)
    DEPARTED,    // Abgeflogen (erste 10 Minuten nach Abflug)
    FLYING,   // In der Luft (Hauptteil des Fluges)
    LANDED,      // Gelandet (letzte 10 Minuten vor Ankunft)
    DEPLANING,   // Passagiere verlassen das Flugzeug (15 Minuten nach Landung)
    COMPLETED,        // Abgeschlossen (nach dem Deplaning)
    UNKNOWN        // Unbekannt (Nullwert oder nicht spezifiziert)
}
