package org.yamcs.pus;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.Processor;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.security.User;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.xtce.MetaCommand;

/**
 * ST[21] Request Sequencing support: builds one embedded sub-command's finalized (wire-ready)
 * bytes, for ground to place into TC[21,1]'s {@code entries} argument -- see pus_analysis/pus21.md
 * §e for the full design rationale.
 *
 * <p>
 * {@code CommandingManager.buildCommand()} alone is not enough: the CCSDS packet-length field is
 * only ever filled by a command postprocessor at the TC data link (e.g.
 * {@link PusCommandPostprocessor#process}), right before actual transmission -- same for the
 * sequence count and CRC. That postprocessor is not reusable here: it is bound to a specific
 * {@link org.yamcs.tctm.Link}'s command-history publisher and sequence-count filler, and calling
 * it would spuriously log command history and consume the live link's sequence counter for a
 * packet that is never actually transmitted over that link -- it only ever travels as opaque bytes
 * inside TC[21,1], to be relayed by the on-board software later. So this class performs the same
 * three finishing steps (packet length, sequence count, CRC) standalone, using a local sequence
 * counter that only needs to be unique <em>within one assembled sequence</em>.
 */
public class Pus21EmbeddedEntryBuilder {

    private static final CrcCciitCalculator CRC = new CrcCciitCalculator();

    private final AtomicInteger localSeqCounter = new AtomicInteger();

    /**
     * Builds one embedded sub-command via {@link org.yamcs.commanding.CommandingManager#buildCommand}
     * and finalizes it into wire-ready bytes (packet length, local sequence count, CRC).
     *
     * @param commandName qualified name of the MetaCommand to build
     * @param args        argument assignments for the command
     * @param origin      origin string recorded on the (never-transmitted) command id
     */
    public byte[] build(Processor processor, User user, String commandName, Map<String, Object> args, String origin)
            throws Exception {
        MetaCommand mc = processor.getMdb().getMetaCommand(commandName);
        if (mc == null) {
            throw new IllegalArgumentException("No such command: " + commandName);
        }
        PreparedCommand pc = processor.getCommandingManager().buildCommand(mc, args, origin, 0, user);
        return finalizeEmbeddedEntry(pc.getBinary());
    }

    /**
     * Appends a CRC-16/CCITT checkword, fills the CCSDS packet-length field, and stamps a local
     * sequence count -- mirrors {@link PusCommandPostprocessor#process} without any of its
     * link-bound side effects (command history, the link's own sequence-count filler).
     */
    byte[] finalizeEmbeddedEntry(byte[] binary) {
        byte[] withCrc = Arrays.copyOf(binary, binary.length + 2);
        ByteBuffer bb = ByteBuffer.wrap(withCrc);

        // CCSDS packet_data_length (bytes 4-5): total_size - 7, matching PusCommandPostprocessor.process().
        bb.putShort(4, (short) (withCrc.length - 7));

        // Local sequence count (bytes 2-3): 2-bit seq-flags preserved from the MDB template
        // (normally "11" unsegmented, see pus.xml's ccsds-seqFlags FixedValueEntry) + 14-bit count.
        int seq = localSeqCounter.getAndIncrement() & 0x3FFF;
        bb.put(2, (byte) ((bb.get(2) & 0xC0) | ((seq >> 8) & 0x3F)));
        bb.put(3, (byte) seq);

        int crc = CRC.compute(withCrc, 0, withCrc.length - 2);
        bb.putShort(withCrc.length - 2, (short) crc);
        return withCrc;
    }
}
