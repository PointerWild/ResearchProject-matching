package el;

import el.structure.ConceptPatternNode;
import el.structure.SubsumptionPattern;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Mutation rule from Algorithm 5.1.
 *
 * This first group of tests focuses on:
 *
 * 1. the existing conjunction-left case;
 * 2. the n = 1 case;
 * 3. preservation of the original Gamma;
 * 4. solved-target handling;
 * 5. failure when no Ci can be mapped to an antecedent atom.
 */
class MutationRuleTest {

    /**
     * Existing conjunction case:
     *
     * TBox:
     *
     * ∃r.A ⊑ ∃s.B
     *
     * Gamma target:
     *
     * ∃r._X_ ⊓ Z ⊑? ∃s.B
     *
     * Mutation chooses:
     *
     * A1 = ∃r.A
     * B  = ∃s.B
     *
     * Dec(∃r._X_, ∃r.A) produces:
     *
     * _X_ ⊑? A
     */
    @Test
    void conjunctionCaseCreatesOneMutationBranch() {
        ELAnalyze analyze =
                analyze(
                        List.of(
                                "∃r.A ⊑ ∃s.B"
                        ),
                        (left, right) -> true
                );

        DecAnalyze dec =
                new DecAnalyze(
                        analyze
                );

        Gamma original =
                gamma(
                        "∃r._X_ ⊓ Z",
                        "∃s.B"
                );

        SubsumptionPattern originalTarget =
                original.getAll().get(0);

        List<Gamma> branches =
                MutationRule.applyAll(
                        originalTarget,
                        original,
                        dec,
                        analyze
                );

        assertEquals(
                1,
                branches.size()
        );

        /*
         * Mutation.applyAll() must not modify the original Gamma.
         */
        assertEquals(
                1,
                original.getAll().size()
        );

        assertFalse(
                originalTarget.solved
        );

        Gamma branch =
                branches.get(0);

        /*
         * The copied target is solved.
         */
        assertTrue(
                branch.getAll().get(0).solved
        );

        /*
         * The branch contains:
         *
         * 1. the solved original target;
         * 2. the generated subgoal _X_ ⊑? A.
         */
        assertEquals(
                2,
                branch.getAll().size()
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
     * n = 1 Mutation:
     *
     * TBox:
     *
     * ∃r.A ⊑ ∃s.B
     *
     * Gamma target:
     *
     * ∃r._X_ ⊑? ∃s.B
     *
     * The left side contains one atom rather than an explicit
     * conjunction node.
     *
     * Decomposition fails because r != s, but Mutation can use:
     *
     * ∃r.A ⊑T ∃s.B
     *
     * and generate:
     *
     * _X_ ⊑? A
     *
     * This test is expected to fail with the old MutationRule because
     * the old implementation accepts only CONJUNCTION left sides.
     */
    @Test
    void n1CreatesMutationBranch() {
        ELAnalyze analyze =
                analyze(
                        List.of(
                                "∃r.A ⊑ ∃s.B"
                        ),
                        (left, right) -> true
                );

        DecAnalyze dec =
                new DecAnalyze(
                        analyze
                );

        Gamma original =
                gamma(
                        "∃r._X_",
                        "∃s.B"
                );

        SubsumptionPattern originalTarget =
                original.getAll().get(0);

        List<Gamma> branches =
                MutationRule.applyAll(
                        originalTarget,
                        original,
                        dec,
                        analyze
                );

        assertEquals(
                1,
                branches.size(),
                "Mutation must support the n = 1 case."
        );

        /*
         * The original Gamma remains unchanged.
         */
        assertEquals(
                1,
                original.getAll().size()
        );

        assertFalse(
                originalTarget.solved
        );

        Gamma branch =
                branches.get(0);

        assertTrue(
                branch.getAll().get(0).solved
        );

        assertEquals(
                2,
                branch.getAll().size()
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
     * An already solved target must not produce Mutation branches.
     */
    @Test
    void solvedTargetProducesNoBranches() {
        ELAnalyze analyze =
                analyze(
                        List.of(
                                "∃r.A ⊑ ∃s.B"
                        ),
                        (left, right) -> true
                );

        DecAnalyze dec =
                new DecAnalyze(
                        analyze
                );

        Gamma gamma =
                gamma(
                        "∃r._X_ ⊓ Z",
                        "∃s.B"
                );

        SubsumptionPattern target =
                gamma.getAll().get(0);

        target.solved = true;

        List<Gamma> branches =
                MutationRule.applyAll(
                        target,
                        gamma,
                        dec,
                        analyze
                );

        assertTrue(
                branches.isEmpty()
        );

        assertTrue(
                target.solved
        );

        assertEquals(
                1,
                gamma.getAll().size()
        );
    }

    /**
     * TBox:
     *
     * A ⊑ D
     *
     * Gamma:
     *
     * ∃r._X_ ⊓ Z ⊑? ∃s.B
     *
     * The only candidate antecedent is A.
     *
     * Neither:
     *
     * Dec(∃r._X_, A)
     *
     * nor:
     *
     * Dec(Z, A)
     *
     * succeeds, so no Mutation branch is possible.
     */
    @Test
    void returnsNoBranchWhenNoCiMatchesAntecedent() {
        ConceptPatternNode a =
                node("A");

        ConceptPatternNode d =
                node("D");

        ELAnalyze analyze =
                analyze(
                        List.of(
                                "A ⊑ D"
                        ),
                        (left, right) ->
                                left.equals(right)
                                        || left.equals(a)
                                        && right.equals(d)
                );

        DecAnalyze dec =
                new DecAnalyze(
                        analyze
                );

        Gamma gamma =
                gamma(
                        "∃r._X_ ⊓ Z",
                        "∃s.B"
                );

        SubsumptionPattern target =
                gamma.getAll().get(0);

        List<Gamma> branches =
                MutationRule.applyAll(
                        target,
                        gamma,
                        dec,
                        analyze
                );

        assertTrue(
                branches.isEmpty()
        );

        assertFalse(
                target.solved
        );

        assertEquals(
                1,
                gamma.getAll().size()
        );
    }

    /**
     * A successful branch must contain copied SubsumptionPattern
     * objects rather than sharing the target object with the original
     * Gamma.
     */
    @Test
    void mutationBranchHasIndependentSolvedState() {
        ELAnalyze analyze =
                analyze(
                        List.of(
                                "∃r.A ⊑ ∃s.B"
                        ),
                        (left, right) -> true
                );

        DecAnalyze dec =
                new DecAnalyze(
                        analyze
                );

        Gamma original =
                gamma(
                        "∃r._X_ ⊓ Z",
                        "∃s.B"
                );

        SubsumptionPattern originalTarget =
                original.getAll().get(0);

        List<Gamma> branches =
                MutationRule.applyAll(
                        originalTarget,
                        original,
                        dec,
                        analyze
                );

        assertEquals(
                1,
                branches.size()
        );

        SubsumptionPattern copiedTarget =
                branches.get(0)
                        .getAll()
                        .get(0);

        assertTrue(
                copiedTarget.solved
        );

        assertFalse(
                originalTarget.solved
        );

        copiedTarget.solved = false;

        assertFalse(
                copiedTarget.solved
        );

        assertFalse(
                originalTarget.solved
        );
    }

    // ---------------------------------------------------------------------
    // Test helpers
    // ---------------------------------------------------------------------

    private static ELAnalyze analyze(
            List<String> tBox,
            SubsumptionOracle oracle
    ) {
        ELAnalyze analyze =
                new ELAnalyze(
                        oracle
                );

        analyze.setTBoxLines(
                tBox
        );

        return analyze;
    }

    private static Gamma gamma(
            String left,
            String right
    ) {
        Gamma gamma =
                new Gamma();

        gamma.add(
                node(left),
                node(right)
        );

        return gamma;
    }

    private static ConceptPatternNode node(
            String expression
    ) {
        return ConceptPatternNode.parse(
                expression
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
                                pattern.left.equals(
                                        expectedLeft
                                )
                                        && pattern.right.equals(
                                        expectedRight
                                )
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
}