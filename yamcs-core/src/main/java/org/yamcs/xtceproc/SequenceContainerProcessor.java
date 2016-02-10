package org.yamcs.xtceproc;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.util.HashSet;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.xtce.RateInStream;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;

public class SequenceContainerProcessor {
    Logger log=LoggerFactory.getLogger(this.getClass().getName());
    ProcessingContext pcontext;
    SequenceContainerProcessor(ProcessingContext pcontext) {
        this.pcontext=pcontext;
    }

    public void extract(SequenceContainer seq) {
        //First add it to the result
        pcontext.containerResult.add(new ContainerExtractionResult(seq, pcontext.bb
                .asReadOnlyBuffer(), pcontext.bitPosition, pcontext.acquisitionTime, pcontext.generationTime));

        RateInStream ris = seq.getRateInStream();
        if(ris != null) {
            pcontext.expirationTime = pcontext.acquisitionTime + ris.getMaxInterval();
        }
        int maxposition=pcontext.bitPosition;

        //then extract the entries
        TreeSet<SequenceEntry> entries=pcontext.subscription.getEntries(seq);
        if(entries!=null) {
            for (SequenceEntry se:entries) {
                try {
                    switch(se.getReferenceLocation()) {
                    case previousEntry:
                        pcontext.bitPosition+=se.getLocationInContainerInBits();
                        break;
                    case containerStart:
                        pcontext.bitPosition=se.getLocationInContainerInBits();
                    }
                    
                    if(pcontext.ignoreOutOfContainerEntries && (pcontext.bitPosition >= pcontext.bb.capacity()*8)) {
                        //the next entry is outside of the packet
                        break;
                    }

                    if(se.getRepeatEntry()==null) {
                        pcontext.sequenceEntryProcessor.extract(se);
                    } else { //this entry is repeated several times
                        long n=pcontext.valueProcessor.getValue(se.getRepeatEntry().getCount());
                        for (int i=0;i<n;i++) {
                            pcontext.sequenceEntryProcessor.extract(se);
                            pcontext.bitPosition+=se.getRepeatEntry().getOffsetSizeInBits();
                        }
                    }
                } catch (BufferUnderflowException e) {
                    log.warn("Got buffer underflow when extracting from the buffer of length "+pcontext.bb.capacity()+" bytes bitPosition "+pcontext.bitPosition+" entry: "+se);
                    break;
                } catch (BufferOverflowException e) {
                    log.warn("Got buffer overflow when extracting from the buffer of length "+pcontext.bb.capacity()+" bytes bitPosition "+pcontext.bitPosition+" entry: "+se);
                    break;
                } catch (IndexOutOfBoundsException e) {
                    log.warn("Got index out of bounds when extracting from the buffer of length "+pcontext.bb.capacity()+" bytes bitPosition "+pcontext.bitPosition+" entry: "+se);
                    break;
                } 
                if(pcontext.bitPosition>maxposition) maxposition=pcontext.bitPosition;
            }
        }

        HashSet<SequenceContainer> inheritingContainers=pcontext.subscription.getInheritingContainers(seq);
        boolean hasDerived=false;

        if(inheritingContainers!=null) {
            //And then any derived containers
            int bitp=pcontext.bitPosition;
            for(SequenceContainer sc:inheritingContainers) {
                if(sc.getRestrictionCriteria().isMet(pcontext.criteriaEvaluator)) {
                    hasDerived=true;
                    pcontext.bitPosition=bitp;
                    extract(sc);
                    if(pcontext.bitPosition>maxposition) maxposition=pcontext.bitPosition;
                }
            }
        }
        pcontext.bitPosition=maxposition;
        //Finaly update the stats. We add the packet into the statistics only if it doesn't have a derived container
        if(!hasDerived && (pcontext.stats != null)) {
            pcontext.stats.newPacket(seq.getName(), (entries==null)?0:entries.size(), 
                    pcontext.acquisitionTime, pcontext.generationTime);
        }
    }
}
