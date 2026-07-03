package el;

import el.structure.ConceptPatternNode;

public class StructuralSubsumptionOracle implements SubsumptionOracle {

    @Override
    public boolean subsumes(ConceptPatternNode left, ConceptPatternNode right) {
        if (right.type == ConceptPatternNode.Type.TOP ||
                right.type == ConceptPatternNode.Type.VARIABLE) {
            return true;
        }

        return left.toString().equals(right.toString());
    }
}