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

    private Header getHeader() {
        return this.header;
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

    // the buffer is assumed to be at the correct position
    protected void writeCFDPPacket(ByteBuffer buffer) {
        getHeader().writeToBuffer(buffer);
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

}
