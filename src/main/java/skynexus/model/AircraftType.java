package skynexus.model;

import skynexus.util.ValidationUtils;

import java.util.Objects;

/**
 * Repräsentiert einen Flugzeugtyp mit seinen technischen Eigenschaften und Kosten.
 * Enthält Herstellerinformationen, Modellbezeichnung, technische Daten
 * wie Passagier- und Frachtkapazität, Reichweite, Geschwindigkeit
 * und die Betriebskosten pro Stunde.
 */
public class AircraftType {
    // Attribute
    private Long id;
    private Manufacturer manufacturer;
    private String model;
    private int paxCapacity;
    private double cargoCapacity;
    private double maxRangeKm;
    private double speedKmh;
    private double costPerHour;

    /**
     * Standard-Konstruktor für Frameworks/JDBC.
     */
    public AircraftType() {
    }

    /**
     * Konstruktor zum Erstellen eines neuen Flugzeugtyps mit allen Feldern.
     *
     * @param manufacturer  Flugzeughersteller (nicht null)
     * @param model         Modellbezeichnung (nicht leer)
     * @param paxCapacity   Passagierkapazität (positiv)
     * @param cargoCapacity Frachtkapazität in kg (nicht negativ)
     * @param maxRangeKm    Maximale Reichweite in km (positiv)
     * @param speedKmh      Reisegeschwindigkeit in km/h (positiv)
     * @param costPerHour   Betriebskosten pro Stunde (nicht negativ)
     * @throws IllegalArgumentException wenn Validierungsfehler auftreten
     */
    public AircraftType(Manufacturer manufacturer, String model, int paxCapacity, double cargoCapacity,
                        double maxRangeKm, double speedKmh, double costPerHour) {
        this.setManufacturer(manufacturer);
        this.setModel(model);
        this.setPaxCapacity(paxCapacity);
        this.setCargoCapacity(cargoCapacity);
        this.setMaxRangeKm(maxRangeKm);
        this.setSpeedKmh(speedKmh);
        this.setCostPerHour(costPerHour);
    }

    /**
     * Gibt die ID des Flugzeugtyps zurück.
     *
     * @return ID des Flugzeugtyps
     */
    public Long getId() {
        return id;
    }

    /**
     * Setzt die ID des Flugzeugtyps.
     *
     * @param id Die neue ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Gibt den Hersteller des Flugzeugtyps zurück.
     *
     * @return Hersteller des Flugzeugtyps
     */
    public Manufacturer getManufacturer() {
        return manufacturer;
    }

    /**
     * Setzt den Hersteller des Flugzeugtyps.
     *
     * @param manufacturer Der neue Hersteller
     * @throws IllegalArgumentException wenn der Hersteller null ist
     */
    public void setManufacturer(Manufacturer manufacturer) {
        ValidationUtils.validateNotNull(manufacturer, "Hersteller");
        this.manufacturer = manufacturer;
    }

    /**
     * Gibt die Modellbezeichnung des Flugzeugtyps zurück.
     *
     * @return Modellbezeichnung
     */
    public String getModel() {
        return model;
    }

    /**
     * Setzt die Modellbezeichnung des Flugzeugtyps.
     *
     * @param model Die neue Modellbezeichnung
     * @throws IllegalArgumentException wenn die Modellbezeichnung leer ist
     */
    public void setModel(String model) {
        ValidationUtils.validateNotEmpty(model, "Modellbezeichnung");
        this.model = model;
    }

    /**
     * Gibt die Passagierkapazität des Flugzeugtyps zurück.
     *
     * @return Passagierkapazität
     */
    public int getPaxCapacity() {
        return paxCapacity;
    }

    /**
     * Setzt die Passagierkapazität des Flugzeugtyps.
     *
     * @param paxCapacity Die neue Passagierkapazität
     * @throws IllegalArgumentException wenn die Kapazität nicht positiv ist
     */
    public void setPaxCapacity(int paxCapacity) {
        ValidationUtils.validatePositive(paxCapacity, "Passagierkapazität");
        this.paxCapacity = paxCapacity;
    }

    /**
     * Gibt die Frachtkapazität des Flugzeugtyps zurück.
     *
     * @return Frachtkapazität in kg
     */
    public double getCargoCapacity() {
        return cargoCapacity;
    }

    /**
     * Setzt die Frachtkapazität des Flugzeugtyps.
     *
     * @param cargoCapacity Die neue Frachtkapazität in kg
     * @throws IllegalArgumentException wenn die Kapazität negativ ist
     */
    public void setCargoCapacity(double cargoCapacity) {
        ValidationUtils.validateNotNegative(cargoCapacity, "Frachtkapazität");
        this.cargoCapacity = cargoCapacity;
    }

    /**
     * Gibt die maximale Reichweite des Flugzeugtyps zurück.
     *
     * @return Maximale Reichweite in km
     */
    public double getMaxRangeKm() {
        return maxRangeKm;
    }

    /**
     * Setzt die maximale Reichweite des Flugzeugtyps.
     *
     * @param maxRangeKm Die neue maximale Reichweite in km
     * @throws IllegalArgumentException wenn die Reichweite nicht positiv ist
     */
    public void setMaxRangeKm(double maxRangeKm) {
        ValidationUtils.validatePositive(maxRangeKm, "Reichweite");
        this.maxRangeKm = maxRangeKm;
    }

    /**
     * Gibt die Reisegeschwindigkeit des Flugzeugtyps zurück.
     *
     * @return Reisegeschwindigkeit in km/h
     */
    public double getSpeedKmh() {
        return speedKmh;
    }

    /**
     * Setzt die Reisegeschwindigkeit des Flugzeugtyps.
     *
     * @param speedKmh Die neue Reisegeschwindigkeit in km/h
     * @throws IllegalArgumentException wenn die Geschwindigkeit nicht positiv ist
     */
    public void setSpeedKmh(double speedKmh) {
        ValidationUtils.validatePositive(speedKmh, "Geschwindigkeit");
        this.speedKmh = speedKmh;
    }

    /**
     * Gibt die Betriebskosten pro Flugstunde zurück.
     *
     * @return Kosten pro Stunde in definierter Währung (z.B. Euro)
     */
    public double getCostPerHour() {
        return costPerHour;
    }

    /**
     * Setzt die Betriebskosten pro Flugstunde.
     *
     * @param costPerHour Kosten pro Stunde
     * @throws IllegalArgumentException wenn die Kosten negativ sind
     */
    public void setCostPerHour(double costPerHour) {
        ValidationUtils.validateNotNegative(costPerHour, "Kosten pro Stunde");
        this.costPerHour = costPerHour;
    }

    /**
     * Gibt eine vollständige Bezeichnung des Flugzeugtyps zurück.
     *
     * @return Formatierte Bezeichnung: "Hersteller Modell"
     */
    public String getFullName() {
        return (manufacturer != null ? manufacturer.getName() : "Unbekannt") + " " + model;
    }

    /**
     * Gibt eine String-Repräsentation des Flugzeugtyps zurück.
     *
     * @return Vollständiger Name des Flugzeugtyps
     */
    @Override
    public String toString() {
        return getFullName();
    }

    /**
     * Vergleicht dieses AircraftType-Objekt mit einem anderen Objekt.
     * Zwei AircraftType-Objekte gelten als gleich, wenn ihre IDs übereinstimmen (sofern vorhanden)
     * oder wenn Hersteller und Modell übereinstimmen.
     *
     * @param o Das zu vergleichende Objekt
     * @return true, wenn die Objekte gleich sind, sonst false
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AircraftType that = (AircraftType) o;

        // Primärer Vergleich über ID, falls vorhanden und ungleich null
        if (id != null && that.id != null) {
            return id.equals(that.id);
        }

        // Fallback-Vergleich über Hersteller und Modell (wenn IDs fehlen oder null sind)
        return Objects.equals(manufacturer, that.manufacturer) && Objects.equals(model, that.model);
    }

    /**
     * Gibt einen Hashcode für dieses AircraftType-Objekt zurück.
     * Basiert auf der ID (sofern vorhanden) oder auf Hersteller und Modell.
     *
     * @return Hashcode
     */
    @Override
    public int hashCode() {
        // Hashcode basiert primär auf ID, falls vorhanden
        if (id != null) {
            return Objects.hash(id);
        }

        // Fallback-Hashcode
        return Objects.hash(manufacturer, model);
    }
}
