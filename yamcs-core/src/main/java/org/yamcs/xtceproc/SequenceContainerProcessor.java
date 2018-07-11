package org.yamcs.xtceproc;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.util.Set;
import java.util.SortedSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.utils.BitBuffer;
import org.yamcs.xtce.RateInStream;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtceproc.ContainerProcessingContext.ContainerProcessingResult;

public class SequenceContainerProcessor {
    Logger log = LoggerFactory.getLogger(this.getClass().getName());
    ContainerProcessingContext pcontext;

    SequenceContainerProcessor(ContainerProcessingContext pcontext) {
        this.pcontext = pcontext;
    }

    public void extract(SequenceContainer seq) {
        ContainerProcessingResult result = pcontext.result;
        BitBuffer buf = pcontext.buffer;
        // First add it to the result
        result.containers.add(new ContainerExtractionResult(seq, buf.array(),
                buf.getPosition() + buf.offset() * 8, result.acquisitionTime, result.generationTime));

        RateInStream ris = seq.getRateInStream();
        if ((ris != null) && ris.getMaxInterval() > 0) {
            result.expireMillis = (long) (pcontext.options.getExpirationTolerance() * ris.getMaxInterval());
        }
        int maxposition = buf.getPosition();

        // then extract the entries
        SortedSet<SequenceEntry> entries = pcontext.subscription.getEntries(seq);
        if (entries != null) {
            for (SequenceEntry se : entries) {
                try {

                    if (se.getIncludeCondition() != null
                            && !se.getIncludeCondition().isMet(pcontext.criteriaEvaluator)) {
                        continue;
                    }

                    switch (se.getReferenceLocation()) {
                    case previousEntry:
                        buf.setPosition(buf.getPosition() + se.getLocationInContainerInBits());
                        break;
                    case containerStart:
                        buf.setPosition(se.getLocationInContainerInBits());
                    }

                    if (pcontext.options.ignoreOutOfContainerEntries() && (buf.getPosition() >= buf.sizeInBits())) {
                        // the next entry is outside of the packet
                        break;
                    }

                    if (se.getRepeatEntry() == null) {
                        pcontext.sequenceEntryProcessor.extract(se);
                    } else { // this entry is repeated several times
                        Long l = pcontext.valueProcessor.getValue(se.getRepeatEntry().getCount());
                        if (l == null) {
                            log.warn("Cannot find value for count {} required for extracting the repeated entry {} ",
                                    se.getRepeatEntry().getCount(), se);
                        } else {
                            long n = l;
                            for (int i = 0; i < n; i++) {
                                pcontext.sequenceEntryProcessor.extract(se);
                                buf.setPosition(buf.getPosition() + se.getRepeatEntry().getOffsetSizeInBits());
                            }
                        }
                    }
                } catch (BufferUnderflowException | BufferOverflowException | IndexOutOfBoundsException e) {
                    log.warn("Got " + e.getClass().getName() + " when extracting from the buffer of length "
                            + buf.sizeInBits() + " bits, bitPosition " + buf.getPosition() + " entry: " + se);
                    e.printStackTrace();
                    break;
                }
                if (buf.getPosition() > maxposition) {
                    maxposition = buf.getPosition();
                }
            }
        }

        Set<SequenceContainer> inheritingContainers = pcontext.subscription.getInheritingContainers(seq);
        boolean hasDerived = false;
        if (inheritingContainers != null) {
            // And then any derived containers
            int bitp = buf.getPosition();
            for (SequenceContainer sc : inheritingContainers) {
                if (sc.getRestrictionCriteria() == null) {
                    log.warn("Container {} inherits without defining an inheritance condition. Ignoring the container.",
                            sc.getName());
                    continue;
                }
                if (sc.getRestrictionCriteria().isMet(pcontext.criteriaEvaluator)) {
                    hasDerived = true;
                    buf.setPosition(bitp);
                    extract(sc);
                    if (buf.getPosition() > maxposition) {
                        maxposition = buf.getPosition();
                    }
                }
            }
        }
        buf.setPosition(maxposition);
        // Finaly update the stats. We add the packet into the statistics only if it doesn't have a derived container
        if (!hasDerived && (result.stats != null)) {
            result.stats.newPacket(seq.getName(), (entries == null) ? 0 : entries.size(),
                    result.acquisitionTime, result.generationTime);
        }
    }

}
