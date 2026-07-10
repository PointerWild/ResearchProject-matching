package el;

import el.structure.ConceptPatternNode;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;

/**
 * DecAnalyze implements the Dec‐function from Figure 2.
 * Now dec(...) returns a DecResult containing:
 *  - success: whether the call succeeded (formerly non‐null)
 *  - subGoals: the set of newly generated subsumptions (possibly empty)
 */
public class DecAnalyze {

    private final ELAnalyze elAnalyze;

    public DecAnalyze(ELAnalyze elAnalyze) {
        this.elAnalyze = elAnalyze;
    }

    /**
     * Structured implementation of Dec(C ⊑? D).
     * @param c the left atom
     * @param d the right atom (not a variable)
     * @return a DecResult(success, subGoals) or null on failure
     */
    public DecResult dec(ConceptPatternNode c, ConceptPatternNode d) {
        // Case 1: if C is a variable, return {C ⊑? D}
        if (c.type == ConceptPatternNode.Type.VARIABLE) {
            return new DecResult(
                    true,
                    Collections.singleton(new SimpleEntry<>(c, d))
            );
        }

        // Case 3: if both are existential with different roles, fail
        if (c.type == ConceptPatternNode.Type.EXISTENTIAL
                && d.type == ConceptPatternNode.Type.EXISTENTIAL
                && !c.role.name.equals(d.role.name)) {
            return null;
        }

        // Case 2: both are existential with the same role
        if (c.type == ConceptPatternNode.Type.EXISTENTIAL
                && d.type == ConceptPatternNode.Type.EXISTENTIAL) {

            ConceptPatternNode fillC = c.existentialFiller;
            ConceptPatternNode fillD = d.existentialFiller;
            List<ConceptPatternNode> dis = splitTopLevelConjunction(fillD);

            // 2a) if C′ and any Di are both ground and C′ ⊑? Di fails, then fail
            if (isGround(fillC)) {
                for (ConceptPatternNode di : dis) {
                    if (isGround(di) && !elAnalyze.subsumes(fillC, di)) {
                        return null;
                    }
                }
            }
            // 2b) otherwise collect all subgoals C′ ⊑? Di where C′ or Di is non‐ground
            Set<SimpleEntry<ConceptPatternNode, ConceptPatternNode>> subs = new LinkedHashSet<>();
            for (ConceptPatternNode di : dis) {
                if (!isGround(fillC) || !isGround(di)) {
                    subs.add(new SimpleEntry<>(fillC, di));
                }
            }
            return new DecResult(true, subs);
        }

        // Case 4: C is a concept name and D is existential → fail
        if (c.type == ConceptPatternNode.Type.CONCEPT_NAME
                && d.type == ConceptPatternNode.Type.EXISTENTIAL) {
            return null;
        }
        // Case 5: C is existential and D is a concept name → fail
        if (c.type == ConceptPatternNode.Type.EXISTENTIAL
                && d.type == ConceptPatternNode.Type.CONCEPT_NAME) {
            return null;
        }

        // Case 6: both C and D are ground atoms
        if (isGround(c) && isGround(d)) {
            boolean ok = elAnalyze.subsumes(c, d);
            // always return non-null subGoals:
            return new DecResult(ok,
                    Collections.emptySet());
        }

        // any other structure is unsupported → fail
        return null;
    }

    // Encapsulates the result of a Dec call
    public static class DecResult {
        /** true if the call succeeded */
        public final boolean success;
        /**
         * The set of generated subgoals when success==true:
         *  - non‐empty for Case 1/2
         *  - empty for Case 6 success without new subgoals
         * When success==false or the call returned null, subGoals==null.
         */
        public final Set<SimpleEntry<ConceptPatternNode, ConceptPatternNode>> subGoals;

        public DecResult(boolean success,
                         Set<SimpleEntry<ConceptPatternNode, ConceptPatternNode>> subGoals) {
            this.success = success;

            //a set of <ConceptPatternNode,ConceptPatternNode>
            this.subGoals = subGoals;
        }
    }

    // —— Helper methods —— //

    /** Returns true iff the node contains no variable anywhere in its subtree */
    private boolean isGround(ConceptPatternNode n) {
        switch (n.type) {
            case VARIABLE:     return false;
            case TOP:
            case CONCEPT_NAME: return true;
            case EXISTENTIAL:  return isGround(n.existentialFiller);
            case CONJUNCTION:
                for (ConceptPatternNode ch : n.conjunctions) {
                    if (!isGround(ch)) return false;
                }
                return true;
        }
        return true;
    }

    /**
     * Splits a possible conjunction into its top‐level conjuncts.
     * If not a conjunction, returns a singleton list [n].
     */
    private List<ConceptPatternNode> splitTopLevelConjunction(ConceptPatternNode n) {
        if (n.type == ConceptPatternNode.Type.CONJUNCTION) {
            return n.conjunctions;
        }
        return Collections.singletonList(n);
    }
}
