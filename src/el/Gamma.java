package el;

import el.structure.ConceptPatternNode;
import el.structure.SubsumptionPattern;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Gamma represents a matching problem Γ: a collection of subsumptions
 * C ⊑? D, each of which can be marked solved or unsolved.
 */
public class Gamma {

    //private final List<SubsumptionPattern> patterns = new ArrayList<>();
    private final List<SubsumptionPattern> patterns;
    /** Create an empty Γ. */
    public Gamma() {
        this.patterns = new ArrayList<>();
    }

    /** Create Γ pre-populated with the given patterns. */
    public Gamma(Collection<SubsumptionPattern> initial) {
        this.patterns = new ArrayList<>(initial);
    }

    /** Deep-copy constructor. */
    private Gamma(List<SubsumptionPattern> toCopy, boolean dummy) {
        this.patterns = toCopy;
    }

    /** Add a new subsumption C ⊑? D (initially unsolved). */
    public void add(ConceptPatternNode left, ConceptPatternNode right) {
        patterns.add(new SubsumptionPattern(left, right));
    }

    /** Remove all patterns and start fresh. */
    public void clear() {
        patterns.clear();
    }

    /** Return an unmodifiable view of all patterns. */
    public List<SubsumptionPattern> getAll() {
        return Collections.unmodifiableList(patterns);
    }

    /** Return only the unsolved patterns. */
    public List<SubsumptionPattern> getUnsolved() {
        return patterns.stream()
                .filter(p -> !p.solved)
                .collect(Collectors.toList());
    }

    /** Return true if all patterns are marked solved. */
    public boolean allSolved() {
        return patterns.stream().allMatch(p -> p.solved);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Gamma:\n");
        for (SubsumptionPattern p : patterns) {
            sb.append("  ")
                    .append(p.toString())
                    .append("\n");
        }
        return sb.toString();
    }

    /**
     * Return the first unsolved pattern, or null if all are solved.
     */
    public SubsumptionPattern nextUnsolved() {
        for (SubsumptionPattern sp : patterns) {
            if (!sp.solved) return sp;
        }
        return null;
    }

    /**
     * Deep-copy this Γ: clone each SubsumptionPattern (including its solved flag).
     */
    public Gamma copy() {
        List<SubsumptionPattern> copyList = new ArrayList<>(patterns.size());
        for (SubsumptionPattern sp : patterns) {
            copyList.add(sp.copy());
        }
        return new Gamma(copyList, true);
    }

}

