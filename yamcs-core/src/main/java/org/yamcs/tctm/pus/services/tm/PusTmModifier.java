package org.yamcs.tctm.pus.services.tm;

import java.time.Instant;
import java.util.Arrays;

import org.yamcs.TmPacket;
import org.yamcs.utils.ByteArrayUtils;

public class PusTmModifier {
    static int messageTypeIndex = 7;
    static int subMessageTypeIndex = 8;
    static int destinationIDIndex = 11;

    public static int decodeByteArrayToInteger(byte[] arr, int length, int offset) {
        int decodedInteger = 0;
        for(int i = 0; i < length; i++) {
            decodedInteger += decodedInteger + (arr[offset] & 0xFF << ((length - (i + 1)) * 8));
            offset += 1;
        }

        return decodedInteger;
    }

    public static int getAPID(TmPacket tmPacket) {
        byte[] primaryHeader = getPrimaryHeader(tmPacket);
        int apid = ByteArrayUtils.decodeUnsignedShort(primaryHeader, 0) & 0x7FF;

        return apid;
    }

    public static int getMessageType(TmPacket tmPacket) {
        return Byte.toUnsignedInt(tmPacket.getPacket()[messageTypeIndex]);
    }

    public static int getMessageSubType(TmPacket tmPacket) {
        return Byte.toUnsignedInt(tmPacket.getPacket()[subMessageTypeIndex]);
    }

    public static byte[] getDataField(TmPacket tmPacket) {
        byte[] tmPacketPayload = tmPacket.getPacket();
        return Arrays.copyOfRange(tmPacketPayload, PusTmManager.secondaryHeaderLength, tmPacketPayload.length);
    }

    public static byte[] getSpareField(TmPacket tmPacket) {
        byte[] tmPacketPayload = tmPacket.getPacket();
        byte[] secondaryHeader = Arrays.copyOfRange(tmPacketPayload, PusTmManager.PRIMARY_HEADER_LENGTH,
                PusTmManager.PRIMARY_HEADER_LENGTH + PusTmManager.secondaryHeaderLength);

        return Arrays.copyOfRange(secondaryHeader, PusTmManager.PUS_HEADER_LENGTH, secondaryHeader.length);
    }

    public static Instant getAbsoluteDatetime(TmPacket tmPacket) {
        return Instant.ofEpochSecond(tmPacket.getGenerationTime());
    }

    public static long getAbsoluteDatetimeLong(TmPacket tmPacket) {
        return tmPacket.getGenerationTime();
    }

    public static byte[] getPrimaryHeader(TmPacket tmPacket) {
        byte[] tmPacketPayload = tmPacket.getPacket();
        return Arrays.copyOfRange(tmPacketPayload, 0, PusTmManager.PRIMARY_HEADER_LENGTH);
    }

    public static byte[] getSecondaryHeader(TmPacket tmPacket) {
        byte[] tmPacketPayload = tmPacket.getPacket();
        return Arrays.copyOfRange(tmPacketPayload, PusTmManager.PRIMARY_HEADER_LENGTH,
                PusTmManager.PRIMARY_HEADER_LENGTH + PusTmManager.secondaryHeaderLength);
    }

    public static int getDestinationID(TmPacket tmPacket) {
        byte[] secondaryHeader = getSecondaryHeader(tmPacket);
        byte[] pusHeader = Arrays.copyOfRange(secondaryHeader, 0, PusTmManager.PUS_HEADER_LENGTH);

        return ByteArrayUtils.decodeUnsignedShort(pusHeader, destinationIDIndex);
    }
}
