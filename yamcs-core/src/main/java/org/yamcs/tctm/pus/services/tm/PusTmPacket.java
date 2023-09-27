package org.yamcs.tctm.pus.services.tm;

import java.util.Arrays;

import org.yamcs.TmPacket;

public class PusTmPacket{
    // FIXME: Check with Sivabhishek to see if this implmented on FSW | = 0
    int spacecraftTimeReferenceStatus;
    int messageTypeCounter;

    int messageType;
    int pusVersionNumber;
    int messageSubType;
    int destinationID;
    long absoluteTime;

    TmPacket tmPacket;

    byte[] dataField;
    byte[] spareField;
    byte[] secondaryHeader;
    byte[] primaryHeader;

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

    public byte[] getSpareField() {
        return this.spareField;
    }

    public byte[] getSecondaryHeader() {
        return this.secondaryHeader;
    }

    public byte[] getPrimaryHeader() {
        return this.primaryHeader;
    }


    public static int getInteger(byte[] arr, int off) {
        return arr[off] << 8 & 0xFF00 | arr[off + 1] & 0xFF;
    }

    public PusTmPacket(TmPacket tmPacket) {
        this.tmPacket = tmPacket;

        byte[] tmPacketPayload = tmPacket.getPacket();
        primaryHeader = Arrays.copyOfRange(tmPacketPayload, 0, TmPusManager.PRIMARY_HEADER_LENGTH);
        secondaryHeader = Arrays.copyOfRange(tmPacketPayload, TmPusManager.PRIMARY_HEADER_LENGTH, TmPusManager.PRIMARY_HEADER_LENGTH + TmPusManager.secondaryHeaderLength);

        byte[] pusHeader = Arrays.copyOfRange(secondaryHeader, 0, TmPusManager.PUS_HEADER_LENGTH);
        spareField = Arrays.copyOfRange(secondaryHeader, TmPusManager.PUS_HEADER_LENGTH, secondaryHeader.length);
        dataField = Arrays.copyOfRange(tmPacketPayload, TmPusManager.secondaryHeaderLength, tmPacketPayload.length);

        setPusVersionNumberAndSpacecraftTimeReferenceStatus(pusHeader[0]);
        setMessageTypeAndSubType(Arrays.copyOfRange(pusHeader, 1, 3));

        this.messageTypeCounter = getInteger(pusHeader, 3);
        this.destinationID = getInteger(pusHeader, 5);
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
