package el.structure;

public class ConceptName {
    public final String name;

    public ConceptName(String name) {
        if (!name.matches("[A-Z]+")) {
            throw new IllegalArgumentException("Invalid concept name: " + name);
        }
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}

