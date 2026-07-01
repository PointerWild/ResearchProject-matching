package el.structure;

public class VariableName {
    public final String name;

    public VariableName(String name) {
        if (!name.matches("_[A-Z]+_")) {
            throw new IllegalArgumentException("Invalid variable name: " + name);
        }
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
