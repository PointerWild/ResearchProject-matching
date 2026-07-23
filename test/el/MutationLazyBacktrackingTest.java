package el;

import el.structure.ConceptPatternNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MutationLazyBacktrackingTest {

    private static final String BASE_IRI =
            "http://example.com/mutation-lazy-test#";

    /**
     * The first locally successful Ci choice eventually fails.
     *
     * The second locally successful Ci choice succeeds.
     *
     * The matcher must backtrack rather than returning false.
     */
    @Test
    void backtracksAfterFirstLocallySuccessfulChoiceFails() {
        boolean result =
                match(
                        List.of(
                                "∃r.A ⊑ B"
                        ),
                        new String[][]{
                                {
                                        "∃r._X_ ⊓ ∃r.A",
                                        "B"
                                },
                                {
                                        "Z",
                                        "_X_"
                                }
                        }
                );

        assertTrue(
                result,
                """
                Expected the matcher to backtrack:
                1. Choosing ∃r._X_ for ∃r.A produces _X_ ⊑? A,
                   which conflicts with Z ⊑? _X_ because Z ⊑T A is false.
                2. Choosing ∃r.A for ∃r.A succeeds without that subgoal.
                """
        );
    }

    /**
     * Mutation must support k = 0:
     *
     *     Tau ⊑T ∃r.A
     */
    @Test
    void supportsEmptyMutationAntecedent() {
        boolean result =
                match(
                        List.of(
                                "Tau ⊑ ∃r.A"
                        ),
                        new String[][]{
                                {
                                        "Tau",
                                        "∃r._X_"
                                }
                        }
                );

        assertTrue(
                result,
                "Mutation must support k = 0."
        );
    }

    /**
     * A general EL-TBox may have a conjunction on the RHS.
     */
    @Test
    void acceptsConjunctiveTBoxRightSide() {
        boolean result =
                match(
                        List.of(
                                "∃r.A ⊑ B ⊓ C"
                        ),
                        new String[][]{
                                {
                                        "∃r._X_ ⊓ ∃r.A",
                                        "B"
                                }
                        }
                );

        assertTrue(
                result,
                "A conjunctive TBox RHS must be accepted."
        );
    }

    private static boolean match(
            List<String> tBox,
            String[][] constraints
    ) {
        try (
                ElkSubsumptionOracle elk =
                        new ElkSubsumptionOracle(
                                tBox,
                                BASE_IRI
                        )
        ) {
            ELAnalyze analyze =
                    new ELAnalyze(
                            elk
                    );

            analyze.setTBoxLines(
                    tBox
            );

            Gamma gamma =
                    new Gamma();

            for (String[] constraint
                    : constraints) {

                gamma.add(
                        ConceptPatternNode.parse(
                                constraint[0]
                        ),
                        ConceptPatternNode.parse(
                                constraint[1]
                        )
                );
            }

            GoalOrientedMatcher matcher =
                    new GoalOrientedMatcher(
                            analyze
                    );

            return matcher.match(
                    gamma
            );
        }
    }
}