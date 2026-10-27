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
 * a stored "request": a complete TC packet embedded in TC[19,1] with no length prefix (§8.19.2.1);
 * its size is taken from its own CCSDS packet data length field. On the ground side the request is
 * typed /PUS/EmbeddedTcType, so the command post-processor fills in its sequence count and length
 * and appends its CRC (see mdb/pus19.xml). When a matching ST[05] event fires and both status layers are enabled, the stored bytes are reconstructed
 * into a {@link PusTcPacket} and dispatched via {@link PusSimulator#processTc}, the same mechanism
 * Pus13Service uses for reassembled large commands -- the released request gets its own independent
 * ACK/NACK/completion chain, exactly as if it had arrived from ground.
 *
 * <p>
 * ST[05] coupling: {@link Pus5Service#sendEvent()} and {@link Pus5Service#raiseEvent} call
 * {@link #onEvent} after transmitting each event TM, since this simulator has a single application
 * process (MAIN_APID). There is no separate EventBus indirection -- a direct call mirrors how
 * Pus14Service/Pus15Service hook into {@link PusSimulator#transmitRealtimeTM}.
 *
 * <p>
 * Instruction rejection (§6.19.8.1e-g, §6.19.8.3d-f, §6.19.9): each rejected instruction is reported
 * with its own failed start of execution notification (TM[1,4]); the valid instructions of the same
 * request are still executed and, if there is at least one, the request completes with TM[1,7]. The
 * successful start of execution (TM[1,3]) is only sent when no instruction was rejected.
 *
 * <p>
 * Verification checks performed on the request of a TC[19,1] instruction (§6.19.8.1d): the embedded
 * packet's CCSDS length must fit in the remaining data and it must be a telecommand (packet type 1).
 * The CRC is only logged, like every other TC this simulator receives.
 */
public class Pus19Service extends AbstractPusService {

    // start errors, one TM[1,4] per rejected instruction (see AbstractPusService for the shared ones)
    static final int START_ERR_EA_DEF_NOT_FOUND = 30;
    static final int START_ERR_EA_DEF_ENABLED = 31;
    static final int START_ERR_MAX_EA_DEFS_REACHED = 32;
    static final int START_ERR_INVALID_REQUEST = 33;

    // maximum number of event-action definitions that can be evaluated contemporaneously (§6.19.8.1e.2)
    static final int MAX_DEFINITIONS = 16;

    private boolean functionEnabled = false;
    final Map<Integer, EaDefinition> definitions = new LinkedHashMap<>();

    Pus19Service(PusSimulator pusSimulator) {
        super(pusSimulator, 19);
    }

    static int key(int appProcessId, int eventDefId) {
        return (appProcessId << 16) | (eventDefId & 0xFFFF);
    }

    static class EaDefinition {
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
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        List<Integer> rejected = new ArrayList<>();
        List<Runnable> valid = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int appPid = bb.getShort() & 0xFFFF;
            int eventId = bb.getShort() & 0xFFFF;
            byte[] request = readRequest(bb);
            if (request == null) {
                // the size of the request cannot be determined, so neither can the next instructions
                log.warn("ST19: invalid request for EA definition ({},{}), rejecting the remaining {} instruction(s)",
                        appPid, eventId, n - i);
                for (int k = i; k < n; k++) {
                    rejected.add(START_ERR_INVALID_REQUEST);
                }
                break;
            }
            int k = key(appPid, eventId);
            EaDefinition existing = definitions.get(k);
            if (existing != null && existing.enabled) {
                log.warn("ST19: EA definition ({},{}) is enabled, rejecting add", appPid, eventId);
                rejected.add(START_ERR_EA_DEF_ENABLED);
            } else if (existing == null && definitions.size() >= MAX_DEFINITIONS) {
                log.warn("ST19: maximum number of EA definitions ({}) reached, rejecting add of ({},{})",
                        MAX_DEFINITIONS, appPid, eventId);
                rejected.add(START_ERR_MAX_EA_DEFS_REACHED);
            } else if (existing != null) {
                // §6.19.8.1h.2: replace the action of the existing (disabled) definition
                valid.add(() -> {
                    existing.request = request;
                    log.info("ST19: replaced the request of EA definition ({},{}): {}", appPid, eventId,
                            describe(request));
                });
            } else {
                // §6.19.8.1h.1: new definitions start disabled; reserve the slot now so that the
                // maximum is also enforced between instructions of this request
                EaDefinition def = new EaDefinition(appPid, eventId, request);
                definitions.put(k, def);
                valid.add(() -> log.info("ST19: added EA definition ({},{}), status=disabled: {}", appPid,
                        eventId, describe(request)));
            }
        }
        finish(tc, rejected, valid);
    }

    /**
     * Reads one embedded request TC whose size is given by its own CCSDS packet data length field.
     *
     * @return null if the request fails the verification checks
     */
    private static byte[] readRequest(ByteBuffer bb) {
        // the user data buffer ends with the CRC of the TC[19,1] itself, which is not part of the request
        int available = bb.remaining() - 2;
        if (available < 7) {
            return null;
        }
        int pos = bb.position();
        boolean isTc = (bb.get(pos) & 0x10) != 0;
        int length = (bb.getShort(pos + 4) & 0xFFFF) + 7;
        if (!isTc || length > available) {
            return null;
        }
        byte[] request = new byte[length];
        bb.get(request);
        return request;
    }

    private static String describe(byte[] request) {
        PusTcPacket p = new PusTcPacket(request);
        return String.format("TC[%d,%d] apid=%d seq count=%d CRC %s", p.getType(), p.getSubtype(), p.getAPID(),
                p.getSequenceCount(), p.isChecksumValid() ? "valid" : "INVALID");
    }

    // ---- TC[19,2]: delete event-action definitions ----

    private void deleteDefinitions(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        List<Integer> rejected = new ArrayList<>();
        List<Runnable> valid = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int appPid = bb.getShort() & 0xFFFF;
            int eventId = bb.getShort() & 0xFFFF;
            EaDefinition def = definitions.get(key(appPid, eventId));
            if (def == null) {
                log.warn("ST19: EA definition ({},{}) not found, rejecting delete", appPid, eventId);
                rejected.add(START_ERR_EA_DEF_NOT_FOUND);
            } else if (def.enabled) {
                log.warn("ST19: EA definition ({},{}) is enabled, rejecting delete", appPid, eventId);
                rejected.add(START_ERR_EA_DEF_ENABLED);
            } else {
                valid.add(() -> {
                    definitions.remove(key(appPid, eventId));
                    log.info("ST19: deleted EA definition ({},{}), {} remaining", appPid, eventId,
                            definitions.size());
                });
            }
        }
        finish(tc, rejected, valid);
    }

    // ---- TC[19,4]/TC[19,5]: enable/disable event-action definitions (specific or all) ----

    private void setDefinitionsEnabled(PusTcPacket tc, boolean enabled) {
        ByteBuffer bb = tc.getUserDataBuffer();
        if (bb.remaining() == 0) {
            ack_start(tc);
            for (EaDefinition def : definitions.values()) {
                def.enabled = enabled;
            }
            log.info("ST19: {} all ({}) EA definitions", enabled ? "enabled" : "disabled", definitions.size());
            ack_completion(tc);
            return;
        }
        int n = bb.get() & 0xFF;
        List<Integer> rejected = new ArrayList<>();
        List<Runnable> valid = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int appPid = bb.getShort() & 0xFFFF;
            int eventId = bb.getShort() & 0xFFFF;
            EaDefinition def = definitions.get(key(appPid, eventId));
            if (def == null) {
                log.warn("ST19: EA definition ({},{}) not found, rejecting {}", appPid, eventId,
                        enabled ? "enable" : "disable");
                rejected.add(START_ERR_EA_DEF_NOT_FOUND);
                continue;
            }
            valid.add(() -> {
                def.enabled = enabled;
                log.info("ST19: {} EA definition ({},{})", enabled ? "enabled" : "disabled", appPid, eventId);
            });
        }
        finish(tc, rejected, valid);
    }

    /**
     * Reports the outcome of a multi-instruction request: one failed start of execution per rejected
     * instruction, then the valid instructions are executed regardless of the faulty ones.
     */
    private void finish(PusTcPacket tc, List<Integer> rejected, List<Runnable> valid) {
        if (rejected.isEmpty()) {
            ack_start(tc);
        } else {
            for (int code : rejected) {
                nack_start(tc, code);
            }
        }
        valid.forEach(Runnable::run);
        if (!valid.isEmpty() || rejected.isEmpty()) {
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
        // N(1) + N x { app_process_id(2), event_def_id(2), ea_status(1), request(var, no length prefix) }
        int userDataLength = 1;
        for (EaDefinition def : toReport) {
            userDataLength += 2 + 2 + 1 + def.request.length;
        }
        PusTmPacket pkt = newPacket(11, userDataLength);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.put((byte) n);
        for (EaDefinition def : toReport) {
            bb.putShort((short) def.appProcessId);
            bb.putShort((short) def.eventDefId);
            bb.put((byte) (def.enabled ? 1 : 0));
            bb.put(def.request);
        }
        pusSimulator.transmitRealtimeTM(pkt);
        log.info("ST19: TM[19,11] sent, {} entries", n);
    }
}
