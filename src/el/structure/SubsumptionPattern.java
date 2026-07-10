package el.structure;

/**
 * A subsumption in Γ that can be marked 'solved' by eager rules.
 * Inherits the basic left/right from Subsumption.
 */
public class SubsumptionPattern extends Subsumption {
    /** Whether this subsumption has been solved (initially false). */
    public boolean solved = false;

    public SubsumptionPattern(ConceptPatternNode left, ConceptPatternNode right) {
        super(left, right);
    }

    /**
     * Deep‐copy this pattern, preserving left/right and the solved flag.
     */
    public SubsumptionPattern copy() {
        SubsumptionPattern cp = new SubsumptionPattern(this.left, this.right);
        cp.solved = this.solved;
        return cp;
    }

    @Override
    public String toString() {
        return (solved ? "[✔] " : "[ ] ") + super.toString();
    }
}
