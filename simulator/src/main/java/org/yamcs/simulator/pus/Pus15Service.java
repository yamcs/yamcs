package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * ST[15] On-Board Storage and Retrieval simulator service. See pus_analysis/pus15.md and
 * pus_simulator_architecture.md for the full design rationale.
 *
 * <p>
 * This is the "core lifecycle" scope: packet store CRUD (create/delete/resize/change-VC/report
 * config), storage enable/disable, status/summary reporting, by-time-range retrieval and open
 * retrieval. The Packet Selection subservice (TC[15,3/4/5/6] and the HK/diagnostic/event filter
 * tables, TC[15,29-40]) is deferred to a follow-up -- see class note below on default storage
 * behaviour in the meantime.
 *
 * <p>
 * <b>Storage filtering default:</b> since the Packet Selection subservice isn't implemented yet,
 * there is no way to populate an application-process storage-control configuration. Rather than
 * make {@code storage_enabled=true} inert (spec-strict default would store nothing without an
 * explicit filter), this simulator stores <i>all</i> outgoing TM in every storage-enabled store --
 * a pass-all default, mirroring the same usability-over-strict-compliance choice already made for
 * ST[14]'s forwarding gate (see Pus14Service). Once the Packet Selection subservice lands, this
 * default narrows to "nothing until explicitly added" per spec.
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

    static final int STORE_TYPE_CIRCULAR = 0;
    static final int STORE_TYPE_BOUNDED = 1;

    static final int MAX_STORES = 64;
    static final long OPEN_RETRIEVAL_POLL_MS = 500;
    static final long BTR_STEP_DELAY_MS = 100;

    private final Map<Integer, PacketStore> stores = new LinkedHashMap<>();

    Pus15Service(PusSimulator pusSimulator) {
        super(pusSimulator, 15);
    }

    @Override
    public void executeTc(PusTcPacket tc) {
        switch (tc.getSubtype()) {
        // TC[15,1] enable storage function of packet stores
        case 1 -> setStorageEnabled(tc, true);
        // TC[15,2] disable storage function of packet stores
        case 2 -> setStorageEnabled(tc, false);
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
        // TC[15,25] resize packet stores
        case 25 -> resizeStores(tc);
        // TC[15,28] change virtual channel used by a packet store
        case 28 -> changeVc(tc);
        default -> {
            log.warn("Unknown ST[15] subtype {}, sending NACK start", tc.getSubtype());
            nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
        }
        }
    }

    /**
     * Called by {@link PusSimulator#transmitRealtimeTM} for every outgoing TM packet. See class
     * javadoc for the current pass-all storage default.
     */
    public void submitToStores(PusTmPacket pkt) {
        if (stores.isEmpty()) {
            return;
        }
        byte[] raw = pkt.getBytes();
        long ts = pusSimulator.timeEncoding.now().millis();
        for (PacketStore s : stores.values()) {
            if (s.storageEnabled) {
                s.append(raw, ts);
            }
        }
    }

    // ---- TC[15,1/2]: enable/disable storage ----

    private void setStorageEnabled(PusTcPacket tc, boolean enabled) {
        ack_start(tc);
        List<PacketStore> targets = resolveTargets(tc, tc.getUserDataBuffer(), false);
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
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        List<PacketStore> targets = new ArrayList<>(n);
        List<long[]> windows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int id = bb.getShort() & 0xFFFF;
            long start = bb.getLong();
            long end = bb.getLong();
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
            windows.add(new long[] { start, end });
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
            runBtrStep(s, toSend, 0);
        }
        ack_completion(tc);
    }

    private void runBtrStep(PacketStore s, List<StoredPacket> toSend, int idx) {
        if (!s.btrEnabled) {
            return; // aborted via TC[15,17]
        }
        if (idx >= toSend.size()) {
            s.btrEnabled = false;
            log.info("ST15: BTR complete on store id={}", s.storeId);
            return;
        }
        pusSimulator.sendStoredPacket(toSend.get(idx).raw);
        pusSimulator.executor.schedule(() -> runBtrStep(s, toSend, idx + 1), BTR_STEP_DELAY_MS,
                TimeUnit.MILLISECONDS);
    }

    private void abortBtr(PusTcPacket tc) {
        ack_start(tc);
        List<PacketStore> targets = resolveTargets(tc, tc.getUserDataBuffer(), false);
        if (targets == null) {
            return;
        }
        for (PacketStore s : targets) {
            s.btrEnabled = false; // runBtrStep checks this before sending the next packet
        }
        ack_completion(tc);
    }

    // ---- TC[15,11]: delete content up to a time limit ----

    private void deleteContent(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        long timeLimit = bb.getLong();
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
        List<PacketStore> targets = resolveTargets(tc, tc.getUserDataBuffer(), false);
        if (targets == null) {
            return;
        }
        sendSummaryReport(targets);
        ack_completion(tc);
    }

    private void sendSummaryReport(List<PacketStore> targets) {
        // N(1) + N x { store_id(2), oldest_ts(8), newest_ts(8), open_retrieval_start_time(8), fill_pct(1), fill_pct_from_start(1) }
        int entrySize = 2 + 8 + 8 + 8 + 1 + 1;
        PusTmPacket pkt = newPacket(13, 1 + targets.size() * entrySize);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.put((byte) targets.size());
        for (PacketStore s : targets) {
            long oldest = s.packets.isEmpty() ? 0 : s.packets.peekFirst().timestamp;
            long newest = s.packets.isEmpty() ? 0 : s.packets.peekLast().timestamp;
            long bytesFromCursor = 0;
            for (StoredPacket p : s.packets) {
                if (p.seq > s.openRetrievalLastSentSeq) {
                    bytesFromCursor += p.raw.length;
                }
            }
            bb.putShort((short) s.storeId);
            bb.putLong(oldest);
            bb.putLong(newest);
            bb.putLong(s.openRetrievalStartTime);
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
        ByteBuffer bb = tc.getUserDataBuffer();
        long startTime = bb.getLong();
        List<PacketStore> targets = resolveTargets(tc, bb, true);
        if (targets == null) {
            return;
        }
        for (PacketStore s : targets) {
            s.openRetrievalStartTime = startTime;
            long lastSeq = -1;
            for (StoredPacket p : s.packets) {
                if (p.timestamp < startTime) {
                    lastSeq = p.seq;
                } else {
                    break;
                }
            }
            s.openRetrievalLastSentSeq = lastSeq;
        }
        ack_completion(tc);
    }

    private void resumeOpenRetrieval(PusTcPacket tc) {
        ack_start(tc);
        List<PacketStore> targets = resolveTargets(tc, tc.getUserDataBuffer(), false);
        if (targets == null) {
            return;
        }
        for (PacketStore s : targets) {
            startOpenRetrieval(s);
        }
        ack_completion(tc);
    }

    private void startOpenRetrieval(PacketStore s) {
        if (s.openRetrievalInProgress) {
            return;
        }
        s.openRetrievalInProgress = true;
        s.openRetrievalTask = pusSimulator.executor.scheduleAtFixedRate(
                () -> openRetrievalTick(s), 0, OPEN_RETRIEVAL_POLL_MS, TimeUnit.MILLISECONDS);
        log.info("ST15: resumed open retrieval on store id={}", s.storeId);
    }

    private void openRetrievalTick(PacketStore s) {
        if (!s.openRetrievalInProgress) {
            return;
        }
        for (StoredPacket p : s.packets) {
            if (p.seq > s.openRetrievalLastSentSeq) {
                pusSimulator.sendStoredPacket(p.raw);
                s.openRetrievalLastSentSeq = p.seq;
                s.openRetrievalStartTime = p.timestamp;
            }
        }
    }

    private void suspendOpenRetrieval(PusTcPacket tc) {
        ack_start(tc);
        List<PacketStore> targets = resolveTargets(tc, tc.getUserDataBuffer(), false);
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
        if (s.openRetrievalTask != null) {
            s.openRetrievalTask.cancel(false);
            s.openRetrievalTask = null;
        }
    }

    // ---- TC[15,18]/TM[15,19]: status report ----

    private void reportStatus(PusTcPacket tc) {
        ack_start(tc);
        sendStatusReport(new ArrayList<>(stores.values()));
        ack_completion(tc);
    }

    private void sendStatusReport(List<PacketStore> targets) {
        // N(1) + N x { store_id(2), storage_status(1), open_retrieval_status(1), btr_status(1) }
        int entrySize = 2 + 1 + 1 + 1;
        PusTmPacket pkt = newPacket(19, 1 + targets.size() * entrySize);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.put((byte) targets.size());
        for (PacketStore s : targets) {
            bb.putShort((short) s.storeId);
            bb.put((byte) (s.storageEnabled ? 1 : 0));
            bb.put((byte) (s.openRetrievalInProgress ? 1 : 0));
            bb.put((byte) (s.btrEnabled ? 1 : 0));
        }
        pusSimulator.transmitRealtimeTM(pkt);
    }

    // ---- TC[15,20]: create packet stores ----

    private void createStores(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        List<PacketStore> pending = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int id = bb.getShort() & 0xFFFF;
            long size = bb.getInt() & 0xFFFFFFFFL;
            int type = bb.get() & 0xFF;
            int vc = bb.get() & 0xFF;
            if (stores.containsKey(id)) {
                log.warn("ST15: store id={} already exists", id);
                nack_completion(tc, COMPL_ERR_STORE_ALREADY_EXISTS);
                return;
            }
            if (stores.size() + pending.size() >= MAX_STORES) {
                log.warn("ST15: max store count ({}) exceeded", MAX_STORES);
                nack_completion(tc, COMPL_ERR_MAX_STORES_EXCEEDED);
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
        ByteBuffer bb = tc.getUserDataBuffer();
        List<PacketStore> targets;
        if (bb.remaining() == 0) {
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
                int id = bb.getShort() & 0xFFFF;
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
        // N(1) + N x { store_id(2), size_bytes(4), store_type(1), vc_id(1) }
        int entrySize = 2 + 4 + 1 + 1;
        PusTmPacket pkt = newPacket(23, 1 + targets.size() * entrySize);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.put((byte) targets.size());
        for (PacketStore s : targets) {
            bb.putShort((short) s.storeId);
            bb.putInt((int) s.sizeBytes);
            bb.put((byte) s.storeType);
            bb.put((byte) s.vcId);
        }
        pusSimulator.transmitRealtimeTM(pkt);
    }

    // ---- TC[15,25]: resize packet stores ----

    private void resizeStores(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        List<PacketStore> targets = new ArrayList<>(n);
        List<Long> sizes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int id = bb.getShort() & 0xFFFF;
            long newSize = bb.getInt() & 0xFFFFFFFFL;
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
        ByteBuffer bb = tc.getUserDataBuffer();
        int id = bb.getShort() & 0xFFFF;
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

    // ---- Shared "all stores" vs "N specific store ids" resolution (Gap 2 dual-variant TCs) ----

    /**
     * @param requireSuspended when true, "all stores" silently means "all suspended stores", and a
     *                         specific store id that isn't suspended is rejected with
     *                         {@link #COMPL_ERR_NOT_SUSPENDED} (used by TC[15,14]).
     * @return the resolved target stores, or {@code null} if a NACK completion was already sent.
     */
    private List<PacketStore> resolveTargets(PusTcPacket tc, ByteBuffer bb, boolean requireSuspended) {
        if (bb.remaining() == 0) {
            List<PacketStore> all = new ArrayList<>(stores.values());
            if (requireSuspended) {
                all.removeIf(s -> s.openRetrievalInProgress);
            }
            return all;
        }
        int n = bb.get() & 0xFF;
        List<PacketStore> targets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int id = bb.getShort() & 0xFFFF;
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

    // ---- State ----

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
        long openRetrievalLastSentSeq = -1;
        long usedBytes = 0;
        long nextSeq = 0;
        final Deque<StoredPacket> packets = new ArrayDeque<>();
        ScheduledFuture<?> openRetrievalTask;

        PacketStore(int storeId, long sizeBytes, int storeType, int vcId) {
            this.storeId = storeId;
            this.sizeBytes = sizeBytes;
            this.storeType = storeType;
            this.vcId = vcId;
        }

        void append(byte[] raw, long ts) {
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
