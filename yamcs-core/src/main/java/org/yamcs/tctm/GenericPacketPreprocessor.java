package org.yamcs.tctm;

import java.util.Map;

import org.yamcs.YamcsServer;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.time.TimeService;

public class GenericPacketPreprocessor implements PacketPreprocessor {
    TimeService timeService;

    public GenericPacketPreprocessor(String yamcsInstance) {
        this(yamcsInstance, null);
    }

    public GenericPacketPreprocessor(String yamcsInstance, Map<String, Object> config) {
        timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    @Override
    public PacketWithTime process(byte[] packet) {
        long now = timeService.getMissionTime();
        PacketWithTime pwt = new PacketWithTime(now, now, 0, packet);
        return pwt;
    }
}
