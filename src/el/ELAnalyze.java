package el;

import el.structure.ConceptPatternNode;
import el.structure.ConceptName;
import el.structure.RoleName;
import static el.structure.PatternDSL.*;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.*;
import java.util.AbstractMap.SimpleEntry;

public class ELAnalyze {

    // —— 原有字段和方法 —— //
    // Private storage for validated Gamma and TBox lines
    private List<String> gammaLines = new ArrayList<>();
    private List<String> tboxLines  = new ArrayList<>();
    private String bottom;

    private SubsumptionOracle subsumptionOracle = new StructuralSubsumptionOracle();

    // Mock subsumption map
    private final Map<String, Boolean> mockMap = new HashMap<>();

    /** 注册一条结构化的 mock subsumption 规则 */
    public void setMockSubsumption(ConceptPatternNode left, ConceptPatternNode right, boolean result) {
        mockMap.put(makeKey(left, right), result);
    }

    /** 生成 key，用于 mockMap 存取 */
    private String makeKey(ConceptPatternNode left, ConceptPatternNode right) {
        return left.toString() + " ⊑ " + right.toString();
    }

    public boolean subsumes(ConceptPatternNode left, ConceptPatternNode right) {
        String key = makeKey(left, right);

        if (mockMap.containsKey(key)) {
            boolean result = mockMap.get(key);
            System.out.printf("[MockSubsumes] %s → %b%n", key, result);
            return result;
        }

        boolean result = subsumptionOracle.subsumes(left, right);
        System.out.printf("[SubsumptionOracle] %s → %b%n", key, result);
        return result;
    }

    /** 旧的基于 String 的随机 mock 版本（保留） */
    public boolean subsumes(String c, String d) {
        boolean result = ThreadLocalRandom.current().nextBoolean();
        System.out.printf("[Mock] Check subsumption: %s ⊑ %s → %b%n", c, d, result);
        return result;
    }

    // —— Gamma/TBox 加载与管理 —— //

    public void loadGamma(String filePath) throws IOException {
        List<String> all = Files.readAllLines(Paths.get(filePath));
        gammaLines.clear();
        for (String line : all) {
            if (ELSyntaxChecker.isValid(line)) {
                gammaLines.add(line);
            } else {
                System.err.println("Invalid Gamma syntax: '" + line + "'");
            }
        }
    }

    public void loadTBox(String filePath) throws IOException {
        List<String> all = Files.readAllLines(Paths.get(filePath));
        tboxLines.clear();
        for (String line : all) {
            if (ELSyntaxChecker.isValid(line)) {
                tboxLines.add(line);
            } else {
                System.err.println("Invalid TBox syntax: '" + line + "'");
            }
        }
    }

    public List<String> getGammaLines() {
        return new ArrayList<>(gammaLines);
    }

    public void setGammaLines(List<String> lines) {
        this.gammaLines = new ArrayList<>(lines);
    }

    public List<String> getTBoxLines() {
        return new ArrayList<>(tboxLines);
    }

    public void setTBoxLines(List<String> lines) {
        this.tboxLines = new ArrayList<>(lines);
    }

    public String getBottom() {
        return bottom;
    }

    public void setBottom(String bottom) {
        this.bottom = bottom;
    }

    public void replaceLeftGroundGammaVariablesWithTop() {
        Pattern varPattern = Pattern.compile("_[A-Z]+_");
        List<String> replaced = new ArrayList<>();
        for (String line : gammaLines) {
            String newLine = varPattern.matcher(line).replaceAll("Tau");
            replaced.add(newLine);
        }
        this.gammaLines = replaced;
    }

    public void replaceRightGroundGammaVariablesWithBottom() {
        if (bottom == null) {
            computeBottom();
        }
        Pattern varPattern = Pattern.compile("_[A-Z]+_");
        List<String> replaced = new ArrayList<>();
        for (String line : gammaLines) {
            Matcher m = varPattern.matcher(line);
            String newLine = m.replaceAll(Matcher.quoteReplacement("(" + bottom + ")"));
            replaced.add(newLine);
        }
        this.gammaLines = replaced;
    }

    public void computeBottom() {
        Set<String> atoms = new LinkedHashSet<>();
        for (String gci : tboxLines) {
            String[] parts = gci.split("\\s*⊑\\s*");
            if (parts.length == 2) {
                atoms.addAll(extractTopLevelAtoms(parts[0].trim()));
                atoms.addAll(extractTopLevelAtoms(parts[1].trim()));
            }
        }
        for (String gci : gammaLines) {
            String[] parts = gci.split("\\s*⊑\\s*");
            if (parts.length == 2) {
                atoms.addAll(extractTopLevelAtoms(parts[1].trim()));
            }
        }
        this.bottom = String.join(" ⊓ ", atoms);
    }

    /**
     * Returns all atoms occurring in the TBox:
     * both the top-level atoms on the left-hand side of each GCI
     * and the atom on the right-hand side.
     */
    public List<ConceptPatternNode> getTBoxAtoms() {
        List<ConceptPatternNode> atoms = new ArrayList<>();
        for (String gci : tboxLines) {
            String[] parts = gci.split("\\s*⊑\\s*");
            if (parts.length != 2) continue;
            // left side may be conjunction of atoms
            List<String> leftStrs = splitTopLevel(parts[0].trim(), '⊓');
            for (String s : leftStrs) {
                atoms.add(parseAtomNode(s.trim()));
            }
            // right side is a single atom
            atoms.add(parseAtomNode(parts[1].trim()));
        }
        return atoms;
    }

    /**
     * Returns the list of GCIs, each as an entry
     * (List of left atoms A₁…Aₖ, single right atom B).
     */
    public List<SimpleEntry<List<ConceptPatternNode>,ConceptPatternNode>> getTBoxGCIs() {
        List<SimpleEntry<List<ConceptPatternNode>,ConceptPatternNode>> result = new ArrayList<>();
        for (String gci : tboxLines) {
            String[] parts = gci.split("\\s*⊑\\s*");
            if (parts.length != 2) continue;
            List<ConceptPatternNode> leftAtoms = new ArrayList<>();
            for (String s : splitTopLevel(parts[0].trim(), '⊓')) {
                leftAtoms.add(parseAtomNode(s.trim()));
            }
            ConceptPatternNode rightAtom = parseAtomNode(parts[1].trim());
            result.add(new SimpleEntry<>(leftAtoms, rightAtom));
        }
        return result;
    }

    // —— 以下为解析辅助方法 —— //

    /**
     * Parse a single atom into a ConceptPatternNode,
     * using PatternDSL factories.
     */
    private ConceptPatternNode parseAtomNode(String atom) {
        atom = atom.trim();
        // 纯大写概念名
        if (atom.matches("[A-Z]+")) {
            return concept(atom);
        }
        // ∃r.Filler
        if (atom.startsWith("∃")) {
            int dot = atom.indexOf('.');
            String role = atom.substring(1, dot).trim();
            String filler = atom.substring(dot + 1).trim();
            ConceptPatternNode fillerNode = parseConjPattern(filler);
            return some(role, fillerNode);
        }
        throw new IllegalArgumentException("Invalid atom in TBox: " + atom);
    }

    /**
     * Parse a (possibly conjunctive) pattern into a ConceptPatternNode.
     * Delegates to PatternDSL.and(...) or parseAtomNode(...)
     */
    private ConceptPatternNode parseConjPattern(String s) {
        List<String> parts = splitTopLevel(s, '⊓');
        if (parts.size() == 1) {
            return parseAtomNode(parts.get(0));
        }
        // 多重顶层 ⊓ 时用 and(...)
        List<ConceptPatternNode> children = new ArrayList<>();
        for (String p : parts) {
            children.add(parseAtomNode(p.trim()));
        }
        return and(children);
    }





    private List<String> extractTopLevelAtoms(String desc) {
        List<String> atoms = new ArrayList<>();
        for (String term : splitTopLevel(desc, '⊓')) {
            String t = term.trim();
            if (t.matches("[A-Z]+") || t.matches("∃[a-z]+\\..+")) {
                atoms.add(t);
            }
        }
        return atoms;
    }


    /**
     * Splits s at top-level occurrences of separator sep, ignoring parentheses.
     * (This method was already present; reprinted here for clarity.)
     */
    private List<String> splitTopLevel(String s, char sep) {
        List<String> parts = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == sep && depth == 0) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(s.substring(start));
        return parts;
    }

    public void setSubsumptionOracle(SubsumptionOracle subsumptionOracle) {
        if (subsumptionOracle == null) {
            throw new IllegalArgumentException("subsumptionOracle cannot be null");
        }
        this.subsumptionOracle = subsumptionOracle;
    }

    public void enableElkReasoner(String baseIri) {
        this.subsumptionOracle = new ElkSubsumptionOracle(this.tboxLines, baseIri);
    }

}
