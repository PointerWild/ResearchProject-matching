package el;

import el.structure.ConceptPatternNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static el.structure.PatternDSL.concept;
import static el.structure.PatternDSL.some;
import static el.structure.PatternDSL.variable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the matching problem:
 *
 * Gamma = {
 *     A ⊑? _X_,
 *     ∃r._X_ ⊑? ∃r.B
 * }
 *
 * under the TBox:
 *
 *     A ⊑ B
 */
class ExistentialVariablePropagationTest {

    private static final String BASE_IRI =
            "http://example.com/variable-propagation-test#";

    /**
     * First verify that Dec transforms:
     *
     *     ∃r._X_ ⊑? ∃r.B
     *
     * into:
     *
     *     _X_ ⊑? B
     */
    @Test
    void decShouldGenerateVariableSubsumption() {

        List<String> tBoxLines =
                List.of("A ⊑ B");

        try (
                ElkSubsumptionOracle elk =
                        new ElkSubsumptionOracle(
                                tBoxLines,
                                BASE_IRI
                        )
        ) {
            ELAnalyze analyze =
                    new ELAnalyze();

            analyze.setTBoxLines(
                    tBoxLines
            );

            analyze.setSubsumptionOracle(
                    elk
            );

            DecAnalyze dec =
                    new DecAnalyze(analyze);

            ConceptPatternNode left =
                    some(
                            "r",
                            variable("_X_")
                    );

            ConceptPatternNode right =
                    some(
                            "r",
                            concept("B")
                    );

            DecAnalyze.DecResult result =
                    dec.dec(left, right);

            assertNotNull(
                    result,
                    "Dec(∃r._X_ ⊑? ∃r.B) should not fail."
            );

            assertTrue(
                    result.success,
                    "Dec should succeed for identical roles."
            );

            assertEquals(
                    1,
                    result.subGoals.size(),
                    "Dec should generate exactly one sub-goal."
            );

            var subGoal =
                    result.subGoals
                            .iterator()
                            .next();

            assertEquals(
                    variable("_X_"),
                    subGoal.getKey(),
                    "The generated left side should be _X_."
            );

            assertEquals(
                    concept("B"),
                    subGoal.getValue(),
                    "The generated right side should be B."
            );
        }
    }

    /**
     * End-to-end Algorithm 5.1 test.
     *
     * Expected execution:
     *
     * 1. Decompose ∃r._X_ ⊑? ∃r.B into _X_ ⊑? B.
     * 2. Apply the eager rule to:
     *
     *        A ⊑? _X_
     *        _X_ ⊑? B
     *
     * 3. Ask ELK whether TBox entails A ⊑ B.
     * 4. Return true.
     */
    @Test
    void matcherShouldExistForExistentialVariablePropagation() {

        List<String> tBoxLines =
                List.of(
                        "A ⊑ B"
                );

        try (
                ElkSubsumptionOracle elk =
                        new ElkSubsumptionOracle(
                                tBoxLines,
                                BASE_IRI
                        )
        ) {
            /*
             * ELAnalyze stores the same TBox that is loaded into ELK.
             */
            ELAnalyze analyze =
                    new ELAnalyze();

            analyze.setTBoxLines(
                    tBoxLines
            );

            analyze.setSubsumptionOracle(
                    elk
            );

            /*
             * Gamma = {
             *     A ⊑? _X_,
             *     ∃r._X_ ⊑? ∃r.B
             * }
             */
            Gamma gamma =
                    new Gamma();

            gamma.add(
                    concept("A"),
                    variable("_X_")
            );

            gamma.add(
                    some(
                            "r",
                            variable("_X_")
                    ),
                    some(
                            "r",
                            concept("B")
                    )
            );

            int axiomsBefore =
                    elk.getAxiomCount();

            /*
             * The real ELK oracle must be injected before the matcher
             * is constructed.
             */
            GoalOrientedMatcher matcher =
                    new GoalOrientedMatcher(
                            analyze
                    );

            boolean hasMatcher =
                    matcher.match(gamma);

            assertTrue(
                    hasMatcher,
                    """
                    Expected a matcher because:
                    1. Dec transforms ∃r._X_ ⊑? ∃r.B into _X_ ⊑? B;
                    2. Gamma then contains A ⊑? _X_ and _X_ ⊑? B;
                    3. TBox entails A ⊑ B.
                    """
            );

            assertEquals(
                    axiomsBefore,
                    elk.getAxiomCount(),
                    "Gamma constraints must not be added to the ontology."
            );

            assertTrue(
                    elk.getElkQueryCount() > 0,
                    "Algorithm 5.1 should perform at least one ELK query."
            );
        }
    }
}