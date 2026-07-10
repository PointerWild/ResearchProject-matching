package el;

import el.structure.ConceptName;
import el.structure.VariableName;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

//  LL(k)／Recursive-descent  checking
//  <el.structure.ConceptPattern> ⊑ <el.structure.ConceptPattern>

public class ELSyntaxChecker {

    private final String input;
    private int pos = 0;

    private ELSyntaxChecker(String s) {
        this.input = s;
    }

    public ELSyntaxChecker() {
        input = new String();
    }

    public static void validate(String s) {
        ELSyntaxChecker parser = new ELSyntaxChecker(s);
        parser.parseSentence();
    }

    /** Public API: returns true iff s is a valid EL ⊑-sentence **/
    public static boolean isValid(String s) {
        try {
            ELSyntaxChecker p = new ELSyntaxChecker(s);
            p.parseSentence();
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** Sentence := Conj ⊑ Conj EOF **/
    private void parseSentence() {
        skipWS();
        parseConj();              // left pattern
        skipWS();
        expect('⊑');              // exactly one subsumption
        skipWS();
        parseConj();              // right pattern
        skipWS();
        if (pos != input.length()) {
            throw new IllegalArgumentException(
                    "Extra characters after valid sentence at pos " + pos);
        }
    }

    /** Conj := el.structure.Atom ( '⊓' el.structure.Atom )* **/
    private void parseConj() {
        parseAtom();
        skipWS();
        while (match('⊓')) {
            skipWS();
            parseAtom();
            skipWS();
        }
    }

    /**
     * el.structure.Atom := 'Tau'
     *       | Variable  '_[A-Z][A-Za-z0-9]*_'
     *       | el.structure.ConceptName  [A-Z][A-Za-z0-9]*'
     *       | '∃' Role '.' Conj
     *       | '(' Conj ')'
     */
    private void parseAtom() {
        skipWS();
        // Tau (Top)
        if (matchLiteral("Tau")) {
            return;
        }
        // Variable: _X_, _YY_, etc.
        if (peek() == '_') {
            parseRegex(
                    VariableName.REGEX
            );

            return;
        }
        // el.structure.ConceptName: A, B, C...
        if (Character.isUpperCase(peek())) {
            parseRegex(
                    ConceptName.REGEX
            );

            return;
        }
        // ∃r. pattern
        if (match('∃')) {
            skipWS();
            parseRegex("[a-z]+");  // role
            skipWS();
            expect('.');           // separator
            skipWS();
            parseConj();           // filler is a full conjunction/pattern
            return;
        }
        // Parenthesized
        if (match('(')) {
            skipWS();
            parseConj();
            skipWS();
            expect(')');
            return;
        }
        throw new IllegalArgumentException(
                "Expected a concept atom at pos " + pos);
    }

    // —— Utility methods —— //

    private void skipWS() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        skipWS();
        if (pos < input.length()) {
            return input.charAt(pos);
        }
        return '\0';
    }

    private boolean match(char c) {
        skipWS();
        if (pos < input.length() && input.charAt(pos) == c) {
            pos++;
            return true;
        }
        return false;
    }

    private boolean matchLiteral(String lit) {
        skipWS();
        if (input.startsWith(lit, pos)) {
            pos += lit.length();
            return true;
        }
        return false;
    }

    private void expect(char c) {
        if (!match(c)) {
            throw new IllegalArgumentException(
                    "Expected '" + c + "' at pos " + pos);
        }
    }

    /**
     * Tries to match regex ^pattern at current pos.
     * Advances pos on success, or throws on failure.
     */
    private void parseRegex(String pattern) {
        skipWS();
        Pattern p = Pattern.compile("^" + pattern);
        Matcher m = p.matcher(input.substring(pos));
        if (!m.find()) {
            throw new IllegalArgumentException(
                    "Expected /" + pattern + "/ at pos " + pos);
        }
        String tok = m.group();
        pos += tok.length();
    }



}
