package el;

import el.structure.ConceptPatternNode;
import el.structure.SubsumptionPattern;

import java.util.ArrayList;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Set;

/**
 * Implements the Decomposition rule (Fig.2):
 *   If s = C1 ⊓ … ⊓ Cn ⊑? D is unsolved in Γ,
 *   pick some Ci, call Dec(Ci ⊑? D).
 *   • If Dec fails → rule fails (return false).
 *   • Otherwise add all returned subGoals to Γ and mark s solved.
 */
public class DecompositionRule {

    /**
     * Try to apply Decomposition to sp within gamma using dec.
     *
     * @param sp    the subsumption pattern C1⊓…⊓Cn ⊑? D (must be unsolved)
     * @param gamma the Gamma instance holding all patterns
     * @param dec   the DecAnalyze to compute Dec(Ci⊑?D)
     * @return true if the rule applied successfully (sp marked solved and possibly new patterns added);
     *         false if Dec failed or sp was not a conjunction.
     */
    public static boolean apply(
            SubsumptionPattern sp,
            Gamma gamma,
            DecAnalyze dec) {

        // only apply to unsolved conjunctions
        if (sp.solved
                || sp.left.type != ConceptPatternNode.Type.CONJUNCTION) {
            return false;
        }

        @SuppressWarnings("unchecked")
        List<ConceptPatternNode> conjuncts = sp.left.conjunctions;

        // try each conjunct Ci
        for (ConceptPatternNode Ci : conjuncts) {
            // call Dec(Ci ⊑? D)
            DecAnalyze.DecResult res = dec.dec(Ci, sp.right);
            if (res == null || !res.success) {
                // failure on this Ci → rule fails
                continue;
                //return false;
            }
            // success: add each subGoal to gamma
            Set<SimpleEntry<ConceptPatternNode, ConceptPatternNode>> subGoals = res.subGoals;
            for (SimpleEntry<ConceptPatternNode, ConceptPatternNode> e : subGoals) {
                gamma.add(e.getKey(), e.getValue());
            }
            // mark the original pattern solved
            sp.solved = true;
            return true;
        }

        // should never reach here, but in case no conjuncts
        return false;
    }

    /**
     * Enumerate all feasible decomposition branches:
     * for each Ci where Dec(Ci ⊑? D) succeeds, clone the Gamma and apply that branch.
     */
    public static List<Gamma> applyAll(
            SubsumptionPattern sp,
            Gamma gamma,
            DecAnalyze dec) {
        List<Gamma> branches = new ArrayList<>();
        //check solvable
        if (sp.solved
                || sp.left.type != ConceptPatternNode.Type.CONJUNCTION) {
            return branches;
        }

        // for each ci, check whether   dec(Ci, sp.right)
        List<ConceptPatternNode> conjuncts = sp.left.conjunctions;
        int origIndex = gamma.getAll().indexOf(sp);
        for (ConceptPatternNode Ci : conjuncts) {
            DecAnalyze.DecResult res = dec.dec(Ci, sp.right);
            if (res == null || !res.success) continue;
            // new brunch of every successful Ci brunch
            Gamma copy = gamma.copy();
            SubsumptionPattern spCopy = copy.getAll().get(origIndex);
            // add
            for (var e : res.subGoals) {
                copy.add(e.getKey(), e.getValue());
            }
            spCopy.solved = true;
            branches.add(copy);
        }
        return branches;
    }


}
