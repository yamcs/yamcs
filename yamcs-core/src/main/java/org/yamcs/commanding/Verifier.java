package org.yamcs.commanding;

import org.yamcs.xtce.CommandVerifier;

abstract class Verifier {
    final protected CommandVerifier cv;
    final protected CommandVerificationHandler cvh;
    
    Verifier nextVerifier;
    Verifier(CommandVerificationHandler cvh, CommandVerifier cv) {
        this.cv = cv;
        this.cvh = cvh;
    }
    
    abstract void start();
}