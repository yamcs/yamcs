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
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

public abstract class CfdpPacket {
    protected final CfdpHeader header;

    private static Logger log = LoggerFactory.getLogger("Packet");
    private static CrcCciitCalculator crcCalculator = new CrcCciitCalculator();
    public static final TupleDefinition CFDP = new TupleDefinition();
    // outgoing CFDP packets
    static {
        CFDP.addColumn(StandardTupleDefinitions.GENTIME_COLUMN, DataType.TIMESTAMP);
        CFDP.addColumn("entityId", DataType.LONG);
        CFDP.addColumn(StandardTupleDefinitions.SEQNUM_COLUMN, DataType.INT);
        CFDP.addColumn("pdu", DataType.BINARY);
    }

    protected CfdpPacket() {
        this.header = null;
    }

    protected CfdpPacket(CfdpHeader header) {
        this.header = header;
    }


    public CfdpHeader getHeader() {
        return this.header;
    }

    public abstract int getDataFieldLength();

    /**
     * Reads a CFDP PDU from the ByteBuffer at the current position.
     * <p>
     * The buffer can contain more data than one PDU, at the end of the call the position will be set to the end of the
     * PDU.
     * <p>
     * In case of error (e.g. cannot decode header) a PduDecodingException is thrown and the position is undetermined.
     * <p>
     * The return may be null if the PDU is not supported
     *
     */
    public static CfdpPacket getCFDPPacket(ByteBuffer bb) throws PduDecodingException {
        int position = bb.position();
        int limit = bb.limit();
        CfdpHeader header;

        try {
            header = new CfdpHeader(bb);
        } catch (BufferUnderflowException e) {
            throw new PduDecodingException("short PDU, size: " + bb.limit(), getData(bb, position, bb.limit()), e);
        }

        int fcsLength = header.withCrc() ? 2 : 0;
        int dataLength = CfdpHeader.getDataLength(bb) - fcsLength;

        if (dataLength < 2) {
            throw new PduDecodingException("data length is " + dataLength + " (expected at least 2)",
                    getData(bb, position, bb.limit()));
        }

        int pduSize = header.getLength() + dataLength + fcsLength;
        if (pduSize > bb.limit()) {
            throw new PduDecodingException(
                    "buffer too short, from header expected PDU of size" + pduSize + " bytes, but only "
                            + bb.limit() + " bytes available",
                    getData(bb, position, bb.limit()));
        }

        if (header.withCrc() &&
            crcCalculator.compute(bb, position, pduSize) != 0) {
            throw new PduDecodingException("invalid CRC checksum",
                    getData(bb, position, pduSize));
        }

        bb.limit(position + pduSize - fcsLength);

        CfdpPacket toReturn = null;
        if (header.isFileDirective()) {
            byte fdcCode = bb.get();
            FileDirectiveCode fdc = FileDirectiveCode.fromCode(fdcCode);
            if (fdc == null) {
                throw new PduDecodingException("Unknown file directive code: " + fdcCode,
                        getData(bb, position, pduSize));
            }
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
                case KEEP_ALIVE:
                    toReturn = new KeepAlivePacket(bb, header);
                    break;
                default:
                    log.warn("Ignoring unknown/not supported " + fdc + " file directive PDU ");
                }
            } catch (BufferUnderflowException e) {
                throw new PduDecodingException("Short " + fdc + " PDU; size: " + pduSize,
                        getData(bb, position, pduSize), e);
            }
        } else {
            try {
                toReturn = new FileDataPacket(bb, header);
            } catch (BufferUnderflowException e) {
                throw new PduDecodingException("Short file data PDU; size: " + pduSize, getData(bb, position, pduSize),
                        e);
            }
        }
        bb.limit(limit);
        bb.position(position + pduSize);
        return toReturn;
    }

    private static byte[] getData(ByteBuffer bb, int position, int length) {
        byte[] data = new byte[length];
        bb.position(position);
        bb.get(data);
        return data;
    }

    public byte[] toByteArray() {
        int dataLength = getDataFieldLength();
        int fcsLength = header.withCrc() ? 2 : 0;
        ByteBuffer buffer = ByteBuffer.allocate(header.getLength() + dataLength + fcsLength);
        header.writeToBuffer(buffer, dataLength + fcsLength);
        writeCFDPPacket(buffer);
        if (fcsLength > 0)
            buffer.putShort((short)crcCalculator.compute(buffer, 0, dataLength));
        return buffer.array();
    }

    public void writeToBuffer(ByteBuffer buffer) {
        int offset = buffer.position();
        int dataLength = getDataFieldLength();
        int fcsLength = header.withCrc() ? 2 : 0;
        header.writeToBuffer(buffer, dataLength + fcsLength);
        writeCFDPPacket(buffer);
        if (fcsLength > 0)
            buffer.putShort((short)crcCalculator.compute(buffer, offset, dataLength));
    }

    public Tuple toTuple(OngoingCfdpTransfer trans) {
        return toTuple(trans.getTransactionId(), trans.getStartTime());
    }

    public Tuple toTuple(long startTime) {
        return toTuple(header.getTransactionId(), startTime);
    }

    public Tuple toTuple(CfdpTransactionId id, long startTime) {
        TupleDefinition td = CFDP;
        ArrayList<Object> al = new ArrayList<>();
        al.add(startTime);
        al.add(id.getInitiatorEntity());
        al.add(id.getSequenceNumber());
        al.add(this.toByteArray());
        return new Tuple(td, al);
    }

    public static CfdpPacket fromTuple(Tuple tuple) {
        byte[] pduData = tuple.getColumn("pdu");
        if (pduData == null) {
            throw new ConfigurationException("no column named 'pdu' in the tuple");
        } else {
            return CfdpPacket.getCFDPPacket(ByteBuffer.wrap(pduData));
        }
    }

    public CfdpTransactionId getTransactionId() {
        return getHeader().getTransactionId();
    }

    // the buffer is assumed to be at the correct position
    protected abstract void writeCFDPPacket(ByteBuffer buffer);

    public enum TransmissionMode {
        ACKNOWLEDGED(0),
        UNACKNOWLEDGED(1);

        private final int value;

        TransmissionMode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static TransmissionMode fromValue(int value) {
            switch (value) {
                case 0: return ACKNOWLEDGED;
                case 1: return UNACKNOWLEDGED;
                default: throw new IllegalArgumentException("Value can only be 0 or 1");
            }
        }
    }
}
