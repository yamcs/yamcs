package org.yamcs.commanding;

import org.yamcs.ContainerExtractionResult;
import org.yamcs.Processor;
import org.yamcs.container.ContainerConsumer;
import org.yamcs.container.ContainerRequestManager;
import org.yamcs.mdb.ContainerProcessingResult;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;

class ContainerVerifier extends Verifier implements ContainerConsumer {
    SequenceContainer container;
    Processor yproc;

    ContainerVerifier(CommandVerificationHandler cvh, CommandVerifier cv, SequenceContainer c) {
        super(cvh, cv);
        this.container = c;
        this.yproc = cvh.getProcessor();
    }

    @Override
    public void processContainer(ContainerProcessingResult cpr, ContainerExtractionResult cer) {
        ContainerRequestManager crm = yproc.getContainerRequestManager();
        crm.unsubscribe(this, container);

        Parameter returnParam = cv.getReturnParameter();
        if (returnParam != null) {
            // Honor <ReturnParmRef>: use the sample for the return parameter from the packet that triggered this
            // verifier. If it is not present in this packet, leave returnPv null so that Verifier.finished() falls
            // back to the processor last value cache.
            ParameterValue pv = cpr.getTmParams().getLastInserted(returnParam);
            if (pv != null) {
                returnPv = pv;
            }
        } else {
            // Store container bytes to cmdhist as the verifier's return value
            returnPv = new ParameterValue(YAMCS_PARAMETER_RETURN_VALUE);
            returnPv.setGenerationTime(cer.getGenerationTime());
            returnPv.setAcquisitionTime(cer.getAcquisitionTime());
            returnPv.setEngValue(ValueUtility.getBinaryValue(cer.getContainerContent()));
        }

        finished(true, null);
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
