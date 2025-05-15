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


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Manufacturer getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(Manufacturer manufacturer) {
        ValidationUtils.validateNotNull(manufacturer, "Hersteller");
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        ValidationUtils.validateNotEmpty(model, "Modellbezeichnung");
        this.model = model;
    }

    public int getPaxCapacity() {
        return paxCapacity;
    }

    public void setPaxCapacity(int paxCapacity) {
        ValidationUtils.validatePositive(paxCapacity, "Passagierkapazität");
        this.paxCapacity = paxCapacity;
    }

    public double getCargoCapacity() {
        return cargoCapacity;
    }

    public void setCargoCapacity(double cargoCapacity) {
        ValidationUtils.validateNotNegative(cargoCapacity, "Frachtkapazität");
        this.cargoCapacity = cargoCapacity;
    }

    public double getMaxRangeKm() {
        return maxRangeKm;
    }

    public void setMaxRangeKm(double maxRangeKm) {
        ValidationUtils.validatePositive(maxRangeKm, "Reichweite");
        this.maxRangeKm = maxRangeKm;
    }

    public double getSpeedKmh() {
        return speedKmh;
    }

    public void setSpeedKmh(double speedKmh) {
        ValidationUtils.validatePositive(speedKmh, "Geschwindigkeit");
        this.speedKmh = speedKmh;
    }

    public double getCostPerHour() {
        return costPerHour;
    }

    public void setCostPerHour(double costPerHour) {
        ValidationUtils.validateNotNegative(costPerHour, "Kosten pro Stunde");
        this.costPerHour = costPerHour;
    }

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
