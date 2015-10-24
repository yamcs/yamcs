package org.yamcs.xtce;

import java.io.Serializable;

public class TransmissionConstraint implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * XTCE: A specialised form of MatchCriteria for transmission constraint
     * that may be suspendable on timeout. 
     */
    final private MatchCriteria matchCriteria;

    /**
     * timeout in milliseconds
     */
    final private long timeout;

    public TransmissionConstraint(MatchCriteria criteria, long timeout) {
        this.matchCriteria = criteria;
        this.timeout = timeout;
    }

    public MatchCriteria getMatchCriteria() {
        return matchCriteria;
    }

    public long getTimeout() {
        return timeout;
    }

    @Override
    public String toString() {
        return "(" + matchCriteria + ", timeout: " + timeout + ")";
    }
}
