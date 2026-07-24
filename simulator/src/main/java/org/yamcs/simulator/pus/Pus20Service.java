package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ST[20] On-Board Parameter Management.
 * <p>
 * Maintains a flat on-board parameter store (param_id -&gt; value) and lets the ground read
 * (TC[20,1] -&gt; TM[20,2]) and write (TC[20,3]) parameter values. The PUS standard marks the
 * value field as "deduced" (its type/size depends on param_id); this sample fixes a mission
 * convention instead: every on-board parameter value is a uint32 (see pus_analysis/pus20.md).
 * <p>
 * Per spec (Sarafare 6.20.4.1f/g), a request that mixes valid and invalid param_ids is not
 * rejected wholesale: valid entries are still processed, invalid ones are skipped (and logged).
 * <p>
 * Supported subtypes:
 * <ul>
 * <li>TC[20,1] - report parameter values -&gt; TM[20,2]</li>
 * <li>TM[20,2] - parameter value report</li>
 * <li>TC[20,3] - set parameter values</li>
 * </ul>
 */
public class Pus20Service extends AbstractPusService {

    // Sample on-board parameter store, pre-seeded with a couple of demo values.
    private final Map<Integer, Long> paramStore = new LinkedHashMap<>(Map.of(
            0x1001, 42L, // voltage_level (mV)
            0x1002, 3000L)); // temperature_raw

    Pus20Service(PusSimulator pusSimulator) {
        super(pusSimulator, 20);
    }

    @Override
    public synchronized void executeTc(PusTcPacket tc) {
        switch (tc.getSubtype()) {
        case 1 -> reportParameterValues(tc);
        case 3 -> setParameterValues(tc);
        default -> nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
        }
    }

    // TC[20,1] report parameter values -> TM[20,2]
    private void reportParameterValues(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;

        ack_start(tc);
        Map<Integer, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int paramId = bb.getShort() & 0xFFFF;
            Long value = paramStore.get(paramId);
            if (value == null) {
                log.warn("TC[20,1] unknown param_id 0x{}, skipping", Integer.toHexString(paramId));
                continue;
            }
            result.put(paramId, value);
        }
        sendParameterValueReport(result);
        ack_completion(tc);
    }

    // TM[20,2] parameter value report
    private void sendParameterValueReport(Map<Integer, Long> entries) {
        int n = entries.size();
        PusTmPacket pkt = newPacket(2, 1 + n * 6);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.put((byte) n);
        for (Map.Entry<Integer, Long> e : entries.entrySet()) {
            bb.putShort((short) (int) e.getKey());
            bb.putInt((int) (long) e.getValue());
        }
        log.info("Sending TM[20,2] parameter value report for {} parameter(s)", n);
        pusSimulator.transmitRealtimeTM(pkt);
    }

    // TC[20,3] set parameter values
    private void setParameterValues(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;

        ack_start(tc);
        for (int i = 0; i < n; i++) {
            int paramId = bb.getShort() & 0xFFFF;
            long value = bb.getInt() & 0xFFFFFFFFL;
            if (!paramStore.containsKey(paramId)) {
                log.warn("TC[20,3] unknown param_id 0x{}, skipping", Integer.toHexString(paramId));
                continue;
            }
            paramStore.put(paramId, value);
            log.info("TC[20,3] set param_id 0x{} -> {}", Integer.toHexString(paramId), value);
        }
        ack_completion(tc);
    }
}
