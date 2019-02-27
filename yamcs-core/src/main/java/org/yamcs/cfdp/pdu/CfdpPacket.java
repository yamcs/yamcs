package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

public abstract class CfdpPacket {

    protected ByteBuffer buffer;
    protected CfdpHeader header;

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

    public CfdpPacket init() {
        this.header = createHeader();
        return this;
    }

    protected CfdpPacket(ByteBuffer buffer, CfdpHeader header) {
        this.header = header;
        this.buffer = buffer;
    }

    protected abstract CfdpHeader createHeader();

    private CfdpHeader getHeader() {
        return this.header;
    }

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

    public byte[] toByteArray() {
        // TODO, 65536 is just a random number, find out what it should be
        ByteBuffer buffer = ByteBuffer.allocate(65536);
        getHeader().writeToBuffer(buffer);
        writeCFDPPacket(buffer);
        if (getHeader().withCrc()) {
            calculateAndAddCrc(buffer);
        }
        byte[] toReturn = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(toReturn);
        return toReturn;
    }

    public Tuple toTuple(int transferId) {
        TupleDefinition td = StandardTupleDefinitions.CFDP.copy();
        ArrayList<Object> al = new ArrayList<>();
        al.add(transferId);
        al.add(this.getHeader().getSequenceNumber());
        al.add(this.toByteArray());
        return new Tuple(td, al);
    }

    private boolean crcValid() {
        // TODO implement
        return true;
    }

    // the buffer is assumed to be at the correct position
    protected abstract void writeCFDPPacket(ByteBuffer buffer);

    private void calculateAndAddCrc(ByteBuffer buffer) {
        // TODO implement correctly; note that also the header.datalength field should depend on the presence/absence of
        // the CRC
        buffer.put((byte) 0x00).put((byte) 0x00);
    }

    /*   @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("PDU type: " + (fileDirective ? "File Directive" : "File Data") + "\n");
        sb.append("Direction: " + (towardsSender ? "Towards Sender" : "Towards Receiver") + "\n");
        sb.append("Transmission mode: " + (acknowledged ? "Acknowledged" : "Unacknowledged") + "\n");
        sb.append("With CRC: " + (withCrc ? "Yes" : "No") + "\n");
        sb.append("Data length: " + dataLength + "\n");
        sb.append("SourceId: " + sourceId + " (" + entityIdLength + ")\n");
        sb.append("DestinationId: " + destinationId + " (" + entityIdLength + ")\n");
        sb.append("Sequence nbr: " + sequenceNr + " (" + sequenceNumberLength + ")\n");
        return sb.toString();
    }
    
    public void writeTo(OutputStream os) throws IOException {
        try {
            if (buffer.hasArray()) {
                os.write(buffer.array());
            }
        } catch (BufferOverflowException e) {
            log.error("Overflow while sending " + this, e);
            ;
        }
    }
    */

}
