package el;
// 2026.06.26 -- for ELK reasoner

import java.io.File;

import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

public class ElkChecker {

    private final OWLOntologyManager manager;
    private final OWLDataFactory dataFactory;
    private final OWLOntology ontology;
    private final OWLReasoner reasoner;
    private final String baseIri;

    public ElkChecker(String ontologyPath, String baseIri) throws Exception {
        this.manager = OWLManager.createOWLOntologyManager();
        this.dataFactory = manager.getOWLDataFactory();
        this.ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPath));
        this.reasoner = new ElkReasonerFactory().createReasoner(ontology);

        this.baseIri = baseIri.endsWith("#") || baseIri.endsWith("/")
                ? baseIri
                : baseIri + "#";

        reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
    }

    public boolean check(OWLClassExpression C, OWLClassExpression D) {
        OWLSubClassOfAxiom axiom = dataFactory.getOWLSubClassOfAxiom(C, D);

        if (!reasoner.isEntailmentCheckingSupported(AxiomType.SUBCLASS_OF)) {
            throw new UnsupportedOperationException("SubClassOf entailment checking is not supported.");
        }

        return reasoner.isEntailed(axiom);
    }

    public boolean check(String subClassName, String superClassName) {
        OWLClass subClass = dataFactory.getOWLClass(IRI.create(baseIri + subClassName));
        OWLClass superClass = dataFactory.getOWLClass(IRI.create(baseIri + superClassName));

        return check(subClass, superClass);
    }

    public OWLDataFactory getDataFactory() {
        return dataFactory;
    }

    public String getBaseIri() {
        return baseIri;
    }

    public void close() {
        reasoner.dispose();
    }
}