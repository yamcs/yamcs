package org.yamcs.tctm;

import java.nio.ByteBuffer;

import org.yamcs.YamcsServer;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.time.TimeService;
import org.yamcs.utils.CcsdsPacket;

/**
 * This implements CCSDS packets as used in Columbus/ISS
 * 
 * @author nm
 *
 */
public class ColumbusPacketPreprocessor implements PacketPreprocessor {
    TimeService timeService;

    public ColumbusPacketPreprocessor(String yamcsInstance) {
        timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    @Override
    public PacketWithTime process(byte[] packet) {
        int apidseqcount = ByteBuffer.wrap(packet).getInt(0);
        return new PacketWithTime(timeService.getMissionTime(), CcsdsPacket.getInstant(packet), apidseqcount, packet);
    }

}
