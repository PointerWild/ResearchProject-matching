package el.structure;

public class Atom {
    public final String raw;

    public Atom(String raw) {
        if (!raw.matches("[A-Z]+") && !raw.matches("∃[a-z]+\\..+")) {
            throw new IllegalArgumentException("Invalid atom: " + raw);
        }
        this.raw = raw.trim();
    }

    @Override
    public String toString() {
        return raw;
    }
}

