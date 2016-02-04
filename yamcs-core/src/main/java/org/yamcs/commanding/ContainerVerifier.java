package org.yamcs.commanding;

import org.yamcs.ContainerExtractionResult;
import org.yamcs.YProcessor;
import org.yamcs.commanding.CommandVerificationHandler.VerifResult;
import org.yamcs.container.ContainerConsumer;
import org.yamcs.container.ContainerRequestManager;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.SequenceContainer;

class ContainerVerifier extends Verifier implements ContainerConsumer {
    SequenceContainer container;
    YProcessor yproc;
    
    ContainerVerifier( CommandVerificationHandler cvh, CommandVerifier cv, SequenceContainer c) {
        super(cvh, cv);
        this.container = c;
        this.yproc = cvh.getProcessor();
    }
    
    
    @Override
    public void processContainer(ContainerExtractionResult cer) {
        ContainerRequestManager crm = yproc.getContainerRequestManager();
        crm.unsubscribe(this, container);
        cvh.onVerifierFinished(this, VerifResult.OK);
    }


    @Override
    void start() {
        ContainerRequestManager crm = yproc.getContainerRequestManager();
        crm.subscribe(this, container);
    }
}