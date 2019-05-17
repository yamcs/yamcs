package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.YConfiguration;
import org.yamcs.cfdp.CfdpTransactionId;
import org.yamcs.tctm.Packet;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

public abstract class CfdpPacket implements Packet {

    protected ByteBuffer buffer;
    protected CfdpHeader header;

    private final static int CCSDSHeaderSizeTC = 8;

    private static Logger log = LoggerFactory.getLogger("Packet");

    private class LV {
        private short length;
        private byte[] value;

        public LV(short length, byte[] value) {
            this.length = length;
            this.value = value;
        }

        public short getLength() {
            return length;
        }

        public byte[] getValue() {
            return value;
        }
    }

    protected CfdpPacket() {
        this.header = null;
    }

    protected final void finishConstruction() {
        header.setDataLength(calculateDataFieldLength());
    }

    protected CfdpPacket(CfdpHeader header) {
        this(null, header);
    }

    protected CfdpPacket(ByteBuffer buffer, CfdpHeader header) {
        this.header = header;
        this.buffer = buffer;
    }

    public CfdpHeader getHeader() {
        return this.header;
    }

    protected abstract int calculateDataFieldLength();

    public static CfdpPacket getCFDPPacket(ByteBuffer buffer) {
        CfdpHeader header = new CfdpHeader(buffer);
        CfdpPacket toReturn = null;
        if (header.isFileDirective()) {
            switch (FileDirectiveCode.readFileDirectiveCode(buffer)) {
            case EOF:
                toReturn = new EofPacket(buffer, header);
                break;
            case Finished:
                toReturn = new FinishedPacket(buffer, header);
                break;
            case ACK:
                toReturn = new AckPacket(buffer, header);
                break;
            case Metadata:
                toReturn = new MetadataPacket(buffer, header);
                break;
            case NAK:
                toReturn = new NakPacket(buffer, header);
                break;
            case Prompt:
                toReturn = new PromptPacket(buffer, header);
                break;
            case KeepAlive:
                toReturn = new KeepAlivePacket(buffer, header);
                break;
            default:
                break;
            }
        } else {
            toReturn = new FileDataPacket(buffer, header);
        }
        if (toReturn != null && header.withCrc()) {
            if (!toReturn.crcValid()) {
                log.error("invalid crc");
            }
        }
        return toReturn;
    }

    @Override
    public byte[] toByteArray() {
        ByteBuffer buffer = ByteBuffer
                .allocate(YConfiguration.getConfiguration("cfdp").getInt("maxPduSize") + CCSDSHeaderSizeTC);

        buffer.position(CCSDSHeaderSizeTC); // make room for the CCSDS header

        getHeader().writeToBuffer(buffer);
        writeCFDPPacket(buffer);
        if (getHeader().withCrc()) {
            calculateAndAddCrc(buffer);
        }

        // now we can calculate and insert the CCSDS header
        short endPosition = (short) buffer.position();
        buffer.rewind();
        prependCCSDSHeader(buffer, endPosition);

        byte[] toReturn = new byte[endPosition];
        buffer.rewind();
        buffer.get(toReturn);
        return toReturn;
    }

    public Tuple toTuple(CfdpTransactionId transferId) {
        TupleDefinition td = StandardTupleDefinitions.CFDP.copy();
        ArrayList<Object> al = new ArrayList<>();
        al.add(transferId.getInitiatorEntity());
        al.add(transferId.getSequenceNumber());
        al.add(this.toByteArray());
        return new Tuple(td, al);
    }

    private void prependCCSDSHeader(ByteBuffer buffer, short endPosition) {
        short length = (short) (endPosition - (short) 7); // end position - CCSDS header + 1 (cfr CCSDS standard)
        byte[] ccsdsHeader = new byte[] { 0x1f, (byte) 0xfd, (byte) 0xc0, 0x00, (byte) ((length >> 8) & 0xff),
                (byte) (length & 0xff),
                0x00, 0x00 };
        buffer.put(ccsdsHeader, 0, ccsdsHeader.length);
    }

    public static CfdpPacket fromTuple(Tuple tuple) {
        System.out.println("CfdpPacket. fromtuple: "+tuple.getDefinition());
        if(tuple.hasColumn("pdu")) {
            return CfdpPacket.getCFDPPacket(ByteBuffer.wrap((byte[]) (tuple.getColumn("pdu"))));
        } else {
            ByteBuffer bb = ByteBuffer.wrap((byte[]) (tuple.getColumn("packet")));
            bb.position(CCSDSHeaderSizeTC);
            return CfdpPacket.getCFDPPacket(bb.slice());
        }
    }

    public CfdpTransactionId getTransactionId() {
        return getHeader().getTransactionId();
    }

    private boolean crcValid() {
        throw new java.lang.UnsupportedOperationException("CFDP CRCs not supported");
    }

    // the buffer is assumed to be at the correct position
    protected abstract void writeCFDPPacket(ByteBuffer buffer);

    private void calculateAndAddCrc(ByteBuffer buffer) {
        throw new java.lang.UnsupportedOperationException("CFDP CRCs not supported");
    }

}
