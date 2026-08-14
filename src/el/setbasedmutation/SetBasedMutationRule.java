package el.setbasedmutation;

import el.DecAnalyze;
import el.ELAnalyze;
import el.Gamma;

import el.structure.ConceptPatternNode;
import el.structure.ConceptPatternOps;
import el.structure.SubsumptionPattern;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;


/**
 * =====================================================================
 * Set-Based Mutation Rule
 * =====================================================================
 *
 *
 * Original Mutation target:
 *
 *     C1 ⊓ ... ⊓ Cn ⊑? D
 *
 *
 * Original Mutation chooses:
 *
 *     A1, ..., Ak, B
 *
 * such that:
 *
 *     A1 ⊓ ... ⊓ Ak ⊑T B
 *
 *
 * ---------------------------------------------------------------------
 * Main optimization idea
 * ---------------------------------------------------------------------
 *
 * Instead of treating:
 *
 *     A1, A2, ..., Ak
 *
 * as an ordered mapping sequence,
 *
 * use:
 *
 *     P = {A1, A2, ..., Ak}
 *
 * as a SET.
 *
 *
 * The generated Dec constraints are also represented as SETS:
 *
 *     Delta =
 *
 *     {
 *         X ⊑? A,
 *         Y ⊑? B,
 *         ...
 *     }
 *
 *
 * =====================================================================
 * Complete flow
 * =====================================================================
 *
 *
 *     Target
 *
 *     C1 ⊓ ... ⊓ Cn ⊑? D
 *
 *             |
 *             v
 *
 *        TBox Atom Set
 *
 *             |
 *             v
 *
 *       compute Support(A)
 *
 *             |
 *             v
 *
 *     remove unsupported atoms
 *
 *             |
 *             v
 *
 *       Supported Atom Set
 *
 *             |
 *             v
 *
 *            choose B
 *
 *             |
 *             v
 *
 *          Dec(B,D)
 *
 *         /           \
 *    failure         success
 *      |                |
 *      X                v
 *
 *                  search P:
 *
 *               P ⊆ SupportedAtoms
 *
 *                     |
 *                     v
 *
 *               conjunction(P)
 *                     ⊑T
 *                      B
 *
 *                     |
 *                     v
 *
 *              Minimal Premise Sets
 *
 *                     |
 *                     v
 *
 *            combine Support(A1),
 *                    Support(A2),
 *                    ...
 *
 *                     |
 *                     v
 *
 *               Delta Sets
 *
 *          Delta1, Delta2, ...
 *
 *                     |
 *                     v
 *
 *              Set minimization
 *
 *
 *       Delta1 = Delta2
 *
 *              -> remove duplicate
 *
 *
 *       Delta1 ⊂ Delta2
 *
 *              -> remove Delta2
 *
 *
 *                     |
 *                     v
 *
 *            Minimal Delta Family
 *
 *                     |
 *                     v
 *
 *         one Delta = one Gamma branch
 *
 *                     |
 *                     v
 *
 *                    DFS
 *
 * =====================================================================
 */
public final class SetBasedMutationRule {

    private SetBasedMutationRule() {
    }


    /**
     * Main entry point.
     */
    public static boolean tryBranches(
            SubsumptionPattern sp,
            Gamma gamma,
            DecAnalyze dec,
            ELAnalyze analyze,
            Predicate<Gamma> branchEvaluator
    ) {

        Objects.requireNonNull(
                sp,
                "sp cannot be null"
        );

        Objects.requireNonNull(
                gamma,
                "gamma cannot be null"
        );

        Objects.requireNonNull(
                dec,
                "dec cannot be null"
        );

        Objects.requireNonNull(
                analyze,
                "analyze cannot be null"
        );

        Objects.requireNonNull(
                branchEvaluator,
                "branchEvaluator cannot be null"
        );


        /*
         * A solved target must not be mutated again.
         */
        if (sp.solved) {
            return false;
        }


        /*
         * Locate the target inside Gamma.
         *
         * Gamma.copy() later preserves the same list order.
         */
        int originalTargetIndex =
                gamma.indexOfIdentity(
                        sp
                );


        if (originalTargetIndex < 0) {

            throw new IllegalArgumentException(
                    "Target does not belong to Gamma."
            );
        }


        /*
         * =========================================================
         * Step 1
         *
         * Target:
         *
         *     C1 ⊓ ... ⊓ Cn ⊑? D
         *
         * Extract:
         *
         *     C1,...,Cn
         * =========================================================
         */
        List<ConceptPatternNode> cis =
                ConceptPatternOps.topLevelAtoms(
                        sp.left
                );


        /*
         * =========================================================
         * Step 2
         *
         * Build complete TBox Atom Set.
         * =========================================================
         */
        List<ConceptPatternNode> tBoxAtoms =
                TBoxAtomSetBuilder.build(
                        analyze
                );


        /*
         * =========================================================
         * Step 3
         *
         * Build Support(A).
         *
         *
         * Support(A)
         *
         *      =
         *
         * {
         *     Delta produced by Dec(Ci,A)
         *     |
         *     Dec(Ci,A) succeeds
         * }
         *
         *
         * Notice:
         *
         * We store Delta Sets directly instead of storing Ci.
         *
         * Therefore:
         *
         * different Ci
         *      |
         *      v
         * same Delta
         *
         * can be merged immediately.
         * =========================================================
         */
        Map<
                ConceptPatternNode,
                List<Set<MutationConstraintKey>>>
                support =
                buildSupportMap(
                        tBoxAtoms,
                        cis,
                        dec
                );


        /*
         * Atoms without support have already been removed.
         */
        List<ConceptPatternNode> supportedAtoms =
                new ArrayList<>(
                        support.keySet()
                );


        /*
         * Fail-first ordering.
         *
         * Atoms having fewer alternatives are processed first.
         */
        supportedAtoms.sort(
                Comparator.comparingInt(
                        atom ->
                                support
                                        .get(atom)
                                        .size()
                )
        );


        /*
         * =========================================================
         * All complete Mutation branches are stored as Sets.
         *
         * The family keeps only inclusion-minimal Sets.
         * =========================================================
         */
        MinimalConstraintSetFamily completeBranches =
                new MinimalConstraintSetFamily();


        /*
         * =========================================================
         * Step 4
         *
         * Choose B from TBox atoms.
         * =========================================================
         */
        for (ConceptPatternNode b
                : tBoxAtoms) {


            /*
             * Mutation finally needs:
             *
             *     Dec(B,D)
             *
             * This is independent of the choices for A1,...,Ak.
             */
            DecAnalyze.DecResult finalResult =
                    dec.dec(
                            b,
                            sp.right
                    );


            /*
             * B cannot be used for this target.
             */
            if (!finalResult.success) {
                continue;
            }


            Set<MutationConstraintKey> finalDelta =
                    toConstraintSet(
                            finalResult.subGoals
                    );


            /*
             * =====================================================
             * Step 5
             *
             * Find premise Sets:
             *
             *     P ⊆ SupportedAtoms
             *
             * such that:
             *
             *     conjunction(P) ⊑T B
             *
             *
             * Only inclusion-minimal premise Sets are returned.
             * =====================================================
             */
            List<Set<ConceptPatternNode>> premiseSets =
                    findMinimalPremiseSets(
                            supportedAtoms,
                            b,
                            analyze
                    );


            /*
             * =====================================================
             * Step 6
             *
             * Convert each premise Set into constraint Delta Sets.
             * =====================================================
             */
            for (Set<ConceptPatternNode> premises
                    : premiseSets) {


                List<Set<MutationConstraintKey>> mappingDeltas =
                        combinePremiseAlternatives(
                                premises,
                                support
                        );


                /*
                 * Add constraints generated by:
                 *
                 *     Dec(B,D)
                 */
                for (Set<MutationConstraintKey> mappingDelta
                        : mappingDeltas) {


                    LinkedHashSet<MutationConstraintKey> complete =
                            new LinkedHashSet<>(
                                    mappingDelta
                            );


                    complete.addAll(
                            finalDelta
                    );


                    /*
                     * Duplicate elimination +
                     * subset pruning happens here.
                     */
                    completeBranches.add(
                            complete
                    );
                }
            }
        }


        /*
         * =========================================================
         * Step 7
         *
         * One minimal Delta Set
         *
         *          =
         *
         * one actual Gamma branch.
         * =========================================================
         */
        for (Set<MutationConstraintKey> delta
                : completeBranches.values()) {


            Gamma branch =
                    createBranch(
                            gamma,
                            originalTargetIndex,
                            delta
                    );


            if (branchEvaluator.test(
                    branch
            )) {

                return true;
            }
        }


        return false;
    }


    /**
     * =============================================================
     * Build Support Map
     * =============================================================
     *
     * For every TBox atom A:
     *
     *     C1 ----Dec----> Delta1
     *     C2 ----Dec----> Delta2
     *     ...
     *     Cn ----Dec----> Deltan
     *
     *
     * Keep only successful Deltas.
     *
     * Then minimize:
     *
     *     duplicate Delta
     *
     *     +
     *
     *     dominated Delta
     *
     * =============================================================
     */
    private static Map<
            ConceptPatternNode,
            List<Set<MutationConstraintKey>>>
    buildSupportMap(
            List<ConceptPatternNode> tBoxAtoms,
            List<ConceptPatternNode> cis,
            DecAnalyze dec
    ) {

        Map<
                ConceptPatternNode,
                List<Set<MutationConstraintKey>>>
                support =
                new LinkedHashMap<>();


        for (ConceptPatternNode atom
                : tBoxAtoms) {


            MinimalConstraintSetFamily alternatives =
                    new MinimalConstraintSetFamily();


            for (ConceptPatternNode ci
                    : cis) {


                DecAnalyze.DecResult result =
                        dec.dec(
                                ci,
                                atom
                        );


                if (!result.success) {
                    continue;
                }


                alternatives.add(
                        toConstraintSet(
                                result.subGoals
                        )
                );
            }


            /*
             * Support(A) = empty
             *
             * means:
             *
             * no Ci can support A.
             *
             * Therefore A cannot participate
             * in the current Mutation premise Set.
             */
            if (!alternatives.isEmpty()) {


                support.put(
                        atom,
                        alternatives.values()
                );
            }
        }


        return support;
    }


    /**
     * =============================================================
     * Find minimal premise Sets
     * =============================================================
     *
     * Search:
     *
     *     P ⊆ SupportedAtoms
     *
     * such that:
     *
     *     conjunction(P) ⊑T B
     *
     * =============================================================
     */
    private static List<Set<ConceptPatternNode>>
    findMinimalPremiseSets(
            List<ConceptPatternNode> atoms,
            ConceptPatternNode b,
            ELAnalyze analyze
    ) {

        List<Set<ConceptPatternNode>> result =
                new ArrayList<>();


        searchPremiseSets(
                atoms,
                b,
                analyze,
                0,
                new LinkedHashSet<>(),
                result
        );


        return result;
    }


    /**
     * =============================================================
     * DFS over the subset lattice
     * =============================================================
     *
     *
     *                    {}
     *
     *           /         |         \
     *
     *        {A}         {B}        {C}
     *
     *       /   \
     *
     *   {A,B}   {A,C}
     *
     *
     * -------------------------------------------------------------
     * Pruning 1
     * -------------------------------------------------------------
     *
     * If:
     *
     *     conjunction(P) ⊑T B
     *
     * then P is sufficient.
     *
     * Keep P and do not visit its supersets.
     *
     *
     * -------------------------------------------------------------
     * Pruning 2
     * -------------------------------------------------------------
     *
     * Suppose current Set is P.
     *
     * Let:
     *
     *     Pmax = P ∪ Remaining
     *
     *
     * If even:
     *
     *     conjunction(Pmax) ⊑T B
     *
     * is false,
     *
     * then no descendant of P can succeed.
     *
     * Therefore prune the entire subtree.
     *
     * =============================================================
     */
    private static void searchPremiseSets(
            List<ConceptPatternNode> atoms,
            ConceptPatternNode b,
            ELAnalyze analyze,
            int start,
            LinkedHashSet<ConceptPatternNode> current,
            List<Set<ConceptPatternNode>> result
    ) {


        /*
         * Current premise Set already works.
         */
        if (analyze.subsumes(
                conjunctionOf(
                        current
                ),
                b
        )) {


            result.add(
                    Set.copyOf(
                            current
                    )
            );


            /*
             * Do not search supersets.
             */
            return;
        }


        if (start >= atoms.size()) {
            return;
        }


        /*
         * =========================================================
         * Maximum-extension pruning.
         *
         * Construct:
         *
         *     current ∪ remaining atoms
         * =========================================================
         */
        LinkedHashSet<ConceptPatternNode> maximal =
                new LinkedHashSet<>(
                        current
                );


        for (int i = start;
             i < atoms.size();
             i++) {


            maximal.add(
                    atoms.get(i)
            );
        }


        /*
         * If even the maximum extension fails,
         * the whole subtree fails.
         */
        if (!analyze.subsumes(
                conjunctionOf(
                        maximal
                ),
                b
        )) {


            return;
        }


        /*
         * =========================================================
         * Expand Set:
         *
         *     P
         *
         *       ↓
         *
         *     P ∪ {Ai}
         * =========================================================
         */
        for (int i = start;
             i < atoms.size();
             i++) {


            ConceptPatternNode atom =
                    atoms.get(i);


            current.add(
                    atom
            );


            searchPremiseSets(
                    atoms,
                    b,
                    analyze,
                    i + 1,
                    current,
                    result
            );


            /*
             * Backtracking.
             */
            current.remove(
                    atom
            );
        }
    }


    /**
     * =============================================================
     * Combine support alternatives
     * =============================================================
     *
     *
     * Example:
     *
     * Alternatives(A1):
     *
     *     {Delta11, Delta12}
     *
     *
     * Alternatives(A2):
     *
     *     {Delta21, Delta22}
     *
     *
     * Then:
     *
     *     Delta11 ∪ Delta21
     *     Delta11 ∪ Delta22
     *     Delta12 ∪ Delta21
     *     Delta12 ∪ Delta22
     *
     *
     * But after EACH stage we minimize the Set family.
     *
     * =============================================================
     */
    private static List<Set<MutationConstraintKey>>
    combinePremiseAlternatives(
            Set<ConceptPatternNode> premises,
            Map<
                    ConceptPatternNode,
                    List<Set<MutationConstraintKey>>>
                    support
    ) {


        /*
         * Initial family:
         *
         *     { empty Set }
         */
        List<Set<MutationConstraintKey>> family =
                List.of(
                        Set.of()
                );


        for (ConceptPatternNode premise
                : premises) {


            List<Set<MutationConstraintKey>> alternatives =
                    support.get(
                            premise
                    );


            if (alternatives == null
                    || alternatives.isEmpty()) {


                return List.of();
            }


            MinimalConstraintSetFamily next =
                    new MinimalConstraintSetFamily();


            /*
             * Set-family product using union.
             */
            for (Set<MutationConstraintKey> current
                    : family) {


                for (Set<MutationConstraintKey> alternative
                        : alternatives) {


                    LinkedHashSet<MutationConstraintKey> union =
                            new LinkedHashSet<>(
                                    current
                            );


                    union.addAll(
                            alternative
                    );


                    /*
                     * Minimize immediately.
                     */
                    next.add(
                            union
                    );
                }
            }


            family =
                    next.values();
        }


        return family;
    }


    /**
     * Convert Dec-generated subgoals into a Set.
     */
    private static Set<MutationConstraintKey>
    toConstraintSet(
            Collection<? extends Map.Entry<
                    ConceptPatternNode,
                    ConceptPatternNode>>
                    subGoals
    ) {


        LinkedHashSet<MutationConstraintKey> result =
                new LinkedHashSet<>();


        for (Map.Entry<
                ConceptPatternNode,
                ConceptPatternNode> subGoal
                : subGoals) {


            result.add(
                    MutationConstraintKey.from(
                            subGoal
                    )
            );
        }


        return Set.copyOf(
                result
        );
    }


    /**
     * =============================================================
     * Create one Gamma branch
     * =============================================================
     *
     * One:
     *
     *     Set<MutationConstraintKey>
     *
     * corresponds to:
     *
     *     one Gamma branch.
     *
     * =============================================================
     */
    private static Gamma createBranch(
            Gamma original,
            int targetIndex,
            Set<MutationConstraintKey> delta
    ) {


        Gamma branch =
                original.copy();


        SubsumptionPattern target =
                branch
                        .getAll()
                        .get(
                                targetIndex
                        );


        /*
         * Add all constraints from this Set.
         */
        for (MutationConstraintKey constraint
                : delta) {


            branch.add(
                    constraint.left(),
                    constraint.right()
            );
        }


        /*
         * The Mutation target is solved.
         */
        target.solved =
                true;


        return branch;
    }


    /**
     * =============================================================
     * Convert Atom Set -> conjunction
     * =============================================================
     *
     *     {}
     *
     *       ->
     *
     *     Tau
     *
     *
     *     {A}
     *
     *       ->
     *
     *     A
     *
     *
     *     {A,B}
     *
     *       ->
     *
     *     A ⊓ B
     *
     * =============================================================
     */
    private static ConceptPatternNode conjunctionOf(
            Collection<ConceptPatternNode> atoms
    ) {


        if (atoms.isEmpty()) {


            return ConceptPatternNode.Tau();
        }


        if (atoms.size() == 1) {


            return atoms
                    .iterator()
                    .next();
        }


        return ConceptPatternNode.conj(
                List.copyOf(
                        atoms
                )
        );
    }
}