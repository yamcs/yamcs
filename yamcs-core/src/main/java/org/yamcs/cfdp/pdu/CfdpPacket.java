package org.yamcs.cfdp.pdu;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.cfdp.OngoingCfdpTransfer;
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

    protected CfdpPacket() {
        this.header = null;
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

    public abstract int getDataFieldLength();

    /**
     * Reads a CFDP PDU from the ByteBuffer which has to have the position set to 0.
     * <p>
     * The buffer can contain more data than one PDU, at the end of the call the position will be set to the end of the
     * PDU.
     * <p>
     * In case of error (e.g. cannot decode header) a PduDecodingException is thrown and the position is undetermined.
     */
    public static CfdpPacket getCFDPPacket(ByteBuffer bb) throws PduDecodingException {
        assert (bb.position() == 0);

        CfdpHeader header;

        try {
            header = new CfdpHeader(bb);
        } catch (BufferUnderflowException e) {
            throw new PduDecodingException("short PDU, size: " + bb.limit(), getData(bb), e);
        }

        int pduSize = header.getLength() + CfdpHeader.getDataLength(bb);
        if (pduSize > bb.limit()) {
            throw new PduDecodingException(
                    "buffer too short, from header expected PDU of size" + pduSize + " bytes, but only "
                            + bb.limit() + " bytes available",
                    getData(bb));
        }
        bb.limit(pduSize);
        CfdpPacket toReturn = null;
        if (header.isFileDirective()) {
            FileDirectiveCode fdc = FileDirectiveCode.readFileDirectiveCode(bb);
            try {
                switch (fdc) {
                case EOF:
                    toReturn = new EofPacket(bb, header);
                    break;
                case FINISHED:
                    toReturn = new FinishedPacket(bb, header);
                    break;
                case ACK:
                    toReturn = new AckPacket(bb, header);
                    break;
                case METADATA:
                    toReturn = new MetadataPacket(bb, header);
                    break;
                case NAK:
                    toReturn = new NakPacket(bb, header);
                    break;
                case PROMPT:
                    toReturn = new PromptPacket(bb, header);
                    break;
                case KEEP_ALIVE:
                    toReturn = new KeepAlivePacket(bb, header);
                    break;
                default:
                    break;
                }
            } catch (BufferUnderflowException e) {
                throw new PduDecodingException("short " + fdc + " PDU; size: " + pduSize, getData(bb), e);
            }
        } else {
            try {
                toReturn = new FileDataPacket(bb, header);
            } catch (BufferUnderflowException e) {
                throw new PduDecodingException("short file data PDU; size: " + pduSize, getData(bb), e);
            }
        }
        if (toReturn != null && header.withCrc()) {
            if (!toReturn.crcValid()) {
                log.error("invalid crc");
            }
        }
        return toReturn;
    }

    private static byte[] getData(ByteBuffer bb) {
        byte[] data = new byte[bb.limit()];
        bb.position(0);
        bb.get(data);
        return data;
    }

    public byte[] toByteArray() {
        int dataLength = getDataFieldLength();
        ByteBuffer buffer = ByteBuffer.allocate(header.getLength() + dataLength);
        getHeader().writeToBuffer(buffer, dataLength);
        writeCFDPPacket(buffer);
        if (getHeader().withCrc()) {
            calculateAndAddCrc(buffer);
        }

        return buffer.array();
    }

    public void writeToBuffer(ByteBuffer buffer) {
        int dataLength = getDataFieldLength();
        header.writeToBuffer(buffer, dataLength);
        writeCFDPPacket(buffer);
        if (header.withCrc()) {
            calculateAndAddCrc(buffer);
        }
    }

    public Tuple toTuple(OngoingCfdpTransfer trans) {
        return toTuple(trans.getTransactionId(), trans.getStartTime());
    }

    public Tuple toTuple(CfdpTransactionId id, long startTime) {
        TupleDefinition td = CFDP.copy();
        ArrayList<Object> al = new ArrayList<>();
        al.add(startTime);
        al.add(id.getInitiatorEntity());
        al.add(id.getSequenceNumber());
        al.add(this.toByteArray());
        return new Tuple(td, al);
    }

    public static CfdpPacket fromTuple(Tuple tuple) {
        if (tuple.hasColumn("pdu")) {
            return CfdpPacket.getCFDPPacket(ByteBuffer.wrap((byte[]) (tuple.getColumn("pdu"))));
        } else {
            throw new ConfigurationException("no column named 'pdu' in the tuple");
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
