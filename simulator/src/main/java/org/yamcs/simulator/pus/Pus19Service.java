package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.yamcs.utils.StringConverter;

/**
 * ST[19] event-action
 * <p>
 * Executes a stored request each time an event (ST[5] report) is generated on-board.
 * <p>
 * Simplifications with respect to ECSS-E-ST-70-41C:
 * <ul>
 * <li>an event-action definition is identified by the event definition id only (no application process id), so there
 * is at most one action per event;</li>
 * <li>a request with faulty instructions is rejected as a whole (consistently with the other services of this
 * simulator).</li>
 * </ul>
 */
public class Pus19Service extends AbstractPusService {
    static final int MAX_DEFINITIONS = 16;

    boolean functionEnabled = true;
    // event id -> definition
    final Map<Integer, EventAction> definitions = new TreeMap<>();

    Pus19Service(PusSimulator pusSimulator) {
        super(pusSimulator, 19);
    }

    @Override
    public synchronized void executeTc(PusTcPacket tc) {
        switch (tc.getSubtype()) {
        // TC[19,1] add event-action definitions
        case 1 -> addDefinitions(tc);
        // TC[19,2] delete event-action definitions
        case 2 -> deleteDefinitions(tc);
        // TC[19,3] delete all event-action definitions
        case 3 -> {
            ack_start(tc);
            definitions.clear();
            log.info("Deleted all event-action definitions");
            ack_completion(tc);
        }
        // TC[19,4] enable event-action definitions
        case 4 -> setStatus(tc, true);
        // TC[19,5] disable event-action definitions
        case 5 -> setStatus(tc, false);
        // TC[19,6] report the status of each event-action definition -> TM[19,7]
        case 6 -> statusReport(tc);
        // TC[19,8] enable the event-action function
        case 8 -> {
            ack_start(tc);
            functionEnabled = true;
            log.info("Enabled the event-action function");
            ack_completion(tc);
        }
        // TC[19,9] disable the event-action function
        case 9 -> {
            ack_start(tc);
            functionEnabled = false;
            log.info("Disabled the event-action function");
            ack_completion(tc);
        }
        default -> nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
        }
    }

    // TC[19,1]: N x (event id, request). New definitions are disabled.
    private void addDefinitions(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        List<EventAction> parsed = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int eventId = bb.get() & 0xFF;
            int length = (bb.getShort(bb.position() + 4) & 0xFFFF) + 7;
            byte[] packet = new byte[length];
            bb.get(packet);
            if (definitions.containsKey(eventId)) {
                log.warn("Rejecting TC[19,1]: there is already an event-action definition for event {}", eventId);
                nack_start(tc, START_ERR_EVENT_ACTION_EXISTS);
                return;
            }
            parsed.add(new EventAction(eventId, packet));
        }
        if (definitions.size() + parsed.size() > MAX_DEFINITIONS) {
            log.warn("Rejecting TC[19,1]: maximum number of event-action definitions ({}) reached", MAX_DEFINITIONS);
            nack_start(tc, START_ERR_MAX_EVENT_ACTIONS_REACHED);
            return;
        }
        ack_start(tc);
        for (var ea : parsed) {
            definitions.put(ea.eventId, ea);
            var etc = new PusTcPacket(ea.packet);
            log.info("Added event-action definition for event {}: {} (seq count {}, CRC {})", ea.eventId,
                    StringConverter.arrayToHexString(ea.packet), etc.getSequenceCount(),
                    etc.isChecksumValid() ? "valid" : "INVALID");
        }
        ack_completion(tc);
    }

    // TC[19,2]: N x event id. Enabled definitions cannot be deleted.
    private void deleteDefinitions(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = bb.get() & 0xFF;
            var ea = definitions.get(ids[i]);
            if (ea == null) {
                log.warn("Rejecting TC[19,2]: no event-action definition for event {}", ids[i]);
                nack_start(tc, START_ERR_UNKNOWN_EVENT_ACTION);
                return;
            }
            if (ea.enabled) {
                log.warn("Rejecting TC[19,2]: the event-action definition for event {} is enabled", ids[i]);
                nack_start(tc, START_ERR_EVENT_ACTION_ENABLED);
                return;
            }
        }
        ack_start(tc);
        for (int id : ids) {
            definitions.remove(id);
            log.info("Deleted event-action definition for event {}", id);
        }
        ack_completion(tc);
    }

    // TC[19,4]/TC[19,5]: N x event id; N = 0 means all definitions
    private void setStatus(PusTcPacket tc, boolean enabled) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        if (n == 0) {
            ack_start(tc);
            definitions.values().forEach(ea -> ea.enabled = enabled);
            log.info("{} all event-action definitions", enabled ? "Enabled" : "Disabled");
        } else {
            int[] ids = new int[n];
            for (int i = 0; i < n; i++) {
                ids[i] = bb.get() & 0xFF;
                if (!definitions.containsKey(ids[i])) {
                    log.warn("Rejecting TC[19,{}]: no event-action definition for event {}", enabled ? 4 : 5, ids[i]);
                    nack_start(tc, START_ERR_UNKNOWN_EVENT_ACTION);
                    return;
                }
            }
            ack_start(tc);
            for (int id : ids) {
                definitions.get(id).enabled = enabled;
                log.info("{} event-action definition for event {}", enabled ? "Enabled" : "Disabled", id);
            }
        }
        ack_completion(tc);
    }

    // TM[19,7]: N x (event id, status)
    private void statusReport(PusTcPacket tc) {
        ack_start(tc);
        var pkt = newPacket(7, 1 + definitions.size() * 2);
        var bb = pkt.getUserDataBuffer();
        bb.put((byte) definitions.size());
        for (var ea : definitions.values()) {
            bb.put((byte) ea.eventId);
            bb.put((byte) (ea.enabled ? 1 : 0));
        }
        pusSimulator.transmitRealtimeTM(pkt);
        ack_completion(tc);
    }

    /**
     * Called by the ST[5] service each time an event report is generated.
     */
    public synchronized void eventReported(int eventId) {
        if (!functionEnabled) {
            return;
        }
        var ea = definitions.get(eventId);
        if (ea == null || !ea.enabled) {
            return;
        }
        var etc = new PusTcPacket(ea.packet.clone());
        log.info("Event {} reported, executing the event-action {}", eventId, etc);
        pusSimulator.processTc(etc);
    }

    static class EventAction {
        final int eventId;
        final byte[] packet;
        boolean enabled = false;

        EventAction(int eventId, byte[] packet) {
            this.eventId = eventId;
            this.packet = packet;
        }
    }
}
