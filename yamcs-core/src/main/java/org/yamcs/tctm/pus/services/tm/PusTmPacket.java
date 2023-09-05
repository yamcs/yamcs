package org.yamcs.tctm.pus.services.tm;

import java.util.Arrays;

import org.yamcs.TmPacket;

public class PusTmPacket{
    int pusVersionNumber;

    // FIXME: Check with Sivabhishek to see if this implmented on FSW | = 0
    int spacecraftTimeReferenceStatus;

    int messageType;
    int messageSubType;

    // FIXME: Check with Sivabhishek to see if this is implemented on FSW | = 0
    int messageTypeCounter;
    int destinationID;

    long absoluteTime;

    TmPacket tmPacket;
    byte[] dataField;

    public int getMessageType() {
        return this.messageType;
    }

    public int getMessageSubType() {
        return this.messageSubType;
    }

    public int getMessageTypeCounter() {
        return this.messageTypeCounter;
    }

    public int getDestinationID() {
        return this.destinationID;
    }

    public long getAbsoluteTime() {
        return this.absoluteTime;
    }

    public TmPacket getTmPacket() {
        return this.tmPacket;
    }

    public byte[] getDataField() {
        return this.dataField;
    }

    public static int getInteger(byte[] arr, int off) {
        return arr[off] << 8 & 0xFF00 | arr[off + 1] & 0xFF;
    }

    public PusTmPacket(TmPacket tmPacket) {
        this.tmPacket = tmPacket;

        byte[] tmPacketPayload = tmPacket.getPacket();
        byte[] tmPacketSecondaryHeader = Arrays.copyOfRange(tmPacketPayload, 6, 17);
        this.dataField = Arrays.copyOfRange(tmPacketPayload, 17, tmPacketPayload.length);

        setPusVersionNumberAndSpacecraftTimeReferenceStatus(tmPacketSecondaryHeader[0]);
        setMessageTypeAndSubType(Arrays.copyOfRange(tmPacketSecondaryHeader, 1, 3));

        this.messageTypeCounter = getInteger(tmPacketSecondaryHeader, 3);
        this.destinationID = getInteger(tmPacketSecondaryHeader, 5);
        this.absoluteTime = tmPacket.getGenerationTime();        
    }

    public void setPusVersionNumberAndSpacecraftTimeReferenceStatus(byte d0) {
        this.pusVersionNumber = (d0 & 0xFF) >> 4;
        this.spacecraftTimeReferenceStatus = (d0 & 0x0F);
    }

    public void setMessageTypeAndSubType(byte[] d) {
        this.messageType = Byte.toUnsignedInt(d[0]);
        this.messageSubType = Byte.toUnsignedInt(d[1]);
    }
}
