package org.yamcs.activities;

/**
 * Exception class carrying the failure reason (text provided by the user) of why a manual activity completed
 * exceptionally.
 * <p>
 * The stacktrace is to be discarded.
 */
@SuppressWarnings("serial")
public class ManualFailureException extends Exception {

    public ManualFailureException(String failureReason) {
        super(failureReason);
    }
}
