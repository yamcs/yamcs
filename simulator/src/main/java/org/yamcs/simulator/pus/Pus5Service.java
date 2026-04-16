package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class Pus5Service extends AbstractPusService {
    int count;
    // Map of eventId -> enabled status. Add all mission event IDs here with their initial state.
    Map<Integer, Boolean> enabled = new HashMap<>(Map.of(1, true, 2, true));

    Pus5Service(PusSimulator pusSimulator) {
        super(pusSimulator, 5);
    }

    @Override
    public void start() {
        pusSimulator.executor.scheduleAtFixedRate(this::sendEvent, 0, 1000, TimeUnit.MILLISECONDS);
    }

    public void sendEvent() {
        var id = count % 5;
        if (id == 0 && enabled.getOrDefault(1, false)) {
            // send event1 (informative, subtype 1)
            PusTmPacket packet = newPacket(1, 7);
            ByteBuffer bb = packet.getUserDataBuffer();
            bb.put((byte) 1);
            bb.putShort((short) count);
            bb.putFloat((float) (count + 3.14159265));
            pusSimulator.transmitRealtimeTM(packet);
        } else if (enabled.getOrDefault(2, false)) {
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
        if (tc.getSubtype() == 5 || tc.getSubtype() == 6) {
            ack_start(tc);
            enableDisableEvents(tc, tc.getSubtype() == 5);
        } else if (tc.getSubtype() == 7) {
            ack_start(tc);
            sendDisabledList(tc);
        } else {
            log.info("invalid subtype {}, sending NACK start", tc.getSubtype());
            nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
        }
    }

    void enableDisableEvents(PusTcPacket tc, boolean enable) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        boolean anyFailure = false;
        for (int i = 0; i < n; i++) {
            int eventId = bb.get() & 0xFF;
            if (!enabled.containsKey(eventId)) {
                log.info("invalid event id {}, sending NACK completion", eventId);
                nack_completion(tc, COMPL_ERR_INVALID_EVENT_ID);
                anyFailure = true;
                // continue — spec §6.5.5.2f: valid instructions shall be processed
                // regardless of the presence of faulty instructions
            } else {
                enabled.put(eventId, enable);
            }
        }
        if (!anyFailure) {
            ack_completion(tc);
        }
    }

    // TC[5,7] handler — responds with TM[5,8] listing all disabled event IDs
    void sendDisabledList(PusTcPacket tc) {
        List<Integer> disabled = new ArrayList<>();
        for (var entry : enabled.entrySet()) {
            if (!entry.getValue()) disabled.add(entry.getKey());
        }
        disabled.sort(null);
        PusTmPacket pkt = newPacket(8, 1 + disabled.size());
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.put((byte) disabled.size());
        for (int id : disabled) bb.put((byte) id);
        pusSimulator.transmitRealtimeTM(pkt);
        ack_completion(tc);
    }

}
