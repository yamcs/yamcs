package org.yamcs.tctm.pus.services.tc;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.CcsdsPacket;
import org.yamcs.tctm.pus.PusTcManager;
import org.yamcs.utils.ByteArrayUtils;

public class PusTcCcsdsPacket extends CcsdsPacket {
    public static int messageTypeIndex = 7;
    public static int subMessageTypeIndex = 8;
    public static int sourceIDInsertionIndex = 9;
    public static int spareFieldInsertionIndex = 11;

    static int dataFieldStartIndex = PusTcManager.DEFAULT_PRIMARY_HEADER_LENGTH + PusTcManager.secondaryHeaderLength;    // NOTE: THIS IS ONLY AFTER THE SECONDAY HEADER HAS BEEN CORRECTLY SET, OTHERWISE DO NOT USE IT

    public PusTcCcsdsPacket(byte[] packet) {
        super(packet);
    }

    public PusTcCcsdsPacket(ByteBuffer bb) {
        super(bb);
    }

    public static int getMessageType(byte[] pc) {
        return Byte.toUnsignedInt(pc[messageTypeIndex]);
    }

    public static int getMessageSubType(byte[] pc) {
        return Byte.toUnsignedInt(pc[subMessageTypeIndex]);
    }

    public static int getMessageType(PreparedCommand pc) {
        return Byte.toUnsignedInt(pc.getBinary()[messageTypeIndex]);
    }

    public static int getMessageSubType(PreparedCommand pc) {
        return Byte.toUnsignedInt(pc.getBinary()[subMessageTypeIndex]);
    }

    public static int getSourceId(byte[] b) {
        return ByteArrayUtils.decodeUnsignedShort(b, sourceIDInsertionIndex);
    }

    /*
     * NOTE: THIS IS ONLY AFTER THE SECONDAY HEADER HAS BEEN CORRECTLY SET,
     * OTHERWISE DO NOT USE IT
     */
    public static byte[] getDataField(byte[] b) {
        return Arrays.copyOfRange(b, dataFieldStartIndex, b.length);
    }

    private static void setSourceID(PreparedCommand pc) {
        int sourceIDInsertionIndex = 9;
        byte[] telecommandPayload = pc.getBinary();

        ByteBuffer buffer = ByteBuffer.wrap(telecommandPayload);
        buffer.putShort(sourceIDInsertionIndex, (short) PusTcManager.sourceId);

        pc.setBinary(telecommandPayload);
    }

    private static void insertSecondaryHeaderSpareField(PreparedCommand pc) {
        byte[] telecommandPayload = pc.getBinary();

        // Insert Spare Field
        byte[] spareField = new byte[PusTcManager.secondaryHeaderSpareLength];
        byte[] newTelecommandBinary = new byte[telecommandPayload.length + PusTcManager.secondaryHeaderSpareLength];

        ByteBuffer bb = ByteBuffer.wrap(newTelecommandBinary);
        bb.put(Arrays.copyOfRange(telecommandPayload, 0, spareFieldInsertionIndex));
        bb.put(spareField);
        bb.put(Arrays.copyOfRange(telecommandPayload, spareFieldInsertionIndex, telecommandPayload.length));

        pc.setBinary(newTelecommandBinary);
    }

    private static void manipulateTimetag(PreparedCommand pc) {
        byte[] telecommandPayload = pc.getBinary();

        long timetag = ByteArrayUtils.decodeLong(telecommandPayload, PusTcManager.DEFAULT_TIMETAG_INDEX);        // FIXME: Check to make sure if the timetag is compiled to seconds / milliseconds within Yamcs

        int newTelecommandPayloadLength = telecommandPayload.length - PusTcManager.timetagLength;
        byte[] newTelecommandPayload = new byte[newTelecommandPayloadLength];
        
        ByteBuffer buffer = ByteBuffer.wrap(newTelecommandPayload);
        buffer.put(Arrays.copyOfRange(telecommandPayload, 0, PusTcManager.DEFAULT_TIMETAG_INDEX));
        buffer.put(Arrays.copyOfRange(telecommandPayload, PusTcManager.DEFAULT_TIMETAG_INDEX + PusTcManager.timetagLength, telecommandPayload.length));

        pc.setBinary(newTelecommandPayload);
        pc.setAttribute(
            CommandHistoryPublisher.Timetag_KEY,
            timetag
        );
    }

    public static PreparedCommand setPusHeadersSpareFieldAndSourceID(PreparedCommand telecommand) {
        manipulateTimetag(telecommand);     // Timetag has to be extracted first from the structure constructed by the MDb before inserting Source ID and Spare Field
        setSourceID(telecommand);
        insertSecondaryHeaderSpareField(telecommand);

        return telecommand;
    }
}
