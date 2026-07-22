package el;

import el.structure.ConceptPatternNode;
import el.structure.SubsumptionPattern;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the two eager rules used by Algorithm 5.1.
 *
 * The tests cover:
 *
 * 1. right-variable eager solving;
 * 2. left-variable eager solving;
 * 3. semantic compatibility checks;
 * 4. eager-rule failure;
 * 5. right-variable rule priority;
 * 6. participation of solved constraints;
 * 7. exactly one eager application per call;
 * 8. normalized-Gamma ground-query invariant.
 */
class EagerSolverTest {

    /**
     * No complete side is a variable:
     *
     * A ⊑? B
     *
     * Therefore no eager rule is applicable.
     */
    @Test
    void returnsFalseWhenNoEagerRuleIsApplicable() throws FailureException {
        TrackingOracle oracle =
                oracleThatMustNotBeCalled();

        EagerSolver solver =
                solverWithOracle(oracle);

        SubsumptionPattern first =
                pattern("A", "B");

        SubsumptionPattern second =
                pattern("∃r.A", "∃r.B");

        boolean applied =
                solver.applyEager(
                        List.of(
                                first,
                                second
                        )
                );

        assertFalse(applied);
        assertFalse(first.solved);
        assertFalse(second.solved);
        assertEquals(0, oracle.queryCount());
    }

    /**
     * Right-variable rule without related constraints:
     *
     * A ⊑? _X_
     *
     * Since there is no _X_ ⊑? D constraint, the target can be
     * marked solved without a semantic query.
     */
    @Test
    void rightVariableWithoutRelatedConstraintsIsSolved() throws FailureException {
        TrackingOracle oracle =
                oracleThatMustNotBeCalled();

        EagerSolver solver =
                solverWithOracle(oracle);

        SubsumptionPattern target =
                pattern("A", "_X_");

        boolean applied =
                solver.applyEager(
                        List.of(target)
                );

        assertTrue(applied);
        assertTrue(target.solved);
        assertEquals(0, oracle.queryCount());
    }

    /**
     * Right-variable rule:
     *
     * A ⊑? _X_
     * _X_ ⊑? B
     * _X_ ⊑? C
     *
     * The rule must check:
     *
     * A ⊑T B
     * A ⊑T C
     */
    @Test
    void rightVariableChecksEveryRelatedConstraint() throws FailureException {
        TrackingOracle oracle =
                new TrackingOracle(
                        (left, right) -> true
                );

        EagerSolver solver =
                solverWithOracle(oracle);

        SubsumptionPattern target =
                pattern("A", "_X_");

        SubsumptionPattern relatedB =
                pattern("_X_", "B");

        SubsumptionPattern relatedC =
                pattern("_X_", "C");

        boolean applied =
                solver.applyEager(
                        List.of(
                                target,
                                relatedB,
                                relatedC
                        )
                );

        assertTrue(applied);
        assertTrue(target.solved);

        /*
         * applyEager() applies exactly one rule.
         */
        assertFalse(relatedB.solved);
        assertFalse(relatedC.solved);

        assertEquals(
                List.of(
                        query("A", "B"),
                        query("A", "C")
                ),
                oracle.getQueries()
        );
    }

    /**
     * Right-variable eager failure:
     *
     * A ⊑? _X_
     * _X_ ⊑? B
     *
     * If A ⊑T B is false, the current branch must fail and the
     * target must remain unsolved.
     */
    @Test
    void rightVariableFailureThrowsFailureException() {
        TrackingOracle oracle =
                new TrackingOracle(
                        (left, right) -> false
                );

        EagerSolver solver =
                solverWithOracle(oracle);

        SubsumptionPattern target =
                pattern("A", "_X_");

        SubsumptionPattern related =
                pattern("_X_", "B");

        FailureException exception =
                assertThrows(
                        FailureException.class,
                        () -> solver.applyEager(
                                List.of(
                                        target,
                                        related
                                )
                        )
                );

        assertTrue(
                exception.getMessage().contains(
                        "A ⊑T B"
                )
        );

        assertFalse(target.solved);
        assertFalse(related.solved);

        assertEquals(
                List.of(
                        query("A", "B")
                ),
                oracle.getQueries()
        );
    }

    /**
     * Left-variable rule without related constraints:
     *
     * _X_ ⊑? D
     *
     * Since there is no C ⊑? _X_ constraint, the target can be
     * marked solved without a semantic query.
     */
    @Test
    void leftVariableWithoutRelatedConstraintsIsSolved() throws FailureException {
        TrackingOracle oracle =
                oracleThatMustNotBeCalled();

        EagerSolver solver =
                solverWithOracle(oracle);

        SubsumptionPattern target =
                pattern("_X_", "D");

        boolean applied =
                solver.applyEager(
                        List.of(target)
                );

        assertTrue(applied);
        assertTrue(target.solved);
        assertEquals(0, oracle.queryCount());
    }

    /**
     * Left-variable rule:
     *
     * _X_ ⊑? D
     * A ⊑? _X_
     * B ⊑? _X_
     *
     * The related right-variable constraints are marked solved because
     * the right-variable eager rule has priority and would have processed
     * them first during eager saturation.
     *
     * The left-variable rule must still use those solved constraints and
     * check:
     *
     * A ⊑T D
     * B ⊑T D
     */
    @Test
    void leftVariableChecksEverySolvedRelatedConstraint() throws FailureException {
        TrackingOracle oracle =
                new TrackingOracle(
                        (left, right) -> true
                );

        EagerSolver solver =
                solverWithOracle(oracle);

        SubsumptionPattern target =
                pattern("_X_", "D");

        SubsumptionPattern relatedA =
                pattern("A", "_X_");

        SubsumptionPattern relatedB =
                pattern("B", "_X_");

        relatedA.solved = true;
        relatedB.solved = true;

        boolean applied =
                solver.applyEager(
                        List.of(
                                target,
                                relatedA,
                                relatedB
                        )
                );

        assertTrue(applied);
        assertTrue(target.solved);
        assertTrue(relatedA.solved);
        assertTrue(relatedB.solved);

        assertEquals(
                List.of(
                        query("A", "D"),
                        query("B", "D")
                ),
                oracle.getQueries()
        );
    }

    /**
     * Left-variable eager failure:
     *
     * _X_ ⊑? D
     * A ⊑? _X_
     *
     * The related constraint represents an already processed
     * right-variable eager constraint.
     *
     * If A ⊑T D is false, the target remains unsolved.
     */
    @Test
    void leftVariableFailureThrowsFailureException() {
        TrackingOracle oracle =
                new TrackingOracle(
                        (left, right) -> false
                );

        EagerSolver solver =
                solverWithOracle(oracle);

        SubsumptionPattern target =
                pattern("_X_", "D");

        SubsumptionPattern related =
                pattern("A", "_X_");

        related.solved = true;

        FailureException exception =
                assertThrows(
                        FailureException.class,
                        () -> solver.applyEager(
                                List.of(
                                        target,
                                        related
                                )
                        )
                );

        assertTrue(
                exception.getMessage().contains(
                        "A ⊑T D"
                )
        );

        assertFalse(target.solved);
        assertTrue(related.solved);

        assertEquals(
                List.of(
                        query("A", "D")
                ),
                oracle.getQueries()
        );
    }

    /**
     * Right-variable rules have priority over left-variable rules,
     * even when the left-variable target occurs earlier in Gamma.
     *
     * _X_ ⊑? D
     * A ⊑? _Y_
     *
     * The second constraint must be solved first.
     */
    @Test
    void rightVariableRuleHasGlobalPriority() throws FailureException {
        TrackingOracle oracle =
                oracleThatMustNotBeCalled();

        EagerSolver solver =
                solverWithOracle(oracle);

        SubsumptionPattern leftVariableTarget =
                pattern("_X_", "D");

        SubsumptionPattern rightVariableTarget =
                pattern("A", "_Y_");

        boolean applied =
                solver.applyEager(
                        List.of(
                                leftVariableTarget,
                                rightVariableTarget
                        )
                );

        assertTrue(applied);
        assertFalse(leftVariableTarget.solved);
        assertTrue(rightVariableTarget.solved);
        assertEquals(0, oracle.queryCount());
    }

    /**
     * Solved constraints remain members of Gamma and must still
     * participate in compatibility checking.
     *
     * A ⊑? _X_
     * _X_ ⊑? B   [already solved]
     *
     * A false A ⊑T B result must still fail the eager application.
     */
    @Test
    void solvedRelatedConstraintStillParticipatesInRightVariableRule() {
        TrackingOracle oracle =
                new TrackingOracle(
                        (left, right) -> false
                );

        EagerSolver solver =
                solverWithOracle(oracle);

        SubsumptionPattern target =
                pattern("A", "_X_");

        SubsumptionPattern solvedRelated =
                pattern("_X_", "B");

        solvedRelated.solved = true;

        assertThrows(
                FailureException.class,
                () -> solver.applyEager(
                        List.of(
                                target,
                                solvedRelated
                        )
                )
        );

        assertFalse(target.solved);
        assertTrue(solvedRelated.solved);

        assertEquals(
                List.of(
                        query("A", "B")
                ),
                oracle.getQueries()
        );
    }

    /**
     * applyEager() must apply exactly one eager rule per call.
     */
    @Test
    void appliesExactlyOneEagerRulePerCall() throws FailureException {
        TrackingOracle oracle =
                oracleThatMustNotBeCalled();

        EagerSolver solver =
                solverWithOracle(oracle);

        SubsumptionPattern first =
                pattern("A", "_X_");

        SubsumptionPattern second =
                pattern("B", "_Y_");

        boolean applied =
                solver.applyEager(
                        List.of(
                                first,
                                second
                        )
                );

        assertTrue(applied);
        assertTrue(first.solved);
        assertFalse(second.solved);
    }

    /**
     * The eager solver must never send a non-ground semantic query
     * to the semantic oracle.
     *
     * A ⊑? _X_
     * _X_ ⊑? ∃r._Y_
     *
     * The required compatibility query would be:
     *
     * A ⊑T ∃r._Y_
     *
     * which is non-ground and therefore violates the normalized-Gamma
     * invariant.
     */
    @Test
    void rejectsNonGroundSemanticQuery() {
        TrackingOracle oracle =
                oracleThatMustNotBeCalled();

        EagerSolver solver =
                solverWithOracle(oracle);

        SubsumptionPattern target =
                pattern("A", "_X_");

        SubsumptionPattern related =
                pattern("_X_", "∃r._Y_");

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> solver.applyEager(
                                List.of(
                                        target,
                                        related
                                )
                        )
                );

        assertTrue(
                exception.getMessage().contains(
                        "semantic query must be ground"
                )
        );

        assertFalse(target.solved);
        assertFalse(related.solved);
        assertEquals(0, oracle.queryCount());
    }

    /**
     * A null Gamma list is invalid.
     */
    @Test
    void rejectsNullGamma() {
        EagerSolver solver =
                solverWithOracle(
                        oracleThatMustNotBeCalled()
                );

        assertThrows(
                NullPointerException.class,
                () -> solver.applyEager(null)
        );
    }

    /**
     * EagerSolver requires a non-null ELAnalyze instance.
     */
    @Test
    void rejectsNullAnalyzer() {
        assertThrows(
                NullPointerException.class,
                () -> new EagerSolver(null)
        );
    }

    // ---------------------------------------------------------------------
    // Test helpers
    // ---------------------------------------------------------------------

    private static EagerSolver solverWithOracle(SubsumptionOracle oracle) {
        return new EagerSolver(
                new ELAnalyze(oracle)
        );
    }

    private static SubsumptionPattern pattern(String left, String right) {
        return new SubsumptionPattern(
                node(left),
                node(right)
        );
    }

    private static ConceptPatternNode node(String expression) {
        return ConceptPatternNode.parse(expression);
    }

    private static SimpleEntry<ConceptPatternNode, ConceptPatternNode> query(
            String left,
            String right
    ) {
        return new SimpleEntry<>(
                node(left),
                node(right)
        );
    }

    private static TrackingOracle oracleThatMustNotBeCalled() {
        return new TrackingOracle(
                (left, right) -> {
                    throw new AssertionError(
                            "The semantic oracle must not be called for: "
                                    + left
                                    + " ⊑ "
                                    + right
                    );
                }
        );
    }

    /**
     * A deterministic test oracle that records every semantic query.
     */
    private static final class TrackingOracle implements SubsumptionOracle {

        private final BiPredicate<
                ConceptPatternNode,
                ConceptPatternNode> behavior;

        private final List<SimpleEntry<
                ConceptPatternNode,
                ConceptPatternNode>> queries =
                new ArrayList<>();

        private TrackingOracle(
                BiPredicate<
                        ConceptPatternNode,
                        ConceptPatternNode> behavior
        ) {
            this.behavior = behavior;
        }

        @Override
        public boolean subsumes(
                ConceptPatternNode left,
                ConceptPatternNode right
        ) {
            queries.add(
                    new SimpleEntry<>(
                            left,
                            right
                    )
            );

            return behavior.test(
                    left,
                    right
            );
        }

        private int queryCount() {
            return queries.size();
        }

        private List<SimpleEntry<
                ConceptPatternNode,
                ConceptPatternNode>> getQueries() {

            return List.copyOf(queries);
        }
    }
}