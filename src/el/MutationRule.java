package el;


import el.structure.ConceptPatternNode;
import el.structure.SubsumptionPattern;
import el.structure.ConceptPatternOps;

import java.util.function.Predicate;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;

/**
 * Implements the “Mutation” rule (Figure 2).
 * Optimized Mutation rule  with preprocessed GCI index and Dec‐cache
 */
public class MutationRule {

    /**
     * Build an index from the TBox: for each right‐hand atom B, collect all left‐hand lists A₁…Aₖ
     * such that A₁⊓…⊓Aₖ ⊑ₜ B in the ontology.
     */
    public static Map<ConceptPatternNode, List<List<ConceptPatternNode>>> buildGciIndex(ELAnalyze el) {
        Map<ConceptPatternNode, List<List<ConceptPatternNode>>> index = new HashMap<>();
        for (var entry : el.getTBoxGCIs()) {
            List<ConceptPatternNode> leftAtoms = entry.getKey();
            ConceptPatternNode B = entry.getValue();
            // Only keep those GCIs whose left‐conjunction truly subsumes B
            ConceptPatternNode conjA = conjunctionOf(leftAtoms);
            if (el.subsumes(conjA, B)) {
                index
                        .computeIfAbsent(B, __ -> new ArrayList<>())
                        .add(leftAtoms);
            }
        }
        return index;
    }


    /**
     * Compatibility API used by existing unit tests.
     *
     * It collects every generated branch into a List.
     * Production DFS should use tryBranches(), which evaluates branches lazily.
     */
    public static List<Gamma> applyAll(
            SubsumptionPattern sp,
            Gamma gamma,
            DecAnalyze dec,
            ELAnalyze elAnalyze
    ) {
        List<Gamma> branches =
                new ArrayList<>();

        /*
         * Always return false from the evaluator so tryBranches() continues
         * generating all branches for tests and diagnostics.
         */
        tryBranches(
                sp,
                gamma,
                dec,
                elAnalyze,
                branch -> {
                    branches.add(branch);
                    return false;
                }
        );

        return branches;
    }
    /**
     * Lazily tries Mutation branches.
     *
     * A branch is created only after one complete choice has been made:
     *
     *     A1 -> Ci1
     *     A2 -> Ci2
     *     ...
     *     Ak -> Cik
     *
     * Each complete branch is immediately passed to branchEvaluator.
     *
     * @param sp              unsolved target subsumption
     * @param gamma           current matching problem
     * @param dec             Dec implementation
     * @param elAnalyze       semantic TBox access
     * @param branchEvaluator usually GoalOrientedMatcher::dfs
     * @return true as soon as one complete branch succeeds;
     *         false only when every possible branch fails
     */
    public static boolean tryBranches(
            SubsumptionPattern sp,
            Gamma gamma,
            DecAnalyze dec,
            ELAnalyze elAnalyze,
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
                elAnalyze,
                "elAnalyze cannot be null"
        );

        Objects.requireNonNull(
                branchEvaluator,
                "branchEvaluator cannot be null"
        );

        /*
         * A solved subsumption must not be mutated again.
         */
        if (sp.solved) {
            return false;
        }

        /*
         * Mutation runs only after eager saturation.
         * Therefore neither complete side may itself be a variable.
         */
        if (sp.left.type
                == ConceptPatternNode.Type.VARIABLE
                || sp.right.type
                == ConceptPatternNode.Type.VARIABLE) {

            throw new IllegalStateException(
                    "Mutation must only run after eager rules are saturated: "
                            + sp
            );
        }

        int originalIndex =
                gamma.indexOfIdentity(sp);

        if (originalIndex < 0) {
            throw new IllegalArgumentException(
                    "The supplied subsumption pattern "
                            + "does not belong to Gamma."
            );
        }

        /*
         * s = C1 ⊓ ... ⊓ Cn ⊑? D
         *
         * For n = 1, cis contains one atom.
         * For a Tau left side, cis is empty.
         */
        List<ConceptPatternNode> cis =
                ConceptPatternOps.topLevelAtoms(
                        sp.left
                );

        /*
         * Each candidate represents:
         *
         *     A1 ⊓ ... ⊓ Ak ⊑T B
         */
        for (var candidate
                : elAnalyze.getTBoxGCIs()) {

            List<ConceptPatternNode> as =
                    candidate.getKey();

            ConceptPatternNode b =
                    candidate.getValue();

            /*
             * Verify the semantic premise of Mutation:
             *
             *     A1 ⊓ ... ⊓ Ak ⊑T B
             *
             * conjunctionOf([]) returns Tau and therefore supports k = 0.
             */
            ConceptPatternNode antecedent =
                    conjunctionOf(as);

            if (!elAnalyze.subsumes(
                    antecedent,
                    b
            )) {
                continue;
            }

            /*
             * The final Dec(B ⊑? D) is independent of the choices made for
             * A1, ..., Ak, so compute it once for this candidate.
             */
            DecAnalyze.DecResult finalResult =
                    dec.dec(
                            b,
                            sp.right
                    );

            if (!finalResult.success) {
                continue;
            }

            /*
             * Enumerate the Cartesian product of all successful choices:
             *
             * A1 -> one successful Ci
             * A2 -> one successful Ci
             * ...
             * Ak -> one successful Ci
             *
             * Each completed combination is immediately evaluated.
             */
            boolean succeeded =
                    tryMappings(
                            0,
                            as,
                            cis,
                            dec,
                            gamma,
                            originalIndex,
                            finalResult.subGoals,
                            new ArrayList<>(),
                            branchEvaluator
                    );

            if (succeeded) {
                /*
                 * One complete DFS branch succeeded.
                 * No further Mutation candidates are needed.
                 */
                return true;
            }
        }

        /*
         * Every candidate and every Ci combination failed.
         */
        return false;
    }

    /**
     * Recursively enumerates all successful choices of Ci for each Aη.
     *
     * This is a depth-first Cartesian-product enumeration.
     *
     * It does not store every Gamma branch in memory. Once a complete
     * combination is reached, one fresh Gamma is constructed and evaluated.
     */
    private static boolean tryMappings(
            int aIndex,
            List<ConceptPatternNode> as,
            List<ConceptPatternNode> cis,
            DecAnalyze dec,
            Gamma originalGamma,
            int originalTargetIndex,
            Set<SimpleEntry<
                    ConceptPatternNode,
                    ConceptPatternNode>> finalSubGoals,
            List<SimpleEntry<
                    ConceptPatternNode,
                    ConceptPatternNode>> accumulatedSubGoals,
            Predicate<Gamma> branchEvaluator
    ) {
        /*
         * Base case:
         *
         * Every Aη has now selected one Ci.
         *
         * When as is empty, aIndex == as.size() immediately.
         * This is exactly the k = 0 case.
         */
        if (aIndex == as.size()) {
            Gamma branch =
                    createBranch(
                            originalGamma,
                            originalTargetIndex,
                            accumulatedSubGoals,
                            finalSubGoals
                    );

            /*
             * This is the correct short-circuit position:
             *
             * stop only if the complete recursive DFS branch succeeds.
             */
            return branchEvaluator.test(
                    branch
            );
        }

        ConceptPatternNode currentA =
                as.get(aIndex);

        /*
         * Every successful Dec(Ci ⊑? Aη) is an alternative OR branch.
         */
        for (ConceptPatternNode ci : cis) {
            DecAnalyze.DecResult result =
                    dec.dec(
                            ci,
                            currentA
                    );

            if (!result.success) {
                continue;
            }

            /*
             * Record how many subgoals existed before selecting this Ci.
             */
            int checkpoint =
                    accumulatedSubGoals.size();

            accumulatedSubGoals.addAll(
                    result.subGoals
            );

            /*
             * Continue choosing a Ci for the next Aη.
             */
            boolean succeeded =
                    tryMappings(
                            aIndex + 1,
                            as,
                            cis,
                            dec,
                            originalGamma,
                            originalTargetIndex,
                            finalSubGoals,
                            accumulatedSubGoals,
                            branchEvaluator
                    );

            /*
             * Restore the accumulated list before trying the next Ci.
             *
             * This is the backtracking step.
             */
            while (accumulatedSubGoals.size()
                    > checkpoint) {

                accumulatedSubGoals.remove(
                        accumulatedSubGoals.size() - 1
                );
            }

            if (succeeded) {
                /*
                 * One complete branch succeeded.
                 * Stop enumerating sibling Ci choices.
                 */
                return true;
            }
        }

        /*
         * No Ci choice for currentA led to a complete successful branch.
         */
        return false;
    }

    /**
     * Creates one independent Gamma for one complete Mutation choice.
     */
    private static Gamma createBranch(
            Gamma originalGamma,
            int originalTargetIndex,
            Collection<SimpleEntry<
                    ConceptPatternNode,
                    ConceptPatternNode>> mappingSubGoals,
            Collection<SimpleEntry<
                    ConceptPatternNode,
                    ConceptPatternNode>> finalSubGoals
    ) {
        /*
         * Gamma.copy() creates new SubsumptionPattern objects and preserves
         * their solved flags.
         */
        Gamma branch =
                originalGamma.copy();

        SubsumptionPattern targetCopy =
                branch.getAll()
                        .get(originalTargetIndex);

        /*
         * Add all subgoals generated by Dec(Ci ⊑? Aη).
         */
        for (SimpleEntry<
                ConceptPatternNode,
                ConceptPatternNode> subGoal
                : mappingSubGoals) {

            branch.add(
                    subGoal.getKey(),
                    subGoal.getValue()
            );
        }

        /*
         * Add all subgoals generated by Dec(B ⊑? D).
         */
        for (SimpleEntry<
                ConceptPatternNode,
                ConceptPatternNode> subGoal
                : finalSubGoals) {

            branch.add(
                    subGoal.getKey(),
                    subGoal.getValue()
            );
        }

        /*
         * Mark only the copied target as solved.
         * The original Gamma remains unchanged.
         */
        targetCopy.solved = true;

        return branch;
    }

    /**
     * Constructs the conjunction represented by a list of atoms.
     *
     * Empty list  -> Tau
     * One atom    -> that atom
     * Multiple    -> conjunction
     */
    private static ConceptPatternNode conjunctionOf(
            List<ConceptPatternNode> atoms
    ) {
        Objects.requireNonNull(
                atoms,
                "atoms cannot be null"
        );

        if (atoms.isEmpty()) {
            return ConceptPatternNode.Tau();
        }

        if (atoms.size() == 1) {
            return atoms.get(0);
        }

        return ConceptPatternNode.conj(
                List.copyOf(atoms)
        );
    }



}


