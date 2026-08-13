package el;

import el.structure.ConceptPatternNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests memoization of Dec(C ⊑? D).
 *
 * These tests verify that:
 *
 * 1. the first Dec call is a cache miss;
 * 2. the same Dec call is subsequently a cache hit;
 * 3. failed Dec results are cached as well;
 * 4. structurally equal ConceptPatternNode objects use the same cache entry;
 * 5. clearing the cache removes all stored results and statistics.
 */
class DecCacheTest {

    private static final String BASE_IRI =
            "http://example.com/dec-cache-test#";


    /**
     * Test 1:
     *
     * First call:
     *
     *     Dec(∃r.B ⊑? ∃r.(_X_ ⊓ A1 ⊓ A2))
     *
     * must be a cache miss.
     *
     * The second identical call must be a cache hit and must
     * not execute the two ELK subsumption queries again.
     */
    @Test
    void repeatedSuccessfulDecUsesCache() {

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

            /*
             * Initial cache state.
             */
            assertEquals(
                    0,
                    dec.getCacheHits()
            );

            assertEquals(
                    0,
                    dec.getCacheMisses()
            );

            assertEquals(
                    0,
                    dec.getCacheSize()
            );

            int queriesBefore =
                    elk.getElkQueryCount();


            /*
             * First Dec call:
             *
             * CACHE MISS
             */
            DecAnalyze.DecResult first =
                    dec.dec(
                            left,
                            right
                    );

            assertTrue(
                    first.success
            );

            assertEquals(
                    0,
                    dec.getCacheHits()
            );

            assertEquals(
                    1,
                    dec.getCacheMisses()
            );

            assertEquals(
                    1,
                    dec.getCacheSize()
            );

            /*
             * Dec Case 2 checks:
             *
             * B ⊑T A1
             * B ⊑T A2
             *
             * Therefore two ELK queries should have been executed.
             */
            assertEquals(
                    2,
                    elk.getElkQueryCount()
                            - queriesBefore
            );

            int queriesAfterFirst =
                    elk.getElkQueryCount();


            /*
             * Second identical Dec call:
             *
             * CACHE HIT
             */
            DecAnalyze.DecResult second =
                    dec.dec(
                            left,
                            right
                    );

            assertTrue(
                    second.success
            );

            assertEquals(
                    1,
                    dec.getCacheHits()
            );

            assertEquals(
                    1,
                    dec.getCacheMisses()
            );

            assertEquals(
                    1,
                    dec.getCacheSize()
            );

            /*
             * Most important assertion:
             *
             * no new ELK query was executed.
             */
            assertEquals(
                    queriesAfterFirst,
                    elk.getElkQueryCount()
            );

            /*
             * The cached DecResult object itself is reused.
             */
            assertSame(
                    first,
                    second
            );
        }
    }


    /**
     * Test 2:
     *
     * Failed Dec results must also be cached.
     *
     * A ⊑? B is not entailed by an empty TBox.
     *
     * First call:
     *     MISS -> ELK query -> failure
     *
     * Second call:
     *     HIT -> same failure result
     */
    @Test
    void repeatedFailedDecUsesCache() {

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

            DecAnalyze dec =
                    new DecAnalyze(
                            analyze
                    );

            ConceptPatternNode left =
                    ConceptPatternNode.parse(
                            "A"
                    );

            ConceptPatternNode right =
                    ConceptPatternNode.parse(
                            "B"
                    );

            /*
             * First call:
             *
             * Cache miss.
             */
            DecAnalyze.DecResult first =
                    dec.dec(
                            left,
                            right
                    );

            assertFalse(
                    first.success
            );

            assertEquals(
                    1,
                    dec.getCacheMisses()
            );

            assertEquals(
                    0,
                    dec.getCacheHits()
            );

            assertEquals(
                    1,
                    dec.getCacheSize()
            );

            int queriesAfterFirst =
                    elk.getElkQueryCount();


            /*
             * Second call:
             *
             * The FAILURE itself must come from cache.
             */
            DecAnalyze.DecResult second =
                    dec.dec(
                            left,
                            right
                    );

            assertFalse(
                    second.success
            );

            assertEquals(
                    1,
                    dec.getCacheMisses()
            );

            assertEquals(
                    1,
                    dec.getCacheHits()
            );

            assertEquals(
                    1,
                    dec.getCacheSize()
            );

            /*
             * No second semantic query.
             */
            assertEquals(
                    queriesAfterFirst,
                    elk.getElkQueryCount()
            );

            assertSame(
                    first,
                    second
            );
        }
    }


    /**
     * Test 3:
     *
     * The cache must use structural equality,
     * not Java object identity.
     *
     * left1 and left2 are different Java objects,
     * but represent the same EL expression.
     *
     * The same holds for right1 and right2.
     */
    @Test
    void structurallyEqualNodesUseSameCacheEntry() {

        List<String> tBox =
                List.of(
                        "B ⊑ A"
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


            /*
             * First pair.
             */
            ConceptPatternNode left1 =
                    ConceptPatternNode.parse(
                            "B"
                    );

            ConceptPatternNode right1 =
                    ConceptPatternNode.parse(
                            "A"
                    );


            /*
             * Second pair:
             *
             * newly parsed -> different Java objects.
             */
            ConceptPatternNode left2 =
                    ConceptPatternNode.parse(
                            "B"
                    );

            ConceptPatternNode right2 =
                    ConceptPatternNode.parse(
                            "A"
                    );


            /*
             * Verify that these really are different objects.
             */
            assertNotSame(
                    left1,
                    left2
            );

            assertNotSame(
                    right1,
                    right2
            );


            /*
             * But structurally they are equal.
             */
            assertEquals(
                    left1,
                    left2
            );

            assertEquals(
                    right1,
                    right2
            );


            /*
             * First pair:
             *
             * MISS.
             */
            DecAnalyze.DecResult first =
                    dec.dec(
                            left1,
                            right1
                    );

            assertTrue(
                    first.success
            );

            assertEquals(
                    1,
                    dec.getCacheMisses()
            );

            assertEquals(
                    0,
                    dec.getCacheHits()
            );

            int queriesAfterFirst =
                    elk.getElkQueryCount();


            /*
             * Different objects, same structure:
             *
             * must still HIT the same cache entry.
             */
            DecAnalyze.DecResult second =
                    dec.dec(
                            left2,
                            right2
                    );

            assertTrue(
                    second.success
            );

            assertEquals(
                    1,
                    dec.getCacheMisses()
            );

            assertEquals(
                    1,
                    dec.getCacheHits()
            );

            /*
             * Still only one key:
             *
             * (B, A)
             */
            assertEquals(
                    1,
                    dec.getCacheSize()
            );

            /*
             * No additional ELK reasoning.
             */
            assertEquals(
                    queriesAfterFirst,
                    elk.getElkQueryCount()
            );

            assertSame(
                    first,
                    second
            );
        }
    }


    /**
     * Test 4:
     *
     * clearCache() must remove all cached Dec results
     * and reset cache statistics.
     */
    @Test
    void clearCacheRemovesStoredResults() {

        List<String> tBox =
                List.of(
                        "B ⊑ A"
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
                            "B"
                    );

            ConceptPatternNode right =
                    ConceptPatternNode.parse(
                            "A"
                    );


            /*
             * MISS
             */
            dec.dec(
                    left,
                    right
            );

            /*
             * HIT
             */
            dec.dec(
                    left,
                    right
            );


            assertEquals(
                    1,
                    dec.getCacheMisses()
            );

            assertEquals(
                    1,
                    dec.getCacheHits()
            );

            assertEquals(
                    1,
                    dec.getCacheSize()
            );


            /*
             * Clear everything.
             */
            dec.clearCache();


            assertEquals(
                    0,
                    dec.getCacheMisses()
            );

            assertEquals(
                    0,
                    dec.getCacheHits()
            );

            assertEquals(
                    0,
                    dec.getCacheSize()
            );


            /*
             * Calling Dec again must now be another MISS.
             */
            dec.dec(
                    left,
                    right
            );

            assertEquals(
                    1,
                    dec.getCacheMisses()
            );

            assertEquals(
                    0,
                    dec.getCacheHits()
            );

            assertEquals(
                    1,
                    dec.getCacheSize()
            );
        }
    }
}