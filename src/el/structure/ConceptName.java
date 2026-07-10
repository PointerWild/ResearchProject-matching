package el.structure;

import java.util.Objects;

/**
 * Name of an EL concept.
 *
 * Examples:
 *     A
 *     B
 *     PERSON
 */
public final class ConceptName {

    public final String name;

    public ConceptName(String name) {
        if (name == null
                || !name.matches("[A-Z]+")) {
            throw new IllegalArgumentException(
                    "Invalid concept name: " + name
            );
        }

        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Two concept names are equal when their textual names are equal.
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof ConceptName other)) {
            return false;
        }

        return name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}