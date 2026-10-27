package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.yamcs.utils.BitBuffer;

/**
 * ST[14] Real-Time Forwarding Control simulator service. See pus_analysis/pus14.md and
 * pus_simulator_architecture.md for the full design rationale.
 *
 * <p>
 * Emulates the satellite's on-board forwarding control: maintains the Application Process
 * Forward-Control Configuration (APFCC), the HK Forward-Control Configuration and the
 * Diagnostic Forward-Control Configuration and the Event Report Blocking Forward-Control
 * Configuration in memory, handles the TC[14,x] configuration/dump commands, and exposes
 * {@link #shouldForward(PusTmPacket)} which {@link PusSimulator} consults for every outgoing TM
 * packet.
 *
 * <p>
 * HK FCC / Diag FCC are maintained as pure bookkeeping (TC[14,5-12]): this simulator's ST[03] HK
 * reports don't carry structure identifiers and ST[04] diagnostic reports don't exist, so these
 * two tables have no effect on {@link #shouldForward(PusTmPacket)} yet (see pus14.md Gap 6).
 * Event blocking is active for ST[05] event-report subtypes 1 through 4.
 *
 * <p>
 * Default state is pass-all: an APID with no APFCC entry is forwarded (simulator usability, see
 * pus14.md Gap 4), and PUS-1 verification reports (type=1) and ST[14]'s own TM (type=14) always
 * bypass the gate so that command verification and FCC dump reports keep working regardless of
 * how restrictive the APFCC is configured -- this simulator uses a single fixed APID for all
 * traffic, so a restrictive APFCC entry would otherwise block its own control channel.
 */
public class Pus14Service extends AbstractPusService {

    private static final int APID_BITS = PusPackedFields.APID_BITS;
    private static final int OCTET_BITS = PusPackedFields.OCTET_BITS;
    private static final int STRUCTURE_ID_BITS = PusPackedFields.STRUCTURE_ID_BITS;
    private static final int EVENT_DEFINITION_ID_BITS = PusPackedFields.EVENT_DEFINITION_ID_BITS;
    private static final int MAX_COUNT = PusPackedFields.MAX_OCTET_COUNT;

    // completion errors (see AbstractPusService for the shared ones)
    static final int COMPL_ERR_APID_NOT_IN_APFCC = 5;
    static final int COMPL_ERR_SVC_NOT_IN_APFCD = 6;
    static final int COMPL_ERR_APID_NOT_IN_HK_FCC = 7;
    static final int COMPL_ERR_APID_NOT_IN_DIAG_FCC = 8;
    static final int COMPL_ERR_APID_NOT_IN_EVENT_FCC = 9;
    static final int COMPL_ERR_EVENT_ID_NOT_IN_EVENT_FCC = 10;
    static final int COMPL_ERR_EVENT_FCC_BLOCKS_ALL = 11;
    static final int COMPL_ERR_REPORT_TOO_LARGE = 12;

    // Application Process Forward-Control Configuration: apid -> ApfcDefinition
    private final Map<Integer, ApfcDefinition> apfcc = new LinkedHashMap<>();

    // HK Forward-Control Configuration: apid -> set of authorized hk structure ids (null = pass-all)
    private final Map<Integer, Set<Integer>> hkFcc = new LinkedHashMap<>();

    // Diagnostic Forward-Control Configuration: apid -> set of authorized diag structure ids (null = pass-all)
    private final Map<Integer, Set<Integer>> diagFcc = new LinkedHashMap<>();

    // Event Report Blocking FCC: absent APID = block none, empty set = block all event definitions.
    private final Map<Integer, Set<Integer>> eventBlockingFcc = new LinkedHashMap<>();

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
        // TC[14,13] delete event definition identifiers from the Event Blocking FCC
        case 13 -> deleteEventBlockingEntries(tc);
        // TC[14,14] add event definition identifiers to the Event Blocking FCC
        case 14 -> addEventBlockingEntries(tc);
        // TC[14,15] report the content of the Event Blocking FCC
        case 15 -> reportFcc(tc, eventBlockingFcc, 16, EVENT_DEFINITION_ID_BITS);
        default -> {
            log.warn("Unknown ST[14] subtype {}, sending NACK start", tc.getSubtype());
            nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
        }
        }
    }

    // ---- TC[14,1] / TM[14,4]: APFCC ----

    private void addReportTypes(PusTcPacket tc) {
        ack_start(tc);
        BitBuffer bits = PusPackedFields.bitBuffer(tc.getUserDataBuffer());
        int n1 = readUnsigned(bits, OCTET_BITS);
        for (int i = 0; i < n1; i++) {
            int apid = readUnsigned(bits, APID_BITS);
            int n2 = readUnsigned(bits, OCTET_BITS);
            if (n2 == 0) {
                // N2=0: add all services for this APID
                apfcc.computeIfAbsent(apid, ApfcDefinition::new);
            } else {
                ApfcDefinition apfcd = apfcc.computeIfAbsent(apid, ApfcDefinition::new);
                for (int j = 0; j < n2; j++) {
                    int svcType = readUnsigned(bits, OCTET_BITS);
                    int n3 = readUnsigned(bits, OCTET_BITS);
                    // N3=0: add all subtypes of this service type (empty set = pass all)
                    Set<Integer> subtypes = apfcd.serviceSubtypes.computeIfAbsent(svcType, k -> new LinkedHashSet<>());
                    for (int k = 0; k < n3; k++) {
                        subtypes.add(readUnsigned(bits, OCTET_BITS));
                    }
                }
            }
        }
        log.info("ST14: added report types to APFCC, now {} APID entries", apfcc.size());
        ack_completion(tc);
    }

    private void deleteReportTypes(PusTcPacket tc) {
        ack_start(tc);
        BitBuffer bits = PusPackedFields.bitBuffer(tc.getUserDataBuffer());
        int n1 = readUnsigned(bits, OCTET_BITS);
        if (n1 == 0) {
            apfcc.clear();
            log.info("ST14: emptied the entire APFCC");
            ack_completion(tc);
            return;
        }
        for (int i = 0; i < n1; i++) {
            int apid = readUnsigned(bits, APID_BITS);
            int n2 = readUnsigned(bits, OCTET_BITS);
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
                int svcType = readUnsigned(bits, OCTET_BITS);
                int n3 = readUnsigned(bits, OCTET_BITS);
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
                        subtypes.remove(readUnsigned(bits, OCTET_BITS));
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
        long bitSize = apfcReportBitSize();
        if (!reportFits(bitSize)) {
            reportTooLarge(tc, 4, bitSize);
            return;
        }
        PusTmPacket pkt = newPacket(4, PusPackedFields.bytesForBits(bitSize));
        BitBuffer bits = PusPackedFields.bitBuffer(pkt.getUserDataBuffer());
        bits.putBits(apfcc.size(), OCTET_BITS);
        for (ApfcDefinition apfcd : apfcc.values()) {
            bits.putBits(apfcd.apid, APID_BITS);
            bits.putBits(apfcd.serviceSubtypes.size(), OCTET_BITS);
            for (var entry : apfcd.serviceSubtypes.entrySet()) {
                bits.putBits(entry.getKey(), OCTET_BITS);
                Set<Integer> subtypes = entry.getValue();
                bits.putBits(subtypes.size(), OCTET_BITS);
                for (int subtype : subtypes) {
                    bits.putBits(subtype, OCTET_BITS);
                }
            }
        }
        pusSimulator.transmitRealtimeTM(pkt);
        ack_completion(tc);
    }

    private long apfcReportBitSize() {
        if (apfcc.size() > MAX_COUNT) {
            return -1;
        }
        long bitSize = OCTET_BITS;
        for (ApfcDefinition apfcd : apfcc.values()) {
            if (apfcd.serviceSubtypes.size() > MAX_COUNT) {
                return -1;
            }
            bitSize += APID_BITS + OCTET_BITS;
            for (Set<Integer> subtypes : apfcd.serviceSubtypes.values()) {
                if (subtypes.size() > MAX_COUNT) {
                    return -1;
                }
                bitSize += 2L * OCTET_BITS + (long) subtypes.size() * OCTET_BITS;
            }
        }
        return bitSize;
    }

    // ---- TC[14,5/6/7] and TC[14,9/10/11]: HK FCC / Diagnostic FCC (bookkeeping only, see class javadoc) ----

    private void addStructIds(ByteBuffer bb, Map<Integer, Set<Integer>> fcc) {
        BitBuffer bits = PusPackedFields.bitBuffer(bb);
        int n1 = readUnsigned(bits, OCTET_BITS);
        for (int i = 0; i < n1; i++) {
            int apid = readUnsigned(bits, APID_BITS);
            int nStructs = readUnsigned(bits, OCTET_BITS);
            if (nStructs == 0) {
                // N_structs=0: authorize all structures for this APID
                fcc.put(apid, null);
            } else {
                Set<Integer> structs = fcc.computeIfAbsent(apid, k -> new LinkedHashSet<>());
                for (int j = 0; j < nStructs; j++) {
                    structs.add(readUnsigned(bits, STRUCTURE_ID_BITS));
                }
            }
        }
    }

    private void deleteFccEntries(PusTcPacket tc, Map<Integer, Set<Integer>> fcc, int rejectionCode) {
        ack_start(tc);
        BitBuffer bits = PusPackedFields.bitBuffer(tc.getUserDataBuffer());
        int n1 = readUnsigned(bits, OCTET_BITS);
        if (n1 == 0) {
            fcc.clear();
            ack_completion(tc);
            return;
        }
        for (int i = 0; i < n1; i++) {
            int apid = readUnsigned(bits, APID_BITS);
            int nStructs = readUnsigned(bits, OCTET_BITS);
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
                structs.remove(readUnsigned(bits, STRUCTURE_ID_BITS));
            }
            if (structs.isEmpty()) {
                fcc.remove(apid);
            }
        }
        ack_completion(tc);
    }

    private void reportFcc(PusTcPacket tc, Map<Integer, Set<Integer>> fcc, int tmSubtype) {
        reportFcc(tc, fcc, tmSubtype, STRUCTURE_ID_BITS);
    }

    private void reportFcc(PusTcPacket tc, Map<Integer, Set<Integer>> fcc, int tmSubtype, int idBits) {
        ack_start(tc);
        long bitSize = fccReportBitSize(fcc, idBits);
        if (!reportFits(bitSize)) {
            reportTooLarge(tc, tmSubtype, bitSize);
            return;
        }
        PusTmPacket pkt = newPacket(tmSubtype, PusPackedFields.bytesForBits(bitSize));
        BitBuffer bits = PusPackedFields.bitBuffer(pkt.getUserDataBuffer());
        bits.putBits(fcc.size(), OCTET_BITS);
        for (var entry : fcc.entrySet()) {
            bits.putBits(entry.getKey(), APID_BITS);
            Set<Integer> ids = entry.getValue();
            int count = ids == null ? 0 : ids.size();
            bits.putBits(count, OCTET_BITS);
            if (ids != null) {
                for (int id : ids) {
                    bits.putBits(id, idBits);
                }
            }
        }
        pusSimulator.transmitRealtimeTM(pkt);
        ack_completion(tc);
    }

    private long fccReportBitSize(Map<Integer, Set<Integer>> fcc, int idBits) {
        if (fcc.size() > MAX_COUNT) {
            return -1;
        }
        long bitSize = OCTET_BITS;
        for (Set<Integer> ids : fcc.values()) {
            int count = ids == null ? 0 : ids.size();
            if (count > MAX_COUNT) {
                return -1;
            }
            bitSize += APID_BITS + OCTET_BITS + (long) count * idBits;
        }
        return bitSize;
    }

    // ---- TC[14,13-15] / TM[14,16]: Event Report Blocking FCC ----

    private void addEventBlockingEntries(PusTcPacket tc) {
        ack_start(tc);
        BitBuffer bits = PusPackedFields.bitBuffer(tc.getUserDataBuffer());
        int n1 = readUnsigned(bits, OCTET_BITS);
        for (int i = 0; i < n1; i++) {
            int apid = readUnsigned(bits, APID_BITS);
            int n2 = readUnsigned(bits, OCTET_BITS);
            if (n2 == 0) {
                eventBlockingFcc.put(apid, new LinkedHashSet<>());
                continue;
            }
            Set<Integer> blocked = eventBlockingFcc.get(apid);
            if (blocked != null && blocked.isEmpty()) {
                log.warn("ST14: event FCC for APID {} already blocks all events", apid);
                nack_completion(tc, COMPL_ERR_EVENT_FCC_BLOCKS_ALL);
                return;
            }
            if (blocked == null) {
                blocked = new LinkedHashSet<>();
                eventBlockingFcc.put(apid, blocked);
            }
            for (int j = 0; j < n2; j++) {
                blocked.add(readUnsigned(bits, EVENT_DEFINITION_ID_BITS));
            }
        }
        ack_completion(tc);
    }

    private void deleteEventBlockingEntries(PusTcPacket tc) {
        ack_start(tc);
        BitBuffer bits = PusPackedFields.bitBuffer(tc.getUserDataBuffer());
        int n1 = readUnsigned(bits, OCTET_BITS);
        if (n1 == 0) {
            eventBlockingFcc.clear();
            ack_completion(tc);
            return;
        }
        for (int i = 0; i < n1; i++) {
            int apid = readUnsigned(bits, APID_BITS);
            int n2 = readUnsigned(bits, OCTET_BITS);
            Set<Integer> blocked = eventBlockingFcc.get(apid);
            if (blocked == null) {
                log.warn("ST14: APID {} not in event FCC", apid);
                nack_completion(tc, COMPL_ERR_APID_NOT_IN_EVENT_FCC);
                return;
            }
            if (n2 == 0) {
                eventBlockingFcc.remove(apid);
                continue;
            }
            for (int j = 0; j < n2; j++) {
                int eventId = readUnsigned(bits, EVENT_DEFINITION_ID_BITS);
                if (!blocked.remove(eventId)) {
                    log.warn("ST14: event ID {} not in event FCC for APID {}", eventId, apid);
                    nack_completion(tc, COMPL_ERR_EVENT_ID_NOT_IN_EVENT_FCC);
                    return;
                }
            }
            if (blocked.isEmpty()) {
                eventBlockingFcc.remove(apid);
            }
        }
        ack_completion(tc);
    }

    private boolean reportFits(long bitSize) {
        return bitSize >= 0 && PusPackedFields.bytesForBits(bitSize) <= pusSimulator.maxTmDataSize();
    }

    private void reportTooLarge(PusTcPacket tc, int tmSubtype, long bitSize) {
        log.warn("ST14: TM[14,{}] report cannot be represented atomically ({} bits)", tmSubtype, bitSize);
        nack_completion(tc, COMPL_ERR_REPORT_TOO_LARGE);
    }

    private static int readUnsigned(BitBuffer bits, int bitCount) {
        return PusPackedFields.readUnsigned(bits, bitCount);
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
        if (!allowedByApfcc(apid, type, pkt.getSubtype())) {
            return false;
        }
        if (type == PusSimulator.PUS_TYPE_EVENT && pkt.getSubtype() >= 1 && pkt.getSubtype() <= 4) {
            Set<Integer> blocked = eventBlockingFcc.get(apid);
            if (blocked == null) {
                return true;
            }
            if (blocked.isEmpty()) {
                return false;
            }
            int eventId = pkt.getUserDataBuffer().get(0) & 0xFF;
            return !blocked.contains(eventId);
        }
        return true;
    }

    private boolean allowedByApfcc(int apid, int type, int subtype) {
        ApfcDefinition apfcd = apfcc.get(apid);
        if (apfcd == null || apfcd.serviceSubtypes.isEmpty()) {
            return true;
        }
        Set<Integer> subtypes = apfcd.serviceSubtypes.get(type);
        return subtypes != null && (subtypes.isEmpty() || subtypes.contains(subtype));
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
