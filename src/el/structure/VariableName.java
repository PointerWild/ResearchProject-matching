package el.structure;

import java.util.Objects;
import java.util.regex.Pattern;

public final class VariableName {

    /**
     * Variable token:
     *
     * _X_
     * _Xs_
     * _Xsasda1_
     */
    public static final String REGEX =
            "_[A-Z][A-Za-z0-9]*_";

    private static final Pattern PATTERN =
            Pattern.compile(REGEX);

    /**
     * Complete token including underscores.
     */
    public final String name;

    public VariableName(String name) {
        if (name == null
                || !PATTERN.matcher(name).matches()) {

            throw new IllegalArgumentException(
                    "Invalid variable name: "
                            + name
                            + ". Expected pattern: "
                            + REGEX
            );
        }

        this.name = name;
    }

    /**
     * _Xsasda1_ -> Xsasda1
     */
    public String getIdentifier() {
        return name.substring(
                1,
                name.length() - 1
        );
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