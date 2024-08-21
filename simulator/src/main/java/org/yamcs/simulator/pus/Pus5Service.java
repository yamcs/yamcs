package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;


public class Pus5Service extends AbstractPusService {
    int count;
    boolean[] enabled = { true, true };

    Pus5Service(PusSimulator pusSimulator) {
        super(pusSimulator, 5);
    }

    @Override
    public void start() {
        pusSimulator.executor.scheduleAtFixedRate(() -> sendEvent(), 0, 1000, TimeUnit.MILLISECONDS);
    }

    public void sendEvent() {
        var id = count % 5;
        if (id == 0 && enabled[0]) {
            // send event1
            PusTmPacket packet = newPacket(1, 7);
            ByteBuffer bb = packet.getUserDataBuffer();
            bb.put((byte) 1);
            bb.putShort((short) count);
            bb.putFloat((float) (count + 3.14159265));

            pusSimulator.transmitRealtimeTM(packet);
        } else if (enabled[1]) {
            // send event2 with subtype (severity level) id
            byte[] msg = ("This is an event with subtype " + id).getBytes(StandardCharsets.UTF_8);
            PusTmPacket packet = newPacket(id, 3 + msg.length);
            ByteBuffer bb = packet.getUserDataBuffer();
            bb.put((byte) 2);
            bb.putShort((short) msg.length);
            bb.put(msg);
            pusSimulator.transmitRealtimeTM(packet);
        }
        count++;
    }


    public void executeTc(PusTcPacket tc) {
        if (tc.getSubtype() == 5 ||tc.getSubtype() == 6 ) {
            ack_start(tc);
            enableDisableEvents(tc, tc.getSubtype() == 5);
        } else if (tc.getSubtype() == 7) {
            nack_start(tc, START_ERR_NOT_IMPLEMENTED);
        } else {
            log.info("invalid subtype {}, sending NACK start", tc.getSubtype());
            nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
            return;
        }
    }

    void enableDisableEvents(PusTcPacket tc, boolean enable) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        for (int i = 0; i < n; i++) {
            int eventId = bb.get() & 0xFF;
            if (eventId == 0 || eventId  > enabled.length) {
                log.info("invalid event id {}, sending NACK start", eventId);
                nack_completion(tc, COMPL_ERR_INVALID_EVENT_ID);
                return;
            }
            enabled[eventId - 1] = enable;
        }
        ack_completion(tc);
    }

}
