package skynexus.model;

import skynexus.util.ValidationUtils;

/**
 * Repräsentiert einen Flugzeughersteller in der Datenbank.
 */
public class Manufacturer {
    private Long id;
    private String name;

    /**
     * Standard-Konstruktor für Frameworks/JDBC.
     */
    public Manufacturer() {
    }

    /**
     * Konstruktor zum Erstellen eines neuen Herstellers mit allen Pflichtfeldern.
     *
     * @param name Name des Herstellers (nicht leer).
     * @throws IllegalArgumentException wenn der Name leer ist
     */
    public Manufacturer(String name) {
        setName(name);
    }

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
     * @return Der Name des Herstellers
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
     * @param o Das zu vergleichende Objekt
     * @return true, wenn die Objekte gleich sind, sonst false
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Manufacturer that = (Manufacturer) o;

        if (id != null && that.id != null) {
            return id.equals(that.id);
        }
        return name.equals(that.name);
    }

    /**
     * Gibt einen Hashcode für dieses Manufacturer-Objekt zurück.
     * Basiert auf der ID (sofern vorhanden) oder auf dem Namen.
     *
     * @return Hashcode
     */
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : name.hashCode();
    }
}
