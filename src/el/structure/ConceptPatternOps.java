package el.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Common operations for ConceptPatternNode.
 */
public final class ConceptPatternOps {

    private ConceptPatternOps() {
    }

    /**
     * Returns all atoms in the top-level conjunction of node.
     *
     * Rules:
     *
     * 1. Tau produces an empty list.
     * 2. Nested top-level conjunctions are recursively flattened.
     * 3. VARIABLE, CONCEPT_NAME and EXISTENTIAL are returned as atoms.
     * 4. Existential fillers are not entered.
     */
    public static List<ConceptPatternNode> topLevelAtoms(ConceptPatternNode node) {
        Objects.requireNonNull(node, "node cannot be null");

        List<ConceptPatternNode> result = new ArrayList<>();

        collectTopLevelAtoms(node, result);

        return List.copyOf(result);
    }

    private static void collectTopLevelAtoms(ConceptPatternNode node,
                                             List<ConceptPatternNode> result) {
        if (node.type == ConceptPatternNode.Type.TOP) {
            return;
        }

        if (node.type == ConceptPatternNode.Type.CONJUNCTION) {
            if (node.conjunctions == null) {
                throw new IllegalStateException(
                        "CONJUNCTION node has no operands: " + node
                );
            }

            for (ConceptPatternNode child : node.conjunctions) {
                collectTopLevelAtoms(child, result);
            }

            return;
        }

        /*
         * VARIABLE, CONCEPT_NAME and EXISTENTIAL are atoms.
         * Do not enter existential fillers.
         */
        result.add(node);
    }
}