package org.yamcs.xtceproc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.utils.BitBuffer;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.ContainerEntry;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SequenceEntry;

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
        BitBuffer buf = pcontext.buffer;
        if(buf.getPosition()%8!=0) 
            log.warn("Container Entry that doesn't start at byte boundary is not supported.{} is supposed to start at bit {}", ce, buf.getPosition());
        if(buf.getPosition()>buf.sizeInBits()) {
            log.warn("Container Entry that doesn't fit in the buffer: {} is supposed to start at bit {}"
                    + " while the packet buffer has capacity {} bits", ce,  buf.getPosition(), buf.sizeInBits());
            return;
        }
        BitBuffer buf1 = buf.slice();
        ContainerProcessingContext cpc1 = new ContainerProcessingContext(pcontext.pdata, buf1, pcontext.result, pcontext.subscription, pcontext.options);
        cpc1.sequenceContainerProcessor.extract(ce.getRefContainer());
        if(ce.getRefContainer().getSizeInBits()<0)
            buf.setPosition(buf.getPosition()+buf1.getPosition());
        else 
            buf.setPosition(buf.getPosition()+ce.getRefContainer().getSizeInBits());
    }

    private void extractParameterEntry(ParameterEntry pe) {
        BitBuffer buf = pcontext.buffer;

        Parameter param = pe.getParameter();
        ParameterType ptype = param.getParameterType();
        ParameterValue pv = new ParameterValue(param);
        pv.setAbsoluteBitOffset(pcontext.containerAbsoluteByteOffset*8+buf.getPosition());
        pv.setBitSize(((BaseDataType)ptype).getEncoding().getSizeInBits());
        pcontext.dataEncodingProcessor.extractRaw(((BaseDataType)ptype).getEncoding(), pv);
        pcontext.pdata.parameterTypeProcessor.calibrate(pv);
        
        pv.setAcquisitionTime(pcontext.result.acquisitionTime);
        pv.setGenerationTime(pcontext.result.generationTime);
        pv.setExpireMillis(pcontext.result.expireMillis);
        pv.setParameterEntry(pe);

        pcontext.result.params.add(pv);
    }
}