package org.yamcs.tctm.ccsds;


public interface Cop1Monitor {
    public enum AlertType {
        LOCKOUT("Lockout detected"), //
        SYNCH("Synchronization Lost"), CLCW("Invalid CLCW received"), //
        LIMIT("Allowed number of transmissions exhausted for a AD Frame"), //
        NNR("CLCW with invalid N(R) received"), //
        T1("Timer expired and transmission limit has been reached"), //
        TERM("A Terminate AD Service directive has been received"), //
        LLIF("Lower Layer Interface problem");

        String msg;

        AlertType(String msg) {
            this.msg = msg;
        }

        public String toString() {
            return msg;
        }
    }
    
    /**
     * Called when the operations have been suspended due to a timeout
     * 
     * @param suspendState
     *            - the state of the FOP-1 when it has been suspended.
     */
    void suspended(int suspendState);

    default void alert(AlertType alert) {};

    /**
     * Called each time when the state changes.
     * 
     * @param oldState
     * @param newState 
     */
    void stateChanged(int oldState, int newState);
    
    /**
     * Called when the COP1 has been disabled
     */
    void disabled();
    
    /**
     * Called when a new CLCW has been received
     */
    default void clcwReceived(int clcw) {};
    
    /**
     * Called when a new command has been added to the COP1 waiting queue 
     */
    default void tcQueued() {};
    /**
     * Called when a new AD frame has been sent queued for being sent upstream
     */
    default void tcSent() {};
}