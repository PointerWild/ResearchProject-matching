package el;

import el.structure.ConceptPatternNode;
import el.structure.SubsumptionPattern;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Decomposition rule from Algorithm 5.1.
 *
 * The tests focus on:
 *
 * 1. n = 1 support;
 * 2. enumeration of every successful Ci;
 * 3. recursive flattening of nested top-level conjunctions;
 * 4. branch isolation;
 * 5. eager-rule precedence;
 * 6. preservation of the original Gamma.
 */
class DecompositionRuleTest {

    /**
     * n = 1:
     *
     * ∃r._X_ ⊑? ∃r.A
     *
     * Dec produces:
     *
     * _X_ ⊑? A
     */
    @Test
    void n1CreatesOneBranchAndOneSubGoal() {
        Gamma original = gamma(
                "∃r._X_",
                "∃r.A"
        );

        SubsumptionPattern originalTarget =
                original.getAll().get(0);

        List<Gamma> branches =
                DecompositionRule.applyAll(
                        originalTarget,
                        original,
                        decWithIdentityOracle()
                );

        assertEquals(
                1,
                branches.size()
        );

        /*
         * applyAll() must not modify the original Gamma.
         */
        assertEquals(
                1,
                original.getAll().size()
        );

        assertFalse(
                originalTarget.solved
        );

        Gamma branch = branches.get(0);

        /*
         * The branch contains:
         *
         * 1. the solved original target;
         * 2. the new unsolved subgoal _X_ ⊑? A.
         */
        assertEquals(
                2,
                branch.getAll().size()
        );

        assertTrue(
                branch.getAll().get(0).solved
        );

        SubsumptionPattern subGoal =
                findPattern(
                        branch,
                        "_X_",
                        "A"
                );

        assertFalse(
                subGoal.solved
        );
    }

    /**
     * Only one Ci can be decomposed successfully:
     *
     * A ⊓ ∃r._X_ ⊑? ∃r.B
     *
     * Dec(A, ∃r.B) fails by Case 4.
     * Dec(∃r._X_, ∃r.B) succeeds.
     */
    @Test
    void onlyOneSuccessfulCiCreatesOneBranch() {
        Gamma original = gamma(
                "A ⊓ ∃r._X_",
                "∃r.B"
        );

        SubsumptionPattern target =
                original.getAll().get(0);

        List<Gamma> branches =
                DecompositionRule.applyAll(
                        target,
                        original,
                        decWithIdentityOracle()
                );

        assertEquals(
                1,
                branches.size()
        );

        Gamma branch = branches.get(0);

        assertTrue(
                branch.getAll().get(0).solved
        );

        assertTrue(
                contains(
                        branch,
                        "_X_",
                        "B"
                )
        );

        assertFalse(
                target.solved
        );
    }

    /**
     * Both Ci choices succeed:
     *
     * _X_ ⊓ _Y_ ⊑? A
     *
     * The deterministic implementation must create two branches:
     *
     * Branch 1 adds _X_ ⊑? A.
     * Branch 2 adds _Y_ ⊑? A.
     */
    @Test
    void everySuccessfulCiCreatesAnIndependentBranch() {
        Gamma original = gamma(
                "_X_ ⊓ _Y_",
                "A"
        );

        SubsumptionPattern target =
                original.getAll().get(0);

        List<Gamma> branches =
                DecompositionRule.applyAll(
                        target,
                        original,
                        decWithIdentityOracle()
                );

        assertEquals(
                2,
                branches.size()
        );

        assertEquals(
                1,
                countBranchesContaining(
                        branches,
                        "_X_",
                        "A"
                )
        );

        assertEquals(
                1,
                countBranchesContaining(
                        branches,
                        "_Y_",
                        "A"
                )
        );

        for (Gamma branch : branches) {
            assertTrue(
                    branch.getAll().get(0).solved
            );

            assertEquals(
                    2,
                    branch.getAll().size()
            );
        }

        /*
         * The original target remains unsolved.
         */
        assertFalse(
                target.solved
        );

        assertEquals(
                1,
                original.getAll().size()
        );
    }

    /**
     * Nested conjunction:
     *
     * A ⊓ (B ⊓ _X_) ⊑? D
     *
     * The top-level atoms must be interpreted as:
     *
     * A, B, _X_
     *
     * The nested conjunction B ⊓ _X_ must not be passed as one
     * complete argument to Dec.
     */
    @Test
    void recursivelyFlattensNestedLeftConjunction() {
        Gamma original = gamma(
                "A ⊓ (B ⊓ _X_)",
                "D"
        );

        SubsumptionPattern target =
                original.getAll().get(0);

        /*
         * A ⊑T D and B ⊑T D are both false.
         * The variable choice _X_ still succeeds by Dec Case 1.
         */
        DecAnalyze dec =
                decWithOracle(
                        (left, right) -> false
                );

        List<Gamma> branches =
                DecompositionRule.applyAll(
                        target,
                        original,
                        dec
                );

        assertEquals(
                1,
                branches.size()
        );

        Gamma branch = branches.get(0);

        assertTrue(
                contains(
                        branch,
                        "_X_",
                        "D"
                )
        );

        assertFalse(
                contains(
                        branch,
                        "B ⊓ _X_",
                        "D"
                )
        );

        assertTrue(
                branch.getAll().get(0).solved
        );

        assertFalse(
                target.solved
        );
    }

    /**
     * A ground Ci may solve Decomposition without generating a subgoal.
     *
     * A ⊓ B ⊑? D
     *
     * The controlled oracle entails only:
     *
     * B ⊑T D
     */
    @Test
    void successfulGroundCiCreatesBranchWithoutNewSubGoals() {
        ConceptPatternNode b =
                node("B");

        ConceptPatternNode d =
                node("D");

        DecAnalyze dec =
                decWithOracle(
                        (left, right) ->
                                left.equals(b)
                                        && right.equals(d)
                );

        Gamma original = gamma(
                "A ⊓ B",
                "D"
        );

        SubsumptionPattern target =
                original.getAll().get(0);

        List<Gamma> branches =
                DecompositionRule.applyAll(
                        target,
                        original,
                        dec
                );

        assertEquals(
                1,
                branches.size()
        );

        Gamma branch = branches.get(0);

        /*
         * Case 6 succeeds but returns no subgoal.
         * Therefore the branch still contains only the original target.
         */
        assertEquals(
                1,
                branch.getAll().size()
        );

        assertTrue(
                branch.getAll().get(0).solved
        );

        assertFalse(
                target.solved
        );
    }

    /**
     * All Ci choices fail:
     *
     * A ⊓ B ⊑? ∃r.C
     *
     * Concept-name to existential Dec calls fail by Case 4.
     */
    @Test
    void returnsEmptyBranchListWhenEveryCiFails() {
        Gamma original = gamma(
                "A ⊓ B",
                "∃r.C"
        );

        SubsumptionPattern target =
                original.getAll().get(0);

        List<Gamma> branches =
                DecompositionRule.applyAll(
                        target,
                        original,
                        decWithIdentityOracle()
                );

        assertTrue(
                branches.isEmpty()
        );

        assertFalse(
                target.solved
        );

        assertEquals(
                1,
                original.getAll().size()
        );
    }

    /**
     * Branches must not share mutable SubsumptionPattern state.
     */
    @Test
    void generatedBranchesHaveIndependentPatternState() {
        Gamma original = gamma(
                "_X_ ⊓ _Y_",
                "A"
        );

        SubsumptionPattern originalTarget =
                original.getAll().get(0);

        List<Gamma> branches =
                DecompositionRule.applyAll(
                        originalTarget,
                        original,
                        decWithIdentityOracle()
                );

        assertEquals(
                2,
                branches.size()
        );

        Gamma xBranch =
                findBranch(
                        branches,
                        "_X_",
                        "A"
                );

        Gamma yBranch =
                findBranch(
                        branches,
                        "_Y_",
                        "A"
                );

        assertNotSame(
                xBranch,
                yBranch
        );

        assertNotSame(
                xBranch.getAll().get(0),
                yBranch.getAll().get(0)
        );

        /*
         * Both copied targets are initially solved.
         */
        assertTrue(
                xBranch.getAll().get(0).solved
        );

        assertTrue(
                yBranch.getAll().get(0).solved
        );

        /*
         * Changing one branch's solved flag must not affect the other
         * branch or the original Gamma.
         */
        xBranch.getAll().get(0).solved = false;

        assertFalse(
                xBranch.getAll().get(0).solved
        );

        assertTrue(
                yBranch.getAll().get(0).solved
        );

        assertFalse(
                originalTarget.solved
        );

        /*
         * Adding a new constraint to one branch must not affect the
         * other branch.
         */
        xBranch.add(
                node("_Z_"),
                node("B")
        );

        assertTrue(
                contains(
                        xBranch,
                        "_Z_",
                        "B"
                )
        );

        assertFalse(
                contains(
                        yBranch,
                        "_Z_",
                        "B"
                )
        );

        assertFalse(
                contains(
                        original,
                        "_Z_",
                        "B"
                )
        );
    }

    /**
     * Complete variable sides must be processed by eager rules before
     * Decomposition is considered.
     */
    @Test
    void rejectsTargetWhenCompleteSideIsVariable() {
        DecAnalyze dec =
                decWithIdentityOracle();

        Gamma leftVariableGamma =
                gamma(
                        "_X_",
                        "A"
                );

        SubsumptionPattern leftVariableTarget =
                leftVariableGamma.getAll().get(0);

        assertThrows(
                IllegalStateException.class,
                () -> DecompositionRule.applyAll(
                        leftVariableTarget,
                        leftVariableGamma,
                        dec
                )
        );

        Gamma rightVariableGamma =
                gamma(
                        "A",
                        "_X_"
                );

        SubsumptionPattern rightVariableTarget =
                rightVariableGamma.getAll().get(0);

        assertThrows(
                IllegalStateException.class,
                () -> DecompositionRule.applyAll(
                        rightVariableTarget,
                        rightVariableGamma,
                        dec
                )
        );
    }

    /**
     * applyAll() must only operate on the exact target object contained
     * in the supplied Gamma.
     */
    @Test
    void rejectsStructurallyEqualButForeignTarget() {
        Gamma gamma =
                gamma(
                        "A",
                        "B"
                );

        SubsumptionPattern foreignTarget =
                new SubsumptionPattern(
                        node("A"),
                        node("B")
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> DecompositionRule.applyAll(
                        foreignTarget,
                        gamma,
                        decWithIdentityOracle()
                )
        );

        assertFalse(
                gamma.getAll().get(0).solved
        );
    }

    /**
     * Compatibility test for apply().
     *
     * apply() executes one concrete Decomposition choice directly on
     * the supplied Gamma.
     */
    @SuppressWarnings("deprecation")
    @Test
    void applyMutatesGammaUsingFirstSuccessfulChoice() {
        Gamma gamma =
                gamma(
                        "A ⊓ _X_",
                        "D"
                );

        SubsumptionPattern target =
                gamma.getAll().get(0);

        /*
         * A ⊑T D is false.
         * _X_ ⊑? D succeeds by Dec Case 1.
         */
        boolean applied =
                DecompositionRule.apply(
                        target,
                        gamma,
                        decWithOracle(
                                (left, right) -> false
                        )
                );

        assertTrue(
                applied
        );

        assertTrue(
                target.solved
        );

        assertTrue(
                contains(
                        gamma,
                        "_X_",
                        "D"
                )
        );

        assertEquals(
                2,
                gamma.getAll().size()
        );
    }

    // ---------------------------------------------------------------------
    // Test helpers
    // ---------------------------------------------------------------------

    private static DecAnalyze decWithIdentityOracle() {
        return decWithOracle(
                ConceptPatternNode::equals
        );
    }

    private static DecAnalyze decWithOracle(SubsumptionOracle oracle) {
        ELAnalyze analyze =
                new ELAnalyze(
                        oracle
                );

        return new DecAnalyze(
                analyze
        );
    }

    private static Gamma gamma(String left, String right) {
        Gamma gamma =
                new Gamma();

        gamma.add(
                node(left),
                node(right)
        );

        return gamma;
    }

    private static ConceptPatternNode node(String expression) {
        return ConceptPatternNode.parse(
                expression
        );
    }

    private static boolean contains(Gamma gamma, String left, String right) {
        SubsumptionPattern expected =
                new SubsumptionPattern(
                        node(left),
                        node(right)
                );

        return gamma.getAll().contains(
                expected
        );
    }

    private static SubsumptionPattern findPattern(
            Gamma gamma,
            String left,
            String right
    ) {
        ConceptPatternNode expectedLeft =
                node(left);

        ConceptPatternNode expectedRight =
                node(right);

        return gamma.getAll()
                .stream()
                .filter(
                        pattern ->
                                pattern.left.equals(expectedLeft)
                                        && pattern.right.equals(expectedRight)
                )
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError(
                                "Missing pattern: "
                                        + left
                                        + " ⊑? "
                                        + right
                                        + "\n"
                                        + gamma
                        )
                );
    }

    private static Gamma findBranch(List<Gamma> branches, String left, String right) {
        return branches.stream()
                .filter(
                        branch ->
                                contains(
                                        branch,
                                        left,
                                        right
                                )
                )
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError(
                                "No branch contains: "
                                        + left
                                        + " ⊑? "
                                        + right
                        )
                );
    }

    private static long countBranchesContaining(
            List<Gamma> branches,
            String left,
            String right
    ) {
        return branches.stream()
                .filter(
                        branch ->
                                contains(
                                        branch,
                                        left,
                                        right
                                )
                )
                .count();
    }
}