package el;

import el.structure.ConceptPatternNode;
import el.structure.ConceptPatternOps;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConceptPatternOpsTest {

    @Test
    void returnsSingletonForSingleAtom() {
        List<ConceptPatternNode> atoms =
                ConceptPatternOps.topLevelAtoms(
                        ConceptPatternNode.parse("A")
                );

        assertEquals(
                List.of(
                        ConceptPatternNode.parse("A")
                ),
                atoms
        );
    }

    @Test
    void recursivelyFlattensNestedConjunction() {
        List<ConceptPatternNode> atoms =
                ConceptPatternOps.topLevelAtoms(
                        ConceptPatternNode.parse(
                                "A ⊓ (B ⊓ C)"
                        )
                );

        assertEquals(
                List.of(
                        ConceptPatternNode.parse("A"),
                        ConceptPatternNode.parse("B"),
                        ConceptPatternNode.parse("C")
                ),
                atoms
        );
    }

    @Test
    void tauProducesEmptyList() {
        List<ConceptPatternNode> atoms =
                ConceptPatternOps.topLevelAtoms(
                        ConceptPatternNode.parse("Tau")
                );

        assertEquals(
                List.of(),
                atoms
        );
    }

    @Test
    void doesNotEnterExistentialFiller() {
        ConceptPatternNode existential =
                ConceptPatternNode.parse(
                        "∃r.(A ⊓ B)"
                );

        List<ConceptPatternNode> atoms =
                ConceptPatternOps.topLevelAtoms(
                        ConceptPatternNode.parse(
                                "C ⊓ ∃r.(A ⊓ B)"
                        )
                );

        assertEquals(
                List.of(
                        ConceptPatternNode.parse("C"),
                        existential
                ),
                atoms
        );
    }

    @Test
    void keepsVariableAsAtom() {
        List<ConceptPatternNode> atoms =
                ConceptPatternOps.topLevelAtoms(
                        ConceptPatternNode.parse(
                                "A ⊓ _X_"
                        )
                );

        assertEquals(
                List.of(
                        ConceptPatternNode.parse("A"),
                        ConceptPatternNode.parse("_X_")
                ),
                atoms
        );
    }

    @Test
    void rejectsNullInput() {
        assertThrows(
                NullPointerException.class,
                () -> ConceptPatternOps.topLevelAtoms(null)
        );
    }
}