package org.yamcs.tctm.ccsds;


interface Cop1Monitor {
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

    void alert(AlertType alert);

    /**
     * Called each time when the state changes. These are the possible FOP1 states:
     * 
     * @param newState
     */
    void stateChanged(int newState);
}