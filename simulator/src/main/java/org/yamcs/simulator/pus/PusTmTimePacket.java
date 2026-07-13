package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;

import org.yamcs.simulator.SimulatorCcsdsPacket;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;

/**
 * PUS time packet APID = 0 no secondary header
 * data is composed of
 * 
 * <pre>
 * rate exponential value - 1 byte = 2 (reporting time every 2^2 = 4 seconds)
 * time - 8 byes
 * crc - 2 bytes
 * </pre>
 * 
 * @author nm
 *
 */
public class PusTmTimePacket extends SimulatorCcsdsPacket {
    static final CrcCciitCalculator crcCalculator = new CrcCciitCalculator();

    public PusTmTimePacket() {
        this(PusTimeEncoding.DEFAULT);
    }

    public PusTmTimePacket(PusTimeEncoding timeEncoding) {
        super(ByteBuffer.allocate(6 + 1 + timeEncoding.getEncodedLength() + 2));
        setHeader(0, 0, 0, 3, getSeq(0));
        bb.position(6);
        bb.put((byte)2);
        PusTime now = timeEncoding.now();
        now.encode(bb, timeEncoding);
        fillChecksum();
    }

    public PusTmTimePacket(byte[] packet) {
        super(packet);
    }

    @Override
    public ByteBuffer getUserDataBuffer() {
        bb.position(6);
        return bb.slice();
    }

    @Override
    protected void fillChecksum() {
        int crc = crcCalculator.compute(bb.array(), bb.arrayOffset(), bb.capacity() - bb.arrayOffset()-2);
        bb.position(bb.capacity()-2);
        bb.putShort((short)crc);
    }

}
