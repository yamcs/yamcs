package org.yamcs.commanding;

import org.yamcs.parameter.Value;

/**
 * Class that can be used to capture the outcome of a verifier execution.
 */
public class VerificationResult {

    public static final VerificationResult SUCCESS = new VerificationResult(true, null, null);
    public static final VerificationResult FAIL = new VerificationResult(false, null, null);

    /**
     * Sentinel result indicating that this verifier does not apply to this particular command instance (for example
     * an algorithm bound to command attributes that are only set when the command is handled in a specific way).
     * <p>
     * Unlike an algorithm that simply never produces a result, this resolves the verifier immediately instead of
     * leaving it pending until its check window times out, and it never applies the verifier's onSuccess/onFail
     * termination action - a skipped verifier can neither complete nor fail the command.
     */
    public static final VerificationResult SKIP = new VerificationResult(true, null, null, true);

    /**
     * Overall result of this verifier (success/fail). Meaningless when {@link #skip} is true.
     * <p>
     * This impacts the acknowledgment status (green/red).
     */
    public boolean success;

    /**
     * Optional message explaining why the command is successful, failed, or skipped (like an error message).
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

    /**
     * If true, this verifier is not applicable to this command instance; see {@link #SKIP}.
     */
    public final boolean skip;

    public VerificationResult(boolean success) {
        this(success, null, null);
    }

    public VerificationResult(boolean success, String message) {
        this(success, message, null);
    }

    public VerificationResult(boolean success, String message, Object returnValue) {
        this(success, message, returnValue, false);
    }

    private VerificationResult(boolean success, String message, Object returnValue, boolean skip) {
        this.success = success;
        this.message = message;
        this.returnValue = returnValue;
        this.skip = skip;
    }

    /**
     * Returns a result that skips this verifier for this command instance (see {@link #SKIP}), with an explanatory
     * message.
     */
    public static VerificationResult skip(String message) {
        return new VerificationResult(true, message, null, true);
    }

    @Override
    public String toString() {
        return skip ? "SKIP" : success ? "SUCCESS" : "FAILURE";
    }
}
