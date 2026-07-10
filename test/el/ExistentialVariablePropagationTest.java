package el;

import el.structure.ConceptPatternNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static el.structure.PatternDSL.concept;
import static el.structure.PatternDSL.some;
import static el.structure.PatternDSL.variable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * Positive TBox:
 *
 *     A ⊑ B
 *
 * Expected result:
 *
 *     true
 */
class ExistentialVariablePropagationTest {

    private static final String BASE_IRI =
            "http://example.com/variable-propagation-test#";

    /**
     * Verifies the syntax categories used by this project:
     *
     * _X_       -> VARIABLE
     * A          -> CONCEPT_NAME
     * ∃r._X_    -> EXISTENTIAL
     * r          -> ROLE_NAME
     * A ⊓ B      -> CONJUNCTION
     *
     * Also verifies structural equality:
     *
     * _X_ equals _X_
     * _X_ does not equal X
     */
    @Test
    void syntaxAndStructuralEqualityShouldWork() {

        ConceptPatternNode parsedVariable =
                ConceptPatternNode.parse("_X_");

        ConceptPatternNode constructedVariable =
                variable("_X_");

        ConceptPatternNode differentVariable =
                variable("_Y_");

        ConceptPatternNode conceptX =
                concept("X");

        ConceptPatternNode parsedConcept =
                ConceptPatternNode.parse("A");

        ConceptPatternNode parsedExistential =
                ConceptPatternNode.parse("∃r._X_");

        ConceptPatternNode parsedConjunction =
                ConceptPatternNode.parse("A ⊓ B");

        /*
         * 1. _X_ must be recognized as a variable.
         */
        assertEquals(
                ConceptPatternNode.Type.VARIABLE,
                parsedVariable.type,
                "_X_ should be recognized as VARIABLE."
        );

        assertNotNull(
                parsedVariable.variable,
                "A VARIABLE node should contain a VariableName."
        );

        assertEquals(
                "_X_",
                parsedVariable.variable.name,
                "The variable name should be _X_."
        );

        /*
         * 2. Two independently created _X_ nodes should be structurally equal.
         */
        assertEquals(
                parsedVariable,
                constructedVariable,
                "Two nodes representing _X_ should be equal."
        );

        assertEquals(
                parsedVariable.hashCode(),
                constructedVariable.hashCode(),
                "Equal variable nodes must have equal hash codes."
        );

        /*
         * 3. _X_ and _Y_ are different variables.
         */
        assertNotEquals(
                parsedVariable,
                differentVariable,
                "_X_ and _Y_ should be different variables."
        );

        /*
         * 4. _X_ and X are different.
         *
         * _X_ is a VARIABLE.
         * X is a CONCEPT_NAME.
         */
        assertNotEquals(
                parsedVariable,
                conceptX,
                "_X_ must not equal concept name X."
        );

        assertEquals(
                ConceptPatternNode.Type.CONCEPT_NAME,
                conceptX.type,
                "X should be recognized as CONCEPT_NAME."
        );

        /*
         * 5. A must be recognized as a concept name.
         */
        assertEquals(
                ConceptPatternNode.Type.CONCEPT_NAME,
                parsedConcept.type,
                "A should be recognized as CONCEPT_NAME."
        );

        assertNotNull(
                parsedConcept.conceptName,
                "A CONCEPT_NAME node should contain a ConceptName."
        );

        assertEquals(
                "A",
                parsedConcept.conceptName.name,
                "The concept name should be A."
        );

        /*
         * 6. ∃r._X_ must be recognized as an existential restriction.
         */
        assertEquals(
                ConceptPatternNode.Type.EXISTENTIAL,
                parsedExistential.type,
                "∃r._X_ should be recognized as EXISTENTIAL."
        );

        assertNotNull(
                parsedExistential.role,
                "An existential restriction should contain a role."
        );

        assertEquals(
                "r",
                parsedExistential.role.name,
                "The role name should be r."
        );

        assertNotNull(
                parsedExistential.existentialFiller,
                "An existential restriction should contain a filler."
        );

        assertEquals(
                ConceptPatternNode.Type.VARIABLE,
                parsedExistential.existentialFiller.type,
                "The filler of ∃r._X_ should be VARIABLE."
        );

        assertEquals(
                "_X_",
                parsedExistential.existentialFiller.variable.name,
                "The existential filler variable should be _X_."
        );

        /*
         * 7. A ⊓ B must be recognized as a conjunction.
         */
        assertEquals(
                ConceptPatternNode.Type.CONJUNCTION,
                parsedConjunction.type,
                "A ⊓ B should be recognized as CONJUNCTION."
        );

        assertNotNull(
                parsedConjunction.conjunctions,
                "A conjunction should contain a list of operands."
        );

        assertEquals(
                2,
                parsedConjunction.conjunctions.size(),
                "A ⊓ B should contain exactly two operands."
        );

        assertEquals(
                concept("A"),
                parsedConjunction.conjunctions.get(0),
                "The first conjunction operand should be A."
        );

        assertEquals(
                concept("B"),
                parsedConjunction.conjunctions.get(1),
                "The second conjunction operand should be B."
        );
    }

    /**
     * Verifies that Dec transforms:
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
            ELAnalyze analyze =
                    new ELAnalyze();

            analyze.setTBoxLines(
                    tBoxLines
            );

            analyze.setSubsumptionOracle(
                    elk
            );

            DecAnalyze dec =
                    new DecAnalyze(
                            analyze
                    );

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
                    dec.dec(
                            left,
                            right
                    );

            assertNotNull(
                    result,
                    "Dec(∃r._X_ ⊑? ∃r.B) should not fail."
            );

            assertTrue(
                    result.success,
                    "Dec should succeed for existential restrictions "
                            + "with the same role."
            );

            assertNotNull(
                    result.subGoals,
                    "A successful Dec result should contain a sub-goal set."
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
                    ConceptPatternNode.Type.VARIABLE,
                    subGoal.getKey().type,
                    "The generated left side should have VARIABLE type."
            );

            assertEquals(
                    "_X_",
                    subGoal.getKey().variable.name,
                    "The generated variable should be _X_."
            );

            assertEquals(
                    variable("_X_"),
                    subGoal.getKey(),
                    "The generated left side should be structurally equal "
                            + "to variable _X_."
            );

            assertEquals(
                    ConceptPatternNode.Type.CONCEPT_NAME,
                    subGoal.getValue().type,
                    "The generated right side should have CONCEPT_NAME type."
            );

            assertEquals(
                    "B",
                    subGoal.getValue().conceptName.name,
                    "The generated concept name should be B."
            );

            assertEquals(
                    concept("B"),
                    subGoal.getValue(),
                    "The generated right side should be structurally equal "
                            + "to concept B."
            );
        }
    }

    /**
     * Positive end-to-end Algorithm 5.1 test.
     *
     * Initial Gamma:
     *
     *     A ⊑? _X_
     *     ∃r._X_ ⊑? ∃r.B
     *
     * Expected execution:
     *
     * 1. Decomposition transforms:
     *
     *        ∃r._X_ ⊑? ∃r.B
     *
     *    into:
     *
     *        _X_ ⊑? B
     *
     * 2. Eager solving connects:
     *
     *        A ⊑? _X_
     *        _X_ ⊑? B
     *
     * 3. ELK checks:
     *
     *        TBox |= A ⊑ B
     *
     * 4. The matching problem succeeds.
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
            ELAnalyze analyze =
                    new ELAnalyze();

            analyze.setTBoxLines(
                    tBoxLines
            );

            analyze.setSubsumptionOracle(
                    elk
            );

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

            int queriesBefore =
                    elk.getElkQueryCount();

            /*
             * ELK must be injected before constructing the matcher.
             */
            GoalOrientedMatcher matcher =
                    new GoalOrientedMatcher(
                            analyze
                    );

            boolean hasMatcher =
                    matcher.match(
                            gamma
                    );

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
                    elk.getElkQueryCount() > queriesBefore,
                    "Algorithm 5.1 should perform at least one ELK query."
            );
        }
    }

    /**
     * Negative end-to-end test.
     *
     * Gamma remains:
     *
     *     A ⊑? _X_
     *     ∃r._X_ ⊑? ∃r.B
     *
     * But the TBox contains only:
     *
     *     C ⊑ B
     *
     * Therefore:
     *
     *     TBox does not entail A ⊑ B
     *
     * and the matching problem must fail.
     */
    @Test
    void matcherShouldFailWhenTBoxDoesNotEntailASubsumedByB() {

        List<String> tBoxLines =
                List.of(
                        "C ⊑ B"
                );

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

            GoalOrientedMatcher matcher =
                    new GoalOrientedMatcher(
                            analyze
                    );

            boolean hasMatcher =
                    matcher.match(
                            gamma
                    );

            assertFalse(
                    hasMatcher,
                    """
                    Matching should fail because:
                    1. Dec generates _X_ ⊑? B;
                    2. Eager solving must check A ⊑ B;
                    3. The TBox contains only C ⊑ B;
                    4. Therefore TBox does not entail A ⊑ B.
                    """
            );

            assertEquals(
                    axiomsBefore,
                    elk.getAxiomCount(),
                    "Gamma constraints must not be added to the ontology."
            );
        }
    }
}