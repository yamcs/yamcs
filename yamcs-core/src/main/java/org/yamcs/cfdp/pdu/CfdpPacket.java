package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.cfdp.CfdpTransactionId;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

public abstract class CfdpPacket {

    protected ByteBuffer buffer;
    protected CfdpHeader header;

    private static Logger log = LoggerFactory.getLogger("Packet");
    public static final TupleDefinition CFDP = new TupleDefinition();
    // outgoing CFDP packets
    static {
        CFDP.addColumn(StandardTupleDefinitions.GENTIME_COLUMN, DataType.TIMESTAMP);
        CFDP.addColumn("entityId", DataType.INT);
        CFDP.addColumn(StandardTupleDefinitions.SEQNUM_COLUMN, DataType.INT);
        CFDP.addColumn("pdu", DataType.BINARY);
    }

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
        int limit = header.getLength()+header.getDataLength();
        if(limit > buffer.limit()) {
            throw new IllegalArgumentException("buffer too short, from header expected "+limit+" bytes, but only "+buffer.limit()+" bytes available");
        }
        buffer.limit(limit);
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

    public byte[] toByteArray() {
        ByteBuffer buffer = ByteBuffer.allocate(header.getLength() + header.getDataLength());

        getHeader().writeToBuffer(buffer);
        writeCFDPPacket(buffer);
        if (getHeader().withCrc()) {
            calculateAndAddCrc(buffer);
        }

        return buffer.array();
    }
    public void writeToBuffer(ByteBuffer buffer) {
        header.writeToBuffer(buffer);
        writeCFDPPacket(buffer);
        if (header.withCrc()) {
            calculateAndAddCrc(buffer);
        }
    }
    
    
    public Tuple toTuple(CfdpTransactionId transferId) {
        TupleDefinition td = CFDP.copy();
        ArrayList<Object> al = new ArrayList<>();
        al.add(transferId.getStartTime());
        al.add(transferId.getInitiatorEntity());
        al.add(transferId.getSequenceNumber());
        al.add(this.toByteArray());
        return new Tuple(td, al);
    }

    public static CfdpPacket fromTuple(Tuple tuple) {
        if(tuple.hasColumn("pdu")) {
            return CfdpPacket.getCFDPPacket(ByteBuffer.wrap((byte[]) (tuple.getColumn("pdu"))));
        } else {
           throw new IllegalStateException();
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
