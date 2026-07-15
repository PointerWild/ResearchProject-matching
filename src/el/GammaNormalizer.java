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
 * Performs exactly the normalization assumed before Algorithm 5.1 in
 * Baader and Morawska's EL matching algorithm.
 */
public final class GammaNormalizer {

    private final ELAnalyze elAnalyze;

    public GammaNormalizer(ELAnalyze elAnalyze) {
        this.elAnalyze = Objects.requireNonNull(
                elAnalyze,
                "elAnalyze cannot be null"
        );
    }

    /**
     * Normalizes without mutating {@code originalGamma}.
     *
     * <ol>
     *     <li>Build the complete RHS-expanded {@code expandedGamma}.</li>
     *     <li>Classify all expanded constraints.</li>
     *     <li>Check every ground-ground constraint before creating the
     *         Algorithm 5.1 work Gamma.</li>
     *     <li>Keep only constraints with exactly one non-ground side.</li>
     * </ol>
     */
    public GammaNormalizationResult normalize(Gamma originalGamma) {
        Objects.requireNonNull(
                originalGamma,
                "originalGamma cannot be null"
        );

        // Phase 1: finish the complete syntactic expansion before any ELK query.
        Gamma expandedGamma = expandRightConjunctions(originalGamma);

        List<SubsumptionPattern> groundSubsumptions = new ArrayList<>();
        List<SubsumptionPattern> nonGroundSubsumptions = new ArrayList<>();

        // Phase 2a: classify the already-complete expanded Gamma.
        for (SubsumptionPattern expanded : expandedGamma.getAll()) {
            boolean leftGround = isGround(expanded.left);
            boolean rightGround = isGround(expanded.right);

            if (!leftGround && !rightGround) {
                throw new IllegalArgumentException(
                        "Invalid EL matching constraint: at least one side "
                                + "must be ground: "
                                + expanded
                );
            }

            if (leftGround && rightGround) {
                groundSubsumptions.add(expanded);
            } else {
                nonGroundSubsumptions.add(expanded);
            }
        }

        // Phase 2b: all ground checks have precedence over Algorithm 5.1.
        List<GammaNormalizationResult.GroundCheckResult> groundChecks =
                new ArrayList<>();

        for (SubsumptionPattern ground : groundSubsumptions) {
            boolean entailed = elAnalyze.subsumes(
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

        // Only after every ground constraint succeeds may the work Gamma exist.
        Gamma normalizedGamma = new Gamma();
        for (SubsumptionPattern nonGround : nonGroundSubsumptions) {
            normalizedGamma.add(
                    nonGround.left,
                    nonGround.right
            );
        }

        validateNormalizedGamma(normalizedGamma);

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

    private Gamma expandRightConjunctions(Gamma originalGamma) {
        Gamma expandedGamma = new Gamma();

        for (SubsumptionPattern original : originalGamma.getAll()) {
            ConceptPatternNode left = Objects.requireNonNull(
                    original.left,
                    "Gamma left side cannot be null"
            );
            ConceptPatternNode right = Objects.requireNonNull(
                    original.right,
                    "Gamma right side cannot be null"
            );

            List<ConceptPatternNode> rightAtoms = new ArrayList<>();
            collectTopLevelRightAtoms(right, rightAtoms);

            for (ConceptPatternNode rightAtom : rightAtoms) {
                expandedGamma.add(left, rightAtom);
            }
        }

        return expandedGamma;
    }

    /** Recursively flattens conjunction nodes only while they are top-level. */
    private void collectTopLevelRightAtoms(
            ConceptPatternNode node,
            List<ConceptPatternNode> output
    ) {
        Objects.requireNonNull(
                node,
                "right-hand expression cannot be null"
        );

        if (node.type != ConceptPatternNode.Type.CONJUNCTION) {
            output.add(node);
            return;
        }

        if (node.conjunctions == null) {
            throw new IllegalStateException(
                    "CONJUNCTION node has no operand list: " + node
            );
        }

        for (ConceptPatternNode child : node.conjunctions) {
            collectTopLevelRightAtoms(child, output);
        }
    }

    private void validateNormalizedGamma(Gamma normalizedGamma) {
        Set<Subsumption> seen = new HashSet<>();

        for (SubsumptionPattern pattern : normalizedGamma.getAll()) {
            boolean leftGround = isGround(pattern.left);
            boolean rightGround = isGround(pattern.right);

            if (!isAtom(pattern.right)) {
                throw new IllegalStateException(
                        "Normalized Gamma has a non-atomic right side: "
                                + pattern
                );
            }
            if (leftGround && rightGround) {
                throw new IllegalStateException(
                        "Normalized Gamma still contains a ground-ground "
                                + "constraint: "
                                + pattern
                );
            }
            if (!leftGround && !rightGround) {
                throw new IllegalStateException(
                        "Normalized Gamma contains two non-ground sides: "
                                + pattern
                );
            }
            if (pattern.solved) {
                throw new IllegalStateException(
                        "Normalized constraints must initially be unsolved: "
                                + pattern
                );
            }
            if (!seen.add(pattern)) {
                throw new IllegalStateException(
                        "Normalized Gamma contains a duplicate constraint: "
                                + pattern
                );
            }
        }
    }

    private boolean isAtom(ConceptPatternNode node) {
        return node.type == ConceptPatternNode.Type.CONCEPT_NAME
                || node.type == ConceptPatternNode.Type.VARIABLE
                || node.type == ConceptPatternNode.Type.EXISTENTIAL
                || node.type == ConceptPatternNode.Type.TOP;
    }

    private boolean isGround(ConceptPatternNode node) {
        Objects.requireNonNull(
                node,
                "concept expression cannot be null"
        );

        return switch (node.type) {
            case VARIABLE -> false;
            case TOP, CONCEPT_NAME -> true;
            case EXISTENTIAL -> isGround(node.existentialFiller);
            case CONJUNCTION -> {
                if (node.conjunctions == null) {
                    throw new IllegalStateException(
                            "CONJUNCTION node has no operand list: " + node
                    );
                }
                yield node.conjunctions.stream().allMatch(this::isGround);
            }
        };
    }

    private void debugPrint(
            Gamma originalGamma,
            Gamma expandedGamma,
            List<GammaNormalizationResult.GroundCheckResult> groundChecks,
            Gamma normalizedGamma,
            String failureReason
    ) {
        if (!elAnalyze.isDebug()) {
            return;
        }

        System.out.println("=== Gamma normalization ===");
        System.out.println("\nOriginal Gamma:");
        printGamma(originalGamma);
        System.out.println("\nExpanded Gamma:");
        printGamma(expandedGamma);
        System.out.println("\nGround checks:");
        for (GammaNormalizationResult.GroundCheckResult check : groundChecks) {
            System.out.printf(
                    "TBox |= %s ⊑ %s -> %b%n",
                    check.left(),
                    check.right(),
                    check.entailed()
            );
        }

        if (failureReason != null) {
            System.out.println("\nNO_MATCHER: " + failureReason);
            return;
        }

        System.out.println("\nNormalized Gamma:");
        printGamma(normalizedGamma);
        System.out.println("\nAlgorithm 5.1 starts.");
    }

    private void printGamma(Gamma gamma) {
        for (SubsumptionPattern pattern : gamma.getAll()) {
            System.out.println(pattern);
        }
    }
}
