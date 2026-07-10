package el;

import el.structure.SubsumptionPattern;
import el.structure.ConceptPatternNode;

import java.util.*;
import java.util.function.BiFunction;
import java.util.AbstractMap.SimpleEntry;

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

    // new pre-processing fields
    //Pre-computes all GCIs in a right-hand‐side → left-hand‐side mapping, so we only scan the relevant GCIs for a given D
    //Caches every Dec(c, a) call, so repeated decompositions aren’t recomputed.
    //Produces one Gamma per feasible (A₁…Aₖ ⊑ₜ B) branch, each with its own fresh copy of the subgoals and solved marking.
    private final Map<ConceptPatternNode,List<List<ConceptPatternNode>>> gciByRight;
    private final BiFunction<ConceptPatternNode, ConceptPatternNode,DecAnalyze.DecResult> decFunc;


    public GoalOrientedMatcher(ELAnalyze elAnalyze) {
        this.elAnalyze = elAnalyze;
        this.decAnalyze = new DecAnalyze(elAnalyze);
        this.eagerSolver = new EagerSolver(elAnalyze);

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
    }


    /**
     * Normalizes the initial Gamma and then executes Algorithm 5.1.
     *
     * @param gamma original, possibly non-normalized matching problem
     * @return true iff the matching problem has a matcher
     */
    public boolean match(
            Gamma gamma
    ) {
        Objects.requireNonNull(
                gamma,
                "gamma cannot be null"
        );

        GammaNormalizer normalizer =
                new GammaNormalizer(
                        elAnalyze
                );

        GammaNormalizationResult normalization =
                normalizer.normalize(
                        gamma
                );

        /*
         * A false ground-ground constraint was discovered.
         */
        if (!normalization.isMatchable()) {
            return false;
        }

        /*
         * DFS receives only normalized constraints.
         *
         * The original Gamma is not modified.
         */
        return dfs(
                normalization.getNormalizedGamma()
        );
    }

    /**
     * DFS with backtracking.
     * @return true on success, false on any failure path
     */
    private boolean dfs(Gamma gamma) {
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

            // -- Mutation branch --
//            for (Gamma gammaMut : MutationRule.applyAll(sp, gamma, decFunc, gciByRight, elAnalyze)){
//                if (dfs(gammaMut)) {
//                    return true;
//                }
//            };
            for (Gamma gammaMut : MutationRule.applyAll(sp, gamma, decAnalyze, elAnalyze)) {
                if (dfs(gammaMut)) {
                    return true;
                }
            }

        // neither branch succeeded → backtrack failure
        return false;
    }

    /** 辅助：统计当前 Γ 中已被标记为 solved 的数量 */
    private int countSolved(Gamma gamma) {
        return (int) gamma.getAll().stream().filter(p -> p.solved).count();
    }
}
