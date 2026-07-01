package el;

import el.structure.ConceptPatternNode;
import el.structure.SubsumptionPattern;

import java.util.List;

/**
 * EagerSolver applies the two eager‐solving rules (Figure 1) to a list of
 * subsumption problems. Each problem is represented as a SubsumptionPattern
 * with a solved flag.
 */
public class EagerSolver {

    private final ELAnalyze elAnalyze;

    public EagerSolver(ELAnalyze elAnalyze) {
        this.elAnalyze = elAnalyze;
    }

    /**
     * Tries to apply one eager rule to Γ.
     *
     * @param gamma the list of subsumptions (some marked solved, some not)
     * @return true if an eager rule was applied successfully (possibly marking one problem solved);
     *         false if a rule application failed.
     *         If no eager rule is applicable, returns true (nothing to do).
     */
    public boolean applyEager(List<SubsumptionPattern> gamma) throws FailureException {
        // 1) Eager – variable on the right
        for (SubsumptionPattern sp : gamma) {
            if (!sp.solved && sp.right.type == ConceptPatternNode.Type.VARIABLE) {
                return applyRightVarRule(sp, gamma);
            }
        }
        // 2) Eager – variable on the left
        for (SubsumptionPattern sp : gamma) {
            if (!sp.solved && sp.left.type == ConceptPatternNode.Type.VARIABLE) {
                return applyLeftVarRule(sp, gamma);
            }
        }
        // no eager rule applicable
        return true;
    }

    /** Implements “Eager Solving – variable on the right” */
    private boolean applyRightVarRule(SubsumptionPattern s, List<SubsumptionPattern> gamma) {
        ConceptPatternNode C = s.left;
        // find any X ⊑? D in Γ
        for (SubsumptionPattern sp : gamma) {
            if (sp != s
                    && !sp.solved
                    && sp.left.type == ConceptPatternNode.Type.VARIABLE
                    && sp.left.equals(s.right)) {
                ConceptPatternNode D = sp.right;
                // if C ⊑_T D fails, whole rule fails
                if (!elAnalyze.subsumes(C, D)) {
                    return false;
                }
            }
        }
        // otherwise mark C ⊑? X solved
        s.solved = true;
        return true;
    }

    /** Implements “Eager Solving – variable on the left” */
    private boolean applyLeftVarRule(SubsumptionPattern s, List<SubsumptionPattern> gamma) {
        ConceptPatternNode D = s.right;
        // find any C ⊑? X in Γ
        for (SubsumptionPattern sp : gamma) {
            if (sp != s
                    && !sp.solved
                    && sp.right.type == ConceptPatternNode.Type.VARIABLE
                    && sp.right.equals(s.left)) {
                ConceptPatternNode C = sp.left;
                // if C ⊑_T D fails, whole rule fails
                if (!elAnalyze.subsumes(C, D)) {
                    return false;
                }
            }
        }
        // otherwise mark X ⊑? D solved
        s.solved = true;
        return true;
    }


}
