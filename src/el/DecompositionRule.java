package el;

import el.structure.ConceptPatternNode;
import el.structure.SubsumptionPattern;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Implements the Decomposition rule:
 *
 * If s = C1 ⊓ ... ⊓ Cn ⊑? D is unsolved in Gamma,
 * choose some Ci and call Dec(Ci ⊑? D).
 *
 * A single atom is treated as the n = 1 case.
 */
public final class DecompositionRule {

    private DecompositionRule() {
    }


    /**
     * Applies the first successful Decomposition choice directly to Gamma.
     *
     * This method represents one concrete non-deterministic choice.
     * It must not be used as the only Decomposition path in the complete
     * deterministic DFS matcher, because later successful choices would
     * otherwise be omitted.
     *
     * GoalOrientedMatcher should use applyAll().
     */
    @Deprecated
    public static boolean apply(
            SubsumptionPattern sp,
            Gamma gamma,
            DecAnalyze dec
    ) {
        if (sp.solved) {
            return false;
        }

        /*
         * A conjunction produces multiple candidates.
         * A single atom produces one candidate.
         */
        List<ConceptPatternNode> conjuncts =
                getTopLevelConjuncts(sp.left);

        for (ConceptPatternNode ci : conjuncts) {
            DecAnalyze.DecResult result =
                    dec.dec(
                            ci,
                            sp.right
                    );

            if (!result.success) {
                continue;
            }

            addSubGoals(
                    gamma,
                    result.subGoals
            );

            sp.solved = true;

            return true;
        }

        return false;
    }

    /**
     * Enumerates all feasible decomposition branches.
     *
     * For every Ci for which Dec(Ci ⊑? D) succeeds, this method creates
     * a new Gamma branch.
     */
    public static List<Gamma> applyAll(
            SubsumptionPattern sp,
            Gamma gamma,
            DecAnalyze dec
    ) {

        List<Gamma> branches =
                new ArrayList<>();

        if (sp.solved) {
            return branches;
        }

        if (sp.left.type == ConceptPatternNode.Type.VARIABLE
                || sp.right.type == ConceptPatternNode.Type.VARIABLE) {

            throw new IllegalStateException(
                    "Decomposition must only run after eager rules are saturated: "
                            + sp
            );
        }

        int originalIndex =
                gamma.indexOfIdentity(sp);

        if (originalIndex < 0) {
            throw new IllegalArgumentException(
                    "The supplied subsumption pattern "
                            + "does not belong to Gamma."
            );
        }

        /*
         * Supports both:
         *
         * C1 ⊓ ... ⊓ Cn ⊑? D
         *
         * and the n = 1 case:
         *
         * C1 ⊑? D
         */
        List<ConceptPatternNode> conjuncts =
                getTopLevelConjuncts(sp.left);

        for (ConceptPatternNode ci : conjuncts) {
            DecAnalyze.DecResult result =
                    dec.dec(
                            ci,
                            sp.right
                    );

            if (!result.success) {
                continue;
            }

            Gamma copy =
                    gamma.copy();

            SubsumptionPattern copiedPattern =
                    copy.getAll().get(
                            originalIndex
                    );

            addSubGoals(
                    copy,
                    result.subGoals
            );

            copiedPattern.solved = true;

            branches.add(copy);
        }

        return branches;
    }

    /**
     * Returns the operands of a top-level conjunction.
     *
     * A non-conjunction is represented as a singleton list because it
     * corresponds to the n = 1 case.
     */
    private static List<ConceptPatternNode> getTopLevelConjuncts(ConceptPatternNode left) {
        List<ConceptPatternNode> result = new ArrayList<>();

        collectTopLevelConjuncts(left, result);

        return List.copyOf(result);
    }

    private static void collectTopLevelConjuncts(ConceptPatternNode node,
                                                 List<ConceptPatternNode> result) {
        if (node.type == ConceptPatternNode.Type.TOP) {
            /*
             * Tau is the empty conjunction.
             */
            return;
        }

        if (node.type == ConceptPatternNode.Type.CONJUNCTION) {
            if (node.conjunctions == null) {
                throw new IllegalStateException(
                        "CONJUNCTION node has no operands: " + node
                );
            }

            for (ConceptPatternNode child : node.conjunctions) {
                collectTopLevelConjuncts(child, result);
            }

            return;
        }

        /*
         * VARIABLE, CONCEPT_NAME and EXISTENTIAL are atoms.
         *
         * Do not enter an existential restriction's filler.
         */
        result.add(node);
    }


    /**
     * Adds all Dec-generated subgoals to Gamma.
     */
    private static void addSubGoals(
            Gamma gamma,
            Set<SimpleEntry<
                    ConceptPatternNode,
                    ConceptPatternNode>> subGoals
    ) {


        for (SimpleEntry<
                ConceptPatternNode,
                ConceptPatternNode> subGoal : subGoals) {

            gamma.add(
                    subGoal.getKey(),
                    subGoal.getValue()
            );
        }
    }
}