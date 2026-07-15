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

    @Test
    void expandsFourConstraintsBeforeProducingOneNormalizedConstraint() {
        List<String> tBox = List.of(
                "∃r.B ⊑ A1",
                "∃r.B ⊑ A2",
                "∃r.B ⊑ A3"
        );

        try (ElkSubsumptionOracle elk = oracle(tBox)) {
            ELAnalyze analyze = analyze(tBox, elk);
            Gamma original = gamma(
                    "∃r.B",
                    "∃r._X_ ⊓ A1 ⊓ A2 ⊓ A3"
            );
            SubsumptionPattern originalPattern = original.getAll().get(0);
            int queriesBefore = elk.getElkQueryCount();
            int axiomsBefore = elk.getAxiomCount();

            GammaNormalizationResult result =
                    new GammaNormalizer(analyze).normalize(original);

            assertTrue(result.isMatchable());
            assertNotSame(original, result.getExpandedGamma());
            assertNotSame(original, result.getNormalizedGamma());

            Gamma expanded = result.getExpandedGamma();
            assertEquals(4, expanded.getAll().size());
            assertContains(expanded, "∃r.B", "∃r._X_");
            assertContains(expanded, "∃r.B", "A1");
            assertContains(expanded, "∃r.B", "A2");
            assertContains(expanded, "∃r.B", "A3");

            Gamma normalized = result.getNormalizedGamma();
            assertEquals(1, normalized.getAll().size());
            assertContains(normalized, "∃r.B", "∃r._X_");
            assertFalse(normalized.getAll().get(0).solved);

            assertEquals(3, result.getGroundChecks().size());
            assertTrue(result.getGroundChecks().stream().allMatch(
                    GammaNormalizationResult.GroundCheckResult::entailed
            ));
            assertEquals(3, elk.getElkQueryCount() - queriesBefore);
            assertEquals(axiomsBefore, elk.getAxiomCount());

            assertEquals(1, original.getAll().size());
            assertEquals(
                    ConceptPatternNode.Type.CONJUNCTION,
                    originalPattern.right.type
            );
            assertFalse(originalPattern.solved);
        }
    }

    @Test
    void failedGroundCheckImmediatelyReturnsNoMatcher() {
        List<String> tBox = List.of(
                "∃r.B ⊑ A1",
                "∃r.B ⊑ A2"
        );

        try (ElkSubsumptionOracle elk = oracle(tBox)) {
            ELAnalyze analyze = analyze(tBox, elk);
            Gamma original = gamma(
                    "∃r.B",
                    "∃r._X_ ⊓ A1 ⊓ A2 ⊓ A3"
            );

            GammaNormalizationResult result =
                    new GammaNormalizer(analyze).normalize(original);

            assertFalse(result.isMatchable());
            assertEquals(4, result.getExpandedGamma().getAll().size());
            assertEquals(3, result.getGroundChecks().size());
            assertFalse(result.getGroundChecks().get(2).entailed());
            assertTrue(result.getFailureReason().contains("∃r.B ⊑ A3"));
            assertEquals(
                    ConceptPatternNode.parse("A3"),
                    result.getFailedSubsumption().right
            );
            assertThrows(
                    IllegalStateException.class,
                    result::getNormalizedGamma
            );

            GoalOrientedMatcher matcher = new GoalOrientedMatcher(analyze);
            assertFalse(matcher.match(original));
            assertEquals(0, matcher.getDfsInvocationCount());
            assertFalse(original.getAll().get(0).solved);
        }
    }

    @Test
    void checksFailingGroundConstraintBeforeAnyEagerRule() {
        List<String> tBox = List.of();

        try (ElkSubsumptionOracle elk = oracle(tBox)) {
            ELAnalyze analyze = analyze(tBox, elk);
            Gamma original = new Gamma();
            original.add(
                    ConceptPatternNode.parse("A1"),
                    ConceptPatternNode.parse("_X_")
            );
            original.add(
                    ConceptPatternNode.parse("∃r.B"),
                    ConceptPatternNode.parse("A3")
            );

            int queriesBefore = elk.getElkQueryCount();
            GoalOrientedMatcher matcher = new GoalOrientedMatcher(analyze);

            assertFalse(matcher.match(original));
            assertEquals(1, elk.getElkQueryCount() - queriesBefore);
            assertEquals(0, matcher.getDfsInvocationCount());
            assertFalse(
                    original.getAll().get(0).solved,
                    "The eager-solvable original constraint must remain untouched."
            );
        }
    }

    @Test
    void constructingMatcherDoesNotQueryElk() {
        List<String> tBox = List.of("A1 ⊑ A2");

        try (ElkSubsumptionOracle elk = oracle(tBox)) {
            ELAnalyze analyze = analyze(tBox, elk);
            int queriesBefore = elk.getElkQueryCount();

            new GoalOrientedMatcher(analyze);

            assertEquals(queriesBefore, elk.getElkQueryCount());
        }
    }

    @Test
    void splitsOnlyTopLevelRightConjunction() {
        List<String> tBox = List.of();

        try (ElkSubsumptionOracle elk = oracle(tBox)) {
            ELAnalyze analyze = analyze(tBox, elk);
            Gamma original = gamma(
                    "∃r.B",
                    "∃r.(_X_ ⊓ A1 ⊓ A2)"
            );

            GammaNormalizationResult result =
                    new GammaNormalizer(analyze).normalize(original);

            assertTrue(result.isMatchable());
            assertEquals(1, result.getExpandedGamma().getAll().size());
            assertEquals(1, result.getNormalizedGamma().getAll().size());

            ConceptPatternNode right =
                    result.getNormalizedGamma().getAll().get(0).right;
            assertEquals(ConceptPatternNode.Type.EXISTENTIAL, right.type);
            assertEquals(
                    ConceptPatternNode.Type.CONJUNCTION,
                    right.existentialFiller.type
            );
            assertEquals(3, right.existentialFiller.conjunctions.size());
            assertEquals(0, result.getGroundChecks().size());
        }
    }

    @Test
    void duplicateExpandedAtomsHaveSetSemanticsAndOneElkQueryEach() {
        List<String> tBox = List.of(
                "∃r.B ⊑ A1",
                "∃r.B ⊑ A2"
        );

        try (ElkSubsumptionOracle elk = oracle(tBox)) {
            ELAnalyze analyze = analyze(tBox, elk);
            Gamma original = gamma(
                    "∃r.B",
                    "∃r._X_ ⊓ A1 ⊓ A1 ⊓ A2"
            );
            int queriesBefore = elk.getElkQueryCount();

            GammaNormalizationResult result =
                    new GammaNormalizer(analyze).normalize(original);

            assertTrue(result.isMatchable());
            assertEquals(3, result.getExpandedGamma().getAll().size());
            assertContains(result.getExpandedGamma(), "∃r.B", "∃r._X_");
            assertContains(result.getExpandedGamma(), "∃r.B", "A1");
            assertContains(result.getExpandedGamma(), "∃r.B", "A2");
            assertEquals(2, result.getGroundChecks().size());
            assertEquals(2, elk.getElkQueryCount() - queriesBefore);
        }
    }

    @Test
    void allTrueGroundConstraintsNormalizeToEmptyGammaAndMatch() {
        List<String> tBox = List.of(
                "∃r.B ⊑ A1",
                "∃r.B ⊑ A2"
        );

        try (ElkSubsumptionOracle elk = oracle(tBox)) {
            ELAnalyze analyze = analyze(tBox, elk);
            Gamma original = gamma("∃r.B", "A1 ⊓ A2");

            GammaNormalizationResult result =
                    new GammaNormalizer(analyze).normalize(original);

            assertTrue(result.isMatchable());
            assertEquals(2, result.getExpandedGamma().getAll().size());
            assertEquals(0, result.getNormalizedGamma().getAll().size());
            assertEquals(2, result.getGroundChecks().size());

            GoalOrientedMatcher matcher = new GoalOrientedMatcher(analyze);
            assertTrue(matcher.match(original));
            assertEquals(1, matcher.getDfsInvocationCount());
        }
    }

    @Test
    void rejectsConstraintWhenBothSidesAreNonGround() {
        List<String> tBox = List.of();

        try (ElkSubsumptionOracle elk = oracle(tBox)) {
            ELAnalyze analyze = analyze(tBox, elk);
            Gamma original = gamma("∃r._X_", "∃s._Y_");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new GammaNormalizer(analyze).normalize(original)
            );

            assertTrue(exception.getMessage().contains(
                    "at least one side must be ground"
            ));
        }
    }

    @Test
    void parsesExtendedConceptAndVariableNamesWithoutAmbiguity() {
        assertConceptName("A1");
        assertConceptName("A2");
        assertConceptName("Abc1");
        assertVariable("_Xs_");
        assertVariable("_Xsasda1_");

        ConceptPatternNode variable =
                ConceptPatternNode.parse("_Xsasda1_");
        assertEquals(ConceptPatternNode.Type.VARIABLE, variable.type);
        assertFalse(variable.type == ConceptPatternNode.Type.CONCEPT_NAME);
    }

    private static ElkSubsumptionOracle oracle(List<String> tBox) {
        return new ElkSubsumptionOracle(tBox, BASE_IRI);
    }

    private static ELAnalyze analyze(
            List<String> tBox,
            ElkSubsumptionOracle elk
    ) {
        ELAnalyze analyze = new ELAnalyze();
        analyze.setTBoxLines(tBox);
        analyze.setSubsumptionOracle(elk);
        return analyze;
    }

    private static Gamma gamma(String left, String right) {
        Gamma gamma = new Gamma();
        gamma.add(
                ConceptPatternNode.parse(left),
                ConceptPatternNode.parse(right)
        );
        return gamma;
    }

    private static void assertContains(
            Gamma gamma,
            String left,
            String right
    ) {
        SubsumptionPattern expected = new SubsumptionPattern(
                ConceptPatternNode.parse(left),
                ConceptPatternNode.parse(right)
        );
        assertTrue(
                gamma.getAll().contains(expected),
                () -> "Missing constraint " + expected + " in " + gamma
        );
    }

    private static void assertConceptName(String input) {
        ConceptPatternNode parsed = ConceptPatternNode.parse(input);
        assertEquals(ConceptPatternNode.Type.CONCEPT_NAME, parsed.type);
        assertEquals(input, parsed.conceptName.name);
    }

    private static void assertVariable(String input) {
        ConceptPatternNode parsed = ConceptPatternNode.parse(input);
        assertEquals(ConceptPatternNode.Type.VARIABLE, parsed.type);
        assertEquals(input, parsed.variable.name);
    }
}
