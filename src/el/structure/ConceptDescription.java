package el.structure;

import java.util.*;

public class ConceptDescription {
    public enum Type { TOP, CONCEPT_NAME, CONJUNCTION, EXISTENTIAL }

    public final Type type;

    public ConceptName conceptName;
    public List<ConceptDescription> conjunctions;
    public RoleName role;
    public ConceptDescription existentialFiller;

    public static ConceptDescription Tau() {
        return new ConceptDescription(Type.TOP);
    }

    public static ConceptDescription fromConcept(ConceptName name) {
        ConceptDescription desc = new ConceptDescription(Type.CONCEPT_NAME);
        desc.conceptName = name;
        return desc;
    }

    public static ConceptDescription conj(List<ConceptDescription> parts) {
        ConceptDescription desc = new ConceptDescription(Type.CONJUNCTION);
        desc.conjunctions = parts;
        return desc;
    }

    public static ConceptDescription exists(RoleName role, ConceptDescription filler) {
        ConceptDescription desc = new ConceptDescription(Type.EXISTENTIAL);
        desc.role = role;
        desc.existentialFiller = filler;
        return desc;
    }

    private ConceptDescription(Type type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return switch (type) {
            case TOP -> "Tau";
            case CONCEPT_NAME -> conceptName.toString();
            case CONJUNCTION -> String.join(" ⊓ ", conjunctions.stream().map(Object::toString).toList());
            case EXISTENTIAL -> "∃" + role + "." + existentialFiller;
        };
    }
}
