package el;

import el.structure.ConceptPatternNode;
import el.structure.SubsumptionPattern;

import java.util.List;
import java.util.Objects;

/**
 * Applies the two eager-solving rules from Figure 1.
 *
 * <p>The return value of {@link #applyEager(List)} has the following meaning:
 *
 * <ul>
 *     <li>{@code true}: one eager rule was applied successfully;</li>
 *     <li>{@code false}: no eager rule is currently applicable;</li>
 *     <li>{@link FailureException}: an eager rule was applicable,
 *         but its semantic subsumption condition failed.</li>
 * </ul>
 */
public final class EagerSolver {

    private final ELAnalyze elAnalyze;

    /**
     * Creates an eager-rule solver.
     *
     * @param elAnalyze analyzer providing semantic subsumption queries
     */
    public EagerSolver(ELAnalyze elAnalyze) {
        this.elAnalyze = Objects.requireNonNull(
                elAnalyze,
                "elAnalyze cannot be null"
        );
    }

    /**
     * Attempts to apply exactly one eager rule to Gamma.
     *
     * <p>The right-variable rule has priority over the left-variable rule.
     *
     * @param gamma all subsumption patterns in the current Gamma
     * @return {@code true} if one rule was applied and one pattern was marked
     *         solved; {@code false} if no eager rule is applicable
     * @throws FailureException if an applicable eager rule fails its
     *                          semantic subsumption condition
     */
    public boolean applyEager(
            List<SubsumptionPattern> gamma
    ) throws FailureException {

        Objects.requireNonNull(
                gamma,
                "gamma cannot be null"
        );

        SubsumptionPattern firstLeftVariable = null;

        for (SubsumptionPattern current : gamma) {
            if (current == null || current.solved) {
                continue;
            }

            /*
             * Eager rule 1:
             *
             *     C ⊑? X
             *
             * Right-variable rules are processed first.
             */
            if (current.right.type
                    == ConceptPatternNode.Type.VARIABLE) {

                applyRightVariableRule(
                        current,
                        gamma
                );

                return true;
            }

            /*
             * Eager rule 2:
             *
             *     X ⊑? D
             *
             * Remember the first one, but process it only after confirming
             * that no right-variable rule is available.
             */
            if (firstLeftVariable == null
                    && current.left.type
                    == ConceptPatternNode.Type.VARIABLE) {

                firstLeftVariable = current;
            }
        }

        if (firstLeftVariable != null) {
            applyLeftVariableRule(
                    firstLeftVariable,
                    gamma
            );

            return true;
        }

        /*
         * Critical:
         *
         * false means that no eager rule is applicable.
         * GoalOrientedMatcher must now leave the eager loop and continue
         * with decomposition or mutation.
         */
        return false;
    }

    /**
     * Applies the eager rule for:
     *
     * <pre>
     *     C ⊑? X
     * </pre>
     *
     * <p>For every constraint:
     *
     * <pre>
     *     X ⊑? D
     * </pre>
     *
     * the semantic condition must hold:
     *
     * <pre>
     *     TBox |= C ⊑ D
     * </pre>
     */
    private void applyRightVariableRule(
            SubsumptionPattern target,
            List<SubsumptionPattern> gamma
    ) throws FailureException {

        ConceptPatternNode left = target.left;

        for (SubsumptionPattern related : gamma) {
            if (related == null || related == target) {
                continue;
            }

            /*
             * Do not skip solved constraints.
             *
             * Solved constraints remain members of Gamma and still
             * participate in the eager-rule compatibility condition.
             */
            if (related.left.type
                    == ConceptPatternNode.Type.VARIABLE
                    && related.left.equals(target.right)) {

                ConceptPatternNode right = related.right;

                ensureGroundQuery(
                        left,
                        right,
                        "right-variable"
                );

                boolean entailed =
                        elAnalyze.subsumes(
                                left,
                                right
                        );

                if (!entailed) {
                    throw new FailureException(
                            "Eager right-variable rule failed: "
                                    + left
                                    + " ⊑T "
                                    + right
                                    + " is false."
                    );
                }
            }
        }

        target.solved = true;
    }

    /**
     * Applies the eager rule for:
     *
     * <pre>
     *     X ⊑? D
     * </pre>
     *
     * <p>For every constraint:
     *
     * <pre>
     *     C ⊑? X
     * </pre>
     *
     * the semantic condition must hold:
     *
     * <pre>
     *     TBox |= C ⊑ D
     * </pre>
     */
    private void applyLeftVariableRule(
            SubsumptionPattern target,
            List<SubsumptionPattern> gamma
    ) throws FailureException {

        ConceptPatternNode right = target.right;

        for (SubsumptionPattern related : gamma) {
            if (related == null || related == target) {
                continue;
            }

            /*
             * Do not skip solved constraints.
             */
            if (related.right.type
                    == ConceptPatternNode.Type.VARIABLE
                    && related.right.equals(target.left)) {

                ConceptPatternNode left = related.left;

                ensureGroundQuery(
                        left,
                        right,
                        "left-variable"
                );

                boolean entailed =
                        elAnalyze.subsumes(
                                left,
                                right
                        );

                if (!entailed) {
                    throw new FailureException(
                            "Eager left-variable rule failed: "
                                    + left
                                    + " ⊑T "
                                    + right
                                    + " is false."
                    );
                }
            }
        }

        target.solved = true;
    }

    /**
     * Ensures that a semantic query sent through ELAnalyze is ground.
     *
     * <p>If this check fails, the normalized-Gamma invariant has been
     * violated and a matching variable would otherwise be sent to ELK.
     */
    private void ensureGroundQuery(
            ConceptPatternNode left,
            ConceptPatternNode right,
            String ruleName
    ) {
        if (!isGround(left) || !isGround(right)) {
            throw new IllegalStateException(
                    "Normalized Gamma invariant violated in eager "
                            + ruleName
                            + " rule: semantic query must be ground, but got "
                            + left
                            + " ⊑ "
                            + right
            );
        }
    }

    /**
     * Returns {@code true} iff the expression contains no matching variable.
     */
    private boolean isGround(
            ConceptPatternNode node
    ) {
        Objects.requireNonNull(
                node,
                "Concept expression cannot be null"
        );

        return switch (node.type) {
            case VARIABLE ->
                    false;

            case TOP, CONCEPT_NAME ->
                    true;

            case EXISTENTIAL ->
                    isGround(
                            node.existentialFiller
                    );

            case CONJUNCTION ->
                    node.conjunctions != null
                            && node.conjunctions
                            .stream()
                            .allMatch(this::isGround);
        };
    }
}