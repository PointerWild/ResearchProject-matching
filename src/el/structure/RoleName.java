package el.structure;

public class RoleName {
    public final String name;

    public RoleName(String name) {
        if (!isValidRoleName(name)) {
            throw new IllegalArgumentException("Invalid role name: " + name);
        }
        this.name = name;
    }

    private boolean isValidRoleName(String name) {
        // Accept: lowercase/underscore/hyphen/digit mix, must start with lowercase letter
        return name.matches("[a-z][a-z0-9_-]*");
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RoleName other)) return false;
        return name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
