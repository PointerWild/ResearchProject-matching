package el.structure;

public class Atom {

    public final String raw;

    public Atom(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException(
                    "Atom cannot be null."
            );
        }

        String cleaned =
                raw.trim();

        /*
         * Ground atom can be:
         *
         * 1. Concept name:
         *    [A-Z][A-Za-z0-9]*
         *
         *    Examples:
         *    A
         *    A1
         *    Abc1
         *    Person123
         *
         * 2. Existential restriction:
         *    ∃r.A
         *    ∃role.Abc1
         */
        boolean isConceptName =
                cleaned.matches(
                        ConceptName.REGEX
                );

        boolean isExistential =
                cleaned.matches(
                        "∃[a-z][a-z0-9_-]*\\..+"
                );

        if (!isConceptName && !isExistential) {
            throw new IllegalArgumentException(
                    "Invalid atom: " + raw
            );
        }

        this.raw = cleaned;
    }

    @Override
    public String toString() {
        return raw;
    }
}

