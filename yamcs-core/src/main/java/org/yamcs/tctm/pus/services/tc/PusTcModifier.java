package org.yamcs.tctm.pus.services.tc;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.yamcs.commanding.PreparedCommand;
import org.yamcs.utils.ByteArrayUtils;

public class PusTcModifier {
    static int messageTypeIndex = 7;
    static int subMessageTypeIndex = 8;

    private static int DEFAULT_TIMETAG_INDEX = PusTcManager.DEFAULT_PRIMARY_HEADER_LENGTH + PusTcManager.DEFAULT_PUS_HEADER_LENGTH;
    public static int DEFAULT_TIMETAG_LENGTH = 8;

    private static PreparedCommand setSourceID(PreparedCommand pc) {
        int sourceIDInsertionIndex = 9;
        byte[] telecommandPayload = pc.getBinary();

        ByteBuffer buffer = ByteBuffer.wrap(telecommandPayload);
        buffer.putShort(sourceIDInsertionIndex, (short) PusTcManager.sourceID);

        pc.setBinary(telecommandPayload);
        return pc;
    }

    private static PreparedCommand insertSecondaryHeaderSpareField(PreparedCommand pc) {
        int spareFieldInsertionIndex = 11;
        byte[] telecommandPayload = pc.getBinary();

        // Insert Spare Field
        byte[] spareField = new byte[PusTcManager.secondaryHeaderSpareLength];
        byte[] newTelecommandBinary = new byte[telecommandPayload.length + PusTcManager.secondaryHeaderSpareLength];

        ByteBuffer bb = ByteBuffer.wrap(newTelecommandBinary);
        bb.put(Arrays.copyOfRange(telecommandPayload, 0, spareFieldInsertionIndex));
        bb.put(spareField);
        bb.put(Arrays.copyOfRange(telecommandPayload, spareFieldInsertionIndex, telecommandPayload.length));

        pc.setBinary(newTelecommandBinary);

        return pc;
    }

    private static PreparedCommand manipulateTimetag(PreparedCommand pc) {
        byte[] telecommandPayload = pc.getBinary();

        long timetag = ByteArrayUtils.decodeLong(telecommandPayload, DEFAULT_TIMETAG_INDEX);        // FIXME: Check to make sure if the timetag is compiled to seconds / milliseconds within Yamcs

        int newTelecommandPayloadLength = telecommandPayload.length - DEFAULT_TIMETAG_LENGTH;
        byte[] newTelecommandPayload = new byte[newTelecommandPayloadLength];
        
        ByteBuffer buffer = ByteBuffer.wrap(newTelecommandPayload);
        buffer.put(Arrays.copyOfRange(telecommandPayload, 0, DEFAULT_TIMETAG_INDEX));
        buffer.put(Arrays.copyOfRange(telecommandPayload, DEFAULT_TIMETAG_INDEX + DEFAULT_TIMETAG_LENGTH, telecommandPayload.length));

        pc.setBinary(newTelecommandPayload);
        pc.setTimetag(timetag);

        return pc;
    }

    public static int getMessageType(PreparedCommand telecommand) {
        return Byte.toUnsignedInt(telecommand.getBinary()[messageTypeIndex]);
    }

    public static int getMessageSubType(PreparedCommand telecommand) {
        return Byte.toUnsignedInt(telecommand.getBinary()[subMessageTypeIndex]);
    }

    public static PreparedCommand setPusHeadersSpareFieldAndSourceID(PreparedCommand telecommand) {
        manipulateTimetag(telecommand);     // Timetag has to be extracted first from the structure constructed by the MDb before inserting Source ID and Spare Field
        setSourceID(telecommand);
        insertSecondaryHeaderSpareField(telecommand);

        return telecommand;
    }
}

