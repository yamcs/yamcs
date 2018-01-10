package org.yamcs.commanding;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.CommandVerifier;

abstract class Verifier {
    final protected CommandVerifier cv;
    final protected CommandVerificationHandler cvh;
    
    enum State {NEW, RUNNING,
        OK, NOK, TIMEOUT};
    volatile State state;
    
    
    Verifier nextVerifier;
    Verifier(CommandVerificationHandler cvh, CommandVerifier cv) {
        this.cv = cv;
        this.cvh = cvh;
    }
    
    void start() {
        state = State.RUNNING;
        doStart();
    }

    void cancel() {
        if(state!=State.RUNNING) {
            return;
        }
        state = State.TIMEOUT;
        doCancel();
        cvh.onVerifierFinished(this);
    }
    
    void finished(boolean result) {
        if(state!=State.RUNNING) {
            return;
        }
        state= result?State.OK:State.NOK;
        cvh.onVerifierFinished(this);
    }
    
    abstract void doStart();
    
    /**
     * Called to cancel the verification in case it didn't finish in the expected time.
     */
    abstract void doCancel();

    /**
     * Called when a command history parameter (an entry in the command history) is received
     * The parameter name is set to /yamcs/cdmHist/&lt;key&gt; where the key is the command history key. 
     * 
     * 
     * @param pv
     */
    public void updatedCommandHistoryParam(ParameterValue pv) { }

    public State getState() {
        return state;
    }
}
