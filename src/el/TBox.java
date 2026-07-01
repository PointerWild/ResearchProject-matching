package el;

import el.structure.ConceptPatternNode;
import el.structure.Subsumption;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * A TBox is a set of general concept inclusion axioms (Subsumption).
 * Internally stored as a LinkedHashSet to preserve insertion order
 * and avoid duplicates.
 */
public class TBox {
    private final Set<Subsumption> axioms = new LinkedHashSet<>();

    /** Create an empty TBox. */
    public TBox() {}

    /** Create a TBox pre‐populated with the given axioms. */
    public TBox(Collection<? extends Subsumption> initial) {
        axioms.addAll(initial);
    }

    /** Add a new axiom; returns true if it was not already present. */
    public boolean add(Subsumption axiom) {
        return axioms.add(axiom);
    }

    /** Remove the given axiom; returns true if it was present. */
    public boolean remove(Subsumption axiom) {
        return axioms.remove(axiom);
    }

    /** Clear all axioms. */
    public void clear() {
        axioms.clear();
    }

    /** Does the TBox contain this axiom? */
    public boolean contains(Subsumption axiom) {
        return axioms.contains(axiom);
    }

    /** Number of axioms in this TBox. */
    public int size() {
        return axioms.size();
    }

    /**
     * Return an unmodifiable view of the axioms.
     * If you need to iterate or query, use this.
     */
    public Set<Subsumption> getAxioms() {
        return Collections.unmodifiableSet(axioms);
    }

    // -- File I/O operations --

    /**
     * Load axioms from a text file (one subsumption per line).
     * Lines that fail ELSyntaxChecker are skipped with a warning.
     */
    public void loadFromFile(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (ELSyntaxChecker.isValid(line)) {
                // parse into two ConceptPatternNode
                String[] parts = line.split("\\s*⊑\\s*");
                // Note: here we treat both sides as pattern nodes (no variable restriction)
                ConceptPatternNode left  = ConceptPatternNode.parse(parts[0]);
                ConceptPatternNode right = ConceptPatternNode.parse(parts[1]);

                //ConceptPatternNode p1 = ConceptPatternNode.parse("∃has-child.(Male ⊓ _X_)");
                //ConceptPatternNode p2 = ConceptPatternNode.parse("(A ⊓ ∃r.(B))");

                add(new Subsumption(left, right));
            } else {
                System.err.println("Invalid GCI syntax (skipped): " + line);
            }
        }
    }

    /**
     * Write all axioms to a text file, one per line in their toString() form.
     */
    public void saveToFile(Path path) throws IOException {
        List<String> out = new ArrayList<>();
        for (Subsumption ax : axioms) {
            out.add(ax.left + " ⊑ " + ax.right);
        }
        Files.write(path, out);
    }

    @Override
    public String toString() {
        return axioms.toString();
    }
}
