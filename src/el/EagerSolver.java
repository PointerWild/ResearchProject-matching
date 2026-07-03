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
        SubsumptionPattern firstLeftVar = null;

        for (SubsumptionPattern sp : gamma) {
            if (sp.solved) {
                continue;
            }

            // 1) Eager – variable on the right
            // C ⊑? X 优先处理
            if (sp.right.type == ConceptPatternNode.Type.VARIABLE) {
                return applyRightVarRule(sp, gamma);
            }

            // 2) Eager – variable on the left
            // X ⊑? D 先记录，不马上 return
            if (firstLeftVar == null && sp.left.type == ConceptPatternNode.Type.VARIABLE) {
                firstLeftVar = sp;
            }
        }

        if (firstLeftVar != null) {
            return applyLeftVarRule(firstLeftVar, gamma);
        }
        // no eager rule applicable
        return true;

    }

    /** Implements “Eager Solving – variable on the right” */
    private boolean applyRightVarRule(SubsumptionPattern s, List<SubsumptionPattern> gamma) throws FailureException {
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
                    //return false;
                    throw new FailureException(
                            "Eager right-variable rule failed: " + C + " ⊑T " + D + " is false"
                    );
                }
            }
        }
        // otherwise mark C ⊑? X solved
        s.solved = true;
        return true;
    }

    /** Implements “Eager Solving – variable on the left” */
    private boolean applyLeftVarRule(SubsumptionPattern s, List<SubsumptionPattern> gamma) throws FailureException{
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
                    //return false;
                    throw new FailureException(
                            "Eager left-variable rule failed: " + C + " ⊑T " + D + " is false"
                    );
                }
            }
        }
        // otherwise mark X ⊑? D solved
        s.solved = true;
        return true;
    }


}
