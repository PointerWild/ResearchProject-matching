package el.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                    : "(" + String.join(" ⊓ ",
                    conjunctions.stream().map(Object::toString).toList()) + ")";
            case EXISTENTIAL -> "∃" + role + "." + existentialFiller.toString();
        };
    }

    /**
     * Parses one complete EL pattern expression.
     *
     * @param expression complete expression text
     * @return parsed expression tree
     * @throws IllegalArgumentException if the input is null, malformed, or has trailing text
     */
    public static ConceptPatternNode parse(String expression) {
        if (expression == null) {
            throw new IllegalArgumentException("Expression cannot be null.");
        }

        Parser parser = new Parser(expression.trim());
        ConceptPatternNode result = parser.parseConj();
        parser.skipWS();
        if (parser.pos != parser.in.length()) {
            throw new IllegalArgumentException(
                    "Extra characters at pos " + parser.pos + ": " + parser.in);
        }
        return result;
    }

    private static class Parser {
        private final String in;
        private int pos = 0;

        Parser(String input) {
            this.in = input;
        }

        // Conj := Atom ('⊓' Atom)*
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

        // Atom := 'Tau' | variable | concept name | ∃r.Atom | '(' Conj ')'
        ConceptPatternNode parseAtom() {
            skipWS();
            if (matchLiteral("Tau")) {
                return Tau();
            }
            char c = peek();
            if (c == '_') {
                String tok = parseRegex("_[A-Z]+_");
                return fromVariable(new VariableName(tok));
            }
            if (Character.isUpperCase(c)) {
                String tok = parseRegex("[A-Z]+");
                return fromConcept(new ConceptName(tok));
            }
            if (match('∃')) {
                String roleName = parseRegex("[a-z]+");
                expect('.');
                // Use one atom as an unparenthesized filler. A conjunction filler
                // remains supported through explicit parentheses: ∃r.(A ⊓ B).
                ConceptPatternNode filler = parseAtom();
                return exists(new RoleName(roleName), filler);
            }
            if (match('(')) {
                ConceptPatternNode inside = parseConj();
                skipWS();
                expect(')');
                return inside;
            }
            throw new IllegalArgumentException(
                    "Unexpected character at pos " + pos + ": " + in);
        }

        private void skipWS() {
            while (pos < in.length() && Character.isWhitespace(in.charAt(pos))) {
                pos++;
            }
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
                throw new IllegalArgumentException(
                        "Expected '" + c + "' at pos " + pos + " in " + in);
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
                throw new IllegalArgumentException(
                        "Expected /" + pattern + "/ at pos " + pos);
            }
            String tok = m.group();
            pos += tok.length();
            return tok;
        }
    }


    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof ConceptPatternNode other)) {
            return false;
        }

        /*
         * VARIABLE and CONCEPT_NAME can never be equal because their types
         * are different.
         */
        if (type != other.type) {
            return false;
        }

        return switch (type) {
            case TOP ->
                    true;

            case CONCEPT_NAME ->
                    Objects.equals(
                            conceptName,
                            other.conceptName
                    );

            case VARIABLE ->
                    Objects.equals(
                            variable,
                            other.variable
                    );

            case EXISTENTIAL ->
                    Objects.equals(
                            role,
                            other.role
                    )
                            && Objects.equals(
                            existentialFiller,
                            other.existentialFiller
                    );

            case CONJUNCTION ->
                    Objects.equals(
                            conjunctions,
                            other.conjunctions
                    );
        };
    }

    @Override
    public int hashCode() {
        return switch (type) {
            case TOP ->
                    Objects.hash(type);

            case CONCEPT_NAME ->
                    Objects.hash(
                            type,
                            conceptName
                    );

            case VARIABLE ->
                    Objects.hash(
                            type,
                            variable
                    );

            case EXISTENTIAL ->
                    Objects.hash(
                            type,
                            role,
                            existentialFiller
                    );

            case CONJUNCTION ->
                    Objects.hash(
                            type,
                            conjunctions
                    );
        };
    }
}
