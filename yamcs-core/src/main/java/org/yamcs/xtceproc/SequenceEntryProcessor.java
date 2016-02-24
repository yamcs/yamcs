package org.yamcs.xtceproc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.ContainerEntry;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.SequenceEntry;

public class SequenceEntryProcessor {
    static Logger log=LoggerFactory.getLogger(SequenceEntryProcessor.class.getName());
    ProcessingContext pcontext;

    SequenceEntryProcessor(ProcessingContext pcontext) {
        this.pcontext=pcontext;
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
            log.warn("Exception when extracting\n"+se+":\n"+e);
            throw e;
        }
    }


    private void extractContainerEntry(ContainerEntry ce) {
        if(pcontext.bitPosition%8!=0) 
            log.warn("Container Entry that doesn't start at byte boundary is not supported."+ ce+" is supposed to start at bit"+pcontext.bitPosition);
        if(pcontext.bitPosition/8>pcontext.bb.capacity()) {
            log.warn("Container Entry that doesn't fit in the buffer: "+ce+" is supposed to start at bit "+pcontext.bitPosition+" while the packet buffer has capacity "+pcontext.bb.capacity()+" bytes");
            return;
        }
        pcontext.bb.position(pcontext.bitPosition/8);
        ProcessingContext pcontext1=new ProcessingContext(pcontext.bb.slice(), pcontext.bitPosition/8, 0,
                pcontext.subscription, pcontext.paramResult, pcontext.containerResult,
                pcontext.acquisitionTime, pcontext.generationTime, pcontext.stats, pcontext.ignoreOutOfContainerEntries);
        pcontext1.sequenceContainerProcessor.extract(ce.getRefContainer());
        if(ce.getRefContainer().getSizeInBits()<0)
            pcontext.bitPosition+=pcontext1.bitPosition;
        else 
            pcontext.bitPosition+=ce.getRefContainer().getSizeInBits();
    }

    private void extractParameterEntry(ParameterEntry pe) {
        ParameterValue pv = pcontext.parameterTypeProcessor.extract(pe.getParameter());
        pv.setAcquisitionTime(pcontext.acquisitionTime);
        pv.setGenerationTime(pcontext.generationTime);
        pv.setExpirationTime(pcontext.expirationTime);
        pv.setParameterEntry(pe);

        pcontext.paramResult.add(pv);
    }
}