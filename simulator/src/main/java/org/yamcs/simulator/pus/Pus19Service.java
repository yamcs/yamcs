package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ST[19] Event-Action Service simulator. See pus_analysis/pus19.md and pus_simulator_architecture.md
 * for the full design rationale.
 *
 * <p>
 * Two independent status layers are maintained on-board: a global {@code functionEnabled} flag
 * (TC[19,8]/TC[19,9]) and a per-definition enabled/disabled status (TC[19,4]/TC[19,5]). Disabling
 * the function does not change individual definition statuses and vice versa (§6.19.5).
 *
 * <p>
 * Each definition maps an event system identifier {@code (app_process_id, event_definition_id)} to
 * a stored "request": a complete, CRC'd raw TC packet supplied by ground inside TC[19,1] (mission
 * convention -- see mdb/pus19.xml NoteSet for how the embedded TC is length-prefixed and encoded as
 * a dynamically-sized binary argument, avoiding the raw-XTCE-gap workaround used elsewhere). When a
 * matching ST[05] event fires and both status layers are enabled, the stored bytes are reconstructed
 * into a {@link PusTcPacket} and dispatched via {@link PusSimulator#processTc}, the same mechanism
 * Pus13Service uses for reassembled large commands -- the released request gets its own independent
 * ACK/NACK/completion chain, exactly as if it had arrived from ground.
 *
 * <p>
 * ST[05] coupling: {@link Pus5Service#sendEvent()} and {@link Pus5Service#raiseEvent} call
 * {@link #onEvent} after transmitting each event TM, since this simulator has a single application
 * process (MAIN_APID). There is no separate EventBus indirection -- a direct call mirrors how
 * Pus14Service/Pus15Service hook into {@link PusSimulator#transmitRealtimeTM}.
 */
public class Pus19Service extends AbstractPusService {

    // completion errors (see AbstractPusService for the shared ones)
    static final int COMPL_ERR_EA_DEF_NOT_FOUND = 5;
    static final int COMPL_ERR_EA_DEF_ENABLED = 6;

    private boolean functionEnabled = false;
    private final Map<Integer, EaDefinition> definitions = new LinkedHashMap<>();

    Pus19Service(PusSimulator pusSimulator) {
        super(pusSimulator, 19);
    }

    private static int key(int appProcessId, int eventDefId) {
        return (appProcessId << 16) | (eventDefId & 0xFFFF);
    }

    private static class EaDefinition {
        final int appProcessId;
        final int eventDefId;
        boolean enabled = false;
        byte[] request;

        EaDefinition(int appProcessId, int eventDefId, byte[] request) {
            this.appProcessId = appProcessId;
            this.eventDefId = eventDefId;
            this.request = request;
        }
    }

    @Override
    public void executeTc(PusTcPacket tc) {
        switch (tc.getSubtype()) {
        // TC[19,1] add event-action definitions
        case 1 -> addDefinitions(tc);
        // TC[19,2] delete event-action definitions
        case 2 -> deleteDefinitions(tc);
        // TC[19,4] enable event-action definitions (specific or all)
        case 4 -> setDefinitionsEnabled(tc, true);
        // TC[19,5] disable event-action definitions (specific or all)
        case 5 -> setDefinitionsEnabled(tc, false);
        // TC[19,6] report status of each event-action definition -> TM[19,7]
        case 6 -> reportStatus(tc);
        // TC[19,8] enable the event-action function
        case 8 -> setFunctionEnabled(tc, true);
        // TC[19,9] disable the event-action function
        case 9 -> setFunctionEnabled(tc, false);
        // TC[19,10] report event-action definitions -> TM[19,11]
        case 10 -> reportDefinitions(tc);
        default -> {
            log.warn("Unknown ST[19] subtype {}, sending NACK start", tc.getSubtype());
            nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
        }
        }
    }

    /**
     * Called by Pus5Service after it transmits an event TM. Autonomously releases the stored
     * request for any enabled definition matching this event, provided the function is enabled.
     */
    public void onEvent(int appProcessId, int eventDefId) {
        if (!functionEnabled) {
            return;
        }
        EaDefinition def = definitions.get(key(appProcessId, eventDefId));
        if (def == null || !def.enabled) {
            return;
        }
        try {
            PusTcPacket released = new PusTcPacket(def.request);
            log.info("ST19: event ({},{}) fired, releasing stored request TC type={} subtype={}",
                    appProcessId, eventDefId, released.getType(), released.getSubtype());
            pusSimulator.processTc(released);
        } catch (Exception e) {
            log.warn("ST19: failed to release stored request for event ({},{})", appProcessId, eventDefId, e);
        }
    }

    // ---- TC[19,1]: add event-action definitions ----

    private void addDefinitions(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        for (int i = 0; i < n; i++) {
            int appPid = bb.getShort() & 0xFFFF;
            int eventId = bb.getShort() & 0xFFFF;
            int reqLen = bb.getShort() & 0xFFFF;
            byte[] request = new byte[reqLen];
            bb.get(request);
            int k = key(appPid, eventId);
            EaDefinition existing = definitions.get(k);
            if (existing != null) {
                existing.request = request;
                log.info("ST19: updated EA definition ({},{})", appPid, eventId);
            } else {
                definitions.put(k, new EaDefinition(appPid, eventId, request));
                log.info("ST19: added EA definition ({},{}), status=disabled", appPid, eventId);
            }
        }
        ack_completion(tc);
    }

    // ---- TC[19,2]: delete event-action definitions ----

    private void deleteDefinitions(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        List<EaDefinition> targets = resolveTargets(tc, bb);
        if (targets == null) {
            return;
        }
        for (EaDefinition def : targets) {
            if (def.enabled) {
                log.warn("ST19: EA definition ({},{}) is enabled, rejecting delete", def.appProcessId,
                        def.eventDefId);
                nack_completion(tc, COMPL_ERR_EA_DEF_ENABLED);
                return;
            }
        }
        for (EaDefinition def : targets) {
            definitions.remove(key(def.appProcessId, def.eventDefId));
        }
        log.info("ST19: deleted {} EA definition(s), {} remaining", targets.size(), definitions.size());
        ack_completion(tc);
    }

    private List<EaDefinition> resolveTargets(PusTcPacket tc, ByteBuffer bb) {
        int n = bb.get() & 0xFF;
        List<EaDefinition> targets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int appPid = bb.getShort() & 0xFFFF;
            int eventId = bb.getShort() & 0xFFFF;
            EaDefinition def = definitions.get(key(appPid, eventId));
            if (def == null) {
                log.warn("ST19: EA definition ({},{}) not found", appPid, eventId);
                nack_completion(tc, COMPL_ERR_EA_DEF_NOT_FOUND);
                return null;
            }
            targets.add(def);
        }
        return targets;
    }

    // ---- TC[19,4]/TC[19,5]: enable/disable event-action definitions (specific or all) ----

    private void setDefinitionsEnabled(PusTcPacket tc, boolean enabled) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        if (bb.remaining() == 0) {
            for (EaDefinition def : definitions.values()) {
                def.enabled = enabled;
            }
            log.info("ST19: {} all ({}) EA definitions", enabled ? "enabled" : "disabled", definitions.size());
            ack_completion(tc);
            return;
        }
        int n = bb.get() & 0xFF;
        boolean anyMissing = false;
        for (int i = 0; i < n; i++) {
            int appPid = bb.getShort() & 0xFFFF;
            int eventId = bb.getShort() & 0xFFFF;
            EaDefinition def = definitions.get(key(appPid, eventId));
            if (def == null) {
                log.warn("ST19: EA definition ({},{}) not found, skipping", appPid, eventId);
                anyMissing = true;
                // spec: valid instructions in the same TC shall still be processed
                continue;
            }
            def.enabled = enabled;
            log.info("ST19: {} EA definition ({},{})", enabled ? "enabled" : "disabled", appPid, eventId);
        }
        if (anyMissing) {
            nack_completion(tc, COMPL_ERR_EA_DEF_NOT_FOUND);
        } else {
            ack_completion(tc);
        }
    }

    // ---- TC[19,6]/TM[19,7]: status report ----

    private void reportStatus(PusTcPacket tc) {
        ack_start(tc);
        sendTm19_7();
        ack_completion(tc);
    }

    private void sendTm19_7() {
        List<EaDefinition> all = new ArrayList<>(definitions.values());
        int n = all.size();
        // N(1) + N x { app_process_id(2), event_def_id(2), ea_status(1) }
        PusTmPacket pkt = newPacket(7, 1 + n * 5);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.put((byte) n);
        for (EaDefinition def : all) {
            bb.putShort((short) def.appProcessId);
            bb.putShort((short) def.eventDefId);
            bb.put((byte) (def.enabled ? 1 : 0));
        }
        pusSimulator.transmitRealtimeTM(pkt);
        log.info("ST19: TM[19,7] sent, {} entries", n);
    }

    // ---- TC[19,8]/TC[19,9]: enable/disable the event-action function ----

    private void setFunctionEnabled(PusTcPacket tc, boolean enabled) {
        ack_start(tc);
        functionEnabled = enabled;
        log.info("ST19: event-action function {}", enabled ? "ENABLED" : "DISABLED");
        ack_completion(tc);
    }

    // ---- TC[19,10]/TM[19,11]: report event-action definitions ----

    private void reportDefinitions(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        List<EaDefinition> toReport;
        if (n == 0) {
            // §8.19.2.10c: N=0 means "report all definitions"
            toReport = new ArrayList<>(definitions.values());
        } else {
            toReport = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                int appPid = bb.getShort() & 0xFFFF;
                int eventId = bb.getShort() & 0xFFFF;
                EaDefinition def = definitions.get(key(appPid, eventId));
                if (def == null) {
                    log.warn("ST19: EA definition ({},{}) not found, omitting from TM[19,11]", appPid, eventId);
                    continue;
                }
                toReport.add(def);
            }
        }
        sendTm19_11(toReport);
        ack_completion(tc);
    }

    private void sendTm19_11(List<EaDefinition> toReport) {
        int n = toReport.size();
        // N(1) + N x { app_process_id(2), event_def_id(2), ea_status(1), request_len(2), request(var) }
        int userDataLength = 1;
        for (EaDefinition def : toReport) {
            userDataLength += 2 + 2 + 1 + 2 + def.request.length;
        }
        PusTmPacket pkt = newPacket(11, userDataLength);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.put((byte) n);
        for (EaDefinition def : toReport) {
            bb.putShort((short) def.appProcessId);
            bb.putShort((short) def.eventDefId);
            bb.put((byte) (def.enabled ? 1 : 0));
            bb.putShort((short) def.request.length);
            bb.put(def.request);
        }
        pusSimulator.transmitRealtimeTM(pkt);
        log.info("ST19: TM[19,11] sent, {} entries", n);
    }
}
