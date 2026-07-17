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
 * Tests for the Gamma normalization phase that must run before
 * Algorithm 5.1.
 *
 * The tests verify the responsibility boundary:
 *
 * 1. GammaNormalizer splits only the top-level conjunction on
 *    the complete right-hand side.
 * 2. GammaNormalizer does not enter existential fillers.
 * 3. Direct ground-ground constraints are checked before DFS.
 * 4. Successful ground-ground constraints are removed.
 * 5. The original Gamma is never modified.
 * 6. GoalOrientedMatcher construction performs no ELK query.
 */
class GammaNormalizerTest {

    private static final String BASE_IRI =
            "http://example.com/gamma-normalization-test#";

    /**
     * Input:
     *
     * ∃r.B ⊑? ∃r._X_ ⊓ A1 ⊓ A2 ⊓ A3
     *
     * Expected expanded Gamma:
     *
     * ∃r.B ⊑? ∃r._X_
     * ∃r.B ⊑? A1
     * ∃r.B ⊑? A2
     * ∃r.B ⊑? A3
     *
     * The last three constraints are ground-ground and are
     * checked using ELK.
     *
     * Expected normalized Gamma:
     *
     * ∃r.B ⊑? ∃r._X_
     */
    @Test
    void expandsFourConstraintsBeforeProducingOneNormalizedConstraint() {
        List<String> tBox =
                List.of(
                        "∃r.B ⊑ A1",
                        "∃r.B ⊑ A2",
                        "∃r.B ⊑ A3"
                );

        try (
                ElkSubsumptionOracle elk =
                        oracle(tBox)
        ) {
            ELAnalyze analyze =
                    analyze(
                            tBox,
                            elk
                    );

            Gamma original =
                    gamma(
                            "∃r.B",
                            "∃r._X_ ⊓ A1 ⊓ A2 ⊓ A3"
                    );

            SubsumptionPattern originalPattern =
                    original.getAll()
                            .get(0);

            int queriesBefore =
                    elk.getElkQueryCount();

            int axiomsBefore =
                    elk.getAxiomCount();

            GammaNormalizationResult result =
                    new GammaNormalizer(
                            analyze
                    ).normalize(
                            original
                    );

            assertTrue(
                    result.isMatchable()
            );

            /*
             * expandedGamma and normalizedGamma must be
             * new Gamma objects.
             */
            assertNotSame(
                    original,
                    result.getExpandedGamma()
            );

            assertNotSame(
                    original,
                    result.getNormalizedGamma()
            );

            Gamma expanded =
                    result.getExpandedGamma();

            assertEquals(
                    4,
                    expanded.getAll()
                            .size()
            );

            assertContains(
                    expanded,
                    "∃r.B",
                    "∃r._X_"
            );

            assertContains(
                    expanded,
                    "∃r.B",
                    "A1"
            );

            assertContains(
                    expanded,
                    "∃r.B",
                    "A2"
            );

            assertContains(
                    expanded,
                    "∃r.B",
                    "A3"
            );

            Gamma normalized =
                    result.getNormalizedGamma();

            assertEquals(
                    1,
                    normalized.getAll()
                            .size()
            );

            assertContains(
                    normalized,
                    "∃r.B",
                    "∃r._X_"
            );

            /*
             * Newly normalized constraints must be unsolved.
             */
            assertFalse(
                    normalized.getAll()
                            .get(0)
                            .solved
            );

            /*
             * Three direct ground-ground constraints were checked.
             */
            assertEquals(
                    3,
                    result.getGroundChecks()
                            .size()
            );

            assertTrue(
                    result.getGroundChecks()
                            .stream()
                            .allMatch(
                                    GammaNormalizationResult
                                            .GroundCheckResult
                                            ::entailed
                            )
            );

            /*
             * Exactly three semantic ELK queries are expected.
             */
            assertEquals(
                    3,
                    elk.getElkQueryCount()
                            - queriesBefore
            );

            /*
             * Semantic queries must not add query axioms
             * to the ontology.
             */
            assertEquals(
                    axiomsBefore,
                    elk.getAxiomCount()
            );

            /*
             * The original Gamma remains unchanged.
             */
            assertEquals(
                    1,
                    original.getAll()
                            .size()
            );

            assertEquals(
                    ConceptPatternNode.Type.CONJUNCTION,
                    originalPattern.right.type
            );

            assertFalse(
                    originalPattern.solved
            );
        }
    }

    /**
     * If one directly occurring ground-ground constraint is not
     * entailed, normalization must return an unmatchable result.
     *
     * Algorithm 5.1 must not start.
     */
    @Test
    void failedGroundCheckImmediatelyReturnsNoMatcher() {
        List<String> tBox =
                List.of(
                        "∃r.B ⊑ A1",
                        "∃r.B ⊑ A2"
                );

        try (
                ElkSubsumptionOracle elk =
                        oracle(tBox)
        ) {
            ELAnalyze analyze =
                    analyze(
                            tBox,
                            elk
                    );

            Gamma original =
                    gamma(
                            "∃r.B",
                            "∃r._X_ ⊓ A1 ⊓ A2 ⊓ A3"
                    );

            GammaNormalizationResult result =
                    new GammaNormalizer(
                            analyze
                    ).normalize(
                            original
                    );

            assertFalse(
                    result.isMatchable()
            );

            /*
             * The complete expanded Gamma must already exist
             * before the semantic checks are performed.
             */
            assertEquals(
                    4,
                    result.getExpandedGamma()
                            .getAll()
                            .size()
            );

            /*
             * A1 and A2 succeed, while A3 fails.
             */
            assertEquals(
                    3,
                    result.getGroundChecks()
                            .size()
            );

            assertFalse(
                    result.getGroundChecks()
                            .get(2)
                            .entailed()
            );

            assertTrue(
                    result.getFailureReason()
                            .contains(
                                    "∃r.B ⊑ A3"
                            )
            );

            assertEquals(
                    ConceptPatternNode.parse(
                            "A3"
                    ),
                    result.getFailedSubsumption()
                            .right
            );

            /*
             * A failed result does not have a normalized Gamma.
             */
            assertThrows(
                    IllegalStateException.class,
                    result::getNormalizedGamma
            );

            /*
             * match() must stop before DFS.
             */
            GoalOrientedMatcher matcher =
                    new GoalOrientedMatcher(
                            analyze
                    );

            assertFalse(
                    matcher.match(
                            original
                    )
            );

            assertEquals(
                    0,
                    matcher.getDfsInvocationCount()
            );

            /*
             * The original pattern remains unsolved.
             */
            assertFalse(
                    original.getAll()
                            .get(0)
                            .solved
            );
        }
    }

    /**
     * A global ground-ground failure has priority over an
     * eager-solvable constraint.
     *
     * Gamma:
     *
     * A1 ⊑? _X_
     * ∃r.B ⊑? A3
     *
     * The second constraint is false, so the matcher must stop
     * before applying the eager rule to the first constraint.
     */
    @Test
    void checksFailingGroundConstraintBeforeAnyEagerRule() {
        List<String> tBox =
                List.of();

        try (
                ElkSubsumptionOracle elk =
                        oracle(tBox)
        ) {
            ELAnalyze analyze =
                    analyze(
                            tBox,
                            elk
                    );

            Gamma original =
                    new Gamma();

            original.add(
                    ConceptPatternNode.parse(
                            "A1"
                    ),
                    ConceptPatternNode.parse(
                            "_X_"
                    )
            );

            original.add(
                    ConceptPatternNode.parse(
                            "∃r.B"
                    ),
                    ConceptPatternNode.parse(
                            "A3"
                    )
            );

            int queriesBefore =
                    elk.getElkQueryCount();

            GoalOrientedMatcher matcher =
                    new GoalOrientedMatcher(
                            analyze
                    );

            assertFalse(
                    matcher.match(
                            original
                    )
            );

            /*
             * Only the direct ground-ground constraint is checked.
             */
            assertEquals(
                    1,
                    elk.getElkQueryCount()
                            - queriesBefore
            );

            /*
             * DFS, including its eager phase, was never entered.
             */
            assertEquals(
                    0,
                    matcher.getDfsInvocationCount()
            );

            /*
             * The eager-solvable constraint in the original Gamma
             * must remain untouched.
             */
            assertFalse(
                    original.getAll()
                            .get(0)
                            .solved
            );
        }
    }

    /**
     * Constructing GoalOrientedMatcher must not query ELK.
     *
     * Semantic processing begins only when match() is invoked.
     */
    @Test
    void constructingMatcherDoesNotQueryElk() {
        List<String> tBox =
                List.of(
                        "A1 ⊑ A2"
                );

        try (
                ElkSubsumptionOracle elk =
                        oracle(tBox)
        ) {
            ELAnalyze analyze =
                    analyze(
                            tBox,
                            elk
                    );

            int queriesBefore =
                    elk.getElkQueryCount();

            new GoalOrientedMatcher(
                    analyze
            );

            assertEquals(
                    queriesBefore,
                    elk.getElkQueryCount()
            );
        }
    }

    /**
     * The complete RHS is one existential atom:
     *
     * ∃r.B ⊑? ∃r.(_X_ ⊓ A1 ⊓ A2)
     *
     * GammaNormalizer must not enter the existential filler.
     *
     * Therefore:
     *
     * 1. expandedGamma contains one constraint;
     * 2. normalizedGamma contains one constraint;
     * 3. no ELK query is performed during normalization.
     */
    @Test
    void splitsOnlyTopLevelRightConjunction() {
        List<String> tBox =
                List.of();

        try (
                ElkSubsumptionOracle elk =
                        oracle(tBox)
        ) {
            ELAnalyze analyze =
                    analyze(
                            tBox,
                            elk
                    );

            Gamma original =
                    gamma(
                            "∃r.B",
                            "∃r.(_X_ ⊓ A1 ⊓ A2)"
                    );

            int queriesBefore =
                    elk.getElkQueryCount();

            int axiomsBefore =
                    elk.getAxiomCount();

            GammaNormalizationResult result =
                    new GammaNormalizer(
                            analyze
                    ).normalize(
                            original
                    );

            assertTrue(
                    result.isMatchable()
            );

            /*
             * The existential RHS remains one complete
             * top-level atom.
             */
            assertEquals(
                    1,
                    result.getExpandedGamma()
                            .getAll()
                            .size()
            );

            assertEquals(
                    1,
                    result.getNormalizedGamma()
                            .getAll()
                            .size()
            );

            ConceptPatternNode right =
                    result.getNormalizedGamma()
                            .getAll()
                            .get(0)
                            .right;

            assertEquals(
                    ConceptPatternNode.Type.EXISTENTIAL,
                    right.type
            );

            assertEquals(
                    ConceptPatternNode.Type.CONJUNCTION,
                    right.existentialFiller.type
            );

            assertEquals(
                    3,
                    right.existentialFiller
                            .conjunctions
                            .size()
            );

            /*
             * B ⊑ A1 and B ⊑ A2 belong to Dec,
             * not global normalization.
             */
            assertEquals(
                    0,
                    result.getGroundChecks()
                            .size()
            );

            assertEquals(
                    0,
                    elk.getElkQueryCount()
                            - queriesBefore
            );

            assertEquals(
                    axiomsBefore,
                    elk.getAxiomCount()
            );

            /*
             * The original Gamma remains unchanged.
             */
            assertEquals(
                    1,
                    original.getAll()
                            .size()
            );

            assertEquals(
                    ConceptPatternNode.Type.EXISTENTIAL,
                    original.getAll()
                            .get(0)
                            .right
                            .type
            );

            assertFalse(
                    original.getAll()
                            .get(0)
                            .solved
            );
        }
    }

    /**
     * Duplicate RHS atoms must have set semantics.
     *
     * Input:
     *
     * ∃r.B ⊑? ∃r._X_ ⊓ A1 ⊓ A1 ⊓ A2
     *
     * A1 must be present and queried only once.
     */
    @Test
    void duplicateExpandedAtomsHaveSetSemanticsAndOneElkQueryEach() {
        List<String> tBox =
                List.of(
                        "∃r.B ⊑ A1",
                        "∃r.B ⊑ A2"
                );

        try (
                ElkSubsumptionOracle elk =
                        oracle(tBox)
        ) {
            ELAnalyze analyze =
                    analyze(
                            tBox,
                            elk
                    );

            Gamma original =
                    gamma(
                            "∃r.B",
                            "∃r._X_ ⊓ A1 ⊓ A1 ⊓ A2"
                    );

            int queriesBefore =
                    elk.getElkQueryCount();

            GammaNormalizationResult result =
                    new GammaNormalizer(
                            analyze
                    ).normalize(
                            original
                    );

            assertTrue(
                    result.isMatchable()
            );

            /*
             * Duplicate A1 is removed by Gamma set semantics.
             */
            assertEquals(
                    3,
                    result.getExpandedGamma()
                            .getAll()
                            .size()
            );

            assertContains(
                    result.getExpandedGamma(),
                    "∃r.B",
                    "∃r._X_"
            );

            assertContains(
                    result.getExpandedGamma(),
                    "∃r.B",
                    "A1"
            );

            assertContains(
                    result.getExpandedGamma(),
                    "∃r.B",
                    "A2"
            );

            assertEquals(
                    2,
                    result.getGroundChecks()
                            .size()
            );

            /*
             * A1 and A2 are each queried exactly once.
             */
            assertEquals(
                    2,
                    elk.getElkQueryCount()
                            - queriesBefore
            );

            assertEquals(
                    1,
                    result.getNormalizedGamma()
                            .getAll()
                            .size()
            );
        }
    }

    /**
     * If all expanded constraints are true ground-ground
     * constraints, normalizedGamma is empty.
     *
     * An empty normalized Gamma is successfully matched.
     */
    @Test
    void allTrueGroundConstraintsNormalizeToEmptyGammaAndMatch() {
        List<String> tBox =
                List.of(
                        "∃r.B ⊑ A1",
                        "∃r.B ⊑ A2"
                );

        try (
                ElkSubsumptionOracle elk =
                        oracle(tBox)
        ) {
            ELAnalyze analyze =
                    analyze(
                            tBox,
                            elk
                    );

            Gamma original =
                    gamma(
                            "∃r.B",
                            "A1 ⊓ A2"
                    );

            GammaNormalizationResult result =
                    new GammaNormalizer(
                            analyze
                    ).normalize(
                            original
                    );

            assertTrue(
                    result.isMatchable()
            );

            assertEquals(
                    2,
                    result.getExpandedGamma()
                            .getAll()
                            .size()
            );

            assertEquals(
                    0,
                    result.getNormalizedGamma()
                            .getAll()
                            .size()
            );

            assertEquals(
                    2,
                    result.getGroundChecks()
                            .size()
            );

            GoalOrientedMatcher matcher =
                    new GoalOrientedMatcher(
                            analyze
                    );

            assertTrue(
                    matcher.match(
                            original
                    )
            );

            /*
             * match() calls DFS once with an empty normalized Gamma.
             * DFS immediately succeeds because no unsolved constraint exists.
             */
            assertEquals(
                    1,
                    matcher.getDfsInvocationCount()
            );
        }
    }

    /**
     * Both original sides are non-ground:
     *
     * ∃r._X_ ⊑? ∃s._Y_
     *
     * This is not a legal matching constraint according to
     * Definition 3.1.
     */
    @Test
    void rejectsConstraintWhenBothSidesAreNonGround() {
        List<String> tBox =
                List.of();

        try (
                ElkSubsumptionOracle elk =
                        oracle(tBox)
        ) {
            ELAnalyze analyze =
                    analyze(
                            tBox,
                            elk
                    );

            Gamma original =
                    gamma(
                            "∃r._X_",
                            "∃s._Y_"
                    );

            int queriesBefore =
                    elk.getElkQueryCount();

            IllegalArgumentException exception =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new GammaNormalizer(
                                    analyze
                            ).normalize(
                                    original
                            )
                    );

            assertTrue(
                    exception.getMessage()
                            .contains(
                                    "at least one side must be ground"
                            )
            );

            /*
             * This validation is structural and occurs before
             * any semantic query.
             */
            assertEquals(
                    queriesBefore,
                    elk.getElkQueryCount()
            );

            assertEquals(
                    1,
                    original.getAll()
                            .size()
            );

            assertFalse(
                    original.getAll()
                            .get(0)
                            .solved
            );
        }
    }

    /**
     * Definition 3.1 must be checked on the complete original
     * constraint before RHS splitting.
     *
     * Original constraint:
     *
     * _X_ ⊑? A1 ⊓ _Y_
     *
     * The complete left and right sides are both non-ground.
     * Therefore, the input must be rejected before producing:
     *
     * _X_ ⊑? A1
     * _X_ ⊑? _Y_
     */
    @Test
    void rejectsOriginalNonGroundProblemBeforeRhsExpansion() {
        List<String> tBox =
                List.of();

        try (
                ElkSubsumptionOracle elk =
                        oracle(tBox)
        ) {
            ELAnalyze analyze =
                    analyze(
                            tBox,
                            elk
                    );

            Gamma original =
                    gamma(
                            "_X_",
                            "A1 ⊓ _Y_"
                    );

            int queriesBefore =
                    elk.getElkQueryCount();

            IllegalArgumentException exception =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new GammaNormalizer(
                                    analyze
                            ).normalize(
                                    original
                            )
                    );

            assertTrue(
                    exception.getMessage()
                            .contains(
                                    "at least one side must be ground"
                            )
            );

            /*
             * The original validation is structural.
             */
            assertEquals(
                    queriesBefore,
                    elk.getElkQueryCount()
            );

            /*
             * The original Gamma was not expanded or modified.
             */
            assertEquals(
                    1,
                    original.getAll()
                            .size()
            );

            assertEquals(
                    ConceptPatternNode.Type.CONJUNCTION,
                    original.getAll()
                            .get(0)
                            .right
                            .type
            );

            assertFalse(
                    original.getAll()
                            .get(0)
                            .solved
            );
        }
    }

    /**
     * C ⊑? Tau is trivially true.
     *
     * It may remain visible in expandedGamma, but it must not
     * enter normalizedGamma or require an ELK query.
     */
    @Test
    void removesTauConstraintWithoutElkQuery() {
        List<String> tBox =
                List.of();

        try (
                ElkSubsumptionOracle elk =
                        oracle(tBox)
        ) {
            ELAnalyze analyze =
                    analyze(
                            tBox,
                            elk
                    );

            Gamma original =
                    gamma(
                            "_X_",
                            "Tau"
                    );

            int queriesBefore =
                    elk.getElkQueryCount();

            int axiomsBefore =
                    elk.getAxiomCount();

            GammaNormalizationResult result =
                    new GammaNormalizer(
                            analyze
                    ).normalize(
                            original
                    );

            assertTrue(
                    result.isMatchable()
            );

            /*
             * The original constraint remains observable in
             * expandedGamma.
             */
            assertEquals(
                    1,
                    result.getExpandedGamma()
                            .getAll()
                            .size()
            );

            assertContains(
                    result.getExpandedGamma(),
                    "_X_",
                    "Tau"
            );

            /*
             * Tau is removed from the Algorithm 5.1 work Gamma.
             */
            assertEquals(
                    0,
                    result.getNormalizedGamma()
                            .getAll()
                            .size()
            );

            /*
             * The minimal implementation handles Tau directly,
             * so it does not produce a semantic ground-check record.
             */
            assertEquals(
                    0,
                    result.getGroundChecks()
                            .size()
            );

            assertEquals(
                    0,
                    elk.getElkQueryCount()
                            - queriesBefore
            );

            assertEquals(
                    axiomsBefore,
                    elk.getAxiomCount()
            );

            assertFalse(
                    original.getAll()
                            .get(0)
                            .solved
            );
        }
    }

    /**
     * Verifies the project naming rules.
     *
     * ConceptName:
     *
     * [A-Z][A-Za-z0-9]*
     *
     * Variable:
     *
     * _[A-Z][A-Za-z0-9]*_
     */
    @Test
    void parsesExtendedConceptAndVariableNamesWithoutAmbiguity() {
        assertConceptName(
                "A1"
        );

        assertConceptName(
                "A2"
        );

        assertConceptName(
                "Abc1"
        );

        assertConceptName(
                "Person123"
        );

        assertVariable(
                "_X_"
        );

        assertVariable(
                "_Xs_"
        );

        assertVariable(
                "_Xsasda1_"
        );

        assertVariable(
                "_Abc1_"
        );

        ConceptPatternNode variable =
                ConceptPatternNode.parse(
                        "_Xsasda1_"
                );

        assertEquals(
                ConceptPatternNode.Type.VARIABLE,
                variable.type
        );

        assertFalse(
                variable.type
                        == ConceptPatternNode.Type.CONCEPT_NAME
        );
    }

    /**
     * Creates the real ELK-backed subsumption oracle used by
     * the tests.
     */
    private static ElkSubsumptionOracle oracle(
            List<String> tBox
    ) {
        return new ElkSubsumptionOracle(
                tBox,
                BASE_IRI
        );
    }

    /**
     * Creates ELAnalyze and injects the same long-lived
     * ELK oracle.
     */
    private static ELAnalyze analyze(
            List<String> tBox,
            ElkSubsumptionOracle elk
    ) {
        ELAnalyze analyze =
                new ELAnalyze();

        analyze.setTBoxLines(
                tBox
        );

        analyze.setSubsumptionOracle(
                elk
        );

        return analyze;
    }

    /**
     * Creates a one-constraint Gamma.
     */
    private static Gamma gamma(
            String left,
            String right
    ) {
        Gamma gamma =
                new Gamma();

        gamma.add(
                ConceptPatternNode.parse(
                        left
                ),
                ConceptPatternNode.parse(
                        right
                )
        );

        return gamma;
    }

    /**
     * Verifies that Gamma contains a structurally equal
     * subsumption pattern.
     */
    private static void assertContains(
            Gamma gamma,
            String left,
            String right
    ) {
        SubsumptionPattern expected =
                new SubsumptionPattern(
                        ConceptPatternNode.parse(
                                left
                        ),
                        ConceptPatternNode.parse(
                                right
                        )
                );

        assertTrue(
                gamma.getAll()
                        .contains(
                                expected
                        ),
                () -> "Missing constraint "
                        + expected
                        + " in "
                        + gamma
        );
    }

    /**
     * Verifies parsing as ConceptName.
     */
    private static void assertConceptName(
            String input
    ) {
        ConceptPatternNode parsed =
                ConceptPatternNode.parse(
                        input
                );

        assertEquals(
                ConceptPatternNode.Type.CONCEPT_NAME,
                parsed.type
        );

        assertEquals(
                input,
                parsed.conceptName.name
        );
    }

    /**
     * Verifies parsing as VariableName.
     */
    private static void assertVariable(
            String input
    ) {
        ConceptPatternNode parsed =
                ConceptPatternNode.parse(
                        input
                );

        assertEquals(
                ConceptPatternNode.Type.VARIABLE,
                parsed.type
        );

        assertEquals(
                input,
                parsed.variable.name
        );
    }
}