package el;

import el.structure.ConceptPatternNode;

import java.util.*;
import java.util.AbstractMap.SimpleEntry;

/**
 * Implements the Dec function from Algorithm 5.1 / Figure 2.
 *
 * A valid call Dec(C ⊑? D) must satisfy:
 *
 * 1. C is a pattern atom:
 *    VARIABLE, CONCEPT_NAME or EXISTENTIAL.
 *
 * 2. D is a non-variable pattern atom:
 *    CONCEPT_NAME or EXISTENTIAL.
 *
 * Dec never returns null.
 */
public final class DecAnalyze {

    private final ELAnalyze elAnalyze;

    public DecAnalyze(ELAnalyze elAnalyze) {
        this.elAnalyze = Objects.requireNonNull(
                elAnalyze,
                "elAnalyze cannot be null"
        );
    }

    /**
     * Structured implementation of Dec(C ⊑? D).
     *
     * @param c the left pattern atom
     * @param d the right pattern atom; must not be a variable
     * @return a successful or failed DecResult; never null
     */
    public DecResult dec(ConceptPatternNode c, ConceptPatternNode d) {
        /*
         * Validate the paper-defined Dec input contract before
         * applying any of the six cases.
         */
        validateInput(c, d);

        /*
         * Case 1:
         *
         * If C is a variable:
         *
         * Dec(C ⊑? D) = {C ⊑? D}
         */
        if (c.type == ConceptPatternNode.Type.VARIABLE) {
            return DecResult.success(
                    Collections.singleton(
                            new SimpleEntry<>(c, d)
                    )
            );
        }

        /*
         * Cases 2 and 3:
         *
         * Both C and D are existential atoms.
         */
        if (c.type == ConceptPatternNode.Type.EXISTENTIAL
                && d.type == ConceptPatternNode.Type.EXISTENTIAL) {

            /*
             * Case 3:
             *
             * C = ∃r.C'
             * D = ∃s.D'
             * r != s
             */
            if (!Objects.equals(c.role.name, d.role.name)) {
                return DecResult.failure(
                        "Different existential roles: "
                                + c.role.name
                                + " and "
                                + d.role.name
                );
            }

            /*
             * Case 2:
             *
             * C = ∃r.C'
             * D = ∃r.(D1 ⊓ ... ⊓ Dn)
             */
            return decSameRoleExistentials(c, d);
        }

        /*
         * Case 4:
         *
         * C is a concept name and D is an existential atom.
         */
        if (c.type == ConceptPatternNode.Type.CONCEPT_NAME
                && d.type == ConceptPatternNode.Type.EXISTENTIAL) {

            return DecResult.failure(
                    "A concept-name atom cannot structurally subsume "
                            + "an existential atom: "
                            + c
                            + " ⊑? "
                            + d
            );
        }

        /*
         * Case 5:
         *
         * C is an existential atom and D is a concept name.
         */
        if (c.type == ConceptPatternNode.Type.EXISTENTIAL
                && d.type == ConceptPatternNode.Type.CONCEPT_NAME) {

            return DecResult.failure(
                    "An existential atom cannot structurally subsume "
                            + "a concept-name atom: "
                            + c
                            + " ⊑? "
                            + d
            );
        }

        /*
         * Case 6:
         *
         * After input validation and Cases 1-5, the remaining
         * valid combination is:
         *
         * concept name ⊑? concept name
         *
         * Both sides are ground, so semantic subsumption is checked
         * using ELK through ELAnalyze.
         */
        if (!isGround(c) || !isGround(d)) {
            throw new IllegalStateException(
                    "Dec reached Case 6 with non-ground arguments: "
                            + c
                            + " ⊑? "
                            + d
            );
        }

        if (!elAnalyze.subsumes(c, d)) {
            return DecResult.failure(
                    "TBox does not entail the ground subsumption: "
                            + c
                            + " ⊑T "
                            + d
            );
        }

        /*
         * Successful ground-ground Dec produces no new subgoal.
         */
        return DecResult.success(Collections.emptySet());
    }

    /**
     * Implements Dec Case 2:
     *
     * Dec(∃r.C' ⊑? ∃r.(D1 ⊓ ... ⊓ Dn))
     */
    private DecResult decSameRoleExistentials(ConceptPatternNode c, ConceptPatternNode d) {
        ConceptPatternNode fillC = Objects.requireNonNull(
                c.existentialFiller,
                "Left existential filler cannot be null"
        );

        ConceptPatternNode fillD = Objects.requireNonNull(
                d.existentialFiller,
                "Right existential filler cannot be null"
        );

        /*
         * Recursively flatten only the top-level conjunction of fillD.
         *
         * This method does not enter an existential restriction's filler.
         *
         * Example:
         *
         * A ⊓ (B ⊓ C)
         *
         * becomes:
         *
         * A, B, C
         *
         * But:
         *
         * A ⊓ ∃s.(B ⊓ C)
         *
         * becomes:
         *
         * A, ∃s.(B ⊓ C)
         */
        List<ConceptPatternNode> dis = splitTopLevelConjunction(fillD);

        Set<SimpleEntry<ConceptPatternNode, ConceptPatternNode>> subGoals =
                new LinkedHashSet<>();

        boolean fillCGround = isGround(fillC);

        for (ConceptPatternNode di : dis) {
            boolean diGround = isGround(di);

            /*
             * Both C' and Di are ground.
             *
             * Check C' ⊑T Di immediately using ELK.
             */
            if (fillCGround && diGround) {
                if (!elAnalyze.subsumes(fillC, di)) {
                    return DecResult.failure(
                            "TBox does not entail the filler subsumption: "
                                    + fillC
                                    + " ⊑T "
                                    + di
                    );
                }

                /*
                 * Successful ground-ground constraints are not added
                 * to Gamma.
                 */
                continue;
            }

            /*
             * Algorithm 5.1 requires every matching subsumption to
             * have at least one ground side.
             *
             * If both sides are non-ground, the normalized-Gamma
             * invariant has been violated.
             */
            if (!fillCGround && !diGround) {
                throw new IllegalStateException(
                        "Dec would generate a matching constraint with "
                                + "two non-ground sides: "
                                + fillC
                                + " ⊑? "
                                + di
                );
            }

            /*
             * At least one side is non-ground.
             *
             * Add C' ⊑? Di as a new subgoal.
             */
            subGoals.add(
                    new SimpleEntry<>(fillC, di)
            );
        }

        return DecResult.success(subGoals);
    }

    /**
     * Validates the complete arguments of Dec(C ⊑? D).
     */
    private void validateInput(ConceptPatternNode c, ConceptPatternNode d) {
        Objects.requireNonNull(c, "Dec left side cannot be null");
        Objects.requireNonNull(d, "Dec right side cannot be null");

        if (!isPatternAtom(c)) {
            throw new IllegalArgumentException(
                    "Dec left side must be a pattern atom, but got: "
                            + c.type
                            + " in "
                            + c
            );
        }

        if (!isPatternAtom(d)) {
            throw new IllegalArgumentException(
                    "Dec right side must be a pattern atom, but got: "
                            + d.type
                            + " in "
                            + d
            );
        }

        /*
         * The paper explicitly requires that the right side of Dec
         * is not a variable.
         */
        if (d.type == ConceptPatternNode.Type.VARIABLE) {
            throw new IllegalArgumentException(
                    "Dec right side must not be a variable: "
                            + d
            );
        }
    }

    /**
     * Returns true iff the node is a pattern atom accepted as a
     * complete Dec argument.
     *
     * TOP and CONJUNCTION are not atoms.
     */
    private boolean isPatternAtom(ConceptPatternNode node) {
        return node.type == ConceptPatternNode.Type.VARIABLE
                || node.type == ConceptPatternNode.Type.CONCEPT_NAME
                || node.type == ConceptPatternNode.Type.EXISTENTIAL;
    }

    /**
     * Splits a possible top-level conjunction recursively.
     *
     * A non-conjunction atom becomes a singleton list.
     *
     * Tau contributes no atom because Tau is the empty conjunction.
     *
     * Existential restrictions remain complete atoms. Their fillers
     * are not entered by this method.
     */
    private List<ConceptPatternNode> splitTopLevelConjunction(ConceptPatternNode node) {
        List<ConceptPatternNode> result = new ArrayList<>();

        collectTopLevelAtoms(node, result);

        return List.copyOf(result);
    }

    /**
     * Recursive helper for splitTopLevelConjunction().
     */
    private void collectTopLevelAtoms(
            ConceptPatternNode node,
            List<ConceptPatternNode> result
    ) {
        Objects.requireNonNull(
                node,
                "Concept expression cannot be null"
        );

        switch (node.type) {
            case TOP -> {
                /*
                 * Tau represents the empty conjunction.
                 * It contributes no atom.
                 */
            }

            case CONJUNCTION -> {
                if (node.conjunctions == null) {
                    throw new IllegalStateException(
                            "CONJUNCTION node has no operands: "
                                    + node
                    );
                }

                for (ConceptPatternNode child : node.conjunctions) {
                    collectTopLevelAtoms(child, result);
                }
            }

            case VARIABLE, CONCEPT_NAME, EXISTENTIAL ->
                    result.add(node);
        }
    }

    /**
     * Returns true iff the complete expression contains no variable.
     */
    private boolean isGround(ConceptPatternNode node) {
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
                    isGround(node.existentialFiller);

            case CONJUNCTION ->
                    node.conjunctions.stream().allMatch(this::isGround);
        };
    }

    /**
     * Result of a Dec call.
     *
     * DecResult itself is never null.
     * subGoals is never null.
     */
    public static final class DecResult {

        /** True iff the Dec call succeeded. */
        public final boolean success;

        /** Newly generated subgoals; always non-null. */
        public final Set<SimpleEntry<ConceptPatternNode, ConceptPatternNode>> subGoals;

        /** Failure explanation; null for successful calls. */
        public final String failureReason;

        private DecResult(
                boolean success,
                Set<SimpleEntry<ConceptPatternNode, ConceptPatternNode>> subGoals,
                String failureReason
        ) {
            this.success = success;

            this.subGoals = Collections.unmodifiableSet(
                    new LinkedHashSet<>(
                            Objects.requireNonNull(
                                    subGoals,
                                    "subGoals cannot be null"
                            )
                    )
            );

            this.failureReason = failureReason;
        }

        /**
         * Creates a successful Dec result.
         */
        public static DecResult success(
                Set<SimpleEntry<ConceptPatternNode, ConceptPatternNode>> subGoals
        ) {
            return new DecResult(
                    true,
                    subGoals,
                    null
            );
        }

        /**
         * Creates a failed Dec result.
         */
        public static DecResult failure(String reason) {
            return new DecResult(
                    false,
                    Collections.emptySet(),
                    Objects.requireNonNull(
                            reason,
                            "Failure reason cannot be null"
                    )
            );
        }
    }
}