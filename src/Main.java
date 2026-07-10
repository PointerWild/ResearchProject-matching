import el.ELAnalyze;
import el.ElkSubsumptionOracle;
import el.Gamma;
import el.GoalOrientedMatcher;
import el.structure.ConceptPatternNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Application entry point for the EL matching system.
 *
 * <p>Responsibilities:
 *
 * <ol>
 *     <li>Load the TBox and Gamma files.</li>
 *     <li>Create one long-lived ELK reasoner.</li>
 *     <li>Inject the reasoner into ELAnalyze.</li>
 *     <li>Create GoalOrientedMatcher.</li>
 *     <li>Run Algorithm 5.1.</li>
 *     <li>Dispose the ELK reasoner automatically.</li>
 * </ol>
 */
public final class Main {

    /**
     * Namespace used when TBox concept names and role names are converted
     * into OWL entities.
     */
    private static final String BASE_IRI =
            "http://example.com/research-project#";

    /**
     * Default file locations.
     *
     * <p>According to the current project structure, TBox.txt and gamma.txt
     * are stored under src/.
     */
    private static final Path DEFAULT_TBOX_PATH =
            Path.of("src", "TBox.txt");

    private static final Path DEFAULT_GAMMA_PATH =
            Path.of("src", "gamma.txt");

    private Main() {
        // Utility entry-point class; do not instantiate.
    }

    public static void main(String[] args) {

        /*
         * Optional command-line usage:
         *
         * java Main <TBox-file> <Gamma-file>
         *
         * When no arguments are supplied, the program uses:
         *
         * src/TBox.txt
         * src/gamma.txt
         */
        Path tBoxPath =
                args.length >= 1
                        ? Path.of(args[0])
                        : DEFAULT_TBOX_PATH;

        Path gammaPath =
                args.length >= 2
                        ? Path.of(args[1])
                        : DEFAULT_GAMMA_PATH;

        try {
            runMatchingSystem(
                    tBoxPath,
                    gammaPath
            );

        } catch (Exception exception) {
            System.err.println();
            System.err.println(
                    "Matching system failed: "
                            + exception.getMessage()
            );

            exception.printStackTrace();

            System.exit(1);
        }
    }

    /**
     * Initializes and runs the complete matching system.
     */
    private static void runMatchingSystem(
            Path tBoxPath,
            Path gammaPath
    ) throws IOException {

        validateInputFile(
                tBoxPath,
                "TBox"
        );

        validateInputFile(
                gammaPath,
                "Gamma"
        );

        System.out.println(
                "========================================"
        );

        System.out.println(
                "EL Matching System"
        );

        System.out.println(
                "========================================"
        );

        System.out.println(
                "TBox file : "
                        + tBoxPath.toAbsolutePath()
        );

        System.out.println(
                "Gamma file: "
                        + gammaPath.toAbsolutePath()
        );

        /*
         * Step 1:
         * Create ELAnalyze.
         *
         * ELAnalyze stores the project-level TBox and Gamma data.
         * It does not create or own the reasoner.
         */
        ELAnalyze analyze =
                new ELAnalyze();

        /*
         * Step 2:
         * Load the same TBox and Gamma that will be used by the complete
         * Algorithm 5.1 implementation.
         */
        analyze.loadTBox(
                tBoxPath
        );

        analyze.loadGamma(
                gammaPath
        );

        List<String> tBoxLines =
                analyze.getTBoxLines();

        List<String> gammaLines =
                analyze.getGammaLines();

        printLoadedInput(
                tBoxLines,
                gammaLines
        );

        /*
         * Step 3:
         * Create exactly one ELK semantic reasoner.
         *
         * It receives the same TBox stored in ELAnalyze.
         *
         * try-with-resources guarantees that close() is called even when
         * matching throws an exception.
         */
        try (
                ElkSubsumptionOracle elkReasoner =
                        new ElkSubsumptionOracle(
                                tBoxLines,
                                BASE_IRI
                        )
        ) {
            /*
             * Step 4:
             * Inject the real semantic oracle into ELAnalyze.
             *
             * From this point onward:
             *
             * ELAnalyze.subsumes(...)
             *      -> ElkSubsumptionOracle.subsumes(...)
             *      -> ELK reasoner.isEntailed(...)
             */
            analyze.setSubsumptionOracle(
                    elkReasoner
            );

            /*
             * Optional ELAnalyze-level logging.
             *
             * ElkSubsumptionOracle itself already prints ELK queries.
             * Set this to true only when both layers of logging are useful.
             */
            analyze.setDebug(false);

            /*
             * Step 5:
             * Convert the textual Gamma expressions into the Gamma structure
             * expected by GoalOrientedMatcher.
             */
            Gamma gamma =
                    createGamma(
                            gammaLines
                    );

            /*
             * Record ontology size before the matcher is constructed.
             *
             * GoalOrientedMatcher may already perform semantic queries in
             * its constructor while creating the TBox GCI index.
             */
            int axiomsBefore =
                    elkReasoner.getAxiomCount();

            /*
             * Step 6:
             * Create GoalOrientedMatcher only after:
             *
             * 1. TBox has been loaded;
             * 2. Gamma has been loaded;
             * 3. the real ELK oracle has been injected.
             */
            GoalOrientedMatcher matcher =
                    new GoalOrientedMatcher(
                            analyze
                    );

            /*
             * Step 7:
             * Execute Algorithm 5.1.
             */
            System.out.println();
            System.out.println(
                    "=== Running Algorithm 5.1 ==="
            );

            long startTime =
                    System.nanoTime();

            boolean hasMatcher =
                    matcher.match(gamma);

            long elapsedNanoseconds =
                    System.nanoTime() - startTime;

            int axiomsAfter =
                    elkReasoner.getAxiomCount();

            /*
             * Step 8:
             * Print the result and diagnostics.
             */
            System.out.println();
            System.out.println(
                    "========================================"
            );

            System.out.println(
                    hasMatcher
                            ? "RESULT: HAS_MATCHER"
                            : "RESULT: NO_MATCHER"
            );

            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "ELK query count       : "
                            + elkReasoner.getElkQueryCount()
            );

            System.out.println(
                    "Ontology axioms before: "
                            + axiomsBefore
            );

            System.out.println(
                    "Ontology axioms after : "
                            + axiomsAfter
            );

            System.out.printf(
                    "Execution time         : %.3f ms%n",
                    elapsedNanoseconds
                            / 1_000_000.0
            );

            /*
             * Gamma queries must never be added to the ontology.
             */
            if (axiomsBefore != axiomsAfter) {
                throw new IllegalStateException(
                        "Ontology was modified while matching. "
                                + "Before: "
                                + axiomsBefore
                                + ", after: "
                                + axiomsAfter
                );
            }

            System.out.println(
                    "Ontology isolation     : OK"
            );
        }

        /*
         * ElkSubsumptionOracle.close() has been called automatically here.
         */
        System.out.println(
                "ELK reasoner closed successfully."
        );

        /*
         * ================================================================
         * OLD DEMO AND MOCK TEST FLOWS ARE INTENTIONALLY DISABLED
         * ================================================================
         *
         * The following old Main-based tests are no longer executed:
         *
         * - PatternDSL printing examples
         * - setMockSubsumption(...) examples
         * - random subsumption tests
         * - manual Dec Case 1–6 tests
         * - mock-based GoalOrientedMatcher test matrix
         * - ELSyntaxChecker examples
         * - bottom-construction examples
         * - left-ground/right-ground replacement demonstrations
         *
         * These tests should be migrated to JUnit files under:
         *
         *     test/el/
         *
         * They must not remain part of the production application entry
         * point.
         */
    }

    /**
     * Converts textual matching constraints into the existing Gamma model.
     *
     * <p>Accepted relation symbols:
     *
     * <pre>
     *     C ⊑ D
     *     C ⊑? D
     * </pre>
     */
    private static Gamma createGamma(
            List<String> gammaLines
    ) {
        Gamma gamma =
                new Gamma();

        for (String line : gammaLines) {
            String[] expressions =
                    splitGammaLine(line);

            ConceptPatternNode left =
                    ConceptPatternNode.parse(
                            expressions[0]
                    );

            ConceptPatternNode right =
                    ConceptPatternNode.parse(
                            expressions[1]
                    );

            gamma.add(
                    left,
                    right
            );
        }

        return gamma;
    }

    /**
     * Splits one textual Gamma constraint into its left and right sides.
     */
    private static String[] splitGammaLine(
            String line
    ) {
        if (line == null) {
            throw new IllegalArgumentException(
                    "Gamma line cannot be null."
            );
        }

        String cleaned =
                line.trim();

        /*
         * Convert matching notation to the parser-independent separator.
         */
        String normalized =
                cleaned.replace(
                        "⊑?",
                        "⊑"
                );

        String[] parts =
                normalized.split(
                        "\\s*⊑\\s*",
                        -1
                );

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "Gamma expression must contain exactly one "
                            + "subsumption relation: "
                            + line
            );
        }

        String left =
                parts[0].trim();

        String right =
                parts[1].trim();

        if (left.isEmpty()
                || right.isEmpty()) {
            throw new IllegalArgumentException(
                    "Both sides of a Gamma expression must be non-empty: "
                            + line
            );
        }

        return new String[]{
                left,
                right
        };
    }

    /**
     * Validates that an input file exists and is readable.
     */
    private static void validateInputFile(
            Path path,
            String label
    ) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException(
                    label
                            + " file does not exist: "
                            + path.toAbsolutePath()
            );
        }

        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(
                    label
                            + " path is not a regular file: "
                            + path.toAbsolutePath()
            );
        }

        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException(
                    label
                            + " file is not readable: "
                            + path.toAbsolutePath()
            );
        }
    }

    /**
     * Prints the input loaded by the matching system.
     */
    private static void printLoadedInput(
            List<String> tBoxLines,
            List<String> gammaLines
    ) {
        System.out.println();
        System.out.println(
                "=== Loaded TBox ==="
        );

        for (int index = 0;
             index < tBoxLines.size();
             index++) {

            System.out.printf(
                    "%3d. %s%n",
                    index + 1,
                    tBoxLines.get(index)
            );
        }

        System.out.println();
        System.out.println(
                "=== Loaded Gamma ==="
        );

        for (int index = 0;
             index < gammaLines.size();
             index++) {

            System.out.printf(
                    "%3d. %s%n",
                    index + 1,
                    gammaLines.get(index)
            );
        }

        System.out.println();
        System.out.println(
                "TBox axiom count : "
                        + tBoxLines.size()
        );

        System.out.println(
                "Gamma constraint count: "
                        + gammaLines.size()
        );
    }
}