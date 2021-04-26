package org.yamcs.xtceproc;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.util.List;

import org.yamcs.ContainerExtractionResult;
import org.yamcs.utils.BitBuffer;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.RateInStream;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtceproc.MatchCriteriaEvaluator.MatchResult;
import org.yamcs.xtceproc.SubscribedContainer.InheritingContainer;

public class SequenceContainerProcessor {
    ContainerProcessingContext pcontext;

    SequenceContainerProcessor(ContainerProcessingContext pcontext) {
        this.pcontext = pcontext;
    }

    public void extract(SubscribedContainer subscribedContainer) throws XtceProcessingException {
        ProcessorData pdata = pcontext.proccessingData;
        SequenceContainer containerDef = subscribedContainer.conainerDef;
        ContainerProcessingResult result = pcontext.result;
        ContainerProcessingOptions options = pcontext.options;

        BitBuffer buf = pcontext.buffer;
        // First add it to the result
        if (pcontext.provideContainerResult) {
            result.containers.add(new ContainerExtractionResult(containerDef, buf.array(),
                    buf.getPosition() + buf.offset() * 8, result.acquisitionTime, result.generationTime));
        }

        RateInStream ris = containerDef.getRateInStream();
        if ((ris != null) && ris.getMaxInterval() > 0) {
            result.expireMillis = (long) (options.getExpirationTolerance() * ris.getMaxInterval());
        }
        int maxposition = buf.getPosition();

        // then extract the entries
        List<SequenceEntry> entries = subscribedContainer.entries;
        for (SequenceEntry se : entries) {
            int position = buf.getPosition();
            try {
                if (se.getIncludeCondition() != null) {
                    MatchCriteriaEvaluator evaluator = pdata.getEvaluator(se.getIncludeCondition());
                    if (evaluator.evaluate(result) != MatchResult.OK) {
                        continue;
                    }
                }

                switch (se.getReferenceLocation()) {
                case PREVIOUS_ENTRY:
                    buf.setPosition(buf.getPosition() + se.getLocationInContainerInBits());
                    break;
                case CONTAINER_START:
                    buf.setPosition(se.getLocationInContainerInBits());
                }

                if (options.ignoreOutOfContainerEntries() && (buf.getPosition() >= buf.sizeInBits())) {
                    // the next entry is outside of the packet
                    break;
                }

                // remember the position where the entry has started because the extract() below may move it and
                // then throw an exception
                position = buf.getPosition();

                if (se.getRepeatEntry() == null) {
                    pcontext.sequenceEntryProcessor.extract(se);
                } else { // this entry is repeated several times
                    long n = pcontext.valueProcessor.getValue(se.getRepeatEntry().getCount());
                    for (int i = 0; i < n; i++) {
                        pcontext.sequenceEntryProcessor.extract(se);
                        buf.setPosition(buf.getPosition() + se.getRepeatEntry().getOffsetSizeInBits());
                    }
                }
            } catch (BufferUnderflowException | BufferOverflowException | IndexOutOfBoundsException e) {
                if (se instanceof ParameterEntry) {
                    ParameterEntry pe = (ParameterEntry) se;
                    throw new XtceProcessingException(
                            "Could not extract parameter " + pe.getParameter().getQualifiedName()
                                    + " from container " + se.getContainer().getQualifiedName()
                                    + " at position " + position
                                    + " because it falls beyond the end of the container. Container size in bits: "
                                    + buf.sizeInBits());
                } else {
                    throw new XtceProcessingException("Could not extract entry " + se + "of size "
                            + buf.sizeInBits() + "bits from container " + se.getContainer().getQualifiedName()
                            + " position " + position
                            + "because it falls beyond the end of the container. Container size in bits: "
                            + buf.sizeInBits());
                }

            }
            if (buf.getPosition() > maxposition) {
                maxposition = buf.getPosition();
            }
        }

        List<InheritingContainer> inheritingContainers = subscribedContainer.inheritingContainers;
        boolean hasDerived = false;
        // And then any derived containers
        int bitp = buf.getPosition();
        for (InheritingContainer inherited : inheritingContainers) {
            MatchResult r = inherited.matches(result);

            if (r == MatchResult.OK) {
                hasDerived = true;
                buf.setPosition(bitp);
                extract(inherited.container);
                if (buf.getPosition() > maxposition) {
                    maxposition = buf.getPosition();
                }
            }
        }
        buf.setPosition(maxposition);
        // Finally update the stats. We add the packet into the statistics only if it doesn't have a derived container
        if (!hasDerived && (result.stats != null)) {
            String pname = result.getPacketName();
            result.stats.newPacket(pname, (entries == null) ? 0 : entries.size(),
                    result.acquisitionTime, result.generationTime, buf.sizeInBits());
        }
    }

}
