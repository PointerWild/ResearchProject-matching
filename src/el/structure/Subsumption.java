// File: Subsumption.java
package el.structure;

import java.util.Objects;

/**
 * A basic subsumption C ⊑? D between two pattern nodes.
 */
public class Subsumption {
    public final ConceptPatternNode left;
    public final ConceptPatternNode right;

    public Subsumption(ConceptPatternNode left, ConceptPatternNode right) {
        this.left  = left;
        this.right = right;
    }

    @Override
    public String toString() {
        return left + " ⊑? " + right;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Subsumption)) return false;
        Subsumption that = (Subsumption) o;
        return left.equals(that.left) && right.equals(that.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right);
    }
}
