package el;

import java.util.Objects;

/**
 * Result of normalizing an initial matching problem Gamma.
 *
 * <p>A result has one of two states:
 *
 * <ul>
 *     <li>matchable: normalization succeeded and produced a normalized Gamma;</li>
 *     <li>unmatchable: a ground-ground constraint was not entailed by the TBox.</li>
 * </ul>
 */
public final class GammaNormalizationResult {

    private final boolean matchable;
    private final Gamma normalizedGamma;
    private final String failureReason;

    private GammaNormalizationResult(
            boolean matchable,
            Gamma normalizedGamma,
            String failureReason
    ) {
        this.matchable = matchable;
        this.normalizedGamma = normalizedGamma;
        this.failureReason = failureReason;
    }

    /**
     * Creates a successful normalization result.
     */
    public static GammaNormalizationResult matchable(
            Gamma normalizedGamma
    ) {
        return new GammaNormalizationResult(
                true,
                Objects.requireNonNull(
                        normalizedGamma,
                        "normalizedGamma cannot be null"
                ),
                null
        );
    }

    /**
     * Creates a result representing an immediately unmatchable problem.
     */
    public static GammaNormalizationResult unmatchable(
            String failureReason
    ) {
        return new GammaNormalizationResult(
                false,
                null,
                Objects.requireNonNull(
                        failureReason,
                        "failureReason cannot be null"
                )
        );
    }

    /**
     * Returns whether normalization produced a potentially matchable Gamma.
     */
    public boolean isMatchable() {
        return matchable;
    }

    /**
     * Returns the normalized Gamma.
     *
     * @throws IllegalStateException when the matching problem was found
     *                               unmatchable during normalization
     */
    public Gamma getNormalizedGamma() {
        if (!matchable) {
            throw new IllegalStateException(
                    "No normalized Gamma exists because the matching "
                            + "problem is unmatchable: "
                            + failureReason
            );
        }

        return normalizedGamma;
    }

    /**
     * Returns the reason why normalization determined that the problem
     * has no matcher, or {@code null} after successful normalization.
     */
    public String getFailureReason() {
        return failureReason;
    }
}