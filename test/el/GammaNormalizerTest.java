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

class GammaNormalizerTest {

    private static final String BASE_IRI =
            "http://example.com/gamma-normalization-test#";

    /**
     * Input:
     *
     * ∃r.B ⊑? ∃r._X_ ⊓ A ⊓ C ⊓ D
     *
     * TBox entails all three ground constraints:
     *
     * ∃r.B ⊑ A
     * ∃r.B ⊑ C
     * ∃r.B ⊑ D
     *
     * Therefore the final normalized Gamma contains only:
     *
     * ∃r.B ⊑? ∃r._X_
     */
    @Test
    void shouldSplitRightConjunctionAndRemoveTrueGroundConstraints() {

        List<String> tBox =
                List.of(
                        "∃r.B ⊑ A",
                        "∃r.B ⊑ C",
                        "∃r.B ⊑ D"
                );

        try (
                ElkSubsumptionOracle elk =
                        new ElkSubsumptionOracle(
                                tBox,
                                BASE_IRI
                        )
        ) {
            ELAnalyze analyze =
                    new ELAnalyze();

            analyze.setTBoxLines(
                    tBox
            );

            analyze.setSubsumptionOracle(
                    elk
            );

            Gamma original =
                    new Gamma();

            original.add(
                    ConceptPatternNode.parse("∃r.B"),
                    ConceptPatternNode.parse(
                            "∃r._X_ ⊓ A ⊓ C ⊓ D"
                    )
            );

            GammaNormalizationResult result =
                    new GammaNormalizer(
                            analyze
                    ).normalize(original);

            assertTrue(
                    result.isMatchable()
            );

            Gamma normalized =
                    result.getNormalizedGamma();

            assertNotSame(
                    original,
                    normalized,
                    "Normalization must create a new Gamma."
            );

            assertEquals(
                    1,
                    normalized.getAll().size()
            );

            SubsumptionPattern remaining =
                    normalized.getAll().get(0);

            assertEquals(
                    ConceptPatternNode.parse("∃r.B"),
                    remaining.left
            );

            assertEquals(
                    ConceptPatternNode.parse("∃r._X_"),
                    remaining.right
            );

            assertFalse(
                    remaining.solved,
                    "Normalized constraints must initially be unsolved."
            );

            /*
             * Three ground-ground constraints were checked:
             *
             * ∃r.B ⊑ A
             * ∃r.B ⊑ C
             * ∃r.B ⊑ D
             */
            assertEquals(
                    3,
                    elk.getElkQueryCount()
            );

            /*
             * Original Gamma remains untouched.
             */
            assertEquals(
                    1,
                    original.getAll().size()
            );

            assertEquals(
                    ConceptPatternNode.Type.CONJUNCTION,
                    original.getAll().get(0).right.type
            );

            assertFalse(
                    original.getAll().get(0).solved
            );
        }
    }

    /**
     * One false ground-ground conjunct makes the entire problem unmatchable.
     */
    @Test
    void falseGroundConjunctShouldMakeProblemUnmatchable() {

        List<String> tBox =
                List.of(
                        "∃r.B ⊑ A",
                        "∃r.B ⊑ C"
                );

        try (
                ElkSubsumptionOracle elk =
                        new ElkSubsumptionOracle(
                                tBox,
                                BASE_IRI
                        )
        ) {
            ELAnalyze analyze =
                    new ELAnalyze();

            analyze.setTBoxLines(
                    tBox
            );

            analyze.setSubsumptionOracle(
                    elk
            );

            Gamma gamma =
                    new Gamma();

            gamma.add(
                    ConceptPatternNode.parse("∃r.B"),
                    ConceptPatternNode.parse(
                            "∃r._X_ ⊓ A ⊓ C ⊓ D"
                    )
            );

            GammaNormalizationResult result =
                    new GammaNormalizer(
                            analyze
                    ).normalize(gamma);

            assertFalse(
                    result.isMatchable(),
                    "The problem must fail because the TBox "
                            + "does not entail ∃r.B ⊑ D."
            );

            assertTrue(
                    result.getFailureReason().contains(
                            "∃r.B ⊑ D"
                    )
            );
        }
    }

    /**
     * The definition of an EL matching problem requires at least one
     * ground side.
     */
    @Test
    void shouldRejectConstraintWithTwoNonGroundSides() {

        List<String> tBox =
                List.of();

        try (
                ElkSubsumptionOracle elk =
                        new ElkSubsumptionOracle(
                                tBox,
                                BASE_IRI
                        )
        ) {
            ELAnalyze analyze =
                    new ELAnalyze();

            analyze.setTBoxLines(
                    tBox
            );

            analyze.setSubsumptionOracle(
                    elk
            );

            Gamma gamma =
                    new Gamma();

            gamma.add(
                    ConceptPatternNode.parse("∃r._X_"),
                    ConceptPatternNode.parse("∃s._Y_")
            );

            IllegalArgumentException exception =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new GammaNormalizer(
                                    analyze
                            ).normalize(gamma)
                    );

            assertTrue(
                    exception.getMessage().contains(
                            "at least one side must be ground"
                    )
            );
        }
    }

    /**
     * Verifies that GoalOrientedMatcher performs normalization before DFS.
     */
    @Test
    void matcherShouldNormalizeBeforeRunningAlgorithm51() {

        List<String> tBox =
                List.of(
                        "∃r.B ⊑ A",
                        "∃r.B ⊑ C",
                        "∃r.B ⊑ D"
                );

        try (
                ElkSubsumptionOracle elk =
                        new ElkSubsumptionOracle(
                                tBox,
                                BASE_IRI
                        )
        ) {
            ELAnalyze analyze =
                    new ELAnalyze();

            analyze.setTBoxLines(
                    tBox
            );

            analyze.setSubsumptionOracle(
                    elk
            );

            Gamma gamma =
                    new Gamma();

            gamma.add(
                    ConceptPatternNode.parse("∃r.B"),
                    ConceptPatternNode.parse(
                            "∃r._X_ ⊓ A ⊓ C ⊓ D"
                    )
            );

            GoalOrientedMatcher matcher =
                    new GoalOrientedMatcher(
                            analyze
                    );

            assertTrue(
                    matcher.match(gamma)
            );

            /*
             * match() must not mark the original input Gamma solved.
             */
            assertFalse(
                    gamma.getAll().get(0).solved
            );
        }
    }

    @Test
    void shouldParseExtendedConceptAndVariableNames() {

        System.out.println(
                ">>> shouldParseExtendedConceptAndVariableNames is running"
        );

        ConceptPatternNode concept =
                ConceptPatternNode.parse("Abc1");

        assertEquals(
                ConceptPatternNode.Type.CONCEPT_NAME,
                concept.type
        );

        assertEquals(
                "Abc1",
                concept.conceptName.name
        );

        ConceptPatternNode variable =
                ConceptPatternNode.parse("_Xsasda1_");

        assertEquals(
                ConceptPatternNode.Type.VARIABLE,
                variable.type
        );

        assertEquals(
                "_Xsasda1_",
                variable.variable.name
        );

        ConceptPatternNode expression =
                ConceptPatternNode.parse(
                        "∃r._Xsasda1_ ⊓ A1 ⊓ Abc1 ⊓ A3"
                );

        assertEquals(
                ConceptPatternNode.Type.CONJUNCTION,
                expression.type
        );

        assertEquals(
                4,
                expression.conjunctions.size()
        );

        System.out.println(
                ">>> extended syntax test passed"
        );
    }

}