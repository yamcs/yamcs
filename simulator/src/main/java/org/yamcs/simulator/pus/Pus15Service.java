package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.yamcs.utils.BitBuffer;

/**
 * ST[15] On-Board Storage and Retrieval simulator service. See pus_analysis/pus15.md and
 * pus_simulator_architecture.md for the full design rationale.
 *
 * <p>
 * Implements packet store lifecycle and retrieval together with the Packet Selection subservice
 * (TC[15,3-5]/TM[15,6] and TC[15,29-39]/TM[15,36/38/40]). Application-process identifiers in
 * packet-selection application and source data are packed 11-bit values.
 *
 * <p>
 * <b>Storage filtering default:</b> an empty application-process storage-control configuration
 * blocks all reports, as required by ECSS-E-ST-70-41C. A storage-enabled packet store therefore
 * remains empty until configured with TC[15,3].
 *
 * <p>
 * <b>Threading:</b> all background retrieval work runs on {@link PusSimulator#executor}, the same
 * single-thread scheduled executor used by every other periodic service (Pus3Service HK
 * generation, Pus13Service fragmented downlinks, etc.) -- there is no concurrent access to
 * {@link PacketStore} state, so no additional synchronization is needed.
 */
public class Pus15Service extends AbstractPusService {

    // completion errors (see AbstractPusService for the shared ones)
    static final int COMPL_ERR_STORE_NOT_FOUND = 5;
    static final int COMPL_ERR_STORE_ALREADY_EXISTS = 6;
    static final int COMPL_ERR_STORE_ACTIVE = 7;
    static final int COMPL_ERR_MAX_STORES_EXCEEDED = 8;
    static final int COMPL_ERR_BTR_ALREADY_ACTIVE = 9;
    static final int COMPL_ERR_NOT_SUSPENDED = 10;
    static final int COMPL_ERR_ACTIVE_RETRIEVAL = 11;
    static final int COMPL_ERR_REPORT_TOO_LARGE = 12;
    static final int COMPL_ERR_APID_NOT_IN_CONFIG = 13;
    static final int COMPL_ERR_SERVICE_NOT_IN_CONFIG = 14;
    static final int COMPL_ERR_IDENTIFIER_NOT_IN_CONFIG = 15;
    static final int COMPL_ERR_WILDCARD_CONFLICT = 16;

    static final int COMPL_ERR_INVALID_DATA = 17;
    // Mission allocation unit: one wire-size unit is 1 KiB (the field remains uint8).
    static final long SIZE_UNIT_BYTES = 1024;

    static final int STORE_TYPE_CIRCULAR = 0;
    static final int STORE_TYPE_BOUNDED = 1;

    static final int MAX_STORES = 64;
    static final long OPEN_RETRIEVAL_POLL_MS = 500;
    static final long BTR_STEP_DELAY_MS = 100;

    private final PusTimeEncoding storageTime;
    private ScheduledFuture<?> retrievalTask;
    private int retrievalTicks;

    private final Map<Integer, PacketStore> stores = new LinkedHashMap<>();

    Pus15Service(PusSimulator pusSimulator) {
        super(pusSimulator, 15);
        storageTime = pusSimulator.storageTimeEncoding;
    }

    @Override
    public void executeTc(PusTcPacket tc) {
        try {
            dispatch(tc);
        } catch (IllegalArgumentException | IndexOutOfBoundsException | java.nio.BufferUnderflowException e) {
            log.warn("ST15: invalid TC[15,{}] data: {}", tc.getSubtype(), e.toString());
            nack_completion(tc, COMPL_ERR_INVALID_DATA);
        }
    }

    private void dispatch(PusTcPacket tc) {
        switch (tc.getSubtype()) {
        // TC[15,1] enable storage function of packet stores
        case 1 -> setStorageEnabled(tc, true);
        // TC[15,2] disable storage function of packet stores
        case 2 -> setStorageEnabled(tc, false);
        // TC[15,3-5] application-process storage-control configuration
        case 3 -> addApplicationReportTypes(tc);
        case 4 -> deleteApplicationReportTypes(tc);
        case 5 -> reportApplicationConfig(tc);
        // TC[15,9] start by-time-range retrieval
        case 9 -> startBtr(tc);
        // TC[15,11] delete content of packet stores up to specified time
        case 11 -> deleteContent(tc);
        // TC[15,12] summary-report content of packet stores -> TM[15,13]
        case 12 -> reportSummary(tc);
        // TC[15,14] change open retrieval start time tag
        case 14 -> changeOpenRetrievalStartTime(tc);
        // TC[15,15] resume open retrieval
        case 15 -> resumeOpenRetrieval(tc);
        // TC[15,16] suspend open retrieval
        case 16 -> suspendOpenRetrieval(tc);
        // TC[15,17] abort by-time-range retrieval
        case 17 -> abortBtr(tc);
        // TC[15,18] report status of each packet store -> TM[15,19]
        case 18 -> reportStatus(tc);
        // TC[15,20] create packet stores
        case 20 -> createStores(tc);
        // TC[15,21] delete packet stores
        case 21 -> deleteStores(tc);
        // TC[15,22] report configuration of each packet store -> TM[15,23]
        case 22 -> reportConfig(tc);
        case 24 -> copyPackets(tc);
        case 26 -> changeStoreType(tc, STORE_TYPE_CIRCULAR);
        case 27 -> changeStoreType(tc, STORE_TYPE_BOUNDED);
        // TC[15,25] resize packet stores
        case 25 -> resizeStores(tc);
        // TC[15,28] change virtual channel used by a packet store
        case 28 -> changeVc(tc);
        // TC[15,29-40] HK, diagnostic and event packet-selection configurations
        case 29 -> addIdentifierConfig(tc, ConfigKind.HK);
        case 30 -> deleteIdentifierConfig(tc, ConfigKind.HK);
        case 31 -> addIdentifierConfig(tc, ConfigKind.DIAGNOSTIC);
        case 32 -> deleteIdentifierConfig(tc, ConfigKind.DIAGNOSTIC);
        case 33 -> deleteIdentifierConfig(tc, ConfigKind.EVENT);
        case 34 -> addIdentifierConfig(tc, ConfigKind.EVENT);
        case 35 -> reportIdentifierConfig(tc, ConfigKind.HK, 36);
        case 37 -> reportIdentifierConfig(tc, ConfigKind.DIAGNOSTIC, 38);
        case 39 -> reportIdentifierConfig(tc, ConfigKind.EVENT, 40);
        default -> {
            log.warn("Unknown ST[15] subtype {}, sending NACK start", tc.getSubtype());
            nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
        }
        }
    }

    /**
     * Called by {@link PusSimulator#transmitRealtimeTM} for every outgoing TM packet.
     */
    public void submitToStores(PusTmPacket pkt) {
        if (stores.isEmpty()) {
            return;
        }
        byte[] raw = pkt.getBytes();
        long ts = storageTime.now().millis();
        for (PacketStore s : stores.values()) {
            if (s.storageEnabled && shouldStore(s, pkt)) {
                s.append(raw, ts);
            }
        }
    }

    private boolean shouldStore(PacketStore store, PusTmPacket pkt) {
        AppProcessSelection app = store.appProcessConfig.get(pkt.getAPID());
        if (app == null) {
            return false;
        }
        if (!app.services.isEmpty()) {
            Set<Integer> subtypes = app.services.get(pkt.getType());
            if (subtypes == null || (!subtypes.isEmpty() && !subtypes.contains(pkt.getSubtype()))) {
                return false;
            }
        }

        if (pkt.getType() == PusSimulator.PUS_TYPE_HK && pkt.getSubtype() == 25) {
            return identifierAllowed(store.hkConfig, pkt.getAPID(), pkt.getUserDataBuffer().getInt(0));
        }
        if (pkt.getType() == PusSimulator.PUS_TYPE_HK && pkt.getSubtype() == 26) {
            return identifierAllowed(store.diagnosticConfig, pkt.getAPID(), pkt.getUserDataBuffer().getInt(0));
        }
        if (pkt.getType() == PusSimulator.PUS_TYPE_EVENT
                && pkt.getSubtype() >= 1 && pkt.getSubtype() <= 4) {
            Set<Integer> blocked = store.eventBlockingConfig.get(pkt.getAPID());
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

    private static boolean identifierAllowed(Map<Integer, PusStructureSelection> config, int apid, int identifier) {
        PusStructureSelection selection = config.get(apid);
        return selection != null && selection.accepts(identifier);
    }

    // ---- TC[15,3-5] / TM[15,6]: application-process packet selection ----

    private void addApplicationReportTypes(PusTcPacket tc) {
        ack_start(tc);
        BitBuffer bits = PusPackedFields.readBuffer(payload(tc));
        PacketStore store = resolveStore(tc, PusPackedFields.readUnsigned(bits, 8));
        if (store == null) {
            return;
        }
        int n1 = PusPackedFields.readUnsigned(bits, PusPackedFields.OCTET_BITS);
        for (int i = 0; i < n1; i++) {
            int apid = PusPackedFields.readUnsigned(bits, PusPackedFields.APID_BITS);
            int n2 = PusPackedFields.readUnsigned(bits, PusPackedFields.OCTET_BITS);
            AppProcessSelection selection = store.appProcessConfig.get(apid);
            if (n2 == 0) {
                store.appProcessConfig.put(apid, new AppProcessSelection());
                continue;
            }
            if (selection != null && selection.services.isEmpty()) {
                log.warn("ST15: APID {} already allows all service types in store {}", apid, store.storeId);
                nack_completion(tc, COMPL_ERR_WILDCARD_CONFLICT);
                return;
            }
            if (selection == null) {
                selection = new AppProcessSelection();
                store.appProcessConfig.put(apid, selection);
            }
            for (int j = 0; j < n2; j++) {
                int service = PusPackedFields.readUnsigned(bits, PusPackedFields.OCTET_BITS);
                int n3 = PusPackedFields.readUnsigned(bits, PusPackedFields.OCTET_BITS);
                Set<Integer> subtypes = selection.services.get(service);
                if (n3 == 0) {
                    selection.services.put(service, new LinkedHashSet<>());
                    continue;
                }
                if (subtypes != null && subtypes.isEmpty()) {
                    log.warn("ST15: APID {} service {} already allows all subtypes in store {}",
                            apid, service, store.storeId);
                    nack_completion(tc, COMPL_ERR_WILDCARD_CONFLICT);
                    return;
                }
                if (subtypes == null) {
                    subtypes = new LinkedHashSet<>();
                    selection.services.put(service, subtypes);
                }
                for (int k = 0; k < n3; k++) {
                    subtypes.add(PusPackedFields.readUnsigned(bits, PusPackedFields.OCTET_BITS));
                }
            }
        }
        ack_completion(tc);
    }

    private void deleteApplicationReportTypes(PusTcPacket tc) {
        ack_start(tc);
        BitBuffer bits = PusPackedFields.readBuffer(payload(tc));
        PacketStore store = resolveStore(tc, PusPackedFields.readUnsigned(bits, 8));
        if (store == null) {
            return;
        }
        int n1 = PusPackedFields.readUnsigned(bits, PusPackedFields.OCTET_BITS);
        if (n1 == 0) {
            store.appProcessConfig.clear();
            ack_completion(tc);
            return;
        }
        for (int i = 0; i < n1; i++) {
            int apid = PusPackedFields.readUnsigned(bits, PusPackedFields.APID_BITS);
            int n2 = PusPackedFields.readUnsigned(bits, PusPackedFields.OCTET_BITS);
            AppProcessSelection selection = store.appProcessConfig.get(apid);
            if (selection == null) {
                log.warn("ST15: APID {} not in application configuration for store {}", apid, store.storeId);
                nack_completion(tc, COMPL_ERR_APID_NOT_IN_CONFIG);
                return;
            }
            if (n2 == 0) {
                store.appProcessConfig.remove(apid);
                continue;
            }
            if (selection.services.isEmpty()) {
                nack_completion(tc, COMPL_ERR_SERVICE_NOT_IN_CONFIG);
                return;
            }
            for (int j = 0; j < n2; j++) {
                int service = PusPackedFields.readUnsigned(bits, PusPackedFields.OCTET_BITS);
                int n3 = PusPackedFields.readUnsigned(bits, PusPackedFields.OCTET_BITS);
                Set<Integer> subtypes = selection.services.get(service);
                if (subtypes == null) {
                    log.warn("ST15: service {} not configured for APID {} in store {}",
                            service, apid, store.storeId);
                    nack_completion(tc, COMPL_ERR_SERVICE_NOT_IN_CONFIG);
                    return;
                }
                if (n3 == 0) {
                    selection.services.remove(service);
                    continue;
                }
                if (subtypes.isEmpty()) {
                    nack_completion(tc, COMPL_ERR_IDENTIFIER_NOT_IN_CONFIG);
                    return;
                }
                for (int k = 0; k < n3; k++) {
                    int subtype = PusPackedFields.readUnsigned(bits, PusPackedFields.OCTET_BITS);
                    if (!subtypes.remove(subtype)) {
                        log.warn("ST15: subtype {} not configured for APID {} service {} in store {}",
                                subtype, apid, service, store.storeId);
                        nack_completion(tc, COMPL_ERR_IDENTIFIER_NOT_IN_CONFIG);
                        return;
                    }
                }
                if (subtypes.isEmpty()) {
                    selection.services.remove(service);
                }
            }
            if (selection.services.isEmpty()) {
                store.appProcessConfig.remove(apid);
            }
        }
        ack_completion(tc);
    }

    private void reportApplicationConfig(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = payload(tc);
        PacketStore store = resolveStore(tc, bb.get() & 0xFF);
        if (store == null) {
            return;
        }
        long bitSize = applicationConfigReportBitSize(store);
        if (!reportFits(bitSize)) {
            reportTooLarge(tc, 6, bitSize);
            return;
        }
        PusTmPacket pkt = newPacket(6, PusPackedFields.bytesForBits(bitSize));
        BitBuffer out = PusPackedFields.bitBuffer(pkt.getUserDataBuffer());
        out.putBits(store.storeId, 8);
        out.putBits(store.appProcessConfig.size(), PusPackedFields.OCTET_BITS);
        for (var appEntry : store.appProcessConfig.entrySet()) {
            out.putBits(appEntry.getKey(), PusPackedFields.APID_BITS);
            Map<Integer, Set<Integer>> services = appEntry.getValue().services;
            out.putBits(services.size(), PusPackedFields.OCTET_BITS);
            for (var serviceEntry : services.entrySet()) {
                out.putBits(serviceEntry.getKey(), PusPackedFields.OCTET_BITS);
                out.putBits(serviceEntry.getValue().size(), PusPackedFields.OCTET_BITS);
                for (int subtype : serviceEntry.getValue()) {
                    out.putBits(subtype, PusPackedFields.OCTET_BITS);
                }
            }
        }
        pusSimulator.transmitRealtimeTM(pkt);
        ack_completion(tc);
    }

    private long applicationConfigReportBitSize(PacketStore store) {
        if (store.appProcessConfig.size() > PusPackedFields.MAX_OCTET_COUNT) {
            return -1;
        }
        long bits = 8 + PusPackedFields.OCTET_BITS;
        for (AppProcessSelection selection : store.appProcessConfig.values()) {
            if (selection.services.size() > PusPackedFields.MAX_OCTET_COUNT) {
                return -1;
            }
            bits += PusPackedFields.APID_BITS + PusPackedFields.OCTET_BITS;
            for (Set<Integer> subtypes : selection.services.values()) {
                if (subtypes.size() > PusPackedFields.MAX_OCTET_COUNT) {
                    return -1;
                }
                bits += 2L * PusPackedFields.OCTET_BITS
                        + (long) subtypes.size() * PusPackedFields.OCTET_BITS;
            }
        }
        return bits;
    }

    // ---- TC[15,29-40]: HK, diagnostic and event identifier configurations ----

    private void addIdentifierConfig(PusTcPacket tc, ConfigKind kind) {
        if (kind != ConfigKind.EVENT) {
            updateStructureConfig(tc, kind, true);
            return;
        }
        ack_start(tc);
        BitBuffer bits = PusPackedFields.readBuffer(payload(tc));
        PacketStore store = resolveStore(tc, PusPackedFields.readUnsigned(bits, 8));
        if (store == null) {
            return;
        }
        Map<Integer, Set<Integer>> config = identifierConfig(store, kind);
        int n1 = PusPackedFields.readUnsigned(bits, PusPackedFields.OCTET_BITS);
        for (int i = 0; i < n1; i++) {
            int apid = PusPackedFields.readUnsigned(bits, PusPackedFields.APID_BITS);
            int n2 = PusPackedFields.readUnsigned(bits, PusPackedFields.OCTET_BITS);
            if (n2 == 0) {
                config.put(apid, new LinkedHashSet<>());
                continue;
            }
            Set<Integer> identifiers = config.get(apid);
            if (identifiers != null && identifiers.isEmpty()) {
                log.warn("ST15: {} configuration for APID {} already has wildcard semantics in store {}",
                        kind, apid, store.storeId);
                nack_completion(tc, COMPL_ERR_WILDCARD_CONFLICT);
                return;
            }
            if (identifiers == null) {
                identifiers = new LinkedHashSet<>();
                config.put(apid, identifiers);
            }
            for (int j = 0; j < n2; j++) {
                identifiers.add(PusPackedFields.readUnsigned(bits, identifierBits(kind)));
            }
        }
        ack_completion(tc);
    }

    private void deleteIdentifierConfig(PusTcPacket tc, ConfigKind kind) {
        if (kind != ConfigKind.EVENT) {
            updateStructureConfig(tc, kind, false);
            return;
        }
        ack_start(tc);
        BitBuffer bits = PusPackedFields.readBuffer(payload(tc));
        PacketStore store = resolveStore(tc, PusPackedFields.readUnsigned(bits, 8));
        if (store == null) {
            return;
        }
        Map<Integer, Set<Integer>> config = identifierConfig(store, kind);
        int n1 = PusPackedFields.readUnsigned(bits, PusPackedFields.OCTET_BITS);
        if (n1 == 0) {
            config.clear();
            ack_completion(tc);
            return;
        }
        for (int i = 0; i < n1; i++) {
            int apid = PusPackedFields.readUnsigned(bits, PusPackedFields.APID_BITS);
            int n2 = PusPackedFields.readUnsigned(bits, PusPackedFields.OCTET_BITS);
            Set<Integer> identifiers = config.get(apid);
            if (identifiers == null) {
                log.warn("ST15: APID {} not in {} configuration for store {}", apid, kind, store.storeId);
                nack_completion(tc, COMPL_ERR_APID_NOT_IN_CONFIG);
                return;
            }
            if (n2 == 0) {
                config.remove(apid);
                continue;
            }
            if (identifiers.isEmpty()) {
                nack_completion(tc, COMPL_ERR_IDENTIFIER_NOT_IN_CONFIG);
                return;
            }
            for (int j = 0; j < n2; j++) {
                int identifier = PusPackedFields.readUnsigned(bits, identifierBits(kind));
                if (!identifiers.remove(identifier)) {
                    log.warn("ST15: identifier {} not in {} configuration for APID {} store {}",
                            identifier, kind, apid, store.storeId);
                    nack_completion(tc, COMPL_ERR_IDENTIFIER_NOT_IN_CONFIG);
                    return;
                }
            }
            if (identifiers.isEmpty()) {
                config.remove(apid);
            }
        }
        ack_completion(tc);
    }

    private void reportIdentifierConfig(PusTcPacket tc, ConfigKind kind, int tmSubtype) {
        if (kind != ConfigKind.EVENT) {
            reportStructureConfig(tc, kind, tmSubtype);
            return;
        }
        ack_start(tc);
        ByteBuffer bb = payload(tc);
        PacketStore store = resolveStore(tc, bb.get() & 0xFF);
        if (store == null) {
            return;
        }
        Map<Integer, Set<Integer>> config = identifierConfig(store, kind);
        int idBits = identifierBits(kind);
        long bitSize = identifierConfigReportBitSize(config, idBits);
        if (!reportFits(bitSize)) {
            reportTooLarge(tc, tmSubtype, bitSize);
            return;
        }
        PusTmPacket pkt = newPacket(tmSubtype, PusPackedFields.bytesForBits(bitSize));
        BitBuffer out = PusPackedFields.bitBuffer(pkt.getUserDataBuffer());
        out.putBits(store.storeId, 8);
        out.putBits(config.size(), PusPackedFields.OCTET_BITS);
        for (var entry : config.entrySet()) {
            out.putBits(entry.getKey(), PusPackedFields.APID_BITS);
            out.putBits(entry.getValue().size(), PusPackedFields.OCTET_BITS);
            for (int identifier : entry.getValue()) {
                out.putBits(identifier, idBits);
            }
        }
        pusSimulator.transmitRealtimeTM(pkt);
        ack_completion(tc);
    }

    private long identifierConfigReportBitSize(Map<Integer, Set<Integer>> config, int idBits) {
        if (config.size() > PusPackedFields.MAX_OCTET_COUNT) {
            return -1;
        }
        long bits = 8 + PusPackedFields.OCTET_BITS;
        for (Set<Integer> identifiers : config.values()) {
            if (identifiers.size() > PusPackedFields.MAX_OCTET_COUNT) {
                return -1;
            }
            bits += PusPackedFields.APID_BITS + PusPackedFields.OCTET_BITS
                    + (long) identifiers.size() * idBits;
        }
        return bits;
    }

    private boolean reportFits(long bitSize) {
        return bitSize >= 0 && PusPackedFields.bytesForBits(bitSize) <= pusSimulator.maxTmDataSize();
    }

    private void reportTooLarge(PusTcPacket tc, int tmSubtype, long bitSize) {
        log.warn("ST15: TM[15,{}] cannot be represented atomically ({} bits)", tmSubtype, bitSize);
        nack_completion(tc, COMPL_ERR_REPORT_TOO_LARGE);
    }

    private PacketStore resolveStore(PusTcPacket tc, int storeId) {
        PacketStore store = stores.get(storeId);
        if (store == null) {
            log.warn("ST15: store id={} not found", storeId);
            nack_completion(tc, COMPL_ERR_STORE_NOT_FOUND);
        }
        return store;
    }

    private static Map<Integer, Set<Integer>> identifierConfig(PacketStore store, ConfigKind kind) {
        return switch (kind) {
        case HK, DIAGNOSTIC -> throw new IllegalArgumentException("Use structure configuration");
        case EVENT -> store.eventBlockingConfig;
        };
    }

    private static int identifierBits(ConfigKind kind) {
        return switch (kind) {
        case HK, DIAGNOSTIC -> PusPackedFields.STRUCTURE_ID_BITS;
        case EVENT -> PusPackedFields.EVENT_DEFINITION_ID_BITS;
        };
    }

    // ---- TC[15,1/2]: enable/disable storage ----

    private void setStorageEnabled(PusTcPacket tc, boolean enabled) {
        ack_start(tc);
        List<PacketStore> targets = resolveTargets(tc, payload(tc), false);
        if (targets == null) {
            return;
        }
        for (PacketStore s : targets) {
            s.storageEnabled = enabled;
        }
        log.info("ST15: {} storage on {} store(s)", enabled ? "enabled" : "disabled", targets.size());
        ack_completion(tc);
    }

    // ---- TC[15,9]/[15,17]: by-time-range retrieval ----

    private void startBtr(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = payload(tc);
        int n = bb.get() & 0xFF;
        List<PacketStore> targets = new ArrayList<>(n);
        List<long[]> windows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int id = bb.get() & 0xFF;
            int priority = bb.get() & 0xFF;
            long start = storageTime.read(bb).millis();
            long end = storageTime.read(bb).millis();
            if (start > end) {
                nack_completion(tc, COMPL_ERR_INVALID_DATA);
                return;
            }
            PacketStore s = stores.get(id);
            if (s == null) {
                log.warn("ST15: store id={} not found", id);
                nack_completion(tc, COMPL_ERR_STORE_NOT_FOUND);
                return;
            }
            if (s.btrEnabled) {
                log.warn("ST15: store id={} already has an active BTR", id);
                nack_completion(tc, COMPL_ERR_BTR_ALREADY_ACTIVE);
                return;
            }
            targets.add(s);
            windows.add(new long[] { start, end, priority });
        }
        for (int i = 0; i < targets.size(); i++) {
            PacketStore s = targets.get(i);
            long[] w = windows.get(i);
            s.btrEnabled = true;
            List<StoredPacket> toSend = new ArrayList<>();
            for (StoredPacket p : s.packets) {
                if (p.timestamp >= w[0] && p.timestamp <= w[1]) {
                    toSend.add(p);
                }
            }
            log.info("ST15: starting BTR on store id={}, {} packet(s) in window [{},{}]",
                    s.storeId, toSend.size(), w[0], w[1]);
            s.btrPriority = (int) w[2];
            s.btrPackets.clear();
            s.btrPackets.addAll(toSend);
            ensureRetrievalTask();
        }
        ack_completion(tc);
    }

    /** One executor owns all state; higher numeric priorities are sent first each tick. */
    private void ensureRetrievalTask() {
        if (retrievalTask == null) {
            retrievalTask = pusSimulator.executor.scheduleAtFixedRate(this::retrievalTick,
                    BTR_STEP_DELAY_MS, BTR_STEP_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void retrievalTick() {
        boolean openTick = ++retrievalTicks % (OPEN_RETRIEVAL_POLL_MS / BTR_STEP_DELAY_MS) == 0;
        List<Retrieval> ready = new ArrayList<>();
        for (PacketStore store : stores.values()) {
            if (store.btrEnabled) {
                StoredPacket packet = store.btrPackets.pollFirst();
                if (packet != null) {
                    ready.add(new Retrieval(store, packet, store.btrPriority, false));
                } else {
                    store.btrEnabled = false;
                }
            }
            if (openTick && store.openRetrievalInProgress) {
                for (StoredPacket packet : store.packets) {
                    if (packet.seq > store.openRetrievalLastSentSeq && packet.timestamp >= store.openRetrievalMinimumTime) {
                        ready.add(new Retrieval(store, packet, store.openPriority, true));
                        break;
                    }
                }
            }
        }
        ready.sort(Comparator.comparingInt(Retrieval::priority).reversed());
        for (Retrieval retrieval : ready) {
            pusSimulator.sendStoredPacket(retrieval.packet.raw, retrieval.store.vcId);
            if (retrieval.open) {
                retrieval.store.openRetrievalLastSentSeq = retrieval.packet.seq;
                retrieval.store.openRetrievalStartTime = retrieval.packet.timestamp;
            }
        }
        if (stores.values().stream().noneMatch(s -> s.openRetrievalInProgress || s.btrEnabled)) {
            retrievalTask.cancel(false);
            retrievalTask = null;
        }
    }

    private record Retrieval(PacketStore store, StoredPacket packet, int priority, boolean open) {
    }

    private void abortBtr(PusTcPacket tc) {
        ack_start(tc);
        List<PacketStore> targets = resolveTargets(tc, payload(tc), false);
        if (targets == null) {
            return;
        }
        for (PacketStore s : targets) {
            s.btrEnabled = false;
            s.btrPackets.clear();
        }
        ack_completion(tc);
    }

    // ---- TC[15,11]: delete content up to a time limit ----

    private void deleteContent(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = payload(tc);
        long timeLimit = storageTime.read(bb).millis();
        List<PacketStore> targets = resolveTargets(tc, bb, false);
        if (targets == null) {
            return;
        }
        for (PacketStore s : targets) {
            if (s.openRetrievalInProgress || s.btrEnabled) {
                log.warn("ST15: store id={} has active retrieval, rejecting content delete", s.storeId);
                nack_completion(tc, COMPL_ERR_ACTIVE_RETRIEVAL);
                return;
            }
        }
        for (PacketStore s : targets) {
            Iterator<StoredPacket> it = s.packets.iterator();
            while (it.hasNext()) {
                StoredPacket p = it.next();
                if (p.timestamp <= timeLimit) {
                    s.usedBytes -= p.raw.length;
                    it.remove();
                }
            }
        }
        ack_completion(tc);
    }

    // ---- TC[15,12]/TM[15,13]: summary report ----

    private void reportSummary(PusTcPacket tc) {
        ack_start(tc);
        List<PacketStore> targets = resolveTargets(tc, payload(tc), false);
        if (targets == null) {
            return;
        }
        long size = 1L + targets.size() * (3L + 3L * storageTime.getEncodedLength());
        if (size > pusSimulator.maxTmDataSize()) {
            reportTooLarge(tc, 13, size * 8);
            return;
        }
        sendSummaryReport(targets);
        ack_completion(tc);
    }

    private void sendSummaryReport(List<PacketStore> targets) {
        // N(1) + N x { store_id(1), oldest_ts(CUC), newest_ts(CUC), open_retrieval_start_time(CUC), fill_pct(1), fill_pct_from_start(1) }
        int entrySize = 1 + 3 * storageTime.getEncodedLength() + 2;
        PusTmPacket pkt = newPacket(13, 1 + targets.size() * entrySize);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.put((byte) targets.size());
        for (PacketStore s : targets) {
            long oldest = s.packets.stream().mapToLong(p -> p.timestamp).min().orElse(0);
            long newest = s.packets.stream().mapToLong(p -> p.timestamp).max().orElse(0);
            long bytesFromCursor = 0;
            for (StoredPacket p : s.packets) {
                if (p.seq > s.openRetrievalLastSentSeq && p.timestamp >= s.openRetrievalMinimumTime) {
                    bytesFromCursor += p.raw.length;
                }
            }
            bb.put((byte) s.storeId);
            storageTime.encode(new PusTime(oldest), bb);
            storageTime.encode(new PusTime(newest), bb);
            storageTime.encode(new PusTime(s.openRetrievalStartTime), bb);
            bb.put((byte) fillPct(s.usedBytes, s.sizeBytes));
            bb.put((byte) fillPct(bytesFromCursor, s.sizeBytes));
        }
        pusSimulator.transmitRealtimeTM(pkt);
    }

    private static int fillPct(long usedBytes, long capacityBytes) {
        return capacityBytes <= 0 ? 0 : (int) Math.min(100, (usedBytes * 100) / capacityBytes);
    }

    // ---- TC[15,14]/[15,15]/[15,16]: open retrieval cursor / resume / suspend ----

    private void changeOpenRetrievalStartTime(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = payload(tc);
        long startTime = storageTime.read(bb).millis();
        List<PacketStore> targets = resolveTargets(tc, bb, true);
        if (targets == null) {
            return;
        }
        for (PacketStore s : targets) {
            s.openRetrievalStartTime = startTime;
            s.openRetrievalMinimumTime = startTime;
            s.openRetrievalLastSentSeq = -1;
        }
        ack_completion(tc);
    }

    private void resumeOpenRetrieval(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = payload(tc);
        int count = bb.get() & 0xFF;
        List<PacketStore> targets = new ArrayList<>();
        List<Integer> priorities = new ArrayList<>();
        if (count == 0) {
            targets.addAll(stores.values());
            for (PacketStore store : targets) {
                priorities.add(store.openPriority);
            }
        } else {
            for (int i = 0; i < count; i++) {
                PacketStore store = resolveStore(tc, bb.get() & 0xFF);
                if (store == null) {
                    return;
                }
                targets.add(store);
                priorities.add(bb.get() & 0xFF);
            }
        }
        for (int i = 0; i < targets.size(); i++) {
            PacketStore store = targets.get(i);
            store.openPriority = priorities.get(i);
            store.openRetrievalInProgress = true;
        }
        if (!targets.isEmpty()) {
            ensureRetrievalTask();
        }
        ack_completion(tc);
    }

    private void suspendOpenRetrieval(PusTcPacket tc) {
        ack_start(tc);
        List<PacketStore> targets = resolveTargets(tc, payload(tc), false);
        if (targets == null) {
            return;
        }
        for (PacketStore s : targets) {
            stopOpenRetrieval(s);
        }
        ack_completion(tc);
    }

    private void stopOpenRetrieval(PacketStore s) {
        s.openRetrievalInProgress = false;

    }

    // ---- TC[15,18]/TM[15,19]: status report ----

    private void reportStatus(PusTcPacket tc) {
        ack_start(tc);
        sendStatusReport(new ArrayList<>(stores.values()));
        ack_completion(tc);
    }

    private void sendStatusReport(List<PacketStore> targets) {
        // N(1) + N x { store_id(1), storage_status(1), open_retrieval_status(1), btr_status(1) }
        int entrySize = 4;
        PusTmPacket pkt = newPacket(19, 1 + targets.size() * entrySize);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.put((byte) targets.size());
        for (PacketStore s : targets) {
            bb.put((byte) s.storeId);
            bb.put((byte) (s.storageEnabled ? 1 : 0));
            bb.put((byte) (s.openRetrievalInProgress ? 1 : 0));
            bb.put((byte) (s.btrEnabled ? 1 : 0));
        }
        pusSimulator.transmitRealtimeTM(pkt);
    }

    // ---- TC[15,20]: create packet stores ----

    private void createStores(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = payload(tc);
        int n = bb.get() & 0xFF;
        List<PacketStore> pending = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int id = bb.get() & 0xFF;
            long size = (bb.get() & 0xFF) * SIZE_UNIT_BYTES;
            int type = bb.get() & 0xFF;
            int vc = bb.get() & 0xFF;
            if (stores.containsKey(id) || pending.stream().anyMatch(s -> s.storeId == id)) {
                log.warn("ST15: store id={} already exists", id);
                nack_completion(tc, COMPL_ERR_STORE_ALREADY_EXISTS);
                return;
            }
            if (stores.size() + pending.size() >= MAX_STORES) {
                log.warn("ST15: max store count ({}) exceeded", MAX_STORES);
                nack_completion(tc, COMPL_ERR_MAX_STORES_EXCEEDED);
                return;
            }
            if (size == 0 || type > STORE_TYPE_BOUNDED) {
                nack_completion(tc, COMPL_ERR_INVALID_DATA);
                return;
            }
            pending.add(new PacketStore(id, size, type, vc));
        }
        for (PacketStore s : pending) {
            stores.put(s.storeId, s);
        }
        log.info("ST15: created {} packet store(s), now {} total", pending.size(), stores.size());
        ack_completion(tc);
    }

    // ---- TC[15,21]: delete packet stores ----

    private void deleteStores(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = payload(tc);
        List<PacketStore> targets;
        if ((bb.get(bb.position()) & 0xFF) == 0) {
            bb.get();
            targets = new ArrayList<>();
            for (PacketStore s : stores.values()) {
                if (!s.isActive()) {
                    targets.add(s);
                }
            }
        } else {
            int n = bb.get() & 0xFF;
            targets = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                int id = bb.get() & 0xFF;
                PacketStore s = stores.get(id);
                if (s == null) {
                    log.warn("ST15: store id={} not found", id);
                    nack_completion(tc, COMPL_ERR_STORE_NOT_FOUND);
                    return;
                }
                if (s.isActive()) {
                    log.warn("ST15: store id={} is active, rejecting delete", id);
                    nack_completion(tc, COMPL_ERR_STORE_ACTIVE);
                    return;
                }
                targets.add(s);
            }
        }
        for (PacketStore s : targets) {
            stopOpenRetrieval(s);
            stores.remove(s.storeId);
        }
        log.info("ST15: deleted {} packet store(s), now {} total", targets.size(), stores.size());
        ack_completion(tc);
    }

    // ---- TC[15,22]/TM[15,23]: configuration report ----

    private void reportConfig(PusTcPacket tc) {
        ack_start(tc);
        sendConfigReport(new ArrayList<>(stores.values()));
        ack_completion(tc);
    }

    private void sendConfigReport(List<PacketStore> targets) {
        // N(1) + N x { store_id(1), size_units(1), store_type(1), vc_id(1) }
        int entrySize = 4;
        PusTmPacket pkt = newPacket(23, 1 + targets.size() * entrySize);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.put((byte) targets.size());
        for (PacketStore s : targets) {
            bb.put((byte) s.storeId);
            bb.put((byte) (s.sizeBytes / SIZE_UNIT_BYTES));
            bb.put((byte) s.storeType);
            bb.put((byte) s.vcId);
        }
        pusSimulator.transmitRealtimeTM(pkt);
    }

    private void copyPackets(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = payload(tc);
        int selector = bb.get() & 0xFF;
        if (selector > 2) {
            nack_completion(tc, COMPL_ERR_INVALID_DATA);
            return;
        }
        long from = selector == 2 ? Long.MIN_VALUE : storageTime.read(bb).millis();
        long to = selector == 1 ? Long.MAX_VALUE : storageTime.read(bb).millis();
        PacketStore source = resolveStore(tc, bb.get() & 0xFF);
        if (source == null) {
            return;
        }
        PacketStore destination = resolveStore(tc, bb.get() & 0xFF);
        if (destination == null) {
            return;
        }
        if (source == destination || from > to) {
            nack_completion(tc, COMPL_ERR_INVALID_DATA);
            return;
        }
        // Snapshot preserves the source; copied packets retain their storage time and raw bytes.
        List<StoredPacket> selected = source.packets.stream()
                .filter(p -> (selector == 1 ? p.timestamp > from : p.timestamp >= from)
                        && (selector == 2 ? p.timestamp < to : p.timestamp <= to)).toList();
        long bytes = selected.stream().mapToLong(p -> p.raw.length).sum();
        if (selected.stream().anyMatch(packet -> packet.raw.length > destination.sizeBytes)
                || (destination.storeType == STORE_TYPE_BOUNDED && destination.usedBytes + bytes > destination.sizeBytes)) {
            nack_completion(tc, COMPL_ERR_INVALID_DATA);
            return;
        }
        for (StoredPacket packet : selected) {
            destination.append(packet.raw, packet.timestamp);
        }
        ack_completion(tc);
    }

    private void changeStoreType(PusTcPacket tc, int type) {
        ack_start(tc);
        PacketStore store = resolveStore(tc, payload(tc).get() & 0xFF);
        if (store == null) {
            return;
        }
        if (store.isActive()) {
            nack_completion(tc, COMPL_ERR_STORE_ACTIVE);
            return;
        }
        store.storeType = type;
        ack_completion(tc);
    }

    private Map<Integer, PusStructureSelection> structureConfig(PacketStore store, ConfigKind kind) {
        return kind == ConfigKind.HK ? store.hkConfig : store.diagnosticConfig;
    }

    private void updateStructureConfig(PusTcPacket tc, ConfigKind kind, boolean add) {
        ack_start(tc);
        BitBuffer bits = PusPackedFields.readBuffer(payload(tc));
        PacketStore store = resolveStore(tc, PusPackedFields.readUnsigned(bits, 8));
        if (store == null) {
            return;
        }
        Map<Integer, PusStructureSelection> config = structureConfig(store, kind);
        int count = PusPackedFields.readUnsigned(bits, 8);
        if (!add && count == 0) {
            config.clear();
        }
        for (int i = 0; i < count; i++) {
            int apid = PusPackedFields.readUnsigned(bits, 11);
            int identifiers = PusPackedFields.readUnsigned(bits, 8);
            PusStructureSelection selection = config.get(apid);
            if (!add && selection == null) {
                nack_completion(tc, COMPL_ERR_APID_NOT_IN_CONFIG);
                return;
            }
            if (identifiers == 0) {
                if (add) {
                    config.put(apid, new PusStructureSelection());
                } else {
                    config.remove(apid);
                }
                continue;
            }
            if (add && selection == null) {
                selection = new PusStructureSelection();
                config.put(apid, selection);
            } else if (selection.rates.isEmpty()) {
                nack_completion(tc, COMPL_ERR_WILDCARD_CONFLICT);
                return;
            }
            for (int j = 0; j < identifiers; j++) {
                int id = PusPackedFields.readUnsigned(bits, 8);
                if (add) {
                    selection.setRate(id, PusPackedFields.readUnsigned(bits, 8));
                } else if (selection.rates.remove(id) == null) {
                    nack_completion(tc, COMPL_ERR_IDENTIFIER_NOT_IN_CONFIG);
                    return;
                }
            }
            if (!add && selection.rates.isEmpty()) {
                config.remove(apid);
            }
        }
        ack_completion(tc);
    }

    private void reportStructureConfig(PusTcPacket tc, ConfigKind kind, int subtype) {
        ack_start(tc);
        PacketStore store = resolveStore(tc, payload(tc).get() & 0xFF);
        if (store == null) {
            return;
        }
        Map<Integer, PusStructureSelection> config = structureConfig(store, kind);
        long size = 16;
        for (PusStructureSelection selection : config.values()) {
            if (selection.rates.size() > 255) {
                reportTooLarge(tc, subtype, -1);
                return;
            }
            size += 19 + selection.rates.size() * 16L;
        }
        if (config.size() > 255 || !reportFits(size)) {
            reportTooLarge(tc, subtype, size);
            return;
        }
        PusTmPacket packet = newPacket(subtype, PusPackedFields.bytesForBits(size));
        BitBuffer bits = PusPackedFields.bitBuffer(packet.getUserDataBuffer());
        bits.putBits(store.storeId, 8);
        bits.putBits(config.size(), 8);
        for (var entry : config.entrySet()) {
            bits.putBits(entry.getKey(), 11);
            bits.putBits(entry.getValue().rates.size(), 8);
            for (var rate : entry.getValue().rates.entrySet()) {
                bits.putBits(rate.getKey(), 8);
                bits.putBits(rate.getValue(), 8);
            }
        }
        pusSimulator.transmitRealtimeTM(packet);
        ack_completion(tc);
    }

    // ---- TC[15,25]: resize packet stores ----

    private void resizeStores(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = payload(tc);
        int n = bb.get() & 0xFF;
        List<PacketStore> targets = new ArrayList<>(n);
        List<Long> sizes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int id = bb.get() & 0xFF;
            long newSize = (bb.get() & 0xFF) * SIZE_UNIT_BYTES;
            PacketStore s = stores.get(id);
            if (s == null) {
                log.warn("ST15: store id={} not found", id);
                nack_completion(tc, COMPL_ERR_STORE_NOT_FOUND);
                return;
            }
            if (s.isActive()) {
                log.warn("ST15: store id={} is active, rejecting resize", id);
                nack_completion(tc, COMPL_ERR_STORE_ACTIVE);
                return;
            }
            if (newSize == 0 || newSize < s.usedBytes) {
                nack_completion(tc, COMPL_ERR_INVALID_DATA);
                return;
            }
            targets.add(s);
            sizes.add(newSize);
        }
        for (int i = 0; i < targets.size(); i++) {
            targets.get(i).sizeBytes = sizes.get(i);
        }
        ack_completion(tc);
    }

    // ---- TC[15,28]: change virtual channel ----

    private void changeVc(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = payload(tc);
        int id = bb.get() & 0xFF;
        int newVc = bb.get() & 0xFF;
        PacketStore s = stores.get(id);
        if (s == null) {
            log.warn("ST15: store id={} not found", id);
            nack_completion(tc, COMPL_ERR_STORE_NOT_FOUND);
            return;
        }
        if (s.isActive()) {
            log.warn("ST15: store id={} is active, rejecting VC change", id);
            nack_completion(tc, COMPL_ERR_STORE_ACTIVE);
            return;
        }
        s.vcId = newVc;
        ack_completion(tc);
    }

    // ---- Counted store selection: N=0 selects all eligible stores ----

    /**
     * @param requireSuspended when true, "all stores" silently means "all suspended stores", and a
     *                         specific store id that isn't suspended is rejected with
     *                         {@link #COMPL_ERR_NOT_SUSPENDED} (used by TC[15,14]).
     * @return the resolved target stores, or {@code null} if a NACK completion was already sent.
     */
    private List<PacketStore> resolveTargets(PusTcPacket tc, ByteBuffer bb, boolean requireSuspended) {
        if ((bb.get(bb.position()) & 0xFF) == 0) {
            bb.get();
            List<PacketStore> all = new ArrayList<>(stores.values());
            if (requireSuspended) {
                all.removeIf(s -> s.openRetrievalInProgress);
            }
            return all;
        }
        int n = bb.get() & 0xFF;
        List<PacketStore> targets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int id = bb.get() & 0xFF;
            PacketStore s = stores.get(id);
            if (s == null) {
                log.warn("ST15: store id={} not found", id);
                nack_completion(tc, COMPL_ERR_STORE_NOT_FOUND);
                return null;
            }
            if (requireSuspended && s.openRetrievalInProgress) {
                log.warn("ST15: store id={} is not suspended", id);
                nack_completion(tc, COMPL_ERR_NOT_SUSPENDED);
                return null;
            }
            targets.add(s);
        }
        return targets;
    }

    private static ByteBuffer payload(PusTcPacket tc) {
        ByteBuffer data = tc.getUserDataBuffer();
        data.limit(data.limit() - 2); // Exclude the packet error-control field.
        return data;
    }

    // ---- State ----

    private enum ConfigKind {
        HK,
        DIAGNOSTIC,
        EVENT
    }

    private static class AppProcessSelection {
        // Empty map = all service types; empty subtype set = all subtypes for that service.
        final Map<Integer, Set<Integer>> services = new LinkedHashMap<>();
    }

    private static class StoredPacket {
        final byte[] raw;
        final long timestamp;
        final long seq;

        StoredPacket(byte[] raw, long timestamp, long seq) {
            this.raw = raw;
            this.timestamp = timestamp;
            this.seq = seq;
        }
    }

    private static class PacketStore {
        final int storeId;
        long sizeBytes;
        int storeType;
        int vcId;
        boolean storageEnabled = false;
        boolean openRetrievalInProgress = false;
        boolean btrEnabled = false;
        long openRetrievalStartTime = 0;
        long openRetrievalMinimumTime = 0;
        long openRetrievalLastSentSeq = -1;
        long usedBytes = 0;
        long nextSeq = 0;
        final Deque<StoredPacket> packets = new ArrayDeque<>();
        final Map<Integer, AppProcessSelection> appProcessConfig = new LinkedHashMap<>();
        // Empty identifier set = all structures for HK/diagnostic, all events blocked for event config.
        final Map<Integer, PusStructureSelection> hkConfig = new LinkedHashMap<>();
        final Map<Integer, PusStructureSelection> diagnosticConfig = new LinkedHashMap<>();
        final Map<Integer, Set<Integer>> eventBlockingConfig = new LinkedHashMap<>();
        int openPriority;
        int btrPriority;
        final Deque<StoredPacket> btrPackets = new ArrayDeque<>();

        PacketStore(int storeId, long sizeBytes, int storeType, int vcId) {
            this.storeId = storeId;
            this.sizeBytes = sizeBytes;
            this.storeType = storeType;
            this.vcId = vcId;
        }

        void append(byte[] raw, long ts) {
            if (raw.length > sizeBytes) {
                return; // An oversized packet must not evict existing circular-store content.
            }
            int size = raw.length;
            if (storeType == STORE_TYPE_CIRCULAR) {
                while (usedBytes + size > sizeBytes && !packets.isEmpty()) {
                    StoredPacket evicted = packets.pollFirst();
                    usedBytes -= evicted.raw.length;
                }
            }
            if (usedBytes + size > sizeBytes) {
                return; // bounded store full, or packet larger than capacity even after eviction
            }
            packets.addLast(new StoredPacket(raw, ts, nextSeq++));
            usedBytes += size;
        }

        boolean isActive() {
            return storageEnabled || openRetrievalInProgress || btrEnabled;
        }
    }
}
