package el;

import el.structure.ConceptPatternNode;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class ElkSubsumptionOracle implements SubsumptionOracle, AutoCloseable {

    private final OWLOntologyManager manager;
    private final OWLDataFactory dataFactory;
    private final OWLOntology ontology;
    private final OWLReasoner reasoner;
    private final String baseIri;

    private int elkQueryCount = 0;
    private int structuralFallbackCount = 0;

    private final SubsumptionOracle fallback = new StructuralSubsumptionOracle();



    public ElkSubsumptionOracle(Collection<String> tBoxLines, String baseIri) {
        try {
            this.manager = OWLManager.createOWLOntologyManager();
            this.dataFactory = manager.getOWLDataFactory();
            this.baseIri = normalizeBaseIri(baseIri);
            this.ontology = manager.createOntology(IRI.create(this.baseIri));

            for (String line : tBoxLines) {
                addTBoxLine(line);
            }

            this.reasoner = new ElkReasonerFactory().createReasoner(ontology);
            this.reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);

        } catch (OWLOntologyCreationException e) {
            throw new IllegalStateException("Failed to initialize ELK ontology.", e);
        }
    }

    @Override
    public boolean subsumes(ConceptPatternNode left, ConceptPatternNode right) {
        if (right.type == ConceptPatternNode.Type.TOP ||
                right.type == ConceptPatternNode.Type.VARIABLE) {
            return true;
        }

        if (containsVariable(left) || containsVariable(right)) {
            structuralFallbackCount++;
            return fallback.subsumes(left, right);
        }

        OWLClassExpression sub = toOwlExpression(left);
        OWLClassExpression sup = toOwlExpression(right);

        OWLSubClassOfAxiom query = dataFactory.getOWLSubClassOfAxiom(sub, sup);

        elkQueryCount++;
        boolean result = reasoner.isEntailed(query);

        System.out.printf("[ELK] %s ⊑ %s -> %b%n", left, right, result);

        return reasoner.isEntailed(query);
    }

    private void addTBoxLine(String line) {
        String cleaned = line.trim();

        if (cleaned.isEmpty()) {
            return;
        }

        if (!ELSyntaxChecker.isValid(cleaned)) {
            throw new IllegalArgumentException("Invalid TBox syntax: " + line);
        }

        String[] parts = cleaned.split("\\s*⊑\\s*");

        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid TBox axiom: " + line);
        }

        ConceptPatternNode left = ConceptPatternNode.parse(parts[0].trim());
        ConceptPatternNode right = ConceptPatternNode.parse(parts[1].trim());

        if (containsVariable(left) || containsVariable(right)) {
            throw new IllegalArgumentException("TBox must be ground. Invalid axiom: " + line);
        }

        OWLClassExpression sub = toOwlExpression(left);
        OWLClassExpression sup = toOwlExpression(right);

        OWLSubClassOfAxiom axiom = dataFactory.getOWLSubClassOfAxiom(sub, sup);
        manager.addAxiom(ontology, axiom);
    }

    private OWLClassExpression toOwlExpression(ConceptPatternNode node) {
        return switch (node.type) {
            case TOP -> dataFactory.getOWLThing();

            case CONCEPT_NAME -> dataFactory.getOWLClass(
                    IRI.create(baseIri + node.conceptName.name)
            );

            case EXISTENTIAL -> {
                OWLObjectProperty property = dataFactory.getOWLObjectProperty(
                        IRI.create(baseIri + node.role.name)
                );
                OWLClassExpression filler = toOwlExpression(node.existentialFiller);
                yield dataFactory.getOWLObjectSomeValuesFrom(property, filler);
            }

            case CONJUNCTION -> {
                Set<OWLClassExpression> parts = new LinkedHashSet<>();
                for (ConceptPatternNode child : node.conjunctions) {
                    parts.add(toOwlExpression(child));
                }
                yield dataFactory.getOWLObjectIntersectionOf(parts);
            }

            case VARIABLE -> throw new IllegalArgumentException(
                    "Variable cannot be converted to OWL expression: " + node
            );
        };
    }

    private boolean containsVariable(ConceptPatternNode node) {
        return switch (node.type) {
            case VARIABLE -> true;
            case TOP, CONCEPT_NAME -> false;
            case EXISTENTIAL -> containsVariable(node.existentialFiller);
            case CONJUNCTION -> node.conjunctions.stream().anyMatch(this::containsVariable);
        };
    }

    private String normalizeBaseIri(String iri) {
        if (iri.endsWith("#") || iri.endsWith("/")) {
            return iri;
        }

        return iri + "#";
    }

    @Override
    public void close() {
        reasoner.dispose();
    }

    public int getElkQueryCount() {
        return elkQueryCount;
    }

    public int getStructuralFallbackCount() {
        return structuralFallbackCount;
    }

}