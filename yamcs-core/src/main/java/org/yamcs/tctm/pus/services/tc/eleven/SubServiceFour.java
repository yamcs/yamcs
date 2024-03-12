package org.yamcs.tctm.pus.services.tc.eleven;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.PusTcManager;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tc.PusTcCcsdsPacket;


public class SubServiceFour implements PusSubService {
    String yamcsInstance;

    final static private byte DEFAULT_ACKNOWLEDGEMENT_FLAGS = 15;  // FIXME: Assuming default ack Flags of (1111)2 | Is this alright?
    final static private byte SUBSERVICE_TYPE = 4;
    final static private int NUMBER_OF_TELECOMMANDS_SIZE = 4;
    final static private int NUMBER_OF_TELECOMMANDS = 1;           // FIXME: Assumed by default that only 1 time-tagged telecommand can be packetised at once

    private static byte acknowledgementFlags;

    SubServiceFour(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;

        acknowledgementFlags = (byte) config.getInt("acknowledgementFlags", DEFAULT_ACKNOWLEDGEMENT_FLAGS);
    }

    private byte[] constructPrimaryHeader(int commandApid) {
        short primaryHeaderFirstHalf = (short) (
            ((ServiceEleven.ccsdsVersionNumber & 0x07) << 13) |
            ((ServiceEleven.ccsdsPacketType & 0x01) << 12) |
            ((ServiceEleven.secondaryHeaderFlag & 0x01) << 11) |
            ((ServiceEleven.fswApidMap.get(commandApid) & 0x07FF))
        );
        short primaryHeaderSecondHalf = (short) (
            ((ServiceEleven.sequenceFlags & 3) << 14) |
            (ServiceEleven.packetSequenceCount)
        );

        byte[] primaryHeader = new byte[6];

        ByteBuffer buffer = ByteBuffer.wrap(primaryHeader);
        buffer.putShort(primaryHeaderFirstHalf);
        buffer.putShort(primaryHeaderSecondHalf);

        return buffer.array();
    }

    private byte[] constructSecondaryHeader() {
        byte pusVersionAcknowledgementFlags = (byte) (
            ((ServiceEleven.pusVersionNumber & 0xF0) << 4) |
            (acknowledgementFlags & 0x0F) 
        );

        byte[] secondaryHeader = new byte[5];

        ByteBuffer bb = ByteBuffer.wrap(secondaryHeader);
        bb.put(pusVersionAcknowledgementFlags);
        bb.put((byte) ServiceEleven.serviceType);
        bb.put((byte) SUBSERVICE_TYPE);

        return secondaryHeader;
    }

    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
        int commandApid = PusTcCcsdsPacket.getAPID(telecommand.getBinary());

        byte[] primaryHeader = constructPrimaryHeader(commandApid);
        byte[] secondaryHeader = constructSecondaryHeader();
        byte[] telecommandPayload = telecommand.getBinary();

        byte[] wrappedTelecommandPayload = new byte[primaryHeader.length + secondaryHeader.length + NUMBER_OF_TELECOMMANDS_SIZE + PusTcManager.timetagLength +  telecommandPayload.length];
        long timetag = telecommand.getTimestampAttribute(CommandHistoryPublisher.Timetag_KEY);

        ByteBuffer buffer = ByteBuffer.wrap(wrappedTelecommandPayload);
        buffer.put(primaryHeader);
        buffer.put(secondaryHeader);
        buffer.putInt(NUMBER_OF_TELECOMMANDS);
        buffer.putInt((int) timetag);       // FF mission only supports 4bytes timetag
        buffer.put(telecommandPayload);

        telecommand.setBinary(wrappedTelecommandPayload);                  // Replace with the wrapped telecommand
        return telecommand;
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

}
