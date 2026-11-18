package org.yamcs.simulator.pus;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ST[20] on-board parameter management (ECSS-E-ST-70-41C, 6.20 and 8.20).
 * <p>
 * Supports TC[20,1] report parameter values (-> TM[20,2]) and TC[20,3] set parameter values. Messages relating to
 * parameter definitions (TC[20,4], TC[20,5], TC[20,6], TM[20,7]) are out of scope.
 * <p>
 * On-board parameters are fixed; their IDs and types are defined in the simulator MDB (e.g. for the PUS example, in
 * examples/pus/src/main/yamcs/mdb/pus20.xml). The value size of the parameter is "deduced" from the parameter ID, the
 * values are encoded big endian.
 * <p>
 * Faulty instructions:
 * <ul>
 * <li>an instruction referring to an unknown parameter ID generates a failed start of execution (TM[1,4]) with the
 * unknown parameter ID as failure data; the valid instructions of the request are processed nevertheless. For TC[20,
 * 3], the size of the value of an unknown parameter (i.e. not in MDB)
 * cannot be deduced, so the instructions following the unknown parameter cannot be parsed and are ignored;</li>
 * <li>a truncated request is rejected as a whole.</li>
 * </ul>
 */
public class Pus20Service extends AbstractPusService {

    // parameter id -> parameter; keep in sync with MDB
    final Map<Integer, OnboardParameter> parameters = new LinkedHashMap<>();

    Pus20Service(PusSimulator pusSimulator) {
        super(pusSimulator, 20);
        addOnboardParam(1, "HkReportInterval", Type.UINT16, 1000); // ms
        addOnboardParam(2, "BatteryHeaterSetpoint", Type.FLOAT32, Float.floatToIntBits(5.0f) & 0xFFFFFFFFL); // degC
        addOnboardParam(3, "AdcsMode", Type.UINT8, 2); // enumeration: 0 SAFE, 1 DETUMBLE, 2 SUN_POINTING, 3
        // NADIR_POINTING
        addOnboardParam(4, "TmDownlinkRate", Type.UINT32, 64000); // bit/s
        addOnboardParam(5, "AdcsControllerGain", Type.FLOAT64, Double.doubleToLongBits(1.25));
        addOnboardParam(6, "MagnetometerBiasX", Type.INT16, -10); // nT
        addOnboardParam(7, "PayloadPowerEnabled", Type.UINT8, 1); // boolean: 0 DISABLED, 1 ENABLED
    }

    // the application data, without the 2 bytes CRC at the end of the packet
    private static ByteBuffer applicationData(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        bb.limit(Math.max(0, bb.limit() - 2));
        return bb;
    }

    private static String describe(OnboardParameter p) {
        return switch (p.type) {
            case FLOAT32 -> Float.toString(Float.intBitsToFloat((int) p.raw));
            case FLOAT64 -> Double.toString(Double.longBitsToDouble(p.raw));
            default -> Long.toString(p.raw);
        };
    }

    private void addOnboardParam(int id, String name, Type type, long raw) {
        parameters.put(id, new OnboardParameter(id, name, type, raw));
    }

    @Override
    public synchronized void executeTc(PusTcPacket tc) {
        switch (tc.getSubtype()) {
            // TC[20,1] report parameter values -> TM[20,2]
            case 1 -> reportValues(tc);
            // TC[20,3] set parameter values
            case 3 -> setValues(tc);
            default -> nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
        }
    }

    // TC[20,1]: N x parameter ID
    private void reportValues(PusTcPacket tc) {
        List<OnboardParameter> valid = new ArrayList<>();
        boolean unknown = false;
        try {
            ByteBuffer bb = applicationData(tc);
            // Get the number of parameters
            int n = bb.get() & 0xFF;
            // For each parameter ID, retrieve the actual parameter.
            // If a parameter is unknown, we can still continue and at least report the known ones.
            for (int i = 0; i < n; i++) {
                int id = bb.get() & 0xFF;
                var p = parameters.get(id);
                if (p == null) {
                    log.warn("TC[20,1]: unknown parameter {}", id);
                    nack_start(tc, START_ERR_UNKNOWN_PARAMETER, new byte[]{(byte) id});
                    unknown = true;
                } else {
                    valid.add(p);
                }
            }
        } catch (BufferUnderflowException e) {
            log.warn("Rejecting TC[20,1]: truncated request");
            nack_start(tc, START_ERR_INVALID_REQUEST);
            return;
        }
        if (valid.isEmpty() && unknown) {
            return;
        }

        ack_start(tc);

        // Create the response -- the ID and value for each requested parameter
        int size = 1;
        for (var p : valid) {
            size += 1 + p.type.size;
        }
        var pkt = newPacket(2, size);
        var out = pkt.getUserDataBuffer();
        out.put((byte) valid.size());
        for (var p : valid) {
            out.put((byte) p.id);
            p.put(out);
        }
        pusSimulator.transmitRealtimeTM(pkt);

        ack_completion(tc);
    }

    // TC[20,3]: N x (parameter ID, value)
    private void setValues(PusTcPacket tc) {
        Map<OnboardParameter, Long> newValues = new LinkedHashMap<>();
        boolean unknown = false;
        try {
            ByteBuffer bb = applicationData(tc);

            // Get the number of parameters
            int n = bb.get() & 0xFF;
            // For each parameter ID, get the actual parameter and prepare to set it to the new value.
            // If the parameter is unknown, we do not know the size of the value. Hence, we cannot set the remaining
            // parameters, because we do not know where in the packet the next parameter starts.
            for (int i = 0; i < n; i++) {
                int id = bb.get() & 0xFF;
                var p = parameters.get(id);
                if (p == null) {
                    // the size of the value cannot be deduced, the rest of the request cannot be parsed
                    log.warn("TC[20,3]: unknown parameter {}, ignoring the remaining instructions", id);
                    nack_start(tc, START_ERR_UNKNOWN_PARAMETER, new byte[]{(byte) id});
                    unknown = true;
                    break;
                }
                newValues.put(p, OnboardParameter.read(p.type, bb));
            }
        } catch (BufferUnderflowException e) {
            log.warn("Rejecting TC[20,3]: truncated request");
            nack_start(tc, START_ERR_INVALID_REQUEST);
            return;
        }
        if (newValues.isEmpty() && unknown) {
            return;
        }

        ack_start(tc);
        newValues.forEach((p, v) -> {
            p.raw = v;
            log.info("Set parameter {} ({}) to {}", p.id, p.name, describe(p));
        });
        ack_completion(tc);
    }

    /**
     * @return the raw value (as stored) of the parameter; for testing
     */
    synchronized long getRawValue(int id) {
        return parameters.get(id).raw;
    }

    public enum Type {
        UINT8(1), UINT16(2), INT16(2), UINT32(4), FLOAT32(4), FLOAT64(8);

        final int size;

        Type(int size) {
            this.size = size;
        }
    }

    /**
     * The on-board parameter value, stored as raw bits (right aligned in a long)
     */
    static class OnboardParameter {
        final int id;
        final String name;
        final Type type;
        long raw;

        OnboardParameter(int id, String name, Type type, long raw) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.raw = raw;
        }

        static long read(Type type, ByteBuffer bb) {
            return switch (type) {
                case UINT8 -> bb.get() & 0xFFL;
                case UINT16 -> bb.getShort() & 0xFFFFL;
                case INT16 -> bb.getShort();
                case UINT32, FLOAT32 -> bb.getInt() & 0xFFFFFFFFL;
                case FLOAT64 -> bb.getLong();
            };
        }

        void put(ByteBuffer bb) {
            switch (type) {
                case UINT8 -> bb.put((byte) raw);
                case UINT16, INT16 -> bb.putShort((short) raw);
                case UINT32, FLOAT32 -> bb.putInt((int) raw);
                case FLOAT64 -> bb.putLong(raw);
            }
        }
    }
}