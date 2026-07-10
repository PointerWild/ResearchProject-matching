package el;

import el.structure.ConceptPatternNode;
import el.structure.SubsumptionPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Normalizes an initial EL matching problem before Algorithm 5.1 starts.
 *
 * <p>For every constraint:
 *
 * <pre>
 *     C ⊑? D
 * </pre>
 *
 * this class performs the following operations:
 *
 * <ol>
 *     <li>verifies that at least one side is ground;</li>
 *     <li>splits a top-level conjunction on the right;</li>
 *     <li>checks ground-ground constraints using the semantic oracle;</li>
 *     <li>removes entailed ground-ground constraints;</li>
 *     <li>returns unmatchable when one ground-ground constraint is false;</li>
 *     <li>creates a fresh Gamma containing only normalized constraints.</li>
 * </ol>
 */
public final class GammaNormalizer {

    private final ELAnalyze elAnalyze;

    public GammaNormalizer(
            ELAnalyze elAnalyze
    ) {
        this.elAnalyze = Objects.requireNonNull(
                elAnalyze,
                "elAnalyze cannot be null"
        );
    }

    /**
     * Normalizes an initial Gamma without modifying the supplied Gamma.
     *
     * @param originalGamma original matching problem
     * @return normalization result
     * @throws IllegalArgumentException if a constraint has variables on both
     *                                  sides and therefore violates the
     *                                  definition of an EL matching problem
     */
    public GammaNormalizationResult normalize(
            Gamma originalGamma
    ) {
        Objects.requireNonNull(
                originalGamma,
                "originalGamma cannot be null"
        );

        /*
         * Build a completely new Gamma.
         *
         * Consequently:
         * - the original Gamma is not modified;
         * - all resulting constraints start as unsolved;
         * - repeated match() calls do not reuse old solved flags.
         */
        Gamma normalizedGamma =
                new Gamma();

        for (SubsumptionPattern original
                : originalGamma.getAll()) {

            ConceptPatternNode left =
                    Objects.requireNonNull(
                            original.left,
                            "Gamma left side cannot be null"
                    );

            ConceptPatternNode right =
                    Objects.requireNonNull(
                            original.right,
                            "Gamma right side cannot be null"
                    );

            boolean leftGround =
                    isGround(left);

            boolean originalRightGround =
                    isGround(right);

            /*
             * Definition of an EL matching problem:
             *
             * at least one of C and D must be ground.
             */
            if (!leftGround && !originalRightGround) {
                throw new IllegalArgumentException(
                        "Invalid EL matching constraint: at least one side "
                                + "must be ground, but both sides contain "
                                + "variables: "
                                + left
                                + " ⊑? "
                                + right
                );
            }

            /*
             * Split only the top-level conjunction of the right-hand side.
             *
             * Example:
             *
             * ∃r.B ⊑? ∃r._X_ ⊓ A ⊓ C
             *
             * becomes candidates:
             *
             * ∃r._X_
             * A
             * C
             *
             * A conjunction inside an existential restriction is not split:
             *
             * ∃r.(_X_ ⊓ A)
             *
             * remains one existential atom.
             */
            List<ConceptPatternNode> rightAtoms =
                    new ArrayList<>();

            collectTopLevelRightAtoms(
                    right,
                    rightAtoms
            );

            /*
             * An empty conjunction represents TOP.
             *
             * C ⊑? TOP is always true and can be removed.
             */
            if (rightAtoms.isEmpty()) {
                continue;
            }

            for (ConceptPatternNode rightAtom
                    : rightAtoms) {

                /*
                 * TOP on the right is always satisfied.
                 */
                if (rightAtom.type
                        == ConceptPatternNode.Type.TOP) {
                    continue;
                }

                if (rightAtom.type
                        == ConceptPatternNode.Type.CONJUNCTION) {
                    throw new IllegalStateException(
                            "Gamma normalization failed to split the "
                                    + "right-hand conjunction: "
                                    + rightAtom
                    );
                }

                boolean rightGround =
                        isGround(rightAtom);

                /*
                 * Safety check after splitting.
                 *
                 * A normalized matching constraint still needs at least
                 * one ground side.
                 */
                if (!leftGround && !rightGround) {
                    throw new IllegalArgumentException(
                            "Normalization generated an invalid matching "
                                    + "constraint with two non-ground sides: "
                                    + left
                                    + " ⊑? "
                                    + rightAtom
                    );
                }

                /*
                 * Both sides are ground.
                 *
                 * Ask the real semantic oracle:
                 *
                 * TBox |= left ⊑ rightAtom
                 */
                if (leftGround && rightGround) {
                    boolean entailed =
                            elAnalyze.subsumes(
                                    left,
                                    rightAtom
                            );

                    /*
                     * A false ground-ground constraint means that no
                     * substitution can solve the matching problem.
                     */
                    if (!entailed) {
                        return GammaNormalizationResult.unmatchable(
                                "TBox does not entail the ground constraint: "
                                        + left
                                        + " ⊑ "
                                        + rightAtom
                        );
                    }

                    /*
                     * The ground constraint is true, so it can be removed
                     * from Gamma.
                     */
                    continue;
                }

                /*
                 * Exactly one side is non-ground and the right side is now
                 * an atom. This is a normalized matching constraint.
                 *
                 * Gamma.add() also prevents duplicate constraints.
                 */
                normalizedGamma.add(
                        left,
                        rightAtom
                );
            }
        }

        return GammaNormalizationResult.matchable(
                normalizedGamma
        );
    }

    /**
     * Recursively flattens only top-level conjunction nodes.
     *
     * <p>This method does not descend into existential fillers.
     */
    private void collectTopLevelRightAtoms(
            ConceptPatternNode node,
            List<ConceptPatternNode> output
    ) {
        Objects.requireNonNull(
                node,
                "right-hand expression cannot be null"
        );

        if (node.type
                != ConceptPatternNode.Type.CONJUNCTION) {

            output.add(node);
            return;
        }

        if (node.conjunctions == null) {
            throw new IllegalStateException(
                    "CONJUNCTION node has no operand list."
            );
        }

        for (ConceptPatternNode child
                : node.conjunctions) {

            collectTopLevelRightAtoms(
                    child,
                    output
            );
        }
    }

    /**
     * Returns true iff the complete expression contains no matching variable.
     */
    private boolean isGround(
            ConceptPatternNode node
    ) {
        Objects.requireNonNull(
                node,
                "concept expression cannot be null"
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

            case CONJUNCTION -> {
                if (node.conjunctions == null) {
                    throw new IllegalStateException(
                            "CONJUNCTION node has no operand list."
                    );
                }

                yield node.conjunctions
                        .stream()
                        .allMatch(this::isGround);
            }
        };
    }
}