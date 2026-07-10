package el;

import el.structure.ConceptPatternNode;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Loads a ground EL TBox into one in-memory OWL ontology and answers
 * subsumption queries with one long-lived ELK reasoner.
 *
 * <p>This class owns only TBox state. Gamma expressions must never be added to
 * its ontology; they may only be submitted as ground query expressions through
 * {@link #subsumes(ConceptPatternNode, ConceptPatternNode)}.</p>
 */
public final class TBoxElkReasoner implements SubsumptionOracle, AutoCloseable {

    private static final char SUBSUMPTION_OPERATOR = '⊑';

    private final OWLOntologyManager ontologyManager;
    private final OWLDataFactory dataFactory;
    private final OWLOntology ontology;
    private OWLReasoner reasoner;
    private final String baseIri;
    private final List<OWLSubClassOfAxiom> tBoxAxioms;
    private boolean closed;

    /**
     * Creates an empty ontology and an ELK reasoner using the supplied base IRI.
     * TBox axioms may subsequently be appended with {@link #loadTBox(Path)}.
     *
     * @param baseIri base IRI used for concept and role entities
     * @throws IllegalArgumentException if {@code baseIri} is null or blank
     * @throws IllegalStateException if the OWL ontology cannot be created
     */
    public TBoxElkReasoner(String baseIri) {
        this.baseIri = normalizeBaseIri(baseIri);
        this.ontologyManager = OWLManager.createOWLOntologyManager();
        this.dataFactory = ontologyManager.getOWLDataFactory();
        this.tBoxAxioms = new ArrayList<>();
        try {
            this.ontology = ontologyManager.createOntology(IRI.create(this.baseIri));
        } catch (OWLOntologyCreationException exception) {
            throw new IllegalStateException("Failed to create OWL ontology for base IRI: "
                    + this.baseIri, exception);
        }
        createReasoner();
    }

    /**
     * Creates an ontology, loads all valid ground TBox axioms from a UTF-8 file,
     * creates one ELK reasoner, and precomputes the class hierarchy.
     *
     * @param tBoxPath path to a TBox file
     * @param baseIri base IRI used for concept and role entities
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if a TBox line is invalid or contains a variable
     * @throws IllegalStateException if the OWL ontology cannot be created
     */
    public TBoxElkReasoner(Path tBoxPath, String baseIri) throws IOException {
        this.baseIri = normalizeBaseIri(baseIri);
        this.ontologyManager = OWLManager.createOWLOntologyManager();
        this.dataFactory = ontologyManager.getOWLDataFactory();
        this.tBoxAxioms = new ArrayList<>();
        try {
            this.ontology = ontologyManager.createOntology(IRI.create(this.baseIri));
        } catch (OWLOntologyCreationException exception) {
            throw new IllegalStateException("Failed to create OWL ontology for base IRI: "
                    + this.baseIri, exception);
        }

        loadTBox(Objects.requireNonNull(tBoxPath, "tBoxPath cannot be null"));
        createReasoner();
    }

    /**
     * Parses and appends all TBox axioms from a UTF-8 file.
     *
     * <p>Blank lines and lines beginning with {@code #} or {@code //} are ignored.
     * Parsing is transactional: if any non-comment line is invalid, no axiom from
     * this invocation is added. When a reasoner already exists, it is flushed and
     * the class hierarchy is recomputed after the new axioms are added.</p>
     *
     * @param path path to the TBox file
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if a non-comment line is invalid
     * @throws IllegalStateException if this reasoner has already been closed
     */
    public void loadTBox(Path path) throws IOException {
        ensureOpen();
        Objects.requireNonNull(path, "path cannot be null");

        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<OWLSubClassOfAxiom> parsedAxioms = new ArrayList<>();

        for (int index = 0; index < lines.size(); index++) {
            String original = lines.get(index);
            String trimmed = original.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                continue;
            }
            parsedAxioms.add(parseTBoxLine(trimmed, path, index + 1));
        }

        boolean ontologyChanged = false;
        for (OWLSubClassOfAxiom axiom : parsedAxioms) {
            if (!tBoxAxioms.contains(axiom)) {
                ontologyManager.addAxiom(ontology, axiom);
                tBoxAxioms.add(axiom);
                ontologyChanged = true;
            }
        }

        if (ontologyChanged && reasoner != null) {
            reasoner.flush();
            reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
        }
    }

    /**
     * Converts a ground project expression node into the corresponding OWL class expression.
     *
     * @param node expression node to convert
     * @return the corresponding OWL class expression
     * @throws IllegalArgumentException if the node contains a variable or is malformed
     */
    public OWLClassExpression toOwlExpression(ConceptPatternNode node) {
        ensureOpen();
        Objects.requireNonNull(node, "node cannot be null");

        return switch (node.type) {
            case TOP -> dataFactory.getOWLThing();
            case CONCEPT_NAME -> {
                if (node.conceptName == null) {
                    throw new IllegalArgumentException("Concept-name node has no concept name.");
                }
                yield dataFactory.getOWLClass(IRI.create(baseIri + node.conceptName.name));
            }
            case VARIABLE -> throw new IllegalArgumentException(
                    "Variable cannot be converted to an OWL class expression.");
            case EXISTENTIAL -> {
                if (node.role == null || node.existentialFiller == null) {
                    throw new IllegalArgumentException(
                            "Existential node must contain both a role and a filler.");
                }
                OWLObjectProperty property = dataFactory.getOWLObjectProperty(
                        IRI.create(baseIri + node.role.name));
                OWLClassExpression filler = toOwlExpression(node.existentialFiller);
                yield dataFactory.getOWLObjectSomeValuesFrom(property, filler);
            }
            case CONJUNCTION -> {
                if (node.conjunctions == null || node.conjunctions.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Conjunction node must contain at least one child.");
                }
                Set<OWLClassExpression> parts = new LinkedHashSet<>();
                for (ConceptPatternNode child : node.conjunctions) {
                    parts.add(toOwlExpression(Objects.requireNonNull(
                            child, "Conjunction child cannot be null")));
                }
                yield dataFactory.getOWLObjectIntersectionOf(parts);
            }
        };
    }

    /**
     * Returns whether an expression contains no variables.
     *
     * @param node expression to inspect
     * @return {@code true} exactly when the expression is ground
     */
    public boolean isGround(ConceptPatternNode node) {
        Objects.requireNonNull(node, "node cannot be null");
        return switch (node.type) {
            case TOP, CONCEPT_NAME -> true;
            case VARIABLE -> false;
            case EXISTENTIAL -> node.existentialFiller != null
                    && isGround(node.existentialFiller);
            case CONJUNCTION -> node.conjunctions != null
                    && !node.conjunctions.isEmpty()
                    && node.conjunctions.stream().allMatch(this::isGround);
        };
    }

    /**
     * Returns whether both expressions contain no variables.
     *
     * @param left left expression
     * @param right right expression
     * @return {@code true} exactly when both expressions are ground
     */
    public boolean isGround(ConceptPatternNode left, ConceptPatternNode right) {
        return isGround(left) && isGround(right);
    }

    /**
     * Tests whether the loaded TBox entails {@code left ⊑ right}.
     *
     * @param left subsumee expression
     * @param right subsumer expression
     * @return {@code true} exactly when ELK entails {@code left ⊑ right}
     * @throws IllegalArgumentException if either expression contains a variable
     * @throws IllegalStateException if this reasoner has already been closed
     */
    @Override
    public boolean subsumes(ConceptPatternNode left, ConceptPatternNode right) {
        ensureOpen();
        Objects.requireNonNull(left, "left cannot be null");
        Objects.requireNonNull(right, "right cannot be null");

        if (!isGround(left, right)) {
            throw new IllegalArgumentException(
                    "Subsumption query contains a variable and cannot be queried by ELK.");
        }

        OWLClassExpression subExpression = toOwlExpression(left);
        OWLClassExpression superExpression = toOwlExpression(right);
        OWLSubClassOfAxiom query = dataFactory.getOWLSubClassOfAxiom(
                subExpression, superExpression);

        boolean result = reasoner.isEntailed(query);
        return result;
    }

    /**
     * Parses two expression strings and tests whether the loaded TBox entails
     * {@code leftExpression ⊑ rightExpression}.
     *
     * @param leftExpression subsumee expression text
     * @param rightExpression subsumer expression text
     * @return the ELK entailment result
     */
    public boolean subsumes(String leftExpression, String rightExpression) {
        Objects.requireNonNull(leftExpression, "leftExpression cannot be null");
        Objects.requireNonNull(rightExpression, "rightExpression cannot be null");
        ConceptPatternNode left = ConceptPatternNode.parse(leftExpression);
        ConceptPatternNode right = ConceptPatternNode.parse(rightExpression);
        return subsumes(left, right);
    }

    /**
     * Returns the in-memory ontology containing only loaded TBox axioms.
     *
     * @return the TBox ontology
     */
    public OWLOntology getOntology() {
        ensureOpen();
        return ontology;
    }

    /**
     * Returns the long-lived ELK reasoner associated with this TBox ontology.
     *
     * @return the ELK reasoner
     */
    public OWLReasoner getReasoner() {
        ensureOpen();
        return reasoner;
    }

    /**
     * Returns an immutable snapshot of loaded TBox subclass axioms.
     *
     * @return immutable TBox axiom list
     */
    public List<OWLSubClassOfAxiom> getTBoxAxioms() {
        return Collections.unmodifiableList(new ArrayList<>(tBoxAxioms));
    }

    /**
     * Returns the number of axioms currently contained in the OWL ontology.
     *
     * @return ontology axiom count
     */
    public int getAxiomCount() {
        return ontology.getAxiomCount();
    }

    /**
     * Returns the normalized base IRI. It always ends in {@code #} or {@code /}.
     *
     * @return normalized base IRI
     */
    public String getBaseIri() {
        return baseIri;
    }

    /**
     * Disposes the ELK reasoner. Repeated calls are harmless.
     */
    @Override
    public void close() {
        if (!closed) {
            if (reasoner != null) {
                reasoner.dispose();
            }
            closed = true;
        }
    }

    private OWLSubClassOfAxiom parseTBoxLine(String line, Path source, int lineNumber) {
        try {
            if (line.contains("⊑?")) {
                throw new IllegalArgumentException(
                        "The query operator ⊑? is not allowed in TBox.");
            }

            int operatorIndex = line.indexOf(SUBSUMPTION_OPERATOR);
            if (operatorIndex < 0) {
                throw new IllegalArgumentException("Missing subsumption operator ⊑.");
            }
            if (operatorIndex != line.lastIndexOf(SUBSUMPTION_OPERATOR)) {
                throw new IllegalArgumentException(
                        "Each TBox line must contain exactly one subsumption operator ⊑.");
            }

            String leftText = line.substring(0, operatorIndex).trim();
            String rightText = line.substring(operatorIndex + 1).trim();
            if (leftText.isEmpty() || rightText.isEmpty()) {
                throw new IllegalArgumentException(
                        "Both sides of a TBox subsumption must be non-empty.");
            }

            ConceptPatternNode left = ConceptPatternNode.parse(leftText);
            ConceptPatternNode right = ConceptPatternNode.parse(rightText);
            if (!isGround(left, right)) {
                throw new IllegalArgumentException("Variables are not allowed in TBox.");
            }

            return dataFactory.getOWLSubClassOfAxiom(
                    toOwlExpression(left), toOwlExpression(right));
        } catch (RuntimeException exception) {
            String reason = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            throw new IllegalArgumentException(
                    "Invalid TBox expression at " + source + ":" + lineNumber + ": " + line
                            + System.lineSeparator() + "Reason: " + reason,
                    exception);
        }
    }

    private void createReasoner() {
        this.reasoner = new ElkReasonerFactory().createReasoner(ontology);
        this.reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
    }

    private static String normalizeBaseIri(String iri) {
        Objects.requireNonNull(iri, "baseIri cannot be null");
        String trimmed = iri.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("baseIri cannot be blank");
        }
        if (trimmed.endsWith("#") || trimmed.endsWith("/")) {
            return trimmed;
        }
        return trimmed + "#";
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("TBoxElkReasoner has already been closed.");
        }
    }
}
