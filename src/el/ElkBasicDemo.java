package el;


import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
        import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

public class ElkBasicDemo {

    public static void main(String[] args) throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();

        String base = "http://example.com/el#";

        OWLOntology ontology = manager.createOntology(IRI.create(base));

        OWLClass A = df.getOWLClass(IRI.create(base + "A"));
        OWLClass B = df.getOWLClass(IRI.create(base + "B"));
        OWLClass C = df.getOWLClass(IRI.create(base + "C"));

        // TBox:
        // A ⊑ B
        // B ⊑ C
        OWLSubClassOfAxiom ax1 = df.getOWLSubClassOfAxiom(A, B);
        OWLSubClassOfAxiom ax2 = df.getOWLSubClassOfAxiom(B, C);

        manager.addAxiom(ontology, ax1);
        manager.addAxiom(ontology, ax2);

        OWLReasoner reasoner = new ElkReasonerFactory().createReasoner(ontology);

        reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);

        // Query: A ⊑ C ?
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(A, C);


        boolean result = reasoner.isEntailed(query);

        System.out.println("A ⊑ C ? " + result);

        reasoner.dispose();
    }
}