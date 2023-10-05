package org.yamcs.tctm.pus.services.tc;

import org.yamcs.commanding.PreparedCommand;
import org.yamcs.utils.ByteArrayUtils;

public class PusTcModifier {
    static int messageTypeIndex = 7;
    static int subMessageTypeInedx = 8;

    private static PreparedCommand setSourceID(PreparedCommand telecommand) {
        int sourceIDInsertionIndex = 9;

        int newTelecommandLength = telecommand.getBinary().length + 2;
        byte[] newTelecommandBinary = new byte[newTelecommandLength];
        byte[] sourceIDArr = new byte[2];


        System.arraycopy(telecommand.getBinary(), 0, newTelecommandBinary, 0, sourceIDInsertionIndex);
        System.arraycopy(ByteArrayUtils.encodeUnsignedShort(PusTcManager.sourceID, sourceIDArr, 0), 0, newTelecommandBinary, sourceIDInsertionIndex, sourceIDArr.length);
        System.arraycopy(telecommand.getBinary(), sourceIDInsertionIndex, newTelecommandBinary, sourceIDInsertionIndex + sourceIDArr.length, telecommand.getBinary().length - sourceIDInsertionIndex);

        telecommand.setBinary(newTelecommandBinary);
        return telecommand;
    }

    private static PreparedCommand insertSecondaryHeaderSpareField(PreparedCommand telecommand) {
        int spareFieldInsertionIndex = 11;

        byte[] secondaryHeaderSpareField = new byte[PusTcManager.secondaryHeaderSpareLength];
        byte[] newTelecommandBinary = new byte[telecommand.getBinary().length + PusTcManager.secondaryHeaderSpareLength];

        System.arraycopy(telecommand.getBinary(), 0, newTelecommandBinary, 0, spareFieldInsertionIndex);
        System.arraycopy(ByteArrayUtils.encodeUnsignedShort(PusTcManager.sourceID, secondaryHeaderSpareField, 0), 0, newTelecommandBinary, spareFieldInsertionIndex, secondaryHeaderSpareField.length);
        System.arraycopy(telecommand.getBinary(), spareFieldInsertionIndex, newTelecommandBinary, spareFieldInsertionIndex + secondaryHeaderSpareField.length, telecommand.getBinary().length - spareFieldInsertionIndex);

        telecommand.setBinary(newTelecommandBinary);
        return telecommand;
    }

    public static int getMessageType(PreparedCommand telecommand) {
        return Byte.toUnsignedInt(telecommand.getBinary()[messageTypeIndex]);
    }

    public static int getMessageSubType(PreparedCommand telecommand) {
        return Byte.toUnsignedInt(telecommand.getBinary()[subMessageTypeInedx]);
    }

    public static PreparedCommand setPusHeadersSpareFieldAndSourceID(PreparedCommand telecommand) {
        telecommand = setSourceID(telecommand);
        telecommand = insertSecondaryHeaderSpareField(telecommand);

        return telecommand;
    }
}

