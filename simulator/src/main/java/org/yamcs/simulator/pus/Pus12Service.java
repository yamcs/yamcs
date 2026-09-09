package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * ST[12] On-board Monitoring simulator service — parameter monitoring (PMON) subservice only,
 * functional monitoring (FMON) is not implemented (not required, see pus_analysis/pus12.md).
 *
 * Periodically samples a small set of self-contained synthetic monitored parameters, evaluates
 * them against limit / expected-value / delta checks, and raises PUS-5 events via
 * {@link Pus5Service#raiseEvent} on checking-status violations.
 */
public class Pus12Service extends AbstractPusService {

    static final int COMPL_ERR_PMON_ID_UNKNOWN = 5;
    static final int COMPL_ERR_PMON_ID_ALREADY_EXISTS = 6;
    static final int COMPL_ERR_PMON_STILL_ENABLED = 7;
    static final int COMPL_ERR_CHECK_TYPE_MISMATCH = 8;
    static final int COMPL_ERR_PARAM_ID_MISMATCH = 9;

    static final int MASTER_TICK_MS = 100;

    /** Table 8-6 raw check_type values. */
    enum CheckType {
        EXPECTED_VALUE, LIMIT, DELTA
    }

    /** Table 8-10 raw PMON status values. */
    enum PmonStatus {
        DISABLED, ENABLED
    }

    /**
     * Ordinals reused across Tables 8-7/8-8/8-9 (numerically identical across check types):
     * 0=good, 1=unchecked, 2=invalid (unused, no validity condition implemented), 3=low/unexpected,
     * 4=high (never produced by EXPECTED_VALUE).
     */
    enum PmonCheckingStatus {
        GOOD, UNCHECKED, INVALID, LOW, HIGH
    }

    static class PmonDefinition {
        final int pmonId;
        int monitoredParamId;
        final CheckType checkType;

        int repetitionNumber;
        int repetitionCounter;
        PmonCheckingStatus pendingStatus = PmonCheckingStatus.UNCHECKED;

        int monitoringIntervalMs;
        long accumulatedIntervalMs;

        PmonStatus status = PmonStatus.DISABLED;
        PmonCheckingStatus checkingStatus = PmonCheckingStatus.UNCHECKED;

        // LIMIT
        float lowLimit, highLimit;
        // DELTA
        float lowThreshold, highThreshold;
        int numConsecutiveDeltas;
        Float lastSampledValue;
        final Deque<Float> deltaHistory = new ArrayDeque<>();
        // shared by LIMIT and DELTA
        int lowEventId, highEventId;
        // EXPECTED_VALUE
        int mask, expectedValue, eventId;

        PmonDefinition(int pmonId, int monitoredParamId, CheckType checkType) {
            this.pmonId = pmonId;
            this.monitoredParamId = monitoredParamId;
            this.checkType = checkType;
        }
    }

    record CheckTransition(int pmonId, PmonCheckingStatus prev, PmonCheckingStatus next) {
    }

    final Map<Integer, PmonDefinition> pmonList = new LinkedHashMap<>();
    final List<CheckTransition> checkTransitionList = new ArrayList<>();

    // Monitored-parameter value sources, keyed by monitored_param_id. Synthetic and self-contained
    // (not wired to CSV-playback handlers) so PMON checks reliably cross their thresholds for a demo,
    // independent of whatever HK data ships. Each supplier's returned Number must match what its
    // PMON definitions' checkType expects (float for LIMIT/DELTA, int for EXPECTED_VALUE).
    final Map<Integer, Supplier<Number>> paramSource = new LinkedHashMap<>();

    private final Random random = new Random();
    private long tickCount;
    private float sineTempCached;
    private float randWalkCurrentCached = 4.5f;
    private float busVoltageCached = 28.0f;
    private int busVoltageSpikeCounter;
    private int modeRegisterCached = 1;
    private int modeRegisterCycleCounter;

    Pus12Service(PusSimulator pusSimulator) {
        super(pusSimulator, 12);

        paramSource.put(1, () -> (Number) sineTempCached);
        paramSource.put(2, () -> (Number) randWalkCurrentCached);
        paramSource.put(3, () -> (Number) busVoltageCached);
        paramSource.put(4, () -> (Number) modeRegisterCached);

        addSeedDefinitions();
    }

    /** Pre-registered, enabled definitions so the demo shows transitions/events with zero manual TCs. */
    private void addSeedDefinitions() {
        PmonDefinition d1 = new PmonDefinition(1, 1, CheckType.LIMIT);
        d1.lowLimit = -10f;
        d1.highLimit = 50f;
        d1.lowEventId = 10;
        d1.highEventId = 11;
        d1.repetitionNumber = 3;
        d1.monitoringIntervalMs = 1000;
        d1.status = PmonStatus.ENABLED;
        pmonList.put(d1.pmonId, d1);

        PmonDefinition d2 = new PmonDefinition(2, 2, CheckType.LIMIT);
        d2.lowLimit = 1.0f;
        d2.highLimit = 8.0f;
        d2.lowEventId = 12;
        d2.highEventId = 13;
        d2.repetitionNumber = 2;
        d2.monitoringIntervalMs = 500;
        d2.status = PmonStatus.ENABLED;
        pmonList.put(d2.pmonId, d2);

        PmonDefinition d3 = new PmonDefinition(3, 3, CheckType.DELTA);
        d3.lowThreshold = 0.05f;
        d3.highThreshold = 1.0f;
        d3.lowEventId = 0; // no event configured for the "below low threshold" direction
        d3.highEventId = 14;
        d3.numConsecutiveDeltas = 3;
        d3.repetitionNumber = 3;
        d3.monitoringIntervalMs = 1000;
        d3.status = PmonStatus.ENABLED;
        pmonList.put(d3.pmonId, d3);

        PmonDefinition d4 = new PmonDefinition(4, 4, CheckType.EXPECTED_VALUE);
        d4.mask = 0xFF;
        d4.expectedValue = 1;
        d4.eventId = 15;
        d4.repetitionNumber = 2;
        d4.monitoringIntervalMs = 1000;
        d4.status = PmonStatus.ENABLED;
        pmonList.put(d4.pmonId, d4);
    }

    @Override
    public void start() {
        pusSimulator.executor.scheduleAtFixedRate(this::tick, 0, MASTER_TICK_MS, TimeUnit.MILLISECONDS);
    }

    private void tick() {
        advanceSignalGenerators();
        for (PmonDefinition def : pmonList.values()) {
            if (def.status != PmonStatus.ENABLED) {
                continue;
            }
            def.accumulatedIntervalMs += MASTER_TICK_MS;
            if (def.accumulatedIntervalMs < def.monitoringIntervalMs) {
                continue;
            }
            def.accumulatedIntervalMs = 0;
            evaluateOne(def);
        }
    }

    /** Advances every synthetic signal once per master tick, independent of any PMON's own interval. */
    private void advanceSignalGenerators() {
        tickCount++;
        double tSec = tickCount * MASTER_TICK_MS / 1000.0;

        // param 1: sine wave, periodically crosses both limits
        sineTempCached = (float) (20 + 35 * Math.sin(2 * Math.PI * tSec / 24.0));

        // param 2: mean-reverting random walk around 4.5, occasional excursions past 1.0/8.0
        randWalkCurrentCached += 0.15f * (4.5f - randWalkCurrentCached) + (float) (random.nextGaussian() * 0.8);

        // param 3: flat 28.0V with a scripted spike to 31.0V every ~15s, held for ~1.5s
        busVoltageSpikeCounter++;
        if (busVoltageSpikeCounter < 150) {
            busVoltageCached = 28.0f;
        } else if (busVoltageSpikeCounter < 165) {
            busVoltageCached = 31.0f;
        } else {
            busVoltageSpikeCounter = 0;
            busVoltageCached = 28.0f;
        }

        // param 4: mode register, mostly NOMINAL(1) with periodic SAFE(2) excursions
        modeRegisterCycleCounter++;
        modeRegisterCached = (modeRegisterCycleCounter % 10 < 8) ? 1 : 2;
    }

    private void evaluateOne(PmonDefinition def) {
        Number raw = paramSource.get(def.monitoredParamId).get();
        PmonCheckingStatus candidate = switch (def.checkType) {
        case LIMIT -> evaluateLimit(def, raw.floatValue());
        case EXPECTED_VALUE -> evaluateExpected(def, raw.intValue());
        case DELTA -> evaluateDelta(def, raw.floatValue());
        };
        applyResult(def, candidate, raw);
    }

    private PmonCheckingStatus evaluateLimit(PmonDefinition def, float v) {
        if (v < def.lowLimit) {
            return PmonCheckingStatus.LOW;
        }
        if (v > def.highLimit) {
            return PmonCheckingStatus.HIGH;
        }
        return PmonCheckingStatus.GOOD;
    }

    private PmonCheckingStatus evaluateExpected(PmonDefinition def, int v) {
        return ((v & def.mask) == def.expectedValue) ? PmonCheckingStatus.GOOD : PmonCheckingStatus.LOW;
    }

    private PmonCheckingStatus evaluateDelta(PmonDefinition def, float v) {
        if (def.lastSampledValue == null) {
            def.lastSampledValue = v;
            return PmonCheckingStatus.UNCHECKED;
        }
        float delta = Math.abs(v - def.lastSampledValue);
        def.lastSampledValue = v;
        def.deltaHistory.addLast(delta);
        while (def.deltaHistory.size() > def.numConsecutiveDeltas) {
            def.deltaHistory.removeFirst();
        }
        if (def.deltaHistory.size() < def.numConsecutiveDeltas) {
            return PmonCheckingStatus.UNCHECKED;
        }
        double avg = def.deltaHistory.stream().mapToDouble(Float::doubleValue).average().orElse(0);
        if (avg < def.lowThreshold) {
            return PmonCheckingStatus.LOW;
        }
        if (avg > def.highThreshold) {
            return PmonCheckingStatus.HIGH;
        }
        return PmonCheckingStatus.GOOD;
    }

    /** N-consecutive-consistent-results state machine (spec's "repetition number"). */
    private void applyResult(PmonDefinition def, PmonCheckingStatus candidate, Number raw) {
        if (candidate == def.pendingStatus) {
            def.repetitionCounter++;
        } else {
            def.pendingStatus = candidate;
            def.repetitionCounter = 1;
        }
        if (def.repetitionCounter >= def.repetitionNumber && candidate != def.checkingStatus) {
            PmonCheckingStatus prev = def.checkingStatus;
            def.checkingStatus = candidate;
            checkTransitionList.add(new CheckTransition(def.pmonId, prev, candidate));
            maybeRaiseEvent(def, candidate, raw);
        }
    }

    /** Fires only on transition into LOW/HIGH, never on recovery — matches the spec's event id fields. */
    private void maybeRaiseEvent(PmonDefinition def, PmonCheckingStatus candidate, Number raw) {
        int eventId = switch (def.checkType) {
        case LIMIT, DELTA -> candidate == PmonCheckingStatus.LOW ? def.lowEventId
                : candidate == PmonCheckingStatus.HIGH ? def.highEventId : 0;
        case EXPECTED_VALUE -> candidate == PmonCheckingStatus.LOW ? def.eventId : 0;
        };
        if (eventId == 0) {
            return;
        }
        byte[] payload;
        if (def.checkType == CheckType.EXPECTED_VALUE) {
            payload = ByteBuffer.allocate(3).putShort((short) def.pmonId).put((byte) raw.intValue()).array();
        } else {
            payload = ByteBuffer.allocate(6).putShort((short) def.pmonId).putFloat(raw.floatValue()).array();
        }
        pusSimulator.pus5Service.raiseEvent(eventId, 2, payload);
    }

    @Override
    public void executeTc(PusTcPacket tc) {
        switch (tc.getSubtype()) {
        case 1 -> {
            ack_start(tc);
            enableDisableList(tc, true);
        }
        case 2 -> {
            ack_start(tc);
            enableDisableList(tc, false);
        }
        case 5 -> {
            ack_start(tc);
            addDefinition(tc);
        }
        case 6 -> {
            ack_start(tc);
            deleteDefinitions(tc);
        }
        case 7 -> {
            ack_start(tc);
            modifyDefinition(tc);
        }
        case 8 -> {
            ack_start(tc);
            reportDefinitions(tc);
        }
        case 11 -> {
            ack_start(tc);
            sendCheckTransitionReport(tc);
        }
        case 13 -> {
            ack_start(tc);
            sendPmonStatusReport(tc);
        }
        default -> {
            log.warn("Unknown ST[12] subtype {}, sending NACK start", tc.getSubtype());
            nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
        }
        }
    }

    // TC[12,1]/TC[12,2] — N + array of pmon_id
    private void enableDisableList(PusTcPacket tc, boolean enable) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        boolean anyFailure = false;
        for (int i = 0; i < n; i++) {
            int pmonId = bb.getShort() & 0xFFFF;
            PmonDefinition def = pmonList.get(pmonId);
            if (def == null) {
                log.info("PMON id {} not found, sending NACK completion", pmonId);
                nack_completion(tc, COMPL_ERR_PMON_ID_UNKNOWN);
                anyFailure = true;
                continue; // spec: valid instructions are processed regardless of faulty ones
            }
            if (enable) {
                def.status = PmonStatus.ENABLED;
                def.repetitionCounter = 0;
                def.pendingStatus = PmonCheckingStatus.UNCHECKED;
            } else {
                def.status = PmonStatus.DISABLED;
                def.checkingStatus = PmonCheckingStatus.UNCHECKED;
            }
        }
        if (!anyFailure) {
            ack_completion(tc);
        }
    }

    // TC[12,5] — single-entry add: pmon_id, param_id, monitoring_interval_ms, rep_number, check_type, criteria.
    // Mission simplification vs. spec's repeated-N-entries: one TC adds one definition.
    private void addDefinition(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int pmonId = bb.getShort() & 0xFFFF;
        int paramId = bb.getShort() & 0xFFFF;
        int intervalMs = bb.getShort() & 0xFFFF;
        int repNumber = bb.get() & 0xFF;
        int checkTypeRaw = bb.get() & 0xFF;

        if (checkTypeRaw >= CheckType.values().length) {
            log.warn("invalid check_type {}, sending NACK completion", checkTypeRaw);
            nack_completion(tc, COMPL_ERR_NOT_IMPLEMENTED);
            return;
        }
        if (pmonList.containsKey(pmonId)) {
            log.info("PMON id {} already exists, sending NACK completion", pmonId);
            nack_completion(tc, COMPL_ERR_PMON_ID_ALREADY_EXISTS);
            return;
        }

        CheckType checkType = CheckType.values()[checkTypeRaw];
        PmonDefinition def = new PmonDefinition(pmonId, paramId, checkType);
        def.repetitionNumber = Math.max(1, repNumber);
        def.monitoringIntervalMs = Math.max(MASTER_TICK_MS, intervalMs);
        readCriteria(bb, def);

        pmonList.put(pmonId, def);
        ack_completion(tc);
    }

    // TC[12,6] — N + array of pmon_id; must be DISABLED to delete.
    private void deleteDefinitions(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        boolean anyFailure = false;
        for (int i = 0; i < n; i++) {
            int pmonId = bb.getShort() & 0xFFFF;
            PmonDefinition def = pmonList.get(pmonId);
            if (def == null) {
                nack_completion(tc, COMPL_ERR_PMON_ID_UNKNOWN);
                anyFailure = true;
                continue;
            }
            if (def.status == PmonStatus.ENABLED) {
                log.info("PMON id {} still enabled, sending NACK completion", pmonId);
                nack_completion(tc, COMPL_ERR_PMON_STILL_ENABLED);
                anyFailure = true;
                continue;
            }
            pmonList.remove(pmonId);
            checkTransitionList.removeIf(t -> t.pmonId() == pmonId);
        }
        if (!anyFailure) {
            ack_completion(tc);
        }
    }

    // TC[12,7] — single-entry modify: pmon_id, param_id, rep_number, check_type, criteria (no interval).
    // param_id/check_type are integrity checks against the existing definition, not mutations.
    private void modifyDefinition(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int pmonId = bb.getShort() & 0xFFFF;
        int paramId = bb.getShort() & 0xFFFF;
        int repNumber = bb.get() & 0xFF;
        int checkTypeRaw = bb.get() & 0xFF;

        PmonDefinition def = pmonList.get(pmonId);
        if (def == null) {
            nack_completion(tc, COMPL_ERR_PMON_ID_UNKNOWN);
            return;
        }
        if (checkTypeRaw >= CheckType.values().length || CheckType.values()[checkTypeRaw] != def.checkType) {
            log.info("PMON id {} check_type mismatch, sending NACK completion", pmonId);
            nack_completion(tc, COMPL_ERR_CHECK_TYPE_MISMATCH);
            return;
        }
        if (paramId != def.monitoredParamId) {
            log.info("PMON id {} param_id mismatch, sending NACK completion", pmonId);
            nack_completion(tc, COMPL_ERR_PARAM_ID_MISMATCH);
            return;
        }

        def.repetitionNumber = Math.max(1, repNumber);
        readCriteria(bb, def);

        def.checkingStatus = PmonCheckingStatus.UNCHECKED;
        def.pendingStatus = PmonCheckingStatus.UNCHECKED;
        def.repetitionCounter = 0;
        def.deltaHistory.clear();
        def.lastSampledValue = null;
        ack_completion(tc);
    }

    private void readCriteria(ByteBuffer bb, PmonDefinition def) {
        switch (def.checkType) {
        case LIMIT -> {
            def.lowLimit = bb.getFloat();
            def.lowEventId = bb.getShort() & 0xFFFF;
            def.highLimit = bb.getFloat();
            def.highEventId = bb.getShort() & 0xFFFF;
        }
        case EXPECTED_VALUE -> {
            def.mask = bb.get() & 0xFF;
            def.expectedValue = bb.get() & 0xFF;
            def.eventId = bb.getShort() & 0xFFFF;
        }
        case DELTA -> {
            def.lowThreshold = bb.getFloat();
            def.lowEventId = bb.getShort() & 0xFFFF;
            def.highThreshold = bb.getFloat();
            def.highEventId = bb.getShort() & 0xFFFF;
            def.numConsecutiveDeltas = Math.max(1, bb.get() & 0xFF);
        }
        }
    }

    // TC[12,8] -> TM[12,9]. N==0 means "report all".
    private void reportDefinitions(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        List<Integer> ids;
        if (n == 0) {
            ids = new ArrayList<>(pmonList.keySet());
        } else {
            ids = new ArrayList<>();
            boolean anyFailure = false;
            for (int i = 0; i < n; i++) {
                int pmonId = bb.getShort() & 0xFFFF;
                if (!pmonList.containsKey(pmonId)) {
                    nack_completion(tc, COMPL_ERR_PMON_ID_UNKNOWN);
                    anyFailure = true;
                    continue;
                }
                ids.add(pmonId);
            }
            if (anyFailure && ids.isEmpty()) {
                return;
            }
        }
        sendPmonDefinitionReport(ids);
        ack_completion(tc);
    }

    // TM[12,9] — each entry is now genuinely variable-length (no zero-padding): the XTCE side decodes
    // it via a nested PMON_ENTRY container whose criteria fields are gated by IncludeCondition on the
    // entry's own check_type (see pus12.xml), so the wire format here must match that field-for-field.
    private static int criteriaSize(CheckType checkType) {
        return switch (checkType) {
        case EXPECTED_VALUE -> 1 + 1 + 2; // mask + expected_value + event_id
        case LIMIT -> 4 + 2 + 4 + 2; // low_limit + low_event_id + high_limit + high_event_id
        case DELTA -> 4 + 2 + 4 + 2 + 1; // low_threshold + low_event_id + high_threshold + high_event_id + num_consecutive_deltas
        };
    }

    private void sendPmonDefinitionReport(List<Integer> ids) {
        int size = 1;
        for (int pmonId : ids) {
            size += 7 + criteriaSize(pmonList.get(pmonId).checkType);
        }
        PusTmPacket pkt = newPacket(9, size);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.put((byte) ids.size());
        for (int pmonId : ids) {
            PmonDefinition def = pmonList.get(pmonId);
            bb.putShort((short) def.pmonId);
            bb.putShort((short) def.monitoredParamId);
            bb.put((byte) def.status.ordinal());
            bb.put((byte) def.repetitionNumber);
            bb.put((byte) def.checkType.ordinal());
            switch (def.checkType) {
            case LIMIT -> {
                bb.putFloat(def.lowLimit);
                bb.putShort((short) def.lowEventId);
                bb.putFloat(def.highLimit);
                bb.putShort((short) def.highEventId);
            }
            case EXPECTED_VALUE -> {
                bb.put((byte) def.mask);
                bb.put((byte) def.expectedValue);
                bb.putShort((short) def.eventId);
            }
            case DELTA -> {
                bb.putFloat(def.lowThreshold);
                bb.putShort((short) def.lowEventId);
                bb.putFloat(def.highThreshold);
                bb.putShort((short) def.highEventId);
                bb.put((byte) def.numConsecutiveDeltas);
            }
            }
        }
        pusSimulator.transmitRealtimeTM(pkt);
    }

    // TC[12,11] -> TM[12,12]: flush accumulated check transitions.
    private void sendCheckTransitionReport(PusTcPacket tc) {
        PusTmPacket pkt = newPacket(12, 1 + checkTransitionList.size() * 4);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.put((byte) checkTransitionList.size());
        for (CheckTransition t : checkTransitionList) {
            bb.putShort((short) t.pmonId());
            bb.put((byte) t.prev().ordinal());
            bb.put((byte) t.next().ordinal());
        }
        checkTransitionList.clear();
        pusSimulator.transmitRealtimeTM(pkt);
        ack_completion(tc);
    }

    // TC[12,13] -> TM[12,14]: pmon_id + status for every definition.
    private void sendPmonStatusReport(PusTcPacket tc) {
        PusTmPacket pkt = newPacket(14, 1 + pmonList.size() * 3);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.put((byte) pmonList.size());
        for (PmonDefinition def : pmonList.values()) {
            bb.putShort((short) def.pmonId);
            bb.put((byte) def.status.ordinal());
        }
        pusSimulator.transmitRealtimeTM(pkt);
        ack_completion(tc);
    }
}
