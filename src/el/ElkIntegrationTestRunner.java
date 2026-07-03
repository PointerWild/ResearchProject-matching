package el;

import el.structure.ConceptPatternNode;

import java.util.List;

import static el.structure.PatternDSL.*;

public class ElkIntegrationTestRunner {

    public static void main(String[] args) throws Exception {
        testDirectElkSubsumption();
        testElAnalyzeBridge();
        testDecAnalyzeUsesElk();
        testEagerMatchingUsesElk();
        testMutationMatchingUsesElk();

        System.out.println();
        System.out.println("ALL ELK INTEGRATION TESTS PASSED.");
    }

    private static void testDirectElkSubsumption() {
        System.out.println("\n=== Test 1: Direct ELK Subsumption ===");

        ElkSubsumptionOracle oracle = new ElkSubsumptionOracle(
                List.of(
                        "A ⊑ B",
                        "B ⊑ C"
                ),
                "http://example.com/el#"
        );

        assertTrue(
                oracle.subsumes(
                        ConceptPatternNode.parse("A"),
                        ConceptPatternNode.parse("B")
                ),
                "A ⊑ B should be true by direct TBox axiom"
        );

        assertTrue(
                oracle.subsumes(
                        ConceptPatternNode.parse("A"),
                        ConceptPatternNode.parse("C")
                ),
                "A ⊑ C should be true by ELK transitive reasoning"
        );

        assertTrue(
                oracle.subsumes(
                        ConceptPatternNode.parse("∃r.A"),
                        ConceptPatternNode.parse("∃r.C")
                ),
                "∃r.A ⊑ ∃r.C should be true by existential monotonicity"
        );

        assertFalse(
                oracle.subsumes(
                        ConceptPatternNode.parse("C"),
                        ConceptPatternNode.parse("A")
                ),
                "C ⊑ A should be false"
        );

        assertTrue(
                oracle.getElkQueryCount() >= 4,
                "ELK should have been called at least 4 times"
        );

        oracle.close();

        System.out.println("[PASS] Direct ELK Subsumption");
    }

    private static void testElAnalyzeBridge() {
        System.out.println("\n=== Test 2: ELAnalyze Bridge ===");

        ELAnalyze el = new ELAnalyze();

        ElkSubsumptionOracle oracle = new ElkSubsumptionOracle(
                List.of(
                        "A ⊑ B",
                        "B ⊑ C"
                ),
                "http://example.com/el#"
        );

        el.setSubsumptionOracle(oracle);

        assertTrue(
                el.subsumes(
                        ConceptPatternNode.parse("A"),
                        ConceptPatternNode.parse("C")
                ),
                "ELAnalyze.subsumes(A, C) should go through ELK and return true"
        );

        assertTrue(
                oracle.getElkQueryCount() >= 1,
                "ELAnalyze should have called ELK"
        );

        oracle.close();

        System.out.println("[PASS] ELAnalyze Bridge");
    }

    private static void testDecAnalyzeUsesElk() {
        System.out.println("\n=== Test 3: DecAnalyze Uses ELK ===");

        ELAnalyze el = new ELAnalyze();

        ElkSubsumptionOracle oracle = new ElkSubsumptionOracle(
                List.of(
                        "A ⊑ B",
                        "B ⊑ C"
                ),
                "http://example.com/el#"
        );

        el.setSubsumptionOracle(oracle);

        DecAnalyze dec = new DecAnalyze(el);

        DecAnalyze.DecResult result = dec.dec(
                ConceptPatternNode.parse("A"),
                ConceptPatternNode.parse("C")
        );

        assertTrue(
                result != null && result.success && result.subGoals.isEmpty(),
                "Dec(A ⊑? C) should succeed via ELK because A ⊑T C"
        );

        assertTrue(
                oracle.getElkQueryCount() >= 1,
                "DecAnalyze should have called ELK through ELAnalyze.subsumes"
        );

        oracle.close();

        System.out.println("[PASS] DecAnalyze Uses ELK");
    }

    private static void testEagerMatchingUsesElk() {
        System.out.println("\n=== Test 4: Eager Matching Uses ELK ===");

        ELAnalyze el = new ELAnalyze();

        ElkSubsumptionOracle oracle = new ElkSubsumptionOracle(
                List.of(
                        "A ⊑ B",
                        "B ⊑ C"
                ),
                "http://example.com/el#"
        );

        el.setSubsumptionOracle(oracle);

        GoalOrientedMatcher matcher = new GoalOrientedMatcher(el);

        Gamma gamma = new Gamma();

        gamma.add(
                ConceptPatternNode.parse("A"),
                ConceptPatternNode.parse("_X_")
        );

        gamma.add(
                ConceptPatternNode.parse("_X_"),
                ConceptPatternNode.parse("C")
        );

        boolean result = matcher.match(gamma);

        assertTrue(
                result,
                "Gamma { A ⊑? _X_, _X_ ⊑? C } should be matchable because ELK proves A ⊑T C"
        );

        assertTrue(
                oracle.getElkQueryCount() >= 1,
                "EagerSolver should have called ELK"
        );

        oracle.close();

        System.out.println("[PASS] Eager Matching Uses ELK");
    }

    private static void testMutationMatchingUsesElk() {
        System.out.println("\n=== Test 5: Mutation Matching Uses ELK ===");

        ELAnalyze el = new ELAnalyze();

        el.setTBoxLines(List.of(
                "A ⊑ B",
                "∃r.A ⊓ B ⊑ ∃s.(C ⊓ ∃t.D)"
        ));

        ElkSubsumptionOracle oracle = new ElkSubsumptionOracle(
                el.getTBoxLines(),
                "http://example.com/el#"
        );

        el.setSubsumptionOracle(oracle);

        GoalOrientedMatcher matcher = new GoalOrientedMatcher(el);

        Gamma gamma = new Gamma();

        gamma.add(
                and(
                        some("r", concept("A")),
                        concept("B")
                ),
                some("s",
                        and(
                                concept("C"),
                                some("t", variable("_X_"))
                        )
                )
        );

        boolean result = matcher.match(gamma);

        assertTrue(
                result,
                "Mutation matching should succeed using TBox axiom ∃r.A ⊓ B ⊑ ∃s.(C ⊓ ∃t.D)"
        );

        assertTrue(
                oracle.getElkQueryCount() >= 1,
                "MutationRule should have called ELK"
        );

        oracle.close();

        System.out.println("[PASS] Mutation Matching Uses ELK");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }
}