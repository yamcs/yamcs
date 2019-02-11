package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Packet {

    protected ByteBuffer buffer;
    protected Header header;

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

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

    protected Packet(ByteBuffer buffer, Header header) {
        this.header = header;
        this.buffer = buffer;
    }

    public static Packet getCFDPPacket(ByteBuffer buffer) {
        Header header = new Header(buffer);
        if (header.isFileDirective()) {
            switch (FileDirectiveCode.readFileDirectiveCode(buffer)) {
            case EOF:
                return new EofPacket(buffer, header);
            case Finished:
                return new FinishedPacket(buffer, header);
            case ACK:
                return new AckPacket(buffer, header);
            case Metadata:
                return new MetadataPacket(buffer, header);
            case NAK:
                return new NakPacket(buffer, header);
            case Prompt:
                return new PromptPacket(buffer, header);
            case KeepAlive:
                return new KeepAlivePacket(buffer, header);
            default:
                break;
            }
        } else {
            return new FileDataPacket(buffer, header);
        }
        return null;
    }

    /*  public CFDPPacket(boolean fileDirective, boolean towardsSender, boolean acknowledged, boolean withCrc,
            int dataLength, int entityIdLength, int sequenceNumberLength, int sourceId, int destinationId,
            int sequenceNr) {
        this.fileDirective = fileDirective;
        this.towardsSender = towardsSender;
        this.acknowledged = acknowledged;
        this.withCrc = withCrc;
        this.dataLength = dataLength;
        this.entityIdLength = entityIdLength;
        this.sequenceNumberLength = sequenceNumberLength;
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.sequenceNr = sequenceNr;
        putHeader();
    }*/

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

    /* private void putHeader() {
        byte b = (byte) ((Utils.boolToByte(!fileDirective) << 4) |
                (Utils.boolToByte(towardsSender) << 3) |
                (Utils.boolToByte(!acknowledged) << 2) |
                (Utils.boolToByte(withCrc) << 1));
        buffer.put(0, b);
        buffer.putShort(1, (short) dataLength);
        b = (byte) ((entityIdLength << 4) |
                (sequenceNumberLength));
        buffer.put(3, b);
        buffer.position(4);
        buffer.put(Utils.longToBytes(sourceId, entityIdLength));
        buffer.position(4 + entityIdLength);
        buffer.put(Utils.longToBytes(sequenceNr, sequenceNumberLength));
        buffer.position(4 + entityIdLength + sequenceNumberLength);
        buffer.put(Utils.longToBytes(destinationId, entityIdLength));
    }*/
}
