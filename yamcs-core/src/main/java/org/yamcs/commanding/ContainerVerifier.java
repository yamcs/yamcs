package org.yamcs.commanding;

import org.yamcs.ContainerExtractionResult;
import org.yamcs.Processor;
import org.yamcs.container.ContainerConsumer;
import org.yamcs.container.ContainerRequestManager;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.SequenceContainer;

class ContainerVerifier extends Verifier implements ContainerConsumer {
    SequenceContainer container;
    Processor yproc;
    
    ContainerVerifier( CommandVerificationHandler cvh, CommandVerifier cv, SequenceContainer c) {
        super(cvh, cv);
        this.container = c;
        this.yproc = cvh.getProcessor();
    }
    
    
    @Override
    public void processContainer(ContainerExtractionResult cer) {
        ContainerRequestManager crm = yproc.getContainerRequestManager();
        crm.unsubscribe(this, container);
        finished(true);
    }


    @Override
    void doStart() {
        ContainerRequestManager crm = yproc.getContainerRequestManager();
        crm.subscribe(this, container);
    }


    @Override
    void doCancel() {
        ContainerRequestManager crm = yproc.getContainerRequestManager();
        crm.unsubscribe(this, container);
    }
}