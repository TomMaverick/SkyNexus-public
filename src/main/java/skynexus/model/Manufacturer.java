package skynexus.model;

import skynexus.util.ValidationUtils;

/**
 * Repräsentiert einen Flugzeughersteller in der Datenbank.
 * Ersetzt das frühere Manufacturer-Enum mit einer vollwertigen Entitätsklasse,
 * die direkt aus der Datenbank geladen werden kann.
 */
public class Manufacturer {
    private Long id;             // Primärschlüssel in der Datenbank
    private String name;         // Name des Herstellers (z.B. "Airbus", "Boeing")

    /**
     * Standard-Konstruktor für Frameworks/JDBC.
     */
    public Manufacturer() {
    }

    /**
     * Konstruktor zum Erstellen eines neuen Herstellers mit allen Pflichtfeldern.
     *
     * @param name Name des Herstellers (nicht leer).
     */
    public Manufacturer(String name) {
        ValidationUtils.validateNotEmpty(name, "Herstellername");
        this.name = name;
    }

    /**
     * Voller Konstruktor mit ID.
     *
     * @param id   Datenbank-ID des Herstellers.
     * @param name Name des Herstellers (nicht leer).
     */
    public Manufacturer(Long id, String name) {
        ValidationUtils.validateNotEmpty(name, "Herstellername");
        this.id = id;
        this.name = name;
    }

    // --- Getter und Setter ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        ValidationUtils.validateNotEmpty(name, "Herstellername");
        this.name = name;
    }

    /**
     * Gibt eine String-Repräsentation des Herstellers zurück.
     *
     * @return Der Name des Herstellers.
     */
    @Override
    public String toString() {
        return name;
    }

    /**
     * Vergleicht dieses Manufacturer-Objekt mit einem anderen Objekt.
     * Zwei Manufacturer-Objekte gelten als gleich, wenn ihre IDs übereinstimmen (sofern vorhanden)
     * oder wenn ihre Namen übereinstimmen.
     *
     * @param o Das zu vergleichende Objekt.
     * @return true, wenn die Objekte gleich sind, sonst false.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Manufacturer that = (Manufacturer) o;
        // Primärer Vergleich über ID, falls vorhanden und ungleich null
        if (id != null && that.id != null) {
            return id.equals(that.id);
        }
        // Fallback-Vergleich über Namen (wenn IDs fehlen oder null sind)
        return name.equals(that.name);
    }

    /**
     * Gibt einen Hashcode für dieses Manufacturer-Objekt zurück.
     * Basiert auf der ID (sofern vorhanden) oder auf dem Namen.
     *
     * @return Der Hashcode.
     */
    @Override
    public int hashCode() {
        // Hashcode basiert primär auf ID, falls vorhanden
        if (id != null) {
            return id.hashCode();
        }
        // Fallback-Hashcode
        return name.hashCode();
    }
}
