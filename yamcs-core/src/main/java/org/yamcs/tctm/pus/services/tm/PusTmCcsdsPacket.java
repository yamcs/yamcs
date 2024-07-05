package org.yamcs.tctm.pus.services.tm;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.yamcs.tctm.CcsdsPacket;
import org.yamcs.tctm.pus.PusTmManager;
import org.yamcs.utils.ByteArrayUtils;

public class PusTmCcsdsPacket extends CcsdsPacket {
    static int messageTypeIndex = 7;
    static int messageSubTypeIndex = 8;
    static int destinationIDIndex = 11;
    static int generationTimeIndex = 13;
    static int generationTimeSize = PusTmManager.absoluteTimeLength;

    public static int messageSubTypeSize = 2;
    public static int messageTypeSize = 2;

    public PusTmCcsdsPacket(byte[] packet) {
        super(packet);
    }

    public PusTmCcsdsPacket(ByteBuffer bb) {
        super(bb);
    }

    public static byte[] getGenerationTime(byte[] b) {
        return Arrays.copyOfRange(b, generationTimeIndex, generationTimeIndex + generationTimeSize);
    }

    public byte[] getGenerationTime() {
        byte[] b = bb.duplicate().array();
        return Arrays.copyOfRange(b, generationTimeIndex, generationTimeIndex + generationTimeSize);
    }

    public static int getMessageType(byte[] b) {
        return Byte.toUnsignedInt(b[messageTypeIndex]);
    }
    
    public int getMessageType() {
        return Byte.toUnsignedInt(bb.get(messageTypeIndex));
    }


    public static int getMessageSubType(byte[]b) {
        return Byte.toUnsignedInt(b[messageSubTypeIndex]);
    }

    public int getMessageSubType() {
        return Byte.toUnsignedInt(bb.get(messageSubTypeIndex));
    }


    public byte[] getDataField() {
        byte[] b = bb.duplicate().array();
        return Arrays.copyOfRange(b, PusTmManager.secondaryHeaderLength + PusTmManager.PRIMARY_HEADER_LENGTH, b.length);
    }

    public byte[] getSpareField() {
        byte[] secondaryHeader = getSecondaryHeader();

        return Arrays.copyOfRange(secondaryHeader, PusTmManager.PUS_HEADER_LENGTH, secondaryHeader.length);
    }

    public byte[] getPusHeaderField() {
        byte[] secondaryHeader = getSecondaryHeader();

        return Arrays.copyOfRange(secondaryHeader, 0, PusTmManager.PUS_HEADER_LENGTH);
    }


    public byte[] getPrimaryHeader() {
        byte[] b = bb.duplicate().array();
        return Arrays.copyOfRange(b, 0, PusTmManager.PRIMARY_HEADER_LENGTH);
    }

    public byte[] getSecondaryHeader() {
        byte[] b = bb.duplicate().array();
        return Arrays.copyOfRange(b, PusTmManager.PRIMARY_HEADER_LENGTH, PusTmManager.PRIMARY_HEADER_LENGTH + PusTmManager.secondaryHeaderLength);
    }


    public int getDestinationID() {
        byte[] secondaryHeader = getSecondaryHeader();
        byte[] pusHeader = Arrays.copyOfRange(secondaryHeader, 0, PusTmManager.PUS_HEADER_LENGTH);

        int pusHeaderDestinationIndex = 5;
        return ByteArrayUtils.decodeUnsignedShort(pusHeader, pusHeaderDestinationIndex);
    }
}
