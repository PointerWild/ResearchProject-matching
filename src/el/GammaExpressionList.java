package el;

import el.structure.ConceptPatternNode;
import el.structure.SubsumptionPattern;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Stores and edits a Gamma matching problem as structured subsumption patterns.
 *
 * <p>Gamma expressions are never added to the TBox ontology. A pattern is sent
 * to ELK only when both sides are ground, and then only as a query through the
 * supplied {@link TBoxElkReasoner}.</p>
 */
public final class GammaExpressionList {

    private static final char SUBSUMPTION_OPERATOR = '⊑';

    private final List<SubsumptionPattern> expressions;
    private final TBoxElkReasoner elkReasoner;

    /**
     * Creates an empty Gamma list using the supplied TBox reasoner for ground queries.
     *
     * @param elkReasoner long-lived TBox reasoner
     */
    public GammaExpressionList(TBoxElkReasoner elkReasoner) {
        this.expressions = new ArrayList<>();
        this.elkReasoner = Objects.requireNonNull(
                elkReasoner, "elkReasoner cannot be null");
    }

    /**
     * Creates a Gamma list and loads expressions from a UTF-8 file.
     *
     * @param gammaPath path to the Gamma file
     * @param elkReasoner long-lived TBox reasoner
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if a non-comment Gamma line is invalid
     */
    public GammaExpressionList(Path gammaPath, TBoxElkReasoner elkReasoner)
            throws IOException {
        this(elkReasoner);
        loadGamma(gammaPath);
    }

    /**
     * Replaces this Gamma list with expressions loaded from a UTF-8 file.
     *
     * <p>Blank lines and lines beginning with {@code #} or {@code //} are ignored.
     * Both {@code ⊑?} and legacy {@code ⊑} operators are accepted. Loading is
     * transactional: the current list is not modified if any line is invalid.</p>
     *
     * @param path path to the Gamma file
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if a non-comment line is invalid
     */
    public void loadGamma(Path path) throws IOException {
        Objects.requireNonNull(path, "path cannot be null");
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<SubsumptionPattern> parsed = new ArrayList<>();

        for (int index = 0; index < lines.size(); index++) {
            String original = lines.get(index);
            String trimmed = original.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                continue;
            }
            parsed.add(parseGammaLine(trimmed, path, index + 1));
        }

        expressions.clear();
        expressions.addAll(parsed);
    }

    /**
     * Parses one Gamma expression using either {@code ⊑?} or {@code ⊑}.
     * Variables are permitted.
     *
     * @param line Gamma expression text
     * @return structured subsumption pattern
     * @throws IllegalArgumentException if the expression is invalid
     */
    public SubsumptionPattern parseGammaLine(String line) {
        Objects.requireNonNull(line, "line cannot be null");
        return parseGammaLine(line, null, -1);
    }

    /**
     * Appends an existing structured pattern.
     *
     * @param pattern pattern to append
     */
    public void add(SubsumptionPattern pattern) {
        expressions.add(Objects.requireNonNull(pattern, "pattern cannot be null"));
    }

    /**
     * Parses and appends one Gamma expression.
     *
     * @param gammaExpression expression text
     * @return the newly appended pattern
     */
    public SubsumptionPattern add(String gammaExpression) {
        SubsumptionPattern pattern = parseGammaLine(gammaExpression);
        expressions.add(pattern);
        return pattern;
    }

    /**
     * Creates and appends a structured Gamma expression.
     *
     * @param left left expression
     * @param right right expression
     * @return the newly appended pattern
     */
    public SubsumptionPattern add(ConceptPatternNode left, ConceptPatternNode right) {
        SubsumptionPattern pattern = new SubsumptionPattern(
                Objects.requireNonNull(left, "left cannot be null"),
                Objects.requireNonNull(right, "right cannot be null"));
        expressions.add(pattern);
        return pattern;
    }

    /**
     * Returns the pattern at an index.
     *
     * @param index zero-based index
     * @return pattern at the index
     */
    public SubsumptionPattern get(int index) {
        return expressions.get(index);
    }

    /**
     * Returns an immutable snapshot of all Gamma expressions.
     *
     * @return immutable expression list
     */
    public List<SubsumptionPattern> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(expressions));
    }

    /**
     * Returns an immutable snapshot of expressions not marked solved.
     *
     * @return unsolved expressions
     */
    public List<SubsumptionPattern> getUnsolved() {
        List<SubsumptionPattern> unsolved = new ArrayList<>();
        for (SubsumptionPattern expression : expressions) {
            if (!expression.solved) {
                unsolved.add(expression);
            }
        }
        return Collections.unmodifiableList(unsolved);
    }

    /**
     * Returns the first expression not marked solved, or {@code null} when none exists.
     *
     * @return first unsolved expression, or {@code null}
     */
    public SubsumptionPattern nextUnsolved() {
        for (SubsumptionPattern expression : expressions) {
            if (!expression.solved) {
                return expression;
            }
        }
        return null;
    }

    /**
     * Removes and returns the expression at an index.
     * This operation changes only the Gamma list.
     *
     * @param index zero-based index
     * @return removed expression
     */
    public SubsumptionPattern remove(int index) {
        return expressions.remove(index);
    }

    /**
     * Removes one equal object from the Gamma list.
     * This operation changes only the Gamma list.
     *
     * @param pattern pattern to remove
     * @return whether an element was removed
     */
    public boolean remove(SubsumptionPattern pattern) {
        return expressions.remove(Objects.requireNonNull(
                pattern, "pattern cannot be null"));
    }

    /**
     * Replaces one Gamma expression and returns the old expression.
     * This operation changes only the Gamma list.
     *
     * @param index zero-based index
     * @param newPattern replacement pattern
     * @return replaced expression
     */
    public SubsumptionPattern replace(int index, SubsumptionPattern newPattern) {
        return expressions.set(index, Objects.requireNonNull(
                newPattern, "newPattern cannot be null"));
    }

    /**
     * Parses a replacement expression, replaces one Gamma item, and returns the old item.
     * This operation changes only the Gamma list.
     *
     * @param index zero-based index
     * @param newExpression replacement expression text
     * @return replaced expression
     */
    public SubsumptionPattern replace(int index, String newExpression) {
        return replace(index, parseGammaLine(newExpression));
    }

    /**
     * Removes all Gamma expressions without changing the TBox ontology.
     */
    public void clear() {
        expressions.clear();
    }

    /**
     * Returns the number of Gamma expressions.
     *
     * @return expression count
     */
    public int size() {
        return expressions.size();
    }

    /**
     * Returns whether the Gamma list is empty.
     *
     * @return {@code true} when the list is empty
     */
    public boolean isEmpty() {
        return expressions.isEmpty();
    }

    /**
     * Returns whether every Gamma expression is marked solved.
     * An empty list is considered solved.
     *
     * @return {@code true} when all expressions are solved
     */
    public boolean allSolved() {
        for (SubsumptionPattern expression : expressions) {
            if (!expression.solved) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether both sides of a Gamma expression are ground.
     *
     * @param pattern pattern to inspect
     * @return {@code true} exactly when no variable occurs
     */
    public boolean isGround(SubsumptionPattern pattern) {
        Objects.requireNonNull(pattern, "pattern cannot be null");
        return elkReasoner.isGround(pattern.left, pattern.right);
    }

    /**
     * Submits one ground Gamma expression as a query to the TBox reasoner.
     * The expression is not added to the ontology.
     *
     * @param pattern ground Gamma expression
     * @return ELK entailment result
     * @throws IllegalArgumentException if the expression contains a variable
     */
    public boolean checkSubsumption(SubsumptionPattern pattern) {
        Objects.requireNonNull(pattern, "pattern cannot be null");
        if (!isGround(pattern)) {
            throw new IllegalArgumentException(
                    "Gamma expression contains a variable and cannot be queried by ELK.");
        }
        return elkReasoner.subsumes(pattern.left, pattern.right);
    }

    /**
     * Submits the ground Gamma expression at an index as an ELK query.
     *
     * @param index zero-based index
     * @return ELK entailment result
     * @throws IllegalArgumentException if the expression contains a variable
     */
    public boolean checkSubsumption(int index) {
        return checkSubsumption(get(index));
    }

    /**
     * Tries to query a Gamma expression without throwing for variables.
     *
     * @param pattern pattern to inspect and possibly query
     * @return the result for a ground expression, otherwise {@link Optional#empty()}
     */
    public Optional<Boolean> tryCheckSubsumption(SubsumptionPattern pattern) {
        Objects.requireNonNull(pattern, "pattern cannot be null");
        if (!isGround(pattern)) {
            return Optional.empty();
        }
        return Optional.of(checkSubsumption(pattern));
    }

    /**
     * Queries every ground Gamma expression and skips expressions containing variables.
     *
     * @return immutable index-to-result map in Gamma order
     */
    public Map<Integer, Boolean> checkAllGroundExpressions() {
        Map<Integer, Boolean> results = new LinkedHashMap<>();
        for (int index = 0; index < expressions.size(); index++) {
            SubsumptionPattern pattern = expressions.get(index);
            if (isGround(pattern)) {
                results.put(index, checkSubsumption(pattern));
            }
        }
        return Collections.unmodifiableMap(results);
    }

    private SubsumptionPattern parseGammaLine(String line, Path source, int lineNumber) {
        String cleaned = line.trim();
        try {
            if (cleaned.isEmpty()) {
                throw new IllegalArgumentException("Gamma expression cannot be empty.");
            }

            int operatorIndex = cleaned.indexOf(SUBSUMPTION_OPERATOR);
            if (operatorIndex < 0) {
                throw new IllegalArgumentException(
                        "Missing Gamma subsumption operator ⊑? or ⊑.");
            }
            if (operatorIndex != cleaned.lastIndexOf(SUBSUMPTION_OPERATOR)) {
                throw new IllegalArgumentException(
                        "Each Gamma line must contain exactly one subsumption operator.");
            }

            int operatorLength = cleaned.startsWith("⊑?", operatorIndex) ? 2 : 1;
            String leftText = cleaned.substring(0, operatorIndex).trim();
            String rightText = cleaned.substring(operatorIndex + operatorLength).trim();
            if (leftText.isEmpty() || rightText.isEmpty()) {
                throw new IllegalArgumentException(
                        "Both sides of a Gamma subsumption must be non-empty.");
            }

            ConceptPatternNode left = ConceptPatternNode.parse(leftText);
            ConceptPatternNode right = ConceptPatternNode.parse(rightText);
            return new SubsumptionPattern(left, right);
        } catch (RuntimeException exception) {
            String reason = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            String location = source == null
                    ? ""
                    : " at " + source + ":" + lineNumber;
            throw new IllegalArgumentException(
                    "Invalid Gamma expression" + location + ": " + cleaned
                            + System.lineSeparator() + "Reason: " + reason,
                    exception);
        }
    }
}
