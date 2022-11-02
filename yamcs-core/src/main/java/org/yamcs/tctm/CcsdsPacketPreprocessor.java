package org.yamcs.tctm;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.YConfiguration;

/**
 * Contains some helper methods for all the pre-processors for CCSDS packets
 */
public abstract class CcsdsPacketPreprocessor extends AbstractPacketPreprocessor {

    private Map<Integer, AtomicInteger> seqCounts = new HashMap<>();

    protected CcsdsPacketPreprocessor(String yamcsInstance, YConfiguration config) {
        super(yamcsInstance, config);
    }

    protected void checkSequence(int apid, int newseq) {
        AtomicInteger ai = seqCounts.computeIfAbsent(apid, k -> new AtomicInteger(-1));
        int oldseq = ai.getAndSet(newseq);

        if (checkForSequenceDiscontinuity && oldseq != -1 && ((newseq - oldseq) & 0x3FFF) != 1) {
            eventProducer.sendWarning("SEQ_COUNT_JUMP",
                    "Sequence count jump for apid: " + apid + " old seq: " + oldseq + " newseq: " + newseq);
        }
    }
}
