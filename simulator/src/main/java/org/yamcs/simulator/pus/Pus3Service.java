package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.yamcs.simulator.DHSHandler;
import org.yamcs.simulator.EpsLvpduHandler;
import org.yamcs.simulator.FlightDataHandler;
import org.yamcs.simulator.PowerHandler;
import org.yamcs.simulator.RCSHandler;

/**
 * ST[03] Housekeeping simulator service.
 *
 * Manages HK and diagnostic parameter report structures, handles all TC[3,1–44] subtypes,
 * and emits TM[3,10/12/25/26/35/36/41] responses.
 *
 * Pre-registers five predefined HK structures (IDs 0–4) matching the existing
 * FlightData/Power/DHS/RCS/EPS handlers, all enabled by default at their original rates.
 *
 * Wire format for struct_id and param_id fields: uint32 (4 bytes), consistent with
 * the existing /PUS/hkid definition in pus.xml and the landing.xml XTCE containers.
 */
public class Pus3Service extends AbstractPusService {

    // Pus3-specific completion error codes (start at 5 to avoid clash with base class)
    static final int COMPL_ERR_STRUCT_ALREADY_EXISTS = 5;
    static final int COMPL_ERR_STRUCT_NOT_FOUND = 6;
    static final int COMPL_ERR_STRUCT_ENABLED = 7;
    static final int COMPL_ERR_PFRD_ALREADY_EXISTS = 8;
    static final int COMPL_ERR_PFRD_NOT_FOUND = 9;

    private final Map<Integer, HkStructure> hkStructures = new LinkedHashMap<>();
    private final Map<Integer, HkStructure> diagStructures = new LinkedHashMap<>();
    private final Map<Integer, List<PfrdEntry>> pfrdRegistry = new LinkedHashMap<>();

    @FunctionalInterface
    interface DataFiller {
        void fill(ByteBuffer bb);
    }

    static class HkStructure {
        final int structId;
        long collectionIntervalMs;
        boolean periodicEnabled;
        final DataFiller filler;
        final int fillerDataSizeBytes;
        List<Integer> simplyParams = new ArrayList<>();
        List<SuperCommSet> superCommSets = new ArrayList<>();
        ScheduledFuture<?> periodicTask;

        HkStructure(int id, long intervalMs, boolean enabled, DataFiller filler, int fillerBytes) {
            this.structId = id;
            this.collectionIntervalMs = intervalMs;
            this.periodicEnabled = enabled;
            this.filler = filler;
            this.fillerDataSizeBytes = fillerBytes;
        }

        HkStructure(int id, long intervalMs) {
            this(id, intervalMs, false, null, 0);
        }
    }

    static class SuperCommSet {
        int repetitionNumber;
        List<Integer> paramIds = new ArrayList<>();

        SuperCommSet(int rep) {
            this.repetitionNumber = rep;
        }
    }

    static class PfrdEntry {
        int reportNature;
        int reportDefId;
        int periodicStatus;
        long collectionIntervalMs;

        PfrdEntry(int nature, int defId, int status, long intervalMs) {
            this.reportNature = nature;
            this.reportDefId = defId;
            this.periodicStatus = status;
            this.collectionIntervalMs = intervalMs;
        }
    }

    Pus3Service(PusSimulator sim,
            FlightDataHandler flight,
            PowerHandler power,
            DHSHandler dhs,
            RCSHandler rcs,
            EpsLvpduHandler eps) {
        super(sim, 3);
        hkStructures.put(0, new HkStructure(0, 200, true,
                bb -> flight.fillPacket(bb.slice()), flight.dataSize()));
        hkStructures.put(1, new HkStructure(1, 1000, true,
                bb -> power.fillPacket(bb.slice()), power.dataSize()));
        hkStructures.put(2, new HkStructure(2, 1000, true,
                bb -> dhs.fillPacket(bb.slice()), dhs.dataSize()));
        hkStructures.put(3, new HkStructure(3, 1000, true,
                bb -> rcs.fillPacket(bb.slice()), rcs.dataSize()));
        hkStructures.put(4, new HkStructure(4, 1000, true,
                bb -> eps.fillPacket(bb.slice()), eps.dataSize()));
    }

    @Override
    public synchronized void start() {
        for (HkStructure s : hkStructures.values()) {
            if (s.periodicEnabled) schedule(s, 25);
        }
        for (HkStructure s : diagStructures.values()) {
            if (s.periodicEnabled) schedule(s, 26);
        }
    }

    private void schedule(HkStructure s, int subtype) {
        s.periodicTask = pusSimulator.executor.scheduleAtFixedRate(
                () -> sendHkReport(s, subtype),
                0, s.collectionIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void cancel(HkStructure s) {
        if (s.periodicTask != null) {
            s.periodicTask.cancel(false);
            s.periodicTask = null;
        }
    }

    private void sendHkReport(HkStructure s, int subtype) {
        int paramDataSize;
        if (s.filler != null) {
            paramDataSize = s.fillerDataSizeBytes;
        } else {
            paramDataSize = s.simplyParams.size() * 4;
            for (SuperCommSet sc : s.superCommSets) {
                paramDataSize += sc.paramIds.size() * 4 * sc.repetitionNumber;
            }
        }
        PusTmPacket pkt = newPacket(subtype, 4 + paramDataSize);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.putInt(s.structId);
        if (s.filler != null) {
            s.filler.fill(bb);
        } else {
            for (int i = 0; i < paramDataSize; i++) bb.put((byte) 0);
        }
        pusSimulator.transmitRealtimeTM(pkt);
    }

    @Override
    public synchronized void executeTc(PusTcPacket tc) {
        switch (tc.getSubtype()) {
        case 1 -> handleCreateStructure(tc, false);
        case 2 -> handleCreateStructure(tc, true);
        case 3 -> handleDeleteStructures(tc, false);
        case 4 -> handleDeleteStructures(tc, true);
        case 5 -> handleSetPeriodicGen(tc, false, true);
        case 6 -> handleSetPeriodicGen(tc, false, false);
        case 7 -> handleSetPeriodicGen(tc, true, true);
        case 8 -> handleSetPeriodicGen(tc, true, false);
        case 9 -> handleReportStructures(tc, false);
        case 11 -> handleReportStructures(tc, true);
        case 27 -> handleOneShot(tc, false);
        case 28 -> handleOneShot(tc, true);
        case 29 -> handleAppendParams(tc, false);
        case 30 -> handleAppendParams(tc, true);
        case 31 -> handleModifyInterval(tc, false);
        case 32 -> handleModifyInterval(tc, true);
        case 33 -> handleReportGenProps(tc, false);
        case 34 -> handleReportGenProps(tc, true);
        case 37 -> handleApplyPfrc(tc);
        case 38 -> handleCreatePfrd(tc);
        case 39 -> handleDeletePfrds(tc);
        case 40 -> handleReportPfrds(tc);
        case 42 -> handleAddPfrdEntries(tc);
        case 43 -> handleRemovePfrdEntries(tc);
        case 44 -> handleModifyPfrdGenProps(tc);
        default -> nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
        }
    }

    // TC[3,1/2] — create HK or diagnostic structure
    private void handleCreateStructure(PusTcPacket tc, boolean isDiag) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int structId = bb.getInt();
        long intervalMs = bb.getInt() & 0xFFFFFFFFL;
        Map<Integer, HkStructure> registry = isDiag ? diagStructures : hkStructures;
        if (registry.containsKey(structId)) {
            nack_completion(tc, COMPL_ERR_STRUCT_ALREADY_EXISTS);
            return;
        }
        HkStructure s = new HkStructure(structId, intervalMs);
        int n1 = bb.get() & 0xFF;
        for (int i = 0; i < n1; i++) s.simplyParams.add(bb.getInt());
        int nfa = bb.get() & 0xFF;
        for (int f = 0; f < nfa; f++) {
            SuperCommSet sc = new SuperCommSet(bb.get() & 0xFF);
            int n2 = bb.get() & 0xFF;
            for (int j = 0; j < n2; j++) sc.paramIds.add(bb.getInt());
            s.superCommSets.add(sc);
        }
        registry.put(structId, s);
        log.info("Created {} structure id={} interval={}ms n1={} nfa={}",
                isDiag ? "diag" : "HK", structId, intervalMs, n1, nfa);
        ack_completion(tc);
    }

    // TC[3,3/4] — delete HK or diagnostic structures
    private void handleDeleteStructures(PusTcPacket tc, boolean isDiag) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        Map<Integer, HkStructure> registry = isDiag ? diagStructures : hkStructures;
        boolean anyFailure = false;
        for (int i = 0; i < n; i++) {
            int structId = bb.getInt();
            HkStructure s = registry.get(structId);
            if (s == null) {
                log.info("{} structure id={} not found", isDiag ? "Diag" : "HK", structId);
                nack_completion(tc, COMPL_ERR_STRUCT_NOT_FOUND);
                anyFailure = true;
                continue;
            }
            cancel(s);
            registry.remove(structId);
            log.info("Deleted {} structure id={}", isDiag ? "diag" : "HK", structId);
        }
        if (!anyFailure) ack_completion(tc);
    }

    // TC[3,5/6/7/8] — enable or disable periodic generation
    private void handleSetPeriodicGen(PusTcPacket tc, boolean isDiag, boolean enable) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        Map<Integer, HkStructure> registry = isDiag ? diagStructures : hkStructures;
        int subtype = isDiag ? 26 : 25;
        boolean anyFailure = false;
        for (int i = 0; i < n; i++) {
            int structId = bb.getInt();
            HkStructure s = registry.get(structId);
            if (s == null) {
                log.info("{} structure id={} not found", isDiag ? "Diag" : "HK", structId);
                nack_completion(tc, COMPL_ERR_STRUCT_NOT_FOUND);
                anyFailure = true;
                continue;
            }
            if (enable && !s.periodicEnabled) {
                s.periodicEnabled = true;
                schedule(s, subtype);
                log.info("Enabled periodic gen for {} struct {}", isDiag ? "diag" : "HK", structId);
            } else if (!enable && s.periodicEnabled) {
                s.periodicEnabled = false;
                cancel(s);
                log.info("Disabled periodic gen for {} struct {}", isDiag ? "diag" : "HK", structId);
            }
        }
        if (!anyFailure) ack_completion(tc);
    }

    // TC[3,9/11] — report structure definitions → TM[3,10/12]
    private void handleReportStructures(PusTcPacket tc, boolean isDiag) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        Map<Integer, HkStructure> registry = isDiag ? diagStructures : hkStructures;
        int tmSubtype = isDiag ? 12 : 10;
        boolean anyFailure = false;
        for (int i = 0; i < n; i++) {
            int structId = bb.getInt();
            HkStructure s = registry.get(structId);
            if (s == null) {
                log.info("{} structure id={} not found", isDiag ? "Diag" : "HK", structId);
                nack_completion(tc, COMPL_ERR_STRUCT_NOT_FOUND);
                anyFailure = true;
                continue;
            }
            sendStructureReport(s, tmSubtype);
        }
        if (!anyFailure) ack_completion(tc);
    }

    private void sendStructureReport(HkStructure s, int subtype) {
        // struct_id(4) + gen_status(1) + interval(4) + N1(1) + N1×param_id(4) + NFA(1) + NFA×[rep(1)+N2(1)+N2×param_id(4)]
        int n1 = s.simplyParams.size();
        int nfa = s.superCommSets.size();
        int scSize = 0;
        for (SuperCommSet sc : s.superCommSets) scSize += 2 + sc.paramIds.size() * 4;
        int payloadSize = 4 + 1 + 4 + 1 + n1 * 4 + 1 + scSize;
        PusTmPacket pkt = newPacket(subtype, payloadSize);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.putInt(s.structId);
        bb.put((byte) (s.periodicEnabled ? 1 : 0));
        bb.putInt((int) s.collectionIntervalMs);
        bb.put((byte) n1);
        for (int p : s.simplyParams) bb.putInt(p);
        bb.put((byte) nfa);
        for (SuperCommSet sc : s.superCommSets) {
            bb.put((byte) sc.repetitionNumber);
            bb.put((byte) sc.paramIds.size());
            for (int p : sc.paramIds) bb.putInt(p);
        }
        pusSimulator.transmitRealtimeTM(pkt);
    }

    // TC[3,27/28] — generate one-shot HK or diagnostic report
    private void handleOneShot(PusTcPacket tc, boolean isDiag) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        Map<Integer, HkStructure> registry = isDiag ? diagStructures : hkStructures;
        int subtype = isDiag ? 26 : 25;
        boolean anyFailure = false;
        for (int i = 0; i < n; i++) {
            int structId = bb.getInt();
            HkStructure s = registry.get(structId);
            if (s == null) {
                log.info("{} structure id={} not found", isDiag ? "Diag" : "HK", structId);
                nack_completion(tc, COMPL_ERR_STRUCT_NOT_FOUND);
                anyFailure = true;
                continue;
            }
            sendHkReport(s, subtype);
        }
        if (!anyFailure) ack_completion(tc);
    }

    // TC[3,29/30] — append parameters to a HK or diagnostic structure
    private void handleAppendParams(PusTcPacket tc, boolean isDiag) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int structId = bb.getInt();
        Map<Integer, HkStructure> registry = isDiag ? diagStructures : hkStructures;
        HkStructure s = registry.get(structId);
        if (s == null) {
            nack_completion(tc, COMPL_ERR_STRUCT_NOT_FOUND);
            return;
        }
        if (s.periodicEnabled) {
            nack_completion(tc, COMPL_ERR_STRUCT_ENABLED);
            return;
        }
        int n1 = bb.get() & 0xFF;
        for (int i = 0; i < n1; i++) s.simplyParams.add(bb.getInt());
        int nfa = bb.get() & 0xFF;
        for (int f = 0; f < nfa; f++) {
            SuperCommSet sc = new SuperCommSet(bb.get() & 0xFF);
            int n2 = bb.get() & 0xFF;
            for (int j = 0; j < n2; j++) sc.paramIds.add(bb.getInt());
            s.superCommSets.add(sc);
        }
        log.info("Appended {} simply-comm params and {} sc-sets to {} struct {}",
                n1, nfa, isDiag ? "diag" : "HK", structId);
        ack_completion(tc);
    }

    // TC[3,31/32] — modify collection interval
    private void handleModifyInterval(PusTcPacket tc, boolean isDiag) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        Map<Integer, HkStructure> registry = isDiag ? diagStructures : hkStructures;
        int subtype = isDiag ? 26 : 25;
        boolean anyFailure = false;
        for (int i = 0; i < n; i++) {
            int structId = bb.getInt();
            long newIntervalMs = bb.getInt() & 0xFFFFFFFFL;
            HkStructure s = registry.get(structId);
            if (s == null) {
                log.info("{} structure id={} not found", isDiag ? "Diag" : "HK", structId);
                nack_completion(tc, COMPL_ERR_STRUCT_NOT_FOUND);
                anyFailure = true;
                continue;
            }
            boolean wasEnabled = s.periodicEnabled;
            if (wasEnabled) cancel(s);
            s.collectionIntervalMs = newIntervalMs;
            if (wasEnabled) schedule(s, subtype);
            log.info("Modified collection interval of {} struct {} to {}ms",
                    isDiag ? "diag" : "HK", structId, newIntervalMs);
        }
        if (!anyFailure) ack_completion(tc);
    }

    // TC[3,33/34] — report periodic generation properties → TM[3,35/36]
    private void handleReportGenProps(PusTcPacket tc, boolean isDiag) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        Map<Integer, HkStructure> registry = isDiag ? diagStructures : hkStructures;
        int tmSubtype = isDiag ? 36 : 35;
        List<HkStructure> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int structId = bb.getInt();
            HkStructure s = registry.get(structId);
            if (s == null) {
                log.info("{} structure id={} not found", isDiag ? "Diag" : "HK", structId);
                nack_completion(tc, COMPL_ERR_STRUCT_NOT_FOUND);
                return;
            }
            entries.add(s);
        }
        // TM[3,35/36]: N(1) | [struct_id(4) + gen_status(1) + interval(4)] × N
        PusTmPacket pkt = newPacket(tmSubtype, 1 + entries.size() * 9);
        ByteBuffer out = pkt.getUserDataBuffer();
        out.put((byte) entries.size());
        for (HkStructure s : entries) {
            out.putInt(s.structId);
            out.put((byte) (s.periodicEnabled ? 1 : 0));
            out.putInt((int) s.collectionIntervalMs);
        }
        pusSimulator.transmitRealtimeTM(pkt);
        ack_completion(tc);
    }

    // TC[3,37] — apply parameter functional reporting configurations
    private void handleApplyPfrc(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int execFlag = bb.get() & 0xFF;
        if (execFlag == 1) {
            for (HkStructure s : hkStructures.values()) {
                if (s.periodicEnabled) { s.periodicEnabled = false; cancel(s); }
            }
            for (HkStructure s : diagStructures.values()) {
                if (s.periodicEnabled) { s.periodicEnabled = false; cancel(s); }
            }
            log.info("PFRC exclusive: disabled all HK and diag structures");
        }
        int n = bb.get() & 0xFF;
        for (int i = 0; i < n; i++) {
            int pfrdId = bb.getInt();
            List<PfrdEntry> pfrd = pfrdRegistry.get(pfrdId);
            if (pfrd == null) {
                log.warn("PFRD id={} not found when applying PFRC", pfrdId);
                continue;
            }
            for (PfrdEntry e : pfrd) {
                Map<Integer, HkStructure> registry = (e.reportNature == 0) ? hkStructures : diagStructures;
                int subtype = (e.reportNature == 0) ? 25 : 26;
                HkStructure s = registry.get(e.reportDefId);
                if (s == null) continue;
                boolean wasEnabled = s.periodicEnabled;
                if (wasEnabled) cancel(s);
                s.collectionIntervalMs = e.collectionIntervalMs;
                s.periodicEnabled = (e.periodicStatus == 1);
                if (s.periodicEnabled) schedule(s, subtype);
            }
        }
        ack_completion(tc);
    }

    // TC[3,38] — create parameter functional reporting definition
    private void handleCreatePfrd(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int pfrdId = bb.getInt();
        if (pfrdRegistry.containsKey(pfrdId)) {
            nack_completion(tc, COMPL_ERR_PFRD_ALREADY_EXISTS);
            return;
        }
        pfrdRegistry.put(pfrdId, parsePfrdEntries(bb));
        log.info("Created PFRD id={}", pfrdId);
        ack_completion(tc);
    }

    // TC[3,39] — delete parameter functional reporting definitions
    private void handleDeletePfrds(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        boolean anyFailure = false;
        for (int i = 0; i < n; i++) {
            int pfrdId = bb.getInt();
            if (!pfrdRegistry.containsKey(pfrdId)) {
                log.info("PFRD id={} not found", pfrdId);
                nack_completion(tc, COMPL_ERR_PFRD_NOT_FOUND);
                anyFailure = true;
                continue;
            }
            pfrdRegistry.remove(pfrdId);
            log.info("Deleted PFRD id={}", pfrdId);
        }
        if (!anyFailure) ack_completion(tc);
    }

    // TC[3,40] — report parameter functional reporting definitions → TM[3,41]
    private void handleReportPfrds(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        boolean anyFailure = false;
        for (int i = 0; i < n; i++) {
            int pfrdId = bb.getInt();
            List<PfrdEntry> pfrd = pfrdRegistry.get(pfrdId);
            if (pfrd == null) {
                log.info("PFRD id={} not found", pfrdId);
                nack_completion(tc, COMPL_ERR_PFRD_NOT_FOUND);
                anyFailure = true;
                continue;
            }
            sendPfrdReport(pfrdId, pfrd);
        }
        if (!anyFailure) ack_completion(tc);
    }

    private void sendPfrdReport(int pfrdId, List<PfrdEntry> entries) {
        // TM[3,41]: pfrd_id(4) + N(1) + N × [nature(1) + def_id(4) + status(1) + interval(4)]
        PusTmPacket pkt = newPacket(41, 4 + 1 + entries.size() * 10);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.putInt(pfrdId);
        bb.put((byte) entries.size());
        for (PfrdEntry e : entries) {
            bb.put((byte) e.reportNature);
            bb.putInt(e.reportDefId);
            bb.put((byte) e.periodicStatus);
            bb.putInt((int) e.collectionIntervalMs);
        }
        pusSimulator.transmitRealtimeTM(pkt);
    }

    // TC[3,42] — add parameter report definitions to a PFRD
    private void handleAddPfrdEntries(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int pfrdId = bb.getInt();
        List<PfrdEntry> pfrd = pfrdRegistry.get(pfrdId);
        if (pfrd == null) {
            nack_completion(tc, COMPL_ERR_PFRD_NOT_FOUND);
            return;
        }
        pfrd.addAll(parsePfrdEntries(bb));
        log.info("Added entries to PFRD id={}", pfrdId);
        ack_completion(tc);
    }

    // TC[3,43] — remove parameter report definitions from a PFRD
    private void handleRemovePfrdEntries(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int pfrdId = bb.getInt();
        List<PfrdEntry> pfrd = pfrdRegistry.get(pfrdId);
        if (pfrd == null) {
            nack_completion(tc, COMPL_ERR_PFRD_NOT_FOUND);
            return;
        }
        int n = bb.get() & 0xFF;
        for (int i = 0; i < n; i++) {
            int nature = bb.get() & 0xFF;
            int defId = bb.getInt();
            pfrd.removeIf(e -> e.reportNature == nature && e.reportDefId == defId);
        }
        log.info("Removed entries from PFRD id={}", pfrdId);
        ack_completion(tc);
    }

    // TC[3,44] — modify periodic generation properties of report defs in a PFRD
    private void handleModifyPfrdGenProps(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int pfrdId = bb.getInt();
        List<PfrdEntry> pfrd = pfrdRegistry.get(pfrdId);
        if (pfrd == null) {
            nack_completion(tc, COMPL_ERR_PFRD_NOT_FOUND);
            return;
        }
        int n = bb.get() & 0xFF;
        for (int i = 0; i < n; i++) {
            int nature = bb.get() & 0xFF;
            int defId = bb.getInt();
            int status = bb.get() & 0xFF;
            long intervalMs = bb.getInt() & 0xFFFFFFFFL;
            for (PfrdEntry e : pfrd) {
                if (e.reportNature == nature && e.reportDefId == defId) {
                    e.periodicStatus = status;
                    e.collectionIntervalMs = intervalMs;
                }
            }
        }
        log.info("Modified gen props in PFRD id={}", pfrdId);
        ack_completion(tc);
    }

    // Parse N entries of the form: nature(1) + def_id(4) + status(1) + interval(4)
    private List<PfrdEntry> parsePfrdEntries(ByteBuffer bb) {
        int n = bb.get() & 0xFF;
        List<PfrdEntry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int nature = bb.get() & 0xFF;
            int defId = bb.getInt();
            int status = bb.get() & 0xFF;
            long intervalMs = bb.getInt() & 0xFFFFFFFFL;
            entries.add(new PfrdEntry(nature, defId, status, intervalMs));
        }
        return entries;
    }
}
