package el.structure;

import java.util.Objects;
import java.util.regex.Pattern;

public final class ConceptName {

    /**
     * Must start with an uppercase letter.
     * Remaining characters may be uppercase letters,
     * lowercase letters, or digits.
     */
    public static final String REGEX =
            "[A-Z][A-Za-z0-9]*";

    private static final Pattern PATTERN =
            Pattern.compile(REGEX);

    public final String name;

    public ConceptName(String name) {
        if (name == null
                || !PATTERN.matcher(name).matches()) {

            throw new IllegalArgumentException(
                    "Invalid concept name: "
                            + name
                            + ". Expected pattern: "
                            + REGEX
            );
        }

        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

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