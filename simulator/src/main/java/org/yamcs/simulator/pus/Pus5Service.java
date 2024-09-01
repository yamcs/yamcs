package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.yamcs.simulator.pus.PusSimulator.*;

public class Pus5Service extends AbstractPusService {

    static final int INVALID_EVENT_ID = 2;

    int count;
    boolean[] enabled = { true, true };

    Pus5Service(PusSimulator pusSimulator) {
        super(pusSimulator, 5);
    }

    public void sendEvent() {
        var id = count % 5;
        if (id == 0 && enabled[0]) {
            // send event1
            PusTmPacket packet = newPacket(1, 6);
            ByteBuffer bb = packet.getUserDataBuffer();
            bb.putShort((short) count);
            bb.putFloat((float) (count + 3.14));

            pusSimulator.transmitRealtimeTM(packet);
        } else if (enabled[1]) {
            // send event2 with subtype (severity level) id
            byte[] msg = ("This is an event with subtype " + id).getBytes(StandardCharsets.UTF_8);
            PusTmPacket packet = newPacket(id, 2 + msg.length);
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
            pusSimulator.transmitRealtimeTM(nack(tc, PUS_SUBTYPE_NACK_START, ERR_INVALID_PUS_SUBTYPE));
            return;
        }

        if (tc.getSubtype() < 5 || tc.getSubtype() > 6) {
            log.info("invalid subtype {}, sending NACK start", tc.getSubtype());
            pusSimulator.transmitRealtimeTM(nack(tc, PUS_SUBTYPE_NACK_START, ERR_INVALID_PUS_SUBTYPE));
            return;
        }

    }

}
