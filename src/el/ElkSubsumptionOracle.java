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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Semantic subsumption oracle backed by the ELK reasoner.
 *
 * <p>This class is responsible only for checking ground EL subsumption:
 *
 * <pre>
 *     TBox |= C SubClassOf D
 * </pre>
 *
 * <p>Matching variables are not OWL entities and must never be submitted
 * to ELK. Gamma constraints are query objects only and are never added to
 * the ontology.
 */
public final class ElkSubsumptionOracle
        implements SubsumptionOracle, AutoCloseable {

    private final OWLOntologyManager manager;
    private final OWLDataFactory dataFactory;
    private final OWLOntology ontology;
    private final OWLReasoner reasoner;
    private final String baseIri;

    /**
     * Number of semantic queries sent to ELK.
     */
    private int elkQueryCount;

    /**
     * Prevents queries after the reasoner has been disposed.
     */
    private boolean closed;

    /**
     * Creates one in-memory ontology and one long-lived ELK reasoner.
     *
     * @param tBoxLines ground TBox axioms written using the project EL syntax
     * @param baseIri   base IRI used for concept names and role names
     */
    public ElkSubsumptionOracle(
            Collection<String> tBoxLines,
            String baseIri
    ) {
        Objects.requireNonNull(
                tBoxLines,
                "tBoxLines cannot be null"
        );

        try {
            this.manager =
                    OWLManager.createOWLOntologyManager();

            this.dataFactory =
                    manager.getOWLDataFactory();

            this.baseIri =
                    normalizeBaseIri(baseIri);

            this.ontology =
                    manager.createOntology(
                            IRI.create(this.baseIri)
                    );

            /*
             * TBox axioms are loaded before the reasoner is created.
             * Only these ground axioms belong to the ontology.
             */
            for (String line : tBoxLines) {
                addTBoxLine(line);
            }

            this.reasoner =
                    new ElkReasonerFactory()
                            .createReasoner(ontology);

            this.reasoner.precomputeInferences(
                    InferenceType.CLASS_HIERARCHY
            );

            this.elkQueryCount = 0;
            this.closed = false;

        } catch (OWLOntologyCreationException e) {
            throw new IllegalStateException(
                    "Failed to initialize ELK ontology.",
                    e
            );
        }
    }

    /**
     * Checks whether the loaded TBox entails:
     *
     * <pre>
     *     left SubClassOf right
     * </pre>
     *
     * <p>Both expressions must be ground. Expressions containing matching
     * variables are rejected instead of being approximated by a structural
     * fallback.
     *
     * @param left  candidate subclass expression
     * @param right candidate superclass expression
     * @return true iff ELK entails the subclass axiom
     */
    @Override
    public boolean subsumes(
            ConceptPatternNode left,
            ConceptPatternNode right
    ) {
        ensureOpen();

        Objects.requireNonNull(
                left,
                "left cannot be null"
        );

        Objects.requireNonNull(
                right,
                "right cannot be null"
        );

        /*
         * Variables belong to matching constraints, not to the OWL TBox.
         * They must be handled by Algorithm 5.1 before an ELK query is made.
         */
        if (!isGround(left) || !isGround(right)) {
            throw new IllegalArgumentException(
                    "Variables cannot be submitted to ELK: "
                            + left
                            + " ⊑ "
                            + right
            );
        }

        OWLClassExpression subExpression =
                toOwlExpression(left);

        OWLClassExpression superExpression =
                toOwlExpression(right);

        /*
         * The query axiom is constructed in memory only.
         * It is deliberately not added to the ontology.
         */
        OWLSubClassOfAxiom query =
                dataFactory.getOWLSubClassOfAxiom(
                        subExpression,
                        superExpression
                );

        /*
         * Exactly one semantic query is sent to ELK.
         */
        boolean result =
                reasoner.isEntailed(query);

        elkQueryCount++;

        System.out.printf(
                "[ELK] %s ⊑ %s -> %b%n",
                left,
                right,
                result
        );

        return result;
    }

    /**
     * Convenience overload for textual EL expressions.
     *
     * @param left  textual subclass expression
     * @param right textual superclass expression
     * @return true iff the loaded TBox entails left SubClassOf right
     */
    public boolean subsumes(
            String left,
            String right
    ) {
        Objects.requireNonNull(
                left,
                "left cannot be null"
        );

        Objects.requireNonNull(
                right,
                "right cannot be null"
        );

        return subsumes(
                ConceptPatternNode.parse(left),
                ConceptPatternNode.parse(right)
        );
    }

    /**
     * Returns whether the given pattern contains no matching variable.
     *
     * @param node concept pattern
     * @return true when the whole expression is ground
     */
    public boolean isGround(
            ConceptPatternNode node
    ) {
        Objects.requireNonNull(
                node,
                "node cannot be null"
        );

        return !containsVariable(node);
    }

    /**
     * Adds one ground TBox axiom to the ontology.
     *
     * <p>This method is used only during construction, before the ELK
     * reasoner is created.
     */
    private void addTBoxLine(String line) {
        Objects.requireNonNull(
                line,
                "TBox line cannot be null"
        );

        String cleaned = line.trim();

        if (cleaned.isEmpty()) {
            return;
        }

        if (!ELSyntaxChecker.isValid(cleaned)) {
            throw new IllegalArgumentException(
                    "Invalid TBox syntax: " + line
            );
        }

        String[] parts =
                cleaned.split("\\s*⊑\\s*");

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "Invalid TBox axiom: " + line
            );
        }

        ConceptPatternNode left =
                ConceptPatternNode.parse(
                        parts[0].trim()
                );

        ConceptPatternNode right =
                ConceptPatternNode.parse(
                        parts[1].trim()
                );

        if (!isGround(left) || !isGround(right)) {
            throw new IllegalArgumentException(
                    "TBox must be ground. Invalid axiom: "
                            + line
            );
        }

        OWLClassExpression subExpression =
                toOwlExpression(left);

        OWLClassExpression superExpression =
                toOwlExpression(right);

        OWLSubClassOfAxiom axiom =
                dataFactory.getOWLSubClassOfAxiom(
                        subExpression,
                        superExpression
                );

        manager.addAxiom(
                ontology,
                axiom
        );
    }

    /**
     * Converts one ground project expression into an OWL class expression.
     */
    private OWLClassExpression toOwlExpression(
            ConceptPatternNode node
    ) {
        Objects.requireNonNull(
                node,
                "node cannot be null"
        );

        return switch (node.type) {
            case TOP ->
                    dataFactory.getOWLThing();

            case CONCEPT_NAME ->
                    dataFactory.getOWLClass(
                            IRI.create(
                                    baseIri
                                            + node.conceptName.name
                            )
                    );

            case EXISTENTIAL -> {
                OWLObjectProperty property =
                        dataFactory.getOWLObjectProperty(
                                IRI.create(
                                        baseIri
                                                + node.role.name
                                )
                        );

                OWLClassExpression filler =
                        toOwlExpression(
                                node.existentialFiller
                        );

                yield dataFactory
                        .getOWLObjectSomeValuesFrom(
                                property,
                                filler
                        );
            }

            case CONJUNCTION -> {
                Set<OWLClassExpression> operands =
                        new LinkedHashSet<>();

                for (ConceptPatternNode child
                        : node.conjunctions) {
                    operands.add(
                            toOwlExpression(child)
                    );
                }

                /*
                 * An empty conjunction represents TOP.
                 * This also avoids passing an unexpected empty collection
                 * to downstream code.
                 */
                if (operands.isEmpty()) {
                    yield dataFactory.getOWLThing();
                }

                yield dataFactory
                        .getOWLObjectIntersectionOf(
                                operands
                        );
            }

            case VARIABLE ->
                    throw new IllegalArgumentException(
                            "Variable cannot be converted "
                                    + "to an OWL expression: "
                                    + node
                    );
        };
    }

    /**
     * Recursively checks whether a pattern contains a matching variable.
     */
    private boolean containsVariable(
            ConceptPatternNode node
    ) {
        return switch (node.type) {
            case VARIABLE ->
                    true;

            case TOP, CONCEPT_NAME ->
                    false;

            case EXISTENTIAL ->
                    containsVariable(
                            node.existentialFiller
                    );

            case CONJUNCTION ->
                    node.conjunctions
                            .stream()
                            .anyMatch(
                                    this::containsVariable
                            );
        };
    }

    /**
     * Normalizes the namespace separator.
     */
    private String normalizeBaseIri(
            String iri
    ) {
        Objects.requireNonNull(
                iri,
                "baseIri cannot be null"
        );

        String normalized = iri.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "baseIri cannot be blank"
            );
        }

        if (normalized.endsWith("#")
                || normalized.endsWith("/")) {
            return normalized;
        }

        return normalized + "#";
    }

    /**
     * Returns the number of actual semantic queries sent to ELK.
     */
    public int getElkQueryCount() {
        return elkQueryCount;
    }

    /**
     * Returns the number of ontology axioms currently stored.
     *
     * <p>Algorithm 5.1 and Gamma queries must not change this value.
     */
    public int getAxiomCount() {
        return ontology.getAxiomCount();
    }

    /**
     * Compatibility method for older diagnostic code.
     *
     * <p>The semantic oracle no longer performs structural fallback,
     * therefore this method always returns zero.
     *
     * @deprecated structural fallback has been removed from semantic queries
     */
    @Deprecated
    public int getStructuralFallbackCount() {
        return 0;
    }

    /**
     * Disposes the long-lived ELK reasoner.
     *
     * <p>The operation is idempotent.
     */
    @Override
    public void close() {
        if (!closed) {
            reasoner.dispose();
            closed = true;
        }
    }

    /**
     * Prevents semantic queries after the reasoner has been closed.
     */
    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException(
                    "ELK reasoner has already been closed."
            );
        }
    }
}