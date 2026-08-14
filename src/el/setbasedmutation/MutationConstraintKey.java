package el.setbasedmutation;

import el.structure.ConceptPatternNode;

import java.util.Map;
import java.util.Objects;

/**
 * 把 Dec 产生的 constraint
 * 变成 Set element
 *
 * Represents one generated matching constraint:
 *
 *     C ⊑? D
 *
 * In Set-based Mutation, one Mutation branch is represented as:
 *
 *     Set<MutationConstraintKey>
 */
public record MutationConstraintKey(
        ConceptPatternNode left,
        ConceptPatternNode right
) {

    public MutationConstraintKey {

        Objects.requireNonNull(
                left,
                "left cannot be null"
        );

        Objects.requireNonNull(
                right,
                "right cannot be null"
        );
    }


    /**
     * Converts a Dec-generated subgoal into a Set element.
     */
    public static MutationConstraintKey from(
            Map.Entry<
                    ConceptPatternNode,
                    ConceptPatternNode> entry
    ) {

        return new MutationConstraintKey(
                entry.getKey(),
                entry.getValue()
        );
    }


    @Override
    public String toString() {

        return left
                + " ⊑? "
                + right;
    }
}