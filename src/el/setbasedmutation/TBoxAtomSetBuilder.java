package el.setbasedmutation;

import el.ELAnalyze;
import el.structure.ConceptPatternNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ================================================================
 * TBox Atom Set Builder
 * ================================================================
 *
 * Extracts all atoms occurring in the TBox.
 *
 *
 * Example:
 *
 * TBox:
 *
 *     ∃r.(A ⊓ ∃s.B) ⊑ C
 *
 *
 * Atom Set:
 *
 *     {
 *         ∃r.(A ⊓ ∃s.B),
 *         A,
 *         ∃s.B,
 *         B,
 *         C
 *     }
 *
 *
 * Important:
 *
 * Unlike topLevelAtoms(), this class recursively enters
 * existential fillers.
 *
 * ================================================================
 */
public final class TBoxAtomSetBuilder {

    private TBoxAtomSetBuilder() {
    }


    public static List<ConceptPatternNode> build(
            ELAnalyze analyze
    ) {

        LinkedHashSet<ConceptPatternNode> atoms =
                new LinkedHashSet<>();


        for (String line
                : analyze.getTBoxLines()) {

            String[] sides =
                    splitTBoxLine(
                            line
                    );


            ConceptPatternNode left =
                    ConceptPatternNode.parse(
                            sides[0]
                    );


            ConceptPatternNode right =
                    ConceptPatternNode.parse(
                            sides[1]
                    );


            collectAtoms(
                    left,
                    atoms
            );


            collectAtoms(
                    right,
                    atoms
            );
        }


        return List.copyOf(
                atoms
        );
    }


    /**
     * Recursively collects all TBox atoms.
     */
    private static void collectAtoms(
            ConceptPatternNode node,
            Set<ConceptPatternNode> result
    ) {

        switch (node.type) {

            /*
             * Tau is represented by the empty premise Set.
             */
            case TOP -> {
            }


            /*
             * Concept names are atoms.
             */
            case CONCEPT_NAME ->

                    result.add(
                            node
                    );


            /*
             * TBox should be ground.
             *
             * Kept defensively here.
             */
            case VARIABLE ->

                    result.add(
                            node
                    );


            /*
             * A conjunction itself is not an atom.
             *
             * Recursively inspect every conjunct.
             */
            case CONJUNCTION -> {

                for (ConceptPatternNode child
                        : node.conjunctions) {

                    collectAtoms(
                            child,
                            result
                    );
                }
            }


            /*
             * An existential restriction IS an atom.
             *
             * Example:
             *
             *     ∃r.(A ⊓ B)
             *
             * is one atom.
             *
             * We also recursively inspect:
             *
             *     A ⊓ B
             */
            case EXISTENTIAL -> {

                result.add(
                        node
                );


                collectAtoms(
                        node.existentialFiller,
                        result
                );
            }
        }
    }


    /**
     * Splits:
     *
     *     C ⊑ D
     *
     * into:
     *
     *     [C, D]
     */
    private static String[] splitTBoxLine(
            String line
    ) {

        String cleaned =
                line.trim();


        int index =
                cleaned.indexOf(
                        '⊑'
                );


        if (index < 0) {

            throw new IllegalArgumentException(
                    "Invalid TBox line: "
                            + line
            );
        }


        String left =
                cleaned
                        .substring(
                                0,
                                index
                        )
                        .trim();


        String right =
                cleaned
                        .substring(
                                index + 1
                        )
                        .trim();


        return new String[]{
                left,
                right
        };
    }
}