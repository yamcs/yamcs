package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.yamcs.simulator.pus.PusSimulator.*;

public class Pus5Service {
    private static final Logger log = LoggerFactory.getLogger(Pus5Service.class);
    static final int INVALID_PUS_SUBTYPE = 1;
    static final int INVALID_EVENT_ID = 2;

    private PusSimulator pusSimulator;
    int count;
    boolean[] enabled = { true, true };

    Pus5Service(PusSimulator pusSimulator) {
        this.pusSimulator = pusSimulator;
    }

    public void sendEvent() {
        var id = count % 5;
        if (id == 0 && enabled[0]) {
            // send event1
            PusTmPacket packet = new PusTmPacket(MAIN_APID, 6, PUS_TYPE_EVENT, 1);
            ByteBuffer bb = packet.getUserDataBuffer();
            bb.putShort((short) count);
            bb.putFloat((float) (count + 3.14));

            pusSimulator.transmitRealtimeTM(packet);
        } else if (enabled[1]) {
            // send event2 with subtype (severity level) id
            byte[] msg = ("This is an event with subtype " + id).getBytes(StandardCharsets.UTF_8);

            PusTmPacket packet = new PusTmPacket(MAIN_APID, 2 + msg.length, PUS_TYPE_EVENT, id);
            ByteBuffer bb = packet.getUserDataBuffer();
            bb.putShort((short) msg.length);
            bb.put(msg);
            pusSimulator.transmitRealtimeTM(packet);
        }
        count++;
    }

    public void executeTc(PusTcPacket tc) {

        if (tc.getSubtype() < 5 || tc.getSubtype() > 6) {
            log.info("invalid subtype {}, sending NACK start", tc.getSubtype());
            pusSimulator.transmitRealtimeTM(nack(tc, PUS_SUBTYPE_NACK_START, INVALID_PUS_SUBTYPE));
            return;
        }

        if (tc.getSubtype() < 5 || tc.getSubtype() > 6) {
            log.info("invalid subtype {}, sending NACK start", tc.getSubtype());
            pusSimulator.transmitRealtimeTM(nack(tc, PUS_SUBTYPE_NACK_START, INVALID_PUS_SUBTYPE));
            return;
        }

    }

}
