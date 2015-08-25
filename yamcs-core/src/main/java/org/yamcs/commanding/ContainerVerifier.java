package org.yamcs.commanding;

import java.nio.ByteBuffer;

import org.yamcs.YProcessor;
import org.yamcs.commanding.CommandVerificationHandler.VerifResult;
import org.yamcs.container.RawContainerConsumer;
import org.yamcs.container.RawContainerRequestManager;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.SequenceContainer;

class ContainerVerifier extends Verifier implements RawContainerConsumer {
    SequenceContainer container;
    YProcessor yproc;
    
    ContainerVerifier( CommandVerificationHandler cvh, CommandVerifier cv, SequenceContainer c) {
        super(cvh, cv);
        this.container = c;
        this.yproc = cvh.getProcessor();
    }
    
    
    @Override
    public void processContainer(SequenceContainer sc, ByteBuffer content) {
        RawContainerRequestManager crm = yproc.getRawContainerRequestManager();
        crm.unsubscribe(this, container);
        cvh.onVerifierFinished(this, VerifResult.OK);
    }


    @Override
    void start() {
        RawContainerRequestManager crm = yproc.getRawContainerRequestManager();
        crm.subscribe(this, container);
    }
}