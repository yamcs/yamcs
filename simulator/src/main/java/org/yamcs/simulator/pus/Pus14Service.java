package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * ST[14] Real-Time Forwarding Control simulator service. See pus_analysis/pus14.md and
 * pus_simulator_architecture.md for the full design rationale.
 *
 * <p>
 * Emulates the satellite's on-board forwarding control: maintains the Application Process
 * Forward-Control Configuration (APFCC), the HK Forward-Control Configuration and the
 * Diagnostic Forward-Control Configuration in memory, handles the TC[14,x] configuration/dump
 * commands, and exposes {@link #shouldForward(PusTmPacket)} which {@link PusSimulator} consults
 * for every outgoing TM packet.
 *
 * <p>
 * HK FCC / Diag FCC are maintained as pure bookkeeping (TC[14,5-12]): this simulator's ST[03] HK
 * reports don't carry structure identifiers and ST[04] diagnostic reports don't exist, so these
 * two tables have no effect on {@link #shouldForward(PusTmPacket)} yet (see pus14.md Gap 6).
 *
 * <p>
 * Default state is pass-all: an APID with no APFCC entry is forwarded (simulator usability, see
 * pus14.md Gap 4), and PUS-1 verification reports (type=1) and ST[14]'s own TM (type=14) always
 * bypass the gate so that command verification and FCC dump reports keep working regardless of
 * how restrictive the APFCC is configured -- this simulator uses a single fixed APID for all
 * traffic, so a restrictive APFCC entry would otherwise block its own control channel.
 */
public class Pus14Service extends AbstractPusService {

    // completion errors (see AbstractPusService for the shared ones)
    static final int COMPL_ERR_APID_NOT_IN_APFCC = 5;
    static final int COMPL_ERR_SVC_NOT_IN_APFCD = 6;
    static final int COMPL_ERR_APID_NOT_IN_HK_FCC = 7;
    static final int COMPL_ERR_APID_NOT_IN_DIAG_FCC = 8;

    // Application Process Forward-Control Configuration: apid -> ApfcDefinition
    private final Map<Integer, ApfcDefinition> apfcc = new LinkedHashMap<>();

    // HK Forward-Control Configuration: apid -> set of authorized hk structure ids (null = pass-all)
    private final Map<Integer, Set<Integer>> hkFcc = new LinkedHashMap<>();

    // Diagnostic Forward-Control Configuration: apid -> set of authorized diag structure ids (null = pass-all)
    private final Map<Integer, Set<Integer>> diagFcc = new LinkedHashMap<>();

    Pus14Service(PusSimulator pusSimulator) {
        super(pusSimulator, 14);
    }

    @Override
    public void executeTc(PusTcPacket tc) {
        switch (tc.getSubtype()) {
        // TC[14,1] add report types to the APFCC
        case 1 -> addReportTypes(tc);
        // TC[14,2] delete report types from the APFCC (or empty the whole APFCC)
        case 2 -> deleteReportTypes(tc);
        // TC[14,3] report the content of the APFCC
        case 3 -> reportApfcc(tc);
        // TC[14,5] add structure identifiers to the HK FCC
        case 5 -> {
            ack_start(tc);
            addStructIds(tc.getUserDataBuffer(), hkFcc);
            ack_completion(tc);
        }
        // TC[14,6] delete structure identifiers from the HK FCC (or empty the whole HK FCC)
        case 6 -> deleteFccEntries(tc, hkFcc, COMPL_ERR_APID_NOT_IN_HK_FCC);
        // TC[14,7] report the content of the HK FCC
        case 7 -> reportFcc(tc, hkFcc, 8);
        // TC[14,9] add structure identifiers to the Diagnostic FCC
        case 9 -> {
            ack_start(tc);
            addStructIds(tc.getUserDataBuffer(), diagFcc);
            ack_completion(tc);
        }
        // TC[14,10] delete structure identifiers from the Diagnostic FCC (or empty the whole Diagnostic FCC)
        case 10 -> deleteFccEntries(tc, diagFcc, COMPL_ERR_APID_NOT_IN_DIAG_FCC);
        // TC[14,11] report the content of the Diagnostic FCC
        case 11 -> reportFcc(tc, diagFcc, 12);
        default -> {
            log.warn("Unknown ST[14] subtype {}, sending NACK start", tc.getSubtype());
            nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
        }
        }
    }

    // ---- TC[14,1] / TM[14,4]: APFCC ----

    private void addReportTypes(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int n1 = bb.get() & 0xFF;
        for (int i = 0; i < n1; i++) {
            int apid = bb.getShort() & 0xFFFF;
            int n2 = bb.get() & 0xFF;
            if (n2 == 0) {
                // N2=0: add all services for this APID
                apfcc.computeIfAbsent(apid, ApfcDefinition::new);
            } else {
                ApfcDefinition apfcd = apfcc.computeIfAbsent(apid, ApfcDefinition::new);
                for (int j = 0; j < n2; j++) {
                    int svcType = bb.get() & 0xFF;
                    int n3 = bb.get() & 0xFF;
                    // N3=0: add all subtypes of this service type (empty set = pass all)
                    Set<Integer> subtypes = apfcd.serviceSubtypes.computeIfAbsent(svcType, k -> new LinkedHashSet<>());
                    for (int k = 0; k < n3; k++) {
                        subtypes.add(bb.get() & 0xFF);
                    }
                }
            }
        }
        log.info("ST14: added report types to APFCC, now {} APID entries", apfcc.size());
        ack_completion(tc);
    }

    private void deleteReportTypes(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        if (bb.remaining() == 0) {
            apfcc.clear();
            log.info("ST14: emptied the entire APFCC");
            ack_completion(tc);
            return;
        }
        int n1 = bb.get() & 0xFF;
        for (int i = 0; i < n1; i++) {
            int apid = bb.getShort() & 0xFFFF;
            int n2 = bb.get() & 0xFF;
            ApfcDefinition apfcd = apfcc.get(apid);
            if (apfcd == null) {
                log.warn("ST14: APID {} not in APFCC, sending NACK completion", apid);
                nack_completion(tc, COMPL_ERR_APID_NOT_IN_APFCC);
                return;
            }
            if (n2 == 0) {
                // N2=0: remove the entire APFCD for this APID
                apfcc.remove(apid);
                continue;
            }
            for (int j = 0; j < n2; j++) {
                int svcType = bb.get() & 0xFF;
                int n3 = bb.get() & 0xFF;
                if (!apfcd.serviceSubtypes.containsKey(svcType)) {
                    log.warn("ST14: service type {} not in APFCD for APID {}, sending NACK completion", svcType, apid);
                    nack_completion(tc, COMPL_ERR_SVC_NOT_IN_APFCD);
                    return;
                }
                if (n3 == 0) {
                    // N3=0: remove the entire STFCD for this service type
                    apfcd.serviceSubtypes.remove(svcType);
                } else {
                    Set<Integer> subtypes = apfcd.serviceSubtypes.get(svcType);
                    for (int k = 0; k < n3; k++) {
                        subtypes.remove(bb.get() & 0xFF);
                    }
                    if (subtypes.isEmpty()) {
                        apfcd.serviceSubtypes.remove(svcType);
                    }
                }
            }
            if (apfcd.serviceSubtypes.isEmpty()) {
                apfcc.remove(apid);
            }
        }
        ack_completion(tc);
    }

    private void reportApfcc(PusTcPacket tc) {
        ack_start(tc);
        for (ApfcDefinition apfcd : apfcc.values()) {
            sendApfcReport(apfcd);
        }
        ack_completion(tc);
    }

    private void sendApfcReport(ApfcDefinition apfcd) {
        int size = 2 + 1;
        for (Set<Integer> subtypes : apfcd.serviceSubtypes.values()) {
            size += 1 + 1 + subtypes.size();
        }
        PusTmPacket pkt = newPacket(4, size);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.putShort((short) apfcd.apid);
        bb.put((byte) apfcd.serviceSubtypes.size());
        for (var entry : apfcd.serviceSubtypes.entrySet()) {
            bb.put(entry.getKey().byteValue());
            Set<Integer> subtypes = entry.getValue();
            bb.put((byte) subtypes.size());
            for (int subtype : subtypes) {
                bb.put((byte) subtype);
            }
        }
        pusSimulator.transmitRealtimeTM(pkt);
    }

    // ---- TC[14,5/6/7] and TC[14,9/10/11]: HK FCC / Diagnostic FCC (bookkeeping only, see class javadoc) ----

    private void addStructIds(ByteBuffer bb, Map<Integer, Set<Integer>> fcc) {
        int n1 = bb.get() & 0xFF;
        for (int i = 0; i < n1; i++) {
            int apid = bb.getShort() & 0xFFFF;
            int nStructs = bb.get() & 0xFF;
            if (nStructs == 0) {
                // N_structs=0: authorize all structures for this APID
                fcc.put(apid, null);
            } else {
                Set<Integer> structs = fcc.computeIfAbsent(apid, k -> new LinkedHashSet<>());
                for (int j = 0; j < nStructs; j++) {
                    structs.add(bb.getShort() & 0xFFFF);
                }
            }
        }
    }

    private void deleteFccEntries(PusTcPacket tc, Map<Integer, Set<Integer>> fcc, int rejectionCode) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        if (bb.remaining() == 0) {
            fcc.clear();
            ack_completion(tc);
            return;
        }
        int n1 = bb.get() & 0xFF;
        for (int i = 0; i < n1; i++) {
            int apid = bb.getShort() & 0xFFFF;
            int nStructs = bb.get() & 0xFF;
            if (!fcc.containsKey(apid)) {
                log.warn("ST14: APID {} not in FCC, sending NACK completion", apid);
                nack_completion(tc, rejectionCode);
                return;
            }
            if (nStructs == 0) {
                // N_structs=0: remove the entire APID entry
                fcc.remove(apid);
                continue;
            }
            Set<Integer> structs = fcc.get(apid);
            if (structs == null) {
                // pass-all entry: nothing to remove from an explicit list
                continue;
            }
            for (int j = 0; j < nStructs; j++) {
                structs.remove(bb.getShort() & 0xFFFF);
            }
            if (structs.isEmpty()) {
                fcc.remove(apid);
            }
        }
        ack_completion(tc);
    }

    private void reportFcc(PusTcPacket tc, Map<Integer, Set<Integer>> fcc, int tmSubtype) {
        ack_start(tc);
        for (var entry : fcc.entrySet()) {
            sendFccReport(tmSubtype, entry.getKey(), entry.getValue());
        }
        ack_completion(tc);
    }

    private void sendFccReport(int tmSubtype, int apid, Set<Integer> structIds) {
        int count = structIds == null ? 0 : structIds.size();
        PusTmPacket pkt = newPacket(tmSubtype, 2 + 1 + count * 2);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.putShort((short) apid);
        bb.put((byte) count);
        if (structIds != null) {
            for (int sid : structIds) {
                bb.putShort((short) sid);
            }
        }
        pusSimulator.transmitRealtimeTM(pkt);
    }

    // ---- Forwarding gate, consulted by PusSimulator.transmitRealtimeTM() for every outgoing TM ----

    public boolean shouldForward(PusTmPacket pkt) {
        int type = pkt.getType();
        if (type == 1 || type == 14) {
            // PUS-1 verification reports and ST[14]'s own TM always get through, otherwise a
            // restrictive APFCC would block command verification and FCC dump reports too
            // (this simulator uses a single fixed APID for all traffic).
            return true;
        }

        int apid = pkt.getAPID();
        ApfcDefinition apfcd = apfcc.get(apid);
        if (apfcd == null) {
            return true; // pass-all default: no APFCC entry for this APID
        }
        if (apfcd.serviceSubtypes.isEmpty()) {
            return true; // APID entry exists with no restrictions -> forward all
        }
        Set<Integer> subtypes = apfcd.serviceSubtypes.get(type);
        if (subtypes == null) {
            return false; // service type not authorized for this APID
        }
        if (subtypes.isEmpty()) {
            return true; // all subtypes of this service type are authorized
        }
        return subtypes.contains(pkt.getSubtype());
    }

    private static class ApfcDefinition {
        final int apid;
        // Empty map = "pass all services for this APID"; empty set value = "pass all subtypes of that service"
        final Map<Integer, Set<Integer>> serviceSubtypes = new LinkedHashMap<>();

        ApfcDefinition(int apid) {
            this.apid = apid;
        }
    }
}
