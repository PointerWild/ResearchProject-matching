// File: el/FailureException.java
package el;

/**
 * Thrown by eager‐solving when an eager rule is applicable but fails.
 */
public class FailureException extends Exception {
    public FailureException() {
        super();
    }
    public FailureException(String message) {
        super(message);
    }
    public FailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
