package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Packet {

    protected ByteBuffer buffer;
    protected Header header;

    private static Logger log = LoggerFactory.getLogger(this.getClass().getName());

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
        Packet toReturn = null;
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
            if (!crcValid(toReturn)) {
                log.error("invalid crc");
            }
        }
        return toReturn;
    }

    public void writePacket(ByteBuffer buffer) {
        getHeader().writeToBuffer(buffer);
        writeCFDPPacket(buffer);
        if (getHeader().withCrc()) {
            calculateAndAddCrc(buffer);
        }
    }

    private boolean crcValid(Packet packet) {
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
