package org.yamcs.xtceproc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.ContainerEntry;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtceproc.ContainerProcessingContext.ContainerProcessingPosition;
import org.yamcs.xtceproc.ContainerProcessingContext.ContainerProcessingResult;

public class SequenceEntryProcessor {
    static Logger log=LoggerFactory.getLogger(SequenceEntryProcessor.class.getName());
    ContainerProcessingContext pcontext;

    SequenceEntryProcessor(ContainerProcessingContext pcontext) {
        this.pcontext = pcontext;
    }

    public void extract(SequenceEntry se) {
        try {
            if(se instanceof ContainerEntry) {
                extractContainerEntry((ContainerEntry)se);
            } else if (se instanceof ParameterEntry) {
                extractParameterEntry((ParameterEntry)se);
            } else {
                throw new UnsupportedOperationException("processing type "+se+" not implemented");
            }
        } catch (RuntimeException e) {
            log.warn("Exception when extracting\n {} :\n",se, e);
            throw e;
        }
    }


    private void extractContainerEntry(ContainerEntry ce) {
        ContainerProcessingPosition cpp = pcontext.position;
        if(cpp.bitPosition%8!=0) 
            log.warn("Container Entry that doesn't start at byte boundary is not supported.{} is supposed to start at bit {}", ce, cpp.bitPosition);
        if(cpp.bitPosition/8>cpp.bb.capacity()) {
            log.warn("Container Entry that doesn't fit in the buffer: {} is supposed to start at bit {}"
                    + " while the packet buffer has capacity {} bytes", ce,  cpp.bitPosition, cpp.bb.capacity());
            return;
        }
        cpp.bb.position(cpp.bitPosition/8);
        ContainerProcessingPosition cpp1 = new ContainerProcessingPosition(cpp.bb.slice(), cpp.bitPosition/8, 0);
        ContainerProcessingContext cpc1=new ContainerProcessingContext(pcontext.pdata, cpp1, pcontext.result, pcontext.subscription, pcontext.ignoreOutOfContainerEntries);
        cpc1.sequenceContainerProcessor.extract(ce.getRefContainer());
        if(ce.getRefContainer().getSizeInBits()<0)
            cpp.bitPosition+=cpc1.position.bitPosition;
        else 
            cpp.bitPosition+=ce.getRefContainer().getSizeInBits();
    }

    private void extractParameterEntry(ParameterEntry pe) {
        ContainerProcessingPosition cpp = pcontext.position;

        Parameter param = pe.getParameter();
        ParameterType ptype = param.getParameterType();
        ParameterValue pv = new ParameterValue(param);
        pv.setAbsoluteBitOffset(pcontext.containerAbsoluteByteOffset*8+cpp.bitPosition);
        pv.setBitSize(((BaseDataType)ptype).getEncoding().getSizeInBits());
        pcontext.dataEncodingProcessor.extractRaw(((BaseDataType)ptype).getEncoding(), pv);
        pcontext.pdata.parameterTypeProcessor.calibrate(pv);
        
        pv.setAcquisitionTime(pcontext.result.acquisitionTime);
        pv.setGenerationTime(pcontext.result.generationTime);
        pv.setExpirationTime(pcontext.result.expirationTime);
        pv.setParameterEntry(pe);

        pcontext.result.params.add(pv);
    }
}