package org.yamcs.xtceproc;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.SortedSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.xtce.RateInStream;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtceproc.ContainerProcessingContext.ContainerProcessingPosition;
import org.yamcs.xtceproc.ContainerProcessingContext.ContainerProcessingResult;

public class SequenceContainerProcessor {
    Logger log=LoggerFactory.getLogger(this.getClass().getName());
    ContainerProcessingContext pcontext;
    SequenceContainerProcessor(ContainerProcessingContext pcontext) {
        this.pcontext = pcontext;
    }

    public void extract(SequenceContainer seq) {
        ContainerProcessingResult result = pcontext.result;
        ContainerProcessingPosition position = pcontext.position;
        ByteBuffer bb = position.bb;
        //First add it to the result
        result.containers.add(new ContainerExtractionResult(seq, bb.asReadOnlyBuffer(), 
                position.bitPosition, result.acquisitionTime, result.generationTime));

        RateInStream ris = seq.getRateInStream();
        if(ris != null) {
            result.expirationTime = result.acquisitionTime + ris.getMaxInterval();
        }
        int maxposition = position.bitPosition;

        //then extract the entries
        SortedSet<SequenceEntry> entries = pcontext.subscription.getEntries(seq);
        if(entries!=null) {
            for (SequenceEntry se:entries) {
                try {
                    switch(se.getReferenceLocation()) {
                    case previousEntry:
                        position.bitPosition+=se.getLocationInContainerInBits();
                        break;
                    case containerStart:
                        position.bitPosition=se.getLocationInContainerInBits();
                    }
                    
                    if(pcontext.ignoreOutOfContainerEntries && (position.bitPosition >= position.bb.capacity()*8)) {
                        //the next entry is outside of the packet
                        break;
                    }

                    if(se.getRepeatEntry()==null) {
                        pcontext.sequenceEntryProcessor.extract(se);
                    } else { //this entry is repeated several times
                        long n = pcontext.valueProcessor.getValue(se.getRepeatEntry().getCount());
                        for (int i=0; i<n; i++) {
                            pcontext.sequenceEntryProcessor.extract(se);
                            position.bitPosition += se.getRepeatEntry().getOffsetSizeInBits();
                        }
                    }
                } catch (BufferUnderflowException|BufferOverflowException|IndexOutOfBoundsException e) {
                    log.warn("Got "+e.getClass().getName()+" when extracting from the buffer of length "+bb.capacity()+" bytes bitPosition "+position.bitPosition+" entry: "+se);
                    break;
                } 
                if(position.bitPosition>maxposition) {
                    maxposition = position.bitPosition;
                }
            }
        }

        Set<SequenceContainer> inheritingContainers = pcontext.subscription.getInheritingContainers(seq);
        boolean hasDerived=false;

        if(inheritingContainers!=null) {
            //And then any derived containers
            int bitp = position.bitPosition;
            for(SequenceContainer sc:inheritingContainers) {
                if(sc.getRestrictionCriteria().isMet(pcontext.criteriaEvaluator)) {
                    hasDerived=true;
                    position.bitPosition=bitp;
                    extract(sc);
                    if(position.bitPosition>maxposition) {
                        maxposition = position.bitPosition;
                    }
                }
            }
        }
        position.bitPosition=maxposition;
        //Finaly update the stats. We add the packet into the statistics only if it doesn't have a derived container
        if(!hasDerived && (result.stats != null)) {
            result.stats.newPacket(seq.getName(), (entries==null)?0:entries.size(), 
                    result.acquisitionTime, result.generationTime);
        }
    }
}
