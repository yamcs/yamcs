package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;

import org.yamcs.simulator.SimulatorCcsdsPacket;

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
    private final int crcLength;

    public PusTmTimePacket() {
        this(Pus9Service.DEFAULT_RATE_EXPONENT, PusTimeEncoding.DEFAULT);
    }

    public PusTmTimePacket(PusTimeEncoding timeEncoding) {
        this(Pus9Service.DEFAULT_RATE_EXPONENT, timeEncoding);
    }

    public PusTmTimePacket(int rateExponent, PusTimeEncoding timeEncoding) {
        this(rateExponent, timeEncoding, PusPacketConfig.DEFAULT.tmCrc());
    }

    public PusTmTimePacket(int rateExponent, PusTimeEncoding timeEncoding, boolean crc) {
        super(ByteBuffer.allocate(7 + timeEncoding.getEncodedLength() + (crc ? 2 : 0)));
        crcLength = crc ? 2 : 0;
        setHeader(0, 0, 0, 3, getSeq(0));
        bb.position(6);
        bb.put((byte) rateExponent);
        PusTime now = timeEncoding.now();
        now.encode(bb, timeEncoding);
        fillChecksum();
    }

    public PusTmTimePacket(byte[] packet) {
        super(packet);
        crcLength = 2;
    }

    @Override
    public ByteBuffer getUserDataBuffer() {
        return bb.slice(6, bb.capacity() - 6 - crcLength);
    }

    @Override
    protected void fillChecksum() {
        if (crcLength != 0) {
            PusTcPacket.writeChecksum(bb);
        }
    }

}
