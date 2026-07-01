package el.structure;

import java.util.List;

public class ConceptDSL {

    public static ConceptDescription concept(String name) {
        return ConceptDescription.fromConcept(new ConceptName(name));
    }

    public static ConceptDescription some(String role, ConceptDescription filler) {
        return ConceptDescription.exists(new RoleName(role), filler);
    }

    public static ConceptDescription and(ConceptDescription... parts) {
        return ConceptDescription.conj(List.of(parts));
    }

    public static ConceptDescription top() {
        return ConceptDescription.Tau();
    }
}
