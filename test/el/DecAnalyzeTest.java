package el;

import el.structure.ConceptPatternNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the responsibility boundary between Gamma normalization
 * and the Dec procedure.
 */
class DecAnalyzeTest {

    private static final String BASE_IRI =
            "http://example.com/dec-test#";

    /**
     * Global normalization must leave:
     *
     * ∃r.B ⊑? ∃r.(_X_ ⊓ A1 ⊓ A2)
     *
     * unchanged.
     *
     * Dec must then:
     *
     * 1. check B ⊑ A1;
     * 2. check B ⊑ A2;
     * 3. return only B ⊑? _X_.
     */
    @Test
    void handlesConjunctionInsideExistentialFiller() {
        List<String> tBox =
                List.of(
                        "B ⊑ A1",
                        "B ⊑ A2"
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

            DecAnalyze dec =
                    new DecAnalyze(
                            analyze
                    );

            ConceptPatternNode left =
                    ConceptPatternNode.parse(
                            "∃r.B"
                    );

            ConceptPatternNode right =
                    ConceptPatternNode.parse(
                            "∃r.(_X_ ⊓ A1 ⊓ A2)"
                    );

            int queriesBefore =
                    elk.getElkQueryCount();

            int axiomsBefore =
                    elk.getAxiomCount();

            DecAnalyze.DecResult result =
                    dec.dec(
                            left,
                            right
                    );

            assertNotNull(
                    result
            );

            assertTrue(
                    result.success
            );

            /*
             * Local ground checks:
             *
             * B ⊑ A1
             * B ⊑ A2
             */
            assertEquals(
                    2,
                    elk.getElkQueryCount()
                            - queriesBefore
            );

            /*
             * Only B ⊑? _X_ remains as a non-ground subgoal.
             */
            assertEquals(
                    1,
                    result.subGoals
                            .size()
            );

            var subGoal =
                    result.subGoals
                            .iterator()
                            .next();

            assertEquals(
                    ConceptPatternNode.parse(
                            "B"
                    ),
                    subGoal.getKey()
            );

            assertEquals(
                    ConceptPatternNode.parse(
                            "_X_"
                    ),
                    subGoal.getValue()
            );

            /*
             * Queries must not modify the ontology.
             */
            assertEquals(
                    axiomsBefore,
                    elk.getAxiomCount()
            );
        }
    }
}