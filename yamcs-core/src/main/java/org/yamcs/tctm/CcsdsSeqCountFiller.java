package org.yamcs.tctm;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.utils.ByteArrayUtils;

/**
 * Fills in the CCSDS primary header sequence count per APID.
 * <p>
 * By default each instance maintains its own independent counters, so each link has its own sequence count per APID.
 * <p>
 * A name can be supplied via {@link #CcsdsSeqCountFiller(String)} to create a named shared counter: all instances
 * constructed with the same name share the same counters. This is useful when multiple links must present a continuous
 * sequence to the target.
 */
public class CcsdsSeqCountFiller {
    // Named shared counters - keyed by name then by APID
    private static final Map<String, Map<Integer, Integer>> namedSeqCounts = new HashMap<>();

    // Per-instance (or shared) APID → seq-count map
    private final Map<Integer, Integer> seqCounts;

    /** Creates an instance-local counter that is not shared with any other link. */
    public CcsdsSeqCountFiller() {
        this.seqCounts = new HashMap<>();
    }

    /**
     * Creates a named counter shared with all other instances that use the same name.
     *
     * @param name
     *            shared counter name; instances with the same name share the same per-APID counters
     */
    public CcsdsSeqCountFiller(String name) {
        synchronized (namedSeqCounts) {
            this.seqCounts = namedSeqCounts.computeIfAbsent(name, k -> new HashMap<>());
        }
    }

    private int getSeqCount(int apid) {
        synchronized (seqCounts) {
            int seqCount = seqCounts.getOrDefault(apid, 0);
            var nextSeqCount = (seqCount + 1) % (1 << 14);
            seqCounts.put(apid, nextSeqCount);
            return seqCount;
        }
    }

    /**
     * Generates a sequence count and fills it in the CCSDS primary header.
     *
     * @param packet
     * @return the generated sequence count
     */
    public int fill(byte[] packet) {
        int apidseqcount = ByteArrayUtils.decodeInt(packet, 0);

        int apid = (apidseqcount >> 16) & 0x07FF;
        int seqFlags = apidseqcount >>> 14;

        int seqCount = getSeqCount(apid);

        ByteArrayUtils.encodeUnsignedShort((short) ((seqFlags << 14) | seqCount), packet, 2);

        return seqCount;
    }

    public void setSequence(int apid, int seqCount) {
        synchronized (seqCounts) {
            seqCounts.put(apid, seqCount);
        }
    }
}
