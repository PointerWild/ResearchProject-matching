package el.structure;

import java.util.List;

public class PatternDSL {

    public static ConceptPatternNode concept(String name) {
        return ConceptPatternNode.fromConcept(new ConceptName(name));
    }

    public static ConceptPatternNode variable(String name) {
        return ConceptPatternNode.fromVariable(new VariableName(name));
    }

    public static ConceptPatternNode some(String role, ConceptPatternNode filler) {
        return ConceptPatternNode.exists(new RoleName(role), filler);
    }

    public static ConceptPatternNode and(ConceptPatternNode... parts) {
        return ConceptPatternNode.conj(List.of(parts));
    }


    public static ConceptPatternNode and(List<ConceptPatternNode> parts) {
        return and(parts.toArray(new ConceptPatternNode[0]));
    }

    public static ConceptPatternNode top() {
        return ConceptPatternNode.Tau();
    }

    public static ConceptPattern pattern(ConceptPatternNode root) {
        return new ConceptPattern(root);
    }
}
