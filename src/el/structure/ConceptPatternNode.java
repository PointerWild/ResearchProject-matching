package el.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;


public class ConceptPatternNode {
    public enum Type { TOP, CONCEPT_NAME, VARIABLE, CONJUNCTION, EXISTENTIAL }

    public final Type type;
    public ConceptName conceptName;
    public VariableName variable;
    public List<ConceptPatternNode> conjunctions;
    public RoleName role;
    public ConceptPatternNode existentialFiller;

    public static ConceptPatternNode Tau() {
        return new ConceptPatternNode(Type.TOP);
    }

    public static ConceptPatternNode fromConcept(ConceptName name) {
        ConceptPatternNode desc = new ConceptPatternNode(Type.CONCEPT_NAME);
        desc.conceptName = name;
        return desc;
    }

    public static ConceptPatternNode fromVariable(VariableName var) {
        ConceptPatternNode desc = new ConceptPatternNode(Type.VARIABLE);
        desc.variable = var;
        return desc;
    }

    public static ConceptPatternNode conj(List<ConceptPatternNode> parts) {
        ConceptPatternNode desc = new ConceptPatternNode(Type.CONJUNCTION);
        desc.conjunctions = parts;
        return desc;
    }

    public static ConceptPatternNode exists(RoleName role, ConceptPatternNode filler) {
        ConceptPatternNode desc = new ConceptPatternNode(Type.EXISTENTIAL);
        desc.role = role;
        desc.existentialFiller = filler;
        return desc;
    }

    public ConceptPatternNode(Type type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return switch (type) {
            case TOP -> "Tau";
            case CONCEPT_NAME -> conceptName.toString();
            case VARIABLE -> variable.toString();
            case CONJUNCTION -> conjunctions.size() == 1
                    ? conjunctions.get(0).toString()
                    : "(" + String.join(" ⊓ ", conjunctions.stream().map(Object::toString).toList()) + ")";
            case EXISTENTIAL -> "∃" + role + "." + existentialFiller.toString();  // ← 不加额外括号
        };
    }

    /**
     * Parse an EL‐pattern string (possibly a conjunction) into a node.
     */
    public static ConceptPatternNode parse(String s) {
        return new Parser(s.trim()).parseConj();
    }

    // —— 内部递归解析器 —— //
    private static class Parser {
        private final String in;
        private int pos = 0;

        Parser(String input) {
            this.in = input;
        }

        // Parse conjunction := Atom ('⊓' Atom)*
        ConceptPatternNode parseConj() {
            skipWS();
            List<ConceptPatternNode> parts = new ArrayList<>();
            parts.add(parseAtom());
            skipWS();
            while (match('⊓')) {
                skipWS();
                parts.add(parseAtom());
                skipWS();
            }
            if (parts.size() == 1) {
                return parts.get(0);
            }
            return conj(parts);
        }

        // Parse Atom := 'Tau' | variable | concept name | ∃r.Conj | '(' Conj ')'
        ConceptPatternNode parseAtom() {
            skipWS();
            if (matchLiteral("Tau")) {
                return Tau();
            }
            char c = peek();
            // variable
            if (c == '_') {
                String tok = parseRegex("_[A-Z]+_");
                return fromVariable(new VariableName(tok));
            }
            // concept name
            if (Character.isUpperCase(c)) {
                String tok = parseRegex("[A-Z]+");
                return fromConcept(new ConceptName(tok));
            }
            // existential
            if (match('∃')) {
                String roleName = parseRegex("[a-z]+");
                expect('.');
                ConceptPatternNode filler = parseConj();
                return exists(new RoleName(roleName), filler);
            }
            // parenthesized
            if (match('(')) {
                ConceptPatternNode inside = parseConj();
                skipWS();
                expect(')');
                return inside;
            }
            throw new IllegalArgumentException("Unexpected character at pos " + pos + ": " + in);
        }

        // —— 工具方法 —— //
        private void skipWS() {
            while (pos < in.length() && Character.isWhitespace(in.charAt(pos))) pos++;
        }

        private char peek() {
            skipWS();
            return pos < in.length() ? in.charAt(pos) : '\0';
        }

        private boolean match(char c) {
            skipWS();
            if (pos < in.length() && in.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        private void expect(char c) {
            if (!match(c)) {
                throw new IllegalArgumentException("Expected '" + c + "' at pos " + pos + " in " + in);
            }
        }

        private boolean matchLiteral(String lit) {
            skipWS();
            if (in.startsWith(lit, pos)) {
                pos += lit.length();
                return true;
            }
            return false;
        }

        private String parseRegex(String pattern) {
            skipWS();
            Pattern p = Pattern.compile("^" + pattern);
            Matcher m = p.matcher(in.substring(pos));
            if (!m.find()) {
                throw new IllegalArgumentException("Expected /" + pattern + "/ at pos " + pos);
            }
            String tok = m.group();
            pos += tok.length();
            return tok;
        }
    }

}
