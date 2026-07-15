package el;

import el.structure.ConceptPatternNode;
import el.structure.SubsumptionPattern;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result of the paper-defined normalization that precedes
 * Algorithm 5.1.
 */
public final class GammaNormalizationResult {

    /** One completed semantic check for a ground-ground constraint. */
    public record GroundCheckResult(
            ConceptPatternNode left,
            ConceptPatternNode right,
            boolean entailed
    ) {
        public GroundCheckResult {
            Objects.requireNonNull(left, "left cannot be null");
            Objects.requireNonNull(right, "right cannot be null");
        }
    }

    private final boolean matchable;
    private final Gamma expandedGamma;
    private final Gamma normalizedGamma;
    private final List<GroundCheckResult> groundChecks;
    private final String failureReason;
    private final SubsumptionPattern failedSubsumption;

    private GammaNormalizationResult(
            boolean matchable,
            Gamma expandedGamma,
            Gamma normalizedGamma,
            List<GroundCheckResult> groundChecks,
            String failureReason,
            SubsumptionPattern failedSubsumption
    ) {
        this.matchable = matchable;
        this.expandedGamma = Objects.requireNonNull(
                expandedGamma,
                "expandedGamma cannot be null"
        );
        this.normalizedGamma = normalizedGamma;
        this.groundChecks = List.copyOf(
                Objects.requireNonNull(
                        groundChecks,
                        "groundChecks cannot be null"
                )
        );
        this.failureReason = failureReason;
        this.failedSubsumption = failedSubsumption;
    }

    public static GammaNormalizationResult matchable(
            Gamma expandedGamma,
            Gamma normalizedGamma,
            List<GroundCheckResult> groundChecks
    ) {
        return new GammaNormalizationResult(
                true,
                expandedGamma,
                Objects.requireNonNull(
                        normalizedGamma,
                        "normalizedGamma cannot be null"
                ),
                groundChecks,
                null,
                null
        );
    }

    public static GammaNormalizationResult unmatchable(
            Gamma expandedGamma,
            List<GroundCheckResult> groundChecks,
            SubsumptionPattern failedSubsumption,
            String failureReason
    ) {
        return new GammaNormalizationResult(
                false,
                expandedGamma,
                null,
                groundChecks,
                Objects.requireNonNull(
                        failureReason,
                        "failureReason cannot be null"
                ),
                Objects.requireNonNull(
                        failedSubsumption,
                        "failedSubsumption cannot be null"
                ).copy()
        );
    }

    public boolean isMatchable() {
        return matchable;
    }

    /** Complete RHS-expanded intermediate problem, including ground constraints. */
    public Gamma getExpandedGamma() {
        return expandedGamma;
    }

    /**
     * The only Gamma that may be supplied to Algorithm 5.1.
     *
     * @throws IllegalStateException if a failed ground check made the problem
     *                               immediately unmatchable
     */
    public Gamma getNormalizedGamma() {
        if (!matchable) {
            throw new IllegalStateException(
                    "No normalized Gamma exists because the matching problem "
                            + "is unmatchable: "
                            + failureReason
            );
        }
        return normalizedGamma;
    }

    public List<GroundCheckResult> getGroundChecks() {
        return groundChecks;
    }

    public String getFailureReason() {
        return failureReason;
    }

    /** Returns a defensive copy of the failed constraint, or null on success. */
    public SubsumptionPattern getFailedSubsumption() {
        return failedSubsumption == null
                ? null
                : failedSubsumption.copy();
    }
}
