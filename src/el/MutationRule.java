package el;


import el.structure.ConceptPatternNode;
import el.structure.SubsumptionPattern;
import el.structure.ConceptPatternOps;

import java.util.function.BiFunction;
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
            ConceptPatternNode conjA = ConceptPatternNode.conj(leftAtoms);
            if (el.subsumes(conjA, B)) {
                index
                        .computeIfAbsent(B, __ -> new ArrayList<>())
                        .add(leftAtoms);
            }
        }
        return index;
    }


    /**
     * Enumerate all feasible mutation branches:
     * for each GCI (A₁⊓…⊓Aₖ ⊑ₜ B) where both the decomposition of each Aη and the check B ⊑? D succeed,
     * clone the Gamma and apply that branch.
     */
    public static List<Gamma> applyAll(
            SubsumptionPattern sp,
            Gamma gamma,
            DecAnalyze dec,
            ELAnalyze elAnalyze) {
        //check applable

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

        List<Gamma> branches = new ArrayList<>();
        if (sp.solved) {
            return branches;
        }
        /*
         * Mutation is considered only after eager rules have been
         * saturated. Therefore neither complete side may be a variable.
         */
        if (sp.left.type == ConceptPatternNode.Type.VARIABLE
                || sp.right.type == ConceptPatternNode.Type.VARIABLE) {

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
         * Supports:
         *
         * n = 1:
         * C1 ⊑? D
         *
         * n > 1:
         * C1 ⊓ ... ⊓ Cn ⊑? D
         *
         * Tau becomes the empty atom list.
         */

        List<ConceptPatternNode> cis = ConceptPatternOps.topLevelAtoms(sp.left);


        //Go through all the GCIs in TBox
        for (var gci : elAnalyze.getTBoxGCIs()) {
            var As = gci.getKey();
            var B  = gci.getValue();
            // 1)  A₁⊓…⊓Aₖ ⊑ₜ B
            ConceptPatternNode conjA = ConceptPatternNode.conj(As);
            /*
             * Check:
             *
             * A1 ⊓ ... ⊓ Ak ⊑T B
             */
            ConceptPatternNode conjunction = ConceptPatternNode.conj(As);
            if (!elAnalyze.subsumes(conjunction, B))  continue;


            // 2) for each Aη，at least find one Ci which decompose successfully
            boolean mappingSucceeded = true;
            List<SimpleEntry<ConceptPatternNode, ConceptPatternNode>> allSubGoals = new ArrayList<>();
            for (ConceptPatternNode A : As) {
                boolean found = false;
                for (ConceptPatternNode Ci : cis) {
                    DecAnalyze.DecResult result = dec.dec(Ci, A);
                    if (result.success  ) {
                        allSubGoals.addAll(result.subGoals);
                        found = true;
                        /*
                         * Temporary first-success behavior.
                         *
                         * This break will be removed when all successful
                         * Ci combinations are enumerated.
                         */
                        break;
                    }
                }
                if (!found) {
                    mappingSucceeded = false;
                    break;
                }
            }
            if (!mappingSucceeded) continue;

            // 3) decompose B ⊑? D
            DecAnalyze.DecResult rd = dec.dec(B, sp.right);
            if (!rd.success ) continue;

            // 4)  For that GCI branch, construct a new Gamma
            Gamma copy = gamma.copy();
            SubsumptionPattern spCopy = copy.getAll().get(originalIndex);
            // Add the sub-goals produced by decomposing
            for (SimpleEntry<ConceptPatternNode, ConceptPatternNode> e : allSubGoals) {
                copy.add(e.getKey(), e.getValue());
            }
            // Add the sub-goals generated by decomposing B ⊑? D
            for (SimpleEntry<ConceptPatternNode, ConceptPatternNode> e : rd.subGoals) {
                copy.add(e.getKey(), e.getValue());
            }
            spCopy.solved = true;
            branches.add(copy);
        }
        return branches;
    }

    /**
     * Enumerate all feasible mutation branches for s = C₁⊓…⊓Cₙ ⊑? D in Γ.
     *
     * @param sp          the unsolved conjunction subsumption to mutate
     * @param gamma       the current Gamma
     * @param decFunc     a cached Dec function: Dec(c,a) → DecResult
     * @param gciByRight  precomputed index: B → all left‐atom lists A₁…Aₖ
     * @param elAnalyze   to call subsumes for final check (should always succeed for these GCIs)
     * @return a list of new Gamma branches, one per viable GCI
     */
    public static List<Gamma> applyAll(
            SubsumptionPattern sp,
            Gamma gamma,
            BiFunction<ConceptPatternNode, ConceptPatternNode,DecAnalyze.DecResult> decFunc,
            Map<ConceptPatternNode,List<List<ConceptPatternNode>>> gciByRight,
            ELAnalyze elAnalyze) {

        List<Gamma> branches = new ArrayList<>();
        // Only apply to unsolved conjunction‐left patterns
        if (sp.solved) {
            return branches;
        }

        List<ConceptPatternNode> cis    = ConceptPatternOps.topLevelAtoms(sp.left); // C1…Cn
        ConceptPatternNode D    = sp.right;
        int                        idx  = gamma.indexOfIdentity(sp);

        // Fetch only the GCIs whose right side is D
        var candidateLists = gciByRight.getOrDefault(D, Collections.emptyList());

        for (List<ConceptPatternNode> As : candidateLists) {
            // 1) For each Aη in this list, find some Ci that can decompose
            List<SimpleEntry<ConceptPatternNode, ConceptPatternNode>> allSubGoals = new ArrayList<>();
            boolean mappingOK = true;

            for (ConceptPatternNode A : As) {
                boolean found = false;
                for (ConceptPatternNode C : cis) {
                    DecAnalyze.DecResult r = decFunc.apply(C, A);
                    if (r != null && r.success) {
                        allSubGoals.addAll(r.subGoals);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    mappingOK = false;
                    break;
                }
            }
            if (!mappingOK) {
                // this GCI cannot be applied
                continue;
            }

            // 2) Finally decompose B ⊑? D (usually trivial since B == D if indexed correctly)
            DecAnalyze.DecResult finalRes = decFunc.apply(D, D);
            if (finalRes == null || !finalRes.success) {
                // should rarely happen—but if so, skip
                continue;
            }
            allSubGoals.addAll(finalRes.subGoals);

            // 3) Build a fresh Gamma branch
            Gamma copy = gamma.copy();
            SubsumptionPattern spCopy = copy.getAll().get(idx);
            // add every generated sub‐goal
            for (var e : allSubGoals) {
                copy.add(e.getKey(), e.getValue());
            }
            spCopy.solved = true;
            branches.add(copy);
        }

        return branches;
    }


}
