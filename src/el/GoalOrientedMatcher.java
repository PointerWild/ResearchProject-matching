package el;

import el.structure.SubsumptionPattern;
import el.structure.ConceptPatternNode;

import java.util.*;
import java.util.function.BiFunction;
import java.util.AbstractMap.SimpleEntry;

import el.setbasedmutation.SetBasedMutationRule;

/**
 * Implements Algorithm 5.1: goal‐oriented matching by
 * 1) 尽可能地应用 eager 规则
 * 2) 否则对某个未解决的 subsumption 应用 decomposition 或 mutation
 * 循环直到 success 或 failure。
 */
public class GoalOrientedMatcher {

    private final ELAnalyze    elAnalyze;
    private final DecAnalyze   decAnalyze;
    private final EagerSolver  eagerSolver;
    private final GammaNormalizer gammaNormalizer;

    /*
     * 仅用于测试：
     * 验证 normalization 失败时 DFS 没有启动。
     */
    private int dfsInvocationCount;

    // new pre-processing fields
    //Pre-computes all GCIs in a right-hand‐side → left-hand‐side mapping, so we only scan the relevant GCIs for a given D
    //Caches every Dec(c, a) call, so repeated decompositions aren’t recomputed.
    //Produces one Gamma per feasible (A₁…Aₖ ⊑ₜ B) branch, each with its own fresh copy of the subgoals and solved marking.
  //  private final Map<ConceptPatternNode,List<List<ConceptPatternNode>>> gciByRight;
   // private final BiFunction<ConceptPatternNode, ConceptPatternNode,DecAnalyze.DecResult> decFunc;



    public GoalOrientedMatcher(ELAnalyze elAnalyze) {
        this.elAnalyze =
                Objects.requireNonNull(
                        elAnalyze,
                        "elAnalyze cannot be null"
                );

        this.decAnalyze =
                new DecAnalyze(
                        this.elAnalyze
                );

        this.eagerSolver =
                new EagerSolver(
                        this.elAnalyze
                );

        this.gammaNormalizer =
                new GammaNormalizer(
                        this.elAnalyze
                );

        /*delete on 16.july.2026 for test
        // 1) Build the GCI index: for each GCI (A₁…Aₖ ⊑ₜ B), record A‐lists under B
        Map<ConceptPatternNode, List<List<ConceptPatternNode>>> index = new HashMap<>();
        for (var entry : elAnalyze.getTBoxGCIs()) {
            List<ConceptPatternNode> leftAtoms = entry.getKey();
            ConceptPatternNode B        = entry.getValue();
            // only keep those where A₁⊓…⊓Aₖ actually subsumes B
            ConceptPatternNode conjA = ConceptPatternNode.conj(leftAtoms);
            if (elAnalyze.subsumes(conjA, B)) {
                index
                        .computeIfAbsent(B, __ -> new ArrayList<>())
                        .add(leftAtoms);
            }
        }
        this.gciByRight = Collections.unmodifiableMap(index);


        // 2) Prepare a cache for Dec calls: Map<(c,a), DecResult>
        Map<SimpleEntry<ConceptPatternNode, ConceptPatternNode>, DecAnalyze.DecResult> decCache = new HashMap<>();
        this.decFunc = (c, a) -> {
            var key = new SimpleEntry<>(c, a);
            return decCache.computeIfAbsent(key, k -> decAnalyze.dec(c, a));
        };

         */
    }


    /**
     * Normalizes the initial Gamma and then executes Algorithm 5.1.
     *
     * @param originalGamma original, possibly non-normalized matching problem
     * @return true iff the matching problem has a matcher
     */
    public boolean match(
            Gamma originalGamma
    ) {
        Objects.requireNonNull(
                originalGamma,
                "originalGamma cannot be null"
        );

        dfsInvocationCount = 0;
        long startTime = System.nanoTime();
        long hitsBefore = decAnalyze.getCacheHits();
        long missesBefore = decAnalyze.getCacheMisses();
        boolean result;


        GammaNormalizationResult normalization =
                gammaNormalizer.normalize(
                        originalGamma
                );

        /*
         * A false ground-ground constraint was discovered.
         */
        if (!normalization.isMatchable()) {
            result = false;
        }
        else{
            /*
             * DFS receives only normalized constraints.
             *
             * The original Gamma is not modified.
             */
            /*
             * DFS receives only normalized constraints.
             * The original Gamma remains unchanged.
             */
            Gamma normalizedGamma = normalization.getNormalizedGamma();
            result =  dfs(normalizedGamma);

        }

        long endTime = System.nanoTime();
        printBenchmarkStatistics(
                result,
                startTime,
                endTime,
                hitsBefore,
                missesBefore
        );

        return result;
    }

    /**
     * DFS with backtracking.
     * @return true on success, false on any failure path
     */
    private boolean dfs(Gamma gamma) {

        dfsInvocationCount++;

        // —— (1) Eager phase ——
        while (true) {
            try {
                boolean applied = eagerSolver.applyEager(gamma.getAll());
                if (!applied) break;  // no more eager rules applicable
            } catch (FailureException fe) {
                // an eager rule was applicable but failed → prune this branch
                return false;
            }
        }

        // after eager, check if all solved
        SubsumptionPattern next = gamma.nextUnsolved();
        if (next == null) {
            // success: all patterns solved
            return true;
        }

        // —— (2) Non-deterministic choices: try each unsolved pattern in turn ——
        List<SubsumptionPattern> unsolved = gamma.getUnsolved();

        // only get next  unsolved gamma SubsumptionPattern, not a whole loop of SubsumptionPattern sp : unsolved
        //for (SubsumptionPattern sp : unsolved)
        SubsumptionPattern sp = gamma.nextUnsolved();
            // -- Decomposition branch -s- C1 ... Ci--
            for (Gamma gammaDec : DecompositionRule.applyAll(sp, gamma, decAnalyze)) {
                if (dfs(gammaDec)) {
                    return true;
                }
            }

        /*
         * Mutation branches are generated lazily.
         *
         * Each complete branch is immediately sent to dfs().
         * The first successful branch stops the search.
         */

        //boolean mutationSucceeded = MutationRule.tryBranches(sp, gamma, decAnalyze, elAnalyze, this::dfs);

        //try  SetBasedMutationRule
        boolean mutationSucceeded = SetBasedMutationRule.tryBranches( sp, gamma, decAnalyze, elAnalyze, this::dfs);

        if (mutationSucceeded) {
            return true;
        }

        // neither branch succeeded → backtrack failure
        return false;
    }

    /** 辅助：统计当前 Γ 中已被标记为 solved 的数量 */
    private int countSolved(Gamma gamma) {
        return (int) gamma.getAll().stream().filter(p -> p.solved).count();
    }

    /**
     * 仅用于同 package 下的测试。
     *
     * 用于验证 normalization 失败时 DFS 没有启动。
     */
    int getDfsInvocationCount() {
        return dfsInvocationCount;
    }

    /**
     * Prints lightweight benchmark statistics
     * for one complete matching run.
     */
    private void printBenchmarkStatistics(
            boolean result,
            long startTime,
            long endTime,
            long hitsBefore,
            long missesBefore
    ) {

        long cacheHits = decAnalyze.getCacheHits() - hitsBefore;
        long cacheMisses = decAnalyze.getCacheMisses() - missesBefore;
        long decRequests = cacheHits + cacheMisses;
        long actualDecCalculations = cacheMisses;

        double elapsedMs =  (endTime - startTime)  / 1_000_000.0;

        double hitRate;

        if (decRequests == 0) {

            hitRate = 0.0;

        } else {

            hitRate =
                    100.0
                            * cacheHits
                            / decRequests;
        }


        /*
         * Console output.
         */
        System.out.println();

        System.out.println(
                "========== MATCHING BENCHMARK =========="
        );

        System.out.printf(
                "Result          : %s%n",
                result
                        ? "SUCCESS"
                        : "FAILURE"
        );

        System.out.printf(
                "Elapsed time    : %.3f ms%n",
                elapsedMs
        );

        System.out.printf(
                "Dec requests    : %d%n",
                decRequests
        );

        System.out.printf(
                "Actual Dec calc : %d%n",
                actualDecCalculations
        );

        System.out.printf(
                "Cache hits      : %d%n",
                cacheHits
        );

        System.out.printf(
                "Cache misses    : %d%n",
                cacheMisses
        );

        System.out.printf(
                "Cache hit rate  : %.2f%%%n",
                hitRate
        );

        System.out.printf(
                "Cache size      : %d%n",
                decAnalyze.getCacheSize()
        );

        System.out.printf(
                "DFS invocations : %d%n",
                dfsInvocationCount
        );

        System.out.println(
                "========================================"
        );

        System.out.println();


    }
}
