package el.structure;

public class ConceptPattern {
    public final ConceptPatternNode root;

    public ConceptPattern(ConceptPatternNode root) {
        if (!containsVariable(root)) {
            throw new IllegalArgumentException("el.structure.ConceptPattern must contain at least one variable.");
        }
        this.root = root;
    }

    private boolean containsVariable(ConceptPatternNode desc) {
        return switch (desc.type) {
            case VARIABLE -> true;
            case CONCEPT_NAME, TOP -> false;
            case EXISTENTIAL -> containsVariable(desc.existentialFiller);
            case CONJUNCTION -> desc.conjunctions.stream().anyMatch(this::containsVariable);
        };
    }

    @Override
    public String toString() {
        // 输出 root，但如果是 CONJUNCTION，手动去掉最外层括号
        String raw = root.toString();
        if (root.type == ConceptPatternNode.Type.CONJUNCTION
                && raw.startsWith("(") && raw.endsWith(")")) {
            return raw.substring(1, raw.length() - 1);  // 去除最外层括号
        }
        return raw;
    }

}
