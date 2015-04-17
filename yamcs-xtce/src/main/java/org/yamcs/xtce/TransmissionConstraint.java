package org.yamcs.xtce;

public class TransmissionConstraint {
    
    /**
     * XTCE: A specialised form of MatchCriteria for transmission constraint that may be suspendable or time out.
     * 
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
    
    public String toString() {
	return "("+matchCriteria+", timeout: "+timeout+")";
    }
}
