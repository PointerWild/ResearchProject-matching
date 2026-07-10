package el.structure;

import java.util.Objects;

/**
 * Name of a matching variable.
 *
 * Examples:
 *     _X_
 *     _YY_
 */
public final class VariableName {

    public final String name;

    public VariableName(String name) {
        if (name == null
                || !name.matches("_[A-Z]+_")) {
            throw new IllegalArgumentException(
                    "Invalid variable name: " + name
            );
        }

        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Two variable names are equal when their textual names are equal.
     *
     * _X_ equals _X_
     * _X_ does not equal _Y_
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof VariableName other)) {
            return false;
        }

        return name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}