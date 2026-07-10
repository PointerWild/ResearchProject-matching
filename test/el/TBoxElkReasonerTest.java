package el;

import el.structure.ConceptPatternNode;
import el.structure.SubsumptionPattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TBoxElkReasonerTest {

    private static final String BASE_IRI = "http://example.com/research-project#";

    @TempDir
    Path tempDir;

    @Test
    void directSubsumption() throws Exception {
        Path tBox = write("direct-tbox.txt", "A ⊑ B\n");
        try (TBoxElkReasoner elk = new TBoxElkReasoner(tBox, BASE_IRI)) {
            assertTrue(elk.subsumes("A", "B"));
        }
    }

    @Test
    void transitiveSubsumptionIsObtainedFromElk() throws Exception {
        Path tBox = write("transitive-tbox.txt", "A ⊑ B\nB ⊑ C\n");
        try (TBoxElkReasoner elk = new TBoxElkReasoner(tBox, BASE_IRI)) {
            assertTrue(elk.subsumes("A", "C"));
        }
    }

    @Test
    void wrongDirectionIsFalse() throws Exception {
        Path tBox = write("direction-tbox.txt", "A ⊑ B\n");
        try (TBoxElkReasoner elk = new TBoxElkReasoner(tBox, BASE_IRI)) {
            assertFalse(elk.subsumes("B", "A"));
        }
    }

    @Test
    void existentialRestrictionIsMonotone() throws Exception {
        Path tBox = write("existential-tbox.txt", "A ⊑ B\n");
        try (TBoxElkReasoner elk = new TBoxElkReasoner(tBox, BASE_IRI)) {
            assertTrue(elk.subsumes("∃r.A", "∃r.B"));
        }
    }

    @Test
    void conjunctionIsSubsumedByEachConjunct() throws Exception {
        Path tBox = write("empty-tbox.txt", "# empty TBox\n");
        try (TBoxElkReasoner elk = new TBoxElkReasoner(tBox, BASE_IRI)) {
            assertTrue(elk.subsumes("A ⊓ B", "A"));
        }
    }

    @Test
    void tBoxRejectsVariablesWithContext() throws Exception {
        Path tBox = write("variable-tbox.txt", "A ⊑ _X_\n");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new TBoxElkReasoner(tBox, BASE_IRI));

        assertTrue(exception.getMessage().contains("variable-tbox.txt:1"));
        assertTrue(exception.getMessage().contains("A ⊑ _X_"));
        assertTrue(exception.getMessage().contains(
                "Variables are not allowed in TBox."));
    }

    @Test
    void gammaIsReadAsStructuredPatterns() throws Exception {
        Path tBox = write("gamma-structure-tbox.txt", "# empty\n");
        Path gamma = write("gamma-structure.txt", String.join("\n",
                "A ⊑? _X_",
                "∃r._X_ ⊑? ∃r.B",
                "A ⊓ B ⊑? C") + "\n");

        try (TBoxElkReasoner elk = new TBoxElkReasoner(tBox, BASE_IRI)) {
            GammaExpressionList list = new GammaExpressionList(gamma, elk);
            assertEquals(3, list.size());

            assertEquals(ConceptPatternNode.Type.VARIABLE, list.get(0).right.type);

            assertEquals(ConceptPatternNode.Type.EXISTENTIAL, list.get(1).left.type);
            assertEquals(ConceptPatternNode.Type.VARIABLE,
                    list.get(1).left.existentialFiller.type);
            assertEquals(ConceptPatternNode.Type.EXISTENTIAL, list.get(1).right.type);

            assertEquals(ConceptPatternNode.Type.CONJUNCTION, list.get(2).left.type);
        }
    }

    @Test
    void gammaAddIncreasesSize() throws Exception {
        Path tBox = write("add-tbox.txt", "# empty\n");
        try (TBoxElkReasoner elk = new TBoxElkReasoner(tBox, BASE_IRI)) {
            GammaExpressionList list = new GammaExpressionList(elk);
            int before = list.size();
            SubsumptionPattern added = list.add("A ⊑? B");

            assertEquals(before + 1, list.size());
            assertEquals(added, list.get(list.size() - 1));
        }
    }

    @Test
    void gammaRemoveDoesNotChangeOntology() throws Exception {
        Path tBox = write("remove-tbox.txt", "A ⊑ B\n");
        try (TBoxElkReasoner elk = new TBoxElkReasoner(tBox, BASE_IRI)) {
            GammaExpressionList list = new GammaExpressionList(elk);
            list.add("A ⊑? B");
            int tBoxCountBefore = elk.getTBoxAxioms().size();
            int ontologyCountBefore = elk.getAxiomCount();

            list.remove(0);

            assertTrue(list.isEmpty());
            assertEquals(tBoxCountBefore, elk.getTBoxAxioms().size());
            assertEquals(ontologyCountBefore, elk.getAxiomCount());
        }
    }

    @Test
    void gammaReplaceMakesExpressionGroundWithoutChangingOntology() throws Exception {
        Path tBox = write("replace-tbox.txt", "A ⊑ B\nB ⊑ C\n");
        try (TBoxElkReasoner elk = new TBoxElkReasoner(tBox, BASE_IRI)) {
            GammaExpressionList list = new GammaExpressionList(elk);
            list.add("A ⊑? _X_");
            int sizeBefore = list.size();
            int ontologyCountBefore = elk.getAxiomCount();

            list.replace(0, "A ⊑? C");

            assertEquals(sizeBefore, list.size());
            assertTrue(list.isGround(list.get(0)));
            assertEquals(ontologyCountBefore, elk.getAxiomCount());
        }
    }

    @Test
    void variableGammaCannotBeQueriedByElk() throws Exception {
        Path tBox = write("strict-query-tbox.txt", "A ⊑ B\n");
        try (TBoxElkReasoner elk = new TBoxElkReasoner(tBox, BASE_IRI)) {
            GammaExpressionList list = new GammaExpressionList(elk);
            list.add("A ⊑? _X_");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> list.checkSubsumption(0));

            assertEquals(
                    "Gamma expression contains a variable and cannot be queried by ELK.",
                    exception.getMessage());
        }
    }

    @Test
    void groundGammaIsQueriedAgainstTBox() throws Exception {
        Path tBox = write("ground-query-tbox.txt", "A ⊑ B\nB ⊑ C\n");
        try (TBoxElkReasoner elk = new TBoxElkReasoner(tBox, BASE_IRI)) {
            GammaExpressionList list = new GammaExpressionList(elk);
            list.add("A ⊑? C");

            assertTrue(list.checkSubsumption(0));
        }
    }

    @Test
    void allGammaMutationsLeaveOntologyUntouched() throws Exception {
        Path tBox = write("immutable-ontology-tbox.txt", "A ⊑ B\nB ⊑ C\n");
        try (TBoxElkReasoner elk = new TBoxElkReasoner(tBox, BASE_IRI)) {
            GammaExpressionList list = new GammaExpressionList(elk);
            int before = elk.getAxiomCount();

            list.add("A ⊑? _X_");
            list.add("A ⊑? B");
            list.replace(0, "A ⊑? C");
            list.remove(1);
            list.clear();

            assertEquals(before, elk.getAxiomCount());
        }
    }

    @Test
    void batchQuerySkipsNonGroundGamma() throws Exception {
        Path tBox = write("batch-tbox.txt", "A ⊑ B\nB ⊑ C\n");
        try (TBoxElkReasoner elk = new TBoxElkReasoner(tBox, BASE_IRI)) {
            GammaExpressionList list = new GammaExpressionList(elk);
            list.add("A ⊑? B");
            list.add("A ⊑? _X_");
            list.add("∃r.A ⊑? ∃r.B");

            Map<Integer, Boolean> results = list.checkAllGroundExpressions();

            assertEquals(Map.of(0, true, 2, true), results);
            assertEquals(3, list.size());
        }
    }

    @Test
    void parserKeepsTopLevelConjunctionOutsideExistential() throws Exception {
        Path tBox = write("parser-tbox.txt", "# empty\n");
        try (TBoxElkReasoner elk = new TBoxElkReasoner(tBox, BASE_IRI)) {
            GammaExpressionList list = new GammaExpressionList(elk);
            SubsumptionPattern pattern = list.parseGammaLine(
                    "∃r._X_ ⊓ A ⊑? ∃r.(B ⊓ C)");

            assertEquals(ConceptPatternNode.Type.CONJUNCTION, pattern.left.type);
            assertEquals(ConceptPatternNode.Type.EXISTENTIAL,
                    pattern.left.conjunctions.get(0).type);
            assertEquals(ConceptPatternNode.Type.VARIABLE,
                    pattern.left.conjunctions.get(0).existentialFiller.type);
            assertEquals(ConceptPatternNode.Type.EXISTENTIAL, pattern.right.type);
            assertEquals(ConceptPatternNode.Type.CONJUNCTION,
                    pattern.right.existentialFiller.type);
        }
    }

    private Path write(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
