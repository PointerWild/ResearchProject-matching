package el;

import el.structure.ConceptPatternNode;

public interface SubsumptionOracle {
    boolean subsumes(ConceptPatternNode left, ConceptPatternNode right);
}
