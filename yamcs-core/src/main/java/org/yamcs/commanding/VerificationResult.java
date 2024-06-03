package org.yamcs.commanding;

import org.yamcs.parameter.Value;

/**
 * Class that can be used to capture the outcome of a verifier execution.
 */
public class VerificationResult {

    public static final VerificationResult SUCCESS = new VerificationResult(true, null, null);
    public static final VerificationResult FAIL = new VerificationResult(false, null, null);

    /**
     * Overall result of this verifier (success/fail).
     * <p>
     * This impacts the acknowledgment status (green/red).
     */
    public boolean success;

    /**
     * Optional message explaining why the command is successful or not (like an error message).
     */
    public String message;

    /**
     * An optional return value. This may be given either on success or fail.
     * <p>
     * If a verifier is configured to complete the command, the return value of the verifier can become the return value
     * of the command itself.
     * <p>
     * This value will be transformed into a {@link Value}, unless it already is of that type.
     */
    public Object returnValue;

    public VerificationResult(boolean success) {
        this(success, null, null);
    }

    public VerificationResult(boolean success, String message) {
        this(success, message, null);
    }

    public VerificationResult(boolean success, String message, Object returnValue) {
        this.success = success;
        this.message = message;
        this.returnValue = returnValue;
    }

    @Override
    public String toString() {
        return success ? "SUCCESS" : "FAILURE";
    }
}
