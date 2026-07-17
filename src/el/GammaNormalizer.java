package el;

import el.structure.ConceptPatternNode;
import el.structure.Subsumption;
import el.structure.SubsumptionPattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Performs the normalization required before Algorithm 5.1.
 *
 * Responsibilities:
 *
 * 1. Validate that the original Gamma is an EL matching problem.
 * 2. Split only the top-level conjunction on the right-hand side.
 * 3. Check directly occurring ground-ground constraints with ELK.
 * 4. Remove successful ground-ground constraints.
 * 5. Return only normalized constraints to Algorithm 5.1.
 *
 * This class must not decompose conjunctions inside existential fillers.
 * That responsibility belongs to Decomposition + Dec.
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
     * Normalizes the supplied matching problem without modifying it.
     *
     * @param originalGamma original matching problem
     * @return normalization result
     */
    public GammaNormalizationResult normalize(
            Gamma originalGamma
    ) {
        Objects.requireNonNull(
                originalGamma,
                "originalGamma cannot be null"
        );

        /*
         * Definition 3.1 must be checked on the original
         * matching problem before RHS conjunction splitting.
         */
        validateOriginalMatchingProblem(
                originalGamma
        );

        /*
         * Phase 1:
         *
         * Build the complete expanded Gamma before making
         * any semantic query.
         */
        Gamma expandedGamma =
                expandRightConjunctions(
                        originalGamma
                );

        List<SubsumptionPattern> groundSubsumptions =
                new ArrayList<>();

        List<SubsumptionPattern> retainedSubsumptions =
                new ArrayList<>();

        /*
         * Phase 2a:
         *
         * Classify the already-complete expanded Gamma.
         */
        for (SubsumptionPattern expanded
                : expandedGamma.getAll()) {

            boolean leftGround =
                    isGround(
                            expanded.left
                    );

            boolean rightGround =
                    isGround(
                            expanded.right
                    );

            /*
             * C ⊑ Tau is always true.
             *
             * It remains visible in expandedGamma, but it is
             * not retained in normalizedGamma and does not
             * require an ELK query.
             */
            if (expanded.right.type
                    == ConceptPatternNode.Type.TOP) {

                continue;
            }

            /*
             * Defensive check.
             *
             * This should already have been prevented by
             * validateOriginalMatchingProblem().
             */
            if (!leftGround && !rightGround) {
                throw new IllegalArgumentException(
                        "Invalid EL matching constraint: "
                                + "at least one side must be ground: "
                                + expanded
                );
            }

            if (leftGround && rightGround) {
                groundSubsumptions.add(
                        expanded
                );
            } else {
                /*
                 * Exactly one side is non-ground.
                 */
                retainedSubsumptions.add(
                        expanded
                );
            }
        }

        /*
         * Phase 2b:
         *
         * Check all directly occurring ground-ground constraints
         * before creating the Algorithm 5.1 working Gamma.
         */
        List<GammaNormalizationResult.GroundCheckResult>
                groundChecks =
                new ArrayList<>();

        for (SubsumptionPattern ground
                : groundSubsumptions) {

            boolean entailed =
                    elAnalyze.subsumes(
                            ground.left,
                            ground.right
                    );

            groundChecks.add(
                    new GammaNormalizationResult.GroundCheckResult(
                            ground.left,
                            ground.right,
                            entailed
                    )
            );

            /*
             * A false ground-ground constraint cannot be repaired
             * by a substitution.
             */
            if (!entailed) {
                String failureReason =
                        "TBox does not entail the ground constraint: "
                                + ground.left
                                + " ⊑ "
                                + ground.right;

                debugPrint(
                        originalGamma,
                        expandedGamma,
                        groundChecks,
                        null,
                        failureReason
                );

                return GammaNormalizationResult.unmatchable(
                        expandedGamma,
                        groundChecks,
                        ground,
                        failureReason
                );
            }
        }

        /*
         * Only after every global ground check succeeds may the
         * Algorithm 5.1 working Gamma be created.
         */
        Gamma normalizedGamma =
                new Gamma();

        for (SubsumptionPattern retained
                : retainedSubsumptions) {

            normalizedGamma.add(
                    retained.left,
                    retained.right
            );
        }

        validateNormalizedGamma(
                normalizedGamma
        );

        debugPrint(
                originalGamma,
                expandedGamma,
                groundChecks,
                normalizedGamma,
                null
        );

        return GammaNormalizationResult.matchable(
                expandedGamma,
                normalizedGamma,
                groundChecks
        );
    }

    /**
     * Validates Definition 3.1 on the original matching problem.
     *
     * Every original matching constraint must have at least
     * one ground side.
     */
    private void validateOriginalMatchingProblem(
            Gamma originalGamma
    ) {
        for (SubsumptionPattern pattern
                : originalGamma.getAll()) {

            ConceptPatternNode left =
                    Objects.requireNonNull(
                            pattern.left,
                            "Gamma left side cannot be null"
                    );

            ConceptPatternNode right =
                    Objects.requireNonNull(
                            pattern.right,
                            "Gamma right side cannot be null"
                    );

            boolean leftGround =
                    isGround(
                            left
                    );

            boolean rightGround =
                    isGround(
                            right
                    );

            if (!leftGround && !rightGround) {
                throw new IllegalArgumentException(
                        "Invalid EL matching constraint: "
                                + "at least one side must be ground: "
                                + pattern
                );
            }
        }
    }

    /**
     * Creates the complete RHS-expanded Gamma.
     *
     * Only the conjunction at the top level of the complete
     * right-hand side is split.
     */
    private Gamma expandRightConjunctions(
            Gamma originalGamma
    ) {
        Gamma expandedGamma =
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

            List<ConceptPatternNode> rightAtoms =
                    new ArrayList<>();

            collectTopLevelRightAtoms(
                    right,
                    rightAtoms
            );

            for (ConceptPatternNode rightAtom
                    : rightAtoms) {

                expandedGamma.add(
                        left,
                        rightAtom
                );
            }
        }

        return expandedGamma;
    }

    /**
     * Recursively flattens only top-level conjunction nodes.
     *
     * It deliberately stops when an existential node is reached.
     * Therefore, an existential filler is not decomposed here.
     */
    private void collectTopLevelRightAtoms(
            ConceptPatternNode node,
            List<ConceptPatternNode> output
    ) {
        Objects.requireNonNull(
                node,
                "right-hand expression cannot be null"
        );

        /*
         * Concept name, variable, existential and Tau are
         * treated as complete top-level atoms here.
         *
         * In particular, an existential node is added directly;
         * its filler is not visited.
         */
        if (node.type
                != ConceptPatternNode.Type.CONJUNCTION) {

            output.add(
                    node
            );

            return;
        }

        if (node.conjunctions == null) {
            throw new IllegalStateException(
                    "CONJUNCTION node has no operand list: "
                            + node
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
     * Verifies the invariants required before Algorithm 5.1 starts.
     */
    private void validateNormalizedGamma(
            Gamma normalizedGamma
    ) {
        Set<Subsumption> seen =
                new HashSet<>();

        for (SubsumptionPattern pattern
                : normalizedGamma.getAll()) {

            boolean leftGround =
                    isGround(
                            pattern.left
                    );

            boolean rightGround =
                    isGround(
                            pattern.right
                    );

            if (!isPatternAtom(
                    pattern.right
            )) {
                throw new IllegalStateException(
                        "Normalized Gamma has a non-atomic "
                                + "right side: "
                                + pattern
                );
            }

            /*
             * Tau is a trivially true right-hand side and should
             * have been removed during normalization.
             */
            if (pattern.right.type
                    == ConceptPatternNode.Type.TOP) {

                throw new IllegalStateException(
                        "Normalized Gamma contains a trivial "
                                + "Tau constraint: "
                                + pattern
                );
            }

            if (leftGround && rightGround) {
                throw new IllegalStateException(
                        "Normalized Gamma still contains a "
                                + "ground-ground constraint: "
                                + pattern
                );
            }

            if (!leftGround && !rightGround) {
                throw new IllegalStateException(
                        "Normalized Gamma contains two "
                                + "non-ground sides: "
                                + pattern
                );
            }

            if (pattern.solved) {
                throw new IllegalStateException(
                        "Normalized constraints must initially "
                                + "be unsolved: "
                                + pattern
                );
            }

            if (!seen.add(
                    pattern
            )) {
                throw new IllegalStateException(
                        "Normalized Gamma contains a duplicate "
                                + "constraint: "
                                + pattern
                );
            }
        }
    }

    /**
     * Pattern atoms accepted on the RHS of normalized Gamma.
     */
    private boolean isPatternAtom(
            ConceptPatternNode node
    ) {
        return node.type
                == ConceptPatternNode.Type.CONCEPT_NAME
                || node.type
                == ConceptPatternNode.Type.VARIABLE
                || node.type
                == ConceptPatternNode.Type.EXISTENTIAL;
    }

    /**
     * Returns true iff the complete expression contains no variable.
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
                            Objects.requireNonNull(
                                    node.existentialFiller,
                                    "Existential filler cannot be null"
                            )
                    );

            case CONJUNCTION -> {
                if (node.conjunctions == null) {
                    throw new IllegalStateException(
                            "CONJUNCTION node has no operand list: "
                                    + node
                    );
                }

                yield node.conjunctions
                        .stream()
                        .allMatch(
                                this::isGround
                        );
            }
        };
    }

    private void debugPrint(
            Gamma originalGamma,
            Gamma expandedGamma,
            List<GammaNormalizationResult.GroundCheckResult>
                    groundChecks,
            Gamma normalizedGamma,
            String failureReason
    ) {
        if (!elAnalyze.isDebug()) {
            return;
        }

        System.out.println(
                "=== Gamma normalization ==="
        );

        System.out.println(
                "\nOriginal Gamma:"
        );

        printGamma(
                originalGamma
        );

        System.out.println(
                "\nExpanded Gamma:"
        );

        printGamma(
                expandedGamma
        );

        System.out.println(
                "\nGlobal ground checks:"
        );

        if (groundChecks.isEmpty()) {
            System.out.println(
                    "none"
            );
        }

        for (GammaNormalizationResult.GroundCheckResult check
                : groundChecks) {

            System.out.printf(
                    "TBox |= %s ⊑ %s -> %b%n",
                    check.left(),
                    check.right(),
                    check.entailed()
            );
        }

        if (failureReason != null) {
            System.out.println(
                    "\nNO_MATCHER: "
                            + failureReason
            );

            return;
        }

        System.out.println(
                "\nNormalized Gamma:"
        );

        printGamma(
                normalizedGamma
        );

        System.out.println(
                "\nAlgorithm 5.1 starts."
        );
    }

    private void printGamma(
            Gamma gamma
    ) {
        for (SubsumptionPattern pattern
                : gamma.getAll()) {

            System.out.println(
                    pattern
            );
        }
    }
}