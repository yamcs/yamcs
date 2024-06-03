package org.yamcs.algorithms;

import org.yamcs.commanding.VerificationResult;

/**
 * Library of functions available from within Algorithm scripts using this naming scheme:
 * <p>
 * The java method {@code VerifierFunctions.[method]} is available in scripts as {@code Verifier.[method]}
 */
public class VerifierFunctions {

    /**
     * Returns a successful verification result
     */
    public VerificationResult success() {
        return success(null, null);
    }

    /**
     * Returns a successful verification result with provided message
     */
    public VerificationResult success(String message) {
        return success(message, null);
    }

    /**
     * Returns a successful verification result with provided message, and a return value
     */
    public VerificationResult success(String message, Object value) {
        return new VerificationResult(true, message, value);
    }

    /**
     * Returns a failed verification result
     */
    public VerificationResult failure() {
        return failure(null, null);
    }

    /**
     * Returns a failed verification result with provided message
     */
    public VerificationResult failure(String message) {
        return failure(message, null);
    }

    /**
     * Returns a failed verification result with provided message, and a return value
     */
    public VerificationResult failure(String message, Object value) {
        return new VerificationResult(false, message, value);
    }

    /**
     * Create a new verification result
     */
    public VerificationResult createResult(boolean success) {
        return new VerificationResult(success, null, null);
    }
}
