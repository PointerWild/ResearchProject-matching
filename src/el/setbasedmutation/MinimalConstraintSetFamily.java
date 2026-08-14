package el.setbasedmutation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Delta1 = Delta2
 * → duplicate removal
 *
 * Delta1 ⊂ Delta2
 * → subset pruning
 *
 * ================================================================
 * Minimal Constraint Set Family
 * ================================================================
 *
 * Stores only inclusion-minimal Mutation branch deltas.
 *
 *
 * Example:
 *
 *     Delta1 =
 *     {
 *         X ⊑? A
 *     }
 *
 *
 *     Delta2 =
 *     {
 *         X ⊑? A
 *     }
 *
 *
 *     Delta3 =
 *     {
 *         X ⊑? A,
 *         Y ⊑? B
 *     }
 *
 *
 * We have:
 *
 *     Delta1 = Delta2
 *
 * so Delta2 is duplicate.
 *
 *
 * We also have:
 *
 *     Delta1 ⊂ Delta3
 *
 * so Delta3 contains strictly more constraints.
 *
 *
 * Therefore:
 *
 *     {
 *         Delta1,
 *         Delta2,
 *         Delta3
 *     }
 *
 *             |
 *             v
 *
 *         minimize
 *
 *             |
 *             v
 *
 *     {
 *         Delta1
 *     }
 *
 * ================================================================
 */
public final class MinimalConstraintSetFamily {

    private final List<
            Set<MutationConstraintKey>> sets =
            new ArrayList<>();


    /**
     * Adds one candidate constraint Set.
     *
     * @return true if the candidate is retained;
     *         false if it is duplicate or dominated.
     */
    public boolean add(
            Collection<MutationConstraintKey> values
    ) {

        /*
         * Copy into an independent Set.
         */
        Set<MutationConstraintKey> candidate =
                Set.copyOf(
                        new LinkedHashSet<>(
                                values
                        )
                );


        /*
         * ========================================================
         * Rule 1
         *
         * existing ⊆ candidate
         *
         * Example:
         *
         * existing:
         *
         *     { X ⊑? A }
         *
         * candidate:
         *
         *     {
         *         X ⊑? A,
         *         Y ⊑? B
         *     }
         *
         *
         * candidate has at least all constraints of existing.
         *
         * Therefore candidate is stronger and unnecessary.
         *
         * The equality case is also handled here:
         *
         *     existing = candidate
         *
         * ========================================================
         */
        for (Set<MutationConstraintKey> existing
                : sets) {

            if (candidate.containsAll(
                    existing
            )) {

                return false;
            }
        }


        /*
         * ========================================================
         * Rule 2
         *
         * candidate ⊂ existing
         *
         * Then candidate is the weaker / better branch.
         *
         * Remove all existing supersets.
         * ========================================================
         */
        sets.removeIf(
                existing ->
                        existing.containsAll(
                                candidate
                        )
        );


        sets.add(
                candidate
        );


        return true;
    }


    public List<Set<MutationConstraintKey>> values() {

        return List.copyOf(
                sets
        );
    }


    public boolean isEmpty() {

        return sets.isEmpty();
    }


    public int size() {

        return sets.size();
    }
}