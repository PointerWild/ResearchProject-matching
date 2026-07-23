package el;

import el.structure.ConceptName;
import el.structure.ConceptPatternNode;
import el.structure.ConceptPatternOps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides the data and semantic-query services used by the EL matching
 * implementation.
 *
 * <p>This class does not create or own an ELK reasoner. A real
 * {@link SubsumptionOracle} must be created by the application entry point
 * and injected before Algorithm 5.1 is started.
 */
public class ELAnalyze {

    private List<String> gammaLines = new ArrayList<>();
    private List<String> tboxLines = new ArrayList<>();

    private String bottom;

    /**
     * Semantic subsumption provider.
     *
     * <p>In production this should normally be an
     * {@link ElkSubsumptionOracle}.
     */
    private SubsumptionOracle subsumptionOracle;

    /**
     * Controls optional semantic-query output.
     */
    private boolean debug;

    /**
     * Creates an analyzer without a configured semantic oracle.
     *
     * <p>The oracle must be supplied by calling
     * {@link #setSubsumptionOracle(SubsumptionOracle)} before constructing
     * {@link GoalOrientedMatcher}.
     */
    public ELAnalyze() {
        this.debug = false;
    }

    /**
     * Creates an analyzer with an already configured semantic oracle.
     *
     * @param subsumptionOracle semantic subsumption provider
     */
    public ELAnalyze(SubsumptionOracle subsumptionOracle) {
        this();
        setSubsumptionOracle(subsumptionOracle);
    }

    // ---------------------------------------------------------------------
    // Semantic subsumption
    // ---------------------------------------------------------------------

    /**
     * Checks semantic subsumption using the configured oracle.
     *
     * <p>This method does not implement structural matching, mocking or
     * random results. Ground expressions are delegated to the configured
     * semantic oracle.
     *
     * @param left  candidate subclass expression
     * @param right candidate superclass expression
     * @return true iff the configured oracle entails left ⊑ right
     */
    public boolean subsumes(
            ConceptPatternNode left,
            ConceptPatternNode right
    ) {
        Objects.requireNonNull(left, "left cannot be null");
        Objects.requireNonNull(right, "right cannot be null");

        SubsumptionOracle oracle = requireSubsumptionOracle();

        boolean result = oracle.subsumes(left, right);

        if (debug) {
            System.out.printf(
                    "[SemanticSubsumption] %s ⊑ %s -> %b%n",
                    left,
                    right,
                    result
            );
        }

        return result;
    }

    /**
     * Parses two textual EL expressions and delegates the semantic check to
     * the configured oracle.
     *
     * @param left  textual subclass expression
     * @param right textual superclass expression
     * @return true iff the configured oracle entails left ⊑ right
     */
    public boolean subsumes(
            String left,
            String right
    ) {
        Objects.requireNonNull(left, "left cannot be null");
        Objects.requireNonNull(right, "right cannot be null");

        return subsumes(
                ConceptPatternNode.parse(left),
                ConceptPatternNode.parse(right)
        );
    }

    /**
     * Configures the semantic subsumption provider used by Algorithm 5.1.
     *
     * @param subsumptionOracle semantic subsumption provider
     */
    public void setSubsumptionOracle(
            SubsumptionOracle subsumptionOracle
    ) {
        this.subsumptionOracle = Objects.requireNonNull(
                subsumptionOracle,
                "subsumptionOracle cannot be null"
        );
    }

    /**
     * Returns whether a semantic oracle has already been configured.
     */
    public boolean hasSubsumptionOracle() {
        return subsumptionOracle != null;
    }

    /**
     * Returns the configured semantic oracle.
     *
     * @throws IllegalStateException if no oracle has been configured
     */
    public SubsumptionOracle getSubsumptionOracle() {
        return requireSubsumptionOracle();
    }

    private SubsumptionOracle requireSubsumptionOracle() {
        if (subsumptionOracle == null) {
            throw new IllegalStateException(
                    "A real SubsumptionOracle must be configured before "
                            + "checking semantic subsumption."
            );
        }

        return subsumptionOracle;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    // ---------------------------------------------------------------------
    // Loading Gamma and TBox
    // ---------------------------------------------------------------------

    /**
     * Loads Gamma expressions from a UTF-8 text file.
     */
    public void loadGamma(String filePath) throws IOException {
        loadGamma(Paths.get(filePath));
    }

    /**
     * Loads Gamma expressions from a UTF-8 text file.
     */
    public void loadGamma(Path filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath cannot be null");

        List<String> loaded = readMeaningfulLines(filePath);
        List<String> validated = new ArrayList<>();

        for (String line : loaded) {
            validateGammaLine(line);
            validated.add(line);
        }

        this.gammaLines = validated;
    }

    /**
     * Loads ground TBox axioms from a UTF-8 text file.
     */
    public void loadTBox(String filePath) throws IOException {
        loadTBox(Paths.get(filePath));
    }

    /**
     * Loads ground TBox axioms from a UTF-8 text file.
     */
    public void loadTBox(Path filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath cannot be null");

        List<String> loaded = readMeaningfulLines(filePath);
        List<String> validated = new ArrayList<>();

        for (String line : loaded) {
            validateTBoxLine(line);
            validated.add(line);
        }

        this.tboxLines = validated;
    }

    private List<String> readMeaningfulLines(Path filePath)
            throws IOException {

        List<String> allLines = Files.readAllLines(
                filePath,
                StandardCharsets.UTF_8
        );

        List<String> result = new ArrayList<>();

        for (String rawLine : allLines) {
            if (rawLine == null) {
                continue;
            }

            String line = rawLine.trim();

            if (line.isEmpty()
                    || line.startsWith("#")
                    || line.startsWith("//")) {
                continue;
            }

            result.add(line);
        }

        return result;
    }

    private void validateGammaLine(String line) {
        String[] parts = splitSubsumption(line, true);

        ConceptPatternNode.parse(parts[0]);
        ConceptPatternNode.parse(parts[1]);

        /*
         * ELSyntaxChecker historically understands ⊑ rather than ⊑?.
         * Normalize only for syntax validation.
         */
        String normalized = line.replace("⊑?", "⊑");

        if (!ELSyntaxChecker.isValid(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid Gamma syntax: " + line
            );
        }
    }

    private void validateTBoxLine(String line) {
        if (line.contains("⊑?")) {
            throw new IllegalArgumentException(
                    "TBox axioms must use ⊑ rather than ⊑?: " + line
            );
        }

        String[] parts = splitSubsumption(line, false);

        ConceptPatternNode left =
                ConceptPatternNode.parse(parts[0]);

        ConceptPatternNode right =
                ConceptPatternNode.parse(parts[1]);

        if (containsVariable(left) || containsVariable(right)) {
            throw new IllegalArgumentException(
                    "TBox axioms must be ground: " + line
            );
        }

        if (!ELSyntaxChecker.isValid(line)) {
            throw new IllegalArgumentException(
                    "Invalid TBox syntax: " + line
            );
        }
    }

    /**
     * Splits either C ⊑ D or C ⊑? D into two expressions.
     */
    private String[] splitSubsumption(
            String line,
            boolean allowQuestionMark
    ) {
        Objects.requireNonNull(line, "line cannot be null");

        String normalized = line.trim();

        if (allowQuestionMark) {
            normalized = normalized.replace("⊑?", "⊑");
        } else if (normalized.contains("⊑?")) {
            throw new IllegalArgumentException(
                    "Unexpected matching relation in TBox: " + line
            );
        }

        int relationIndex = normalized.indexOf('⊑');

        if (relationIndex < 0
                || normalized.indexOf('⊑', relationIndex + 1) >= 0) {
            throw new IllegalArgumentException(
                    "A line must contain exactly one subsumption relation: "
                            + line
            );
        }

        String left =
                normalized.substring(0, relationIndex).trim();

        String right =
                normalized.substring(relationIndex + 1).trim();

        if (left.isEmpty() || right.isEmpty()) {
            throw new IllegalArgumentException(
                    "Both sides of a subsumption must be non-empty: "
                            + line
            );
        }

        return new String[]{left, right};
    }

    // ---------------------------------------------------------------------
    // Gamma and TBox accessors
    // ---------------------------------------------------------------------

    public List<String> getGammaLines() {
        return new ArrayList<>(gammaLines);
    }

    public void setGammaLines(List<String> lines) {
        Objects.requireNonNull(lines, "lines cannot be null");

        List<String> validated = new ArrayList<>();

        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }

            String cleaned = line.trim();
            validateGammaLine(cleaned);
            validated.add(cleaned);
        }

        this.gammaLines = validated;
    }

    public List<String> getTBoxLines() {
        return new ArrayList<>(tboxLines);
    }

    public void setTBoxLines(List<String> lines) {
        Objects.requireNonNull(lines, "lines cannot be null");

        List<String> validated = new ArrayList<>();

        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }

            String cleaned = line.trim();
            validateTBoxLine(cleaned);
            validated.add(cleaned);
        }

        this.tboxLines = validated;
    }

    // ---------------------------------------------------------------------
    // Bottom handling retained from the original implementation
    // ---------------------------------------------------------------------

    public String getBottom() {
        return bottom;
    }

    public void setBottom(String bottom) {
        this.bottom = bottom;
    }

    /**
     * Retained for the existing left-ground preprocessing code.
     */
    public void replaceLeftGroundGammaVariablesWithTop() {
        Pattern variablePattern =
                Pattern.compile("_[A-Z]+_");

        List<String> replaced = new ArrayList<>();

        for (String line : gammaLines) {
            replaced.add(
                    variablePattern
                            .matcher(line)
                            .replaceAll("Tau")
            );
        }

        this.gammaLines = replaced;
    }

    /**
     * Retained for the existing right-ground preprocessing code.
     */
    public void replaceRightGroundGammaVariablesWithBottom() {
        if (bottom == null) {
            computeBottom();
        }

        Pattern variablePattern =
                Pattern.compile("_[A-Z]+_");

        List<String> replaced = new ArrayList<>();

        for (String line : gammaLines) {
            Matcher matcher =
                    variablePattern.matcher(line);

            String replacement =
                    "(" + bottom + ")";

            replaced.add(
                    matcher.replaceAll(
                            Matcher.quoteReplacement(replacement)
                    )
            );
        }

        this.gammaLines = replaced;
    }

    public void computeBottom() {
        Set<String> atoms =
                new LinkedHashSet<>();

        for (String gci : tboxLines) {
            String[] parts =
                    splitSubsumption(gci, false);

            atoms.addAll(
                    extractTopLevelAtoms(parts[0])
            );

            atoms.addAll(
                    extractTopLevelAtoms(parts[1])
            );
        }

        for (String constraint : gammaLines) {
            String[] parts =
                    splitSubsumption(constraint, true);

            atoms.addAll(
                    extractTopLevelAtoms(parts[1])
            );
        }

        this.bottom =
                String.join(" ⊓ ", atoms);
    }

    // ---------------------------------------------------------------------
    // Structured TBox access used by Algorithm 5.1
    // ---------------------------------------------------------------------

    /**
     * Returns the top-level atoms occurring in the TBox.
     */
    public List<ConceptPatternNode> getTBoxAtoms() {
        List<ConceptPatternNode> atoms =
                new ArrayList<>();

        for (String gci : tboxLines) {
            String[] parts =
                    splitSubsumption(gci, false);

            ConceptPatternNode left =
                    ConceptPatternNode.parse(parts[0]);

            ConceptPatternNode right =
                    ConceptPatternNode.parse(parts[1]);

            atoms.addAll(
                    topLevelAtoms(left)
            );

            atoms.addAll(
                    topLevelAtoms(right)
            );
        }

        return atoms;
    }

    /**
     * Returns the TBox GCIs in the form:
     *
     * <pre>
     *     ([A1, ..., Ak], B)
     * </pre>
     *
     * where the left side is represented as a list of top-level atoms.
     */
    public List<SimpleEntry<
            List<ConceptPatternNode>,
            ConceptPatternNode>> getTBoxGCIs() {

        List<SimpleEntry<
                List<ConceptPatternNode>,
                ConceptPatternNode>> result =
                new ArrayList<>();

        for (String gci : tboxLines) {
            String[] parts =
                    splitSubsumption(gci, false);

            ConceptPatternNode left =
                    ConceptPatternNode.parse(parts[0]);

            ConceptPatternNode right =
                    ConceptPatternNode.parse(parts[1]);

            List<ConceptPatternNode> leftAtoms =
                    topLevelAtoms(
                            left
                    );

            /*
             * A general EL-TBox may contain:
             *
             *     L ⊑ B1 ⊓ ... ⊓ Bm
             *
             * Since:
             *
             *     L ⊑ B1 ⊓ ... ⊓ Bm
             *
             * iff:
             *
             *     L ⊑ B1, ..., L ⊑ Bm
             *
             * expose one candidate for each top-level RHS atom.
             */
            List<ConceptPatternNode> rightAtoms =
                    topLevelAtoms(
                            right
                    );

            for (ConceptPatternNode rightAtom
                    : rightAtoms) {

                result.add(
                        new SimpleEntry<>(
                                new ArrayList<>(
                                        leftAtoms
                                ),
                                rightAtom
                        )
                );
            }
        }

        return result;
    }

    private List<ConceptPatternNode> topLevelAtoms( ConceptPatternNode node ) {
        return ConceptPatternOps.topLevelAtoms(node);
    }

    private boolean containsVariable(
            ConceptPatternNode node
    ) {
        return switch (node.type) {
            case VARIABLE ->
                    true;

            case TOP, CONCEPT_NAME ->
                    false;

            case EXISTENTIAL ->
                    containsVariable(
                            node.existentialFiller
                    );

            case CONJUNCTION ->
                    node.conjunctions
                            .stream()
                            .anyMatch(
                                    this::containsVariable
                            );
        };
    }

    // ---------------------------------------------------------------------
    // Existing textual bottom helper
    // ---------------------------------------------------------------------

    private List<String> extractTopLevelAtoms(
            String description
    ) {
        List<String> atoms =
                new ArrayList<>();

        for (String term :
                splitTopLevel(description, '⊓')) {

            String cleaned =
                    term.trim();

            if (cleaned.matches(
                    ConceptName.REGEX
            )
                    || cleaned.matches(
                    "∃[a-z]+\\..+"
            )) {
                atoms.add(cleaned);
            }

        }

        return atoms;
    }

    private List<String> splitTopLevel(
            String text,
            char separator
    ) {
        List<String> parts =
                new ArrayList<>();

        int depth = 0;
        int start = 0;

        for (int index = 0;
             index < text.length();
             index++) {

            char current = text.charAt(index);

            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;

                if (depth < 0) {
                    throw new IllegalArgumentException(
                            "Unbalanced parentheses: " + text
                    );
                }
            } else if (current == separator
                    && depth == 0) {
                parts.add(
                        text.substring(start, index)
                );

                start = index + 1;
            }
        }

        if (depth != 0) {
            throw new IllegalArgumentException(
                    "Unbalanced parentheses: " + text
            );
        }

        parts.add(
                text.substring(start)
        );

        return parts;
    }
}