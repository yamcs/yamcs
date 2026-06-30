package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * ST[02] Device Access.
 * <p>
 * Distributes commands to / acquires data from on-board physical and logical devices. The
 * service operates at a low level, bypassing nominal on-board software functions. In a real
 * spacecraft the on-board software maps the requests to the applicable bus protocol
 * (MIL-STD-1553B, SpaceWire, I2C, CAN, ...); this sample simply validates the requests against
 * a small device registry, "executes" them, and (for the acquire subtypes) emits the
 * corresponding data report.
 * <p>
 * Supported subtypes:
 * <ul>
 * <li>TC[2,7] - distribute physical device commands</li>
 * <li>TC[2,8] - acquire data from physical devices -&gt; TM[2,9]</li>
 * <li>TM[2,9] - physical device data report</li>
 * <li>TC[2,10] - distribute logical device commands</li>
 * <li>TC[2,11] - acquire data from logical devices -&gt; TM[2,12]</li>
 * <li>TM[2,12] - logical device data report</li>
 * </ul>
 * <p>
 * The wire layout follows the mission conventions documented in {@code mdb/pus2.xml}:
 * protocol_specific_data is a fixed 4 bytes {direction, sub_address, word_count}, command_data
 * is a fixed 8 bytes, command_args / parameter_value are fixed uint32, and the TM[2,9] data
 * block is a fixed 4 x uint16.
 */
public class Pus2Service extends AbstractPusService {

    // start failure codes (match the labels added to /PUS/start-failure-code)
    static final int START_ERR_UNKNOWN_PHYSICAL_DEVICE = 10;
    static final int START_ERR_UNKNOWN_LOGICAL_DEVICE = 11;
    static final int START_ERR_UNKNOWN_DEVICE_COMMAND = 12;
    static final int START_ERR_UNKNOWN_DEVICE_PARAMETER = 13;

    static final int RETURN_CODE_SUCCESS = 0;

    // Sample device registry. A real mission would derive this from the device-access subservice
    // specification.
    private final Set<Integer> physicalDevices = new HashSet<>(Set.of(1, 2));

    // logical_device_id -> set of valid command_ids
    private final Map<Integer, Set<Integer>> logicalCommands = new HashMap<>(Map.of(
            1, new HashSet<>(Set.of(1, 2)), // device 1 (IMU): SET_RATE, SET_MODE
            2, new HashSet<>(Set.of(1)))); // device 2 (GPS): SET_MODE

    // (logical_device_id, parameter_id) -> last acquired value. Pre-seeded with sample values.
    private final Map<Long, Long> logicalParameters = new HashMap<>(Map.of(
            paramKey(1, 1), 100L, // IMU angular rate
            paramKey(1, 2), 25L, // IMU temperature
            paramKey(2, 1), 4200L)); // GPS position fix count

    Pus2Service(PusSimulator pusSimulator) {
        super(pusSimulator, 2);
    }

    @Override
    public synchronized void executeTc(PusTcPacket tc) {
        switch (tc.getSubtype()) {
        case 7 -> distributePhysicalCommands(tc);
        case 8 -> acquirePhysicalData(tc);
        case 10 -> distributeLogicalCommands(tc);
        case 11 -> acquireLogicalData(tc);
        default -> nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
        }
    }

    // TC[2,7] distribute physical device commands
    private void distributePhysicalCommands(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;

        // First pass: validate every instruction before executing any (spec §6.2.7.1.2).
        int[] deviceIds = new int[n];
        byte[][] commandData = new byte[n][];
        for (int i = 0; i < n; i++) {
            deviceIds[i] = bb.get() & 0xFF;
            ProtocolData pd = readProtocolData(bb);
            byte[] cmd = new byte[8];
            bb.get(cmd);
            commandData[i] = cmd;
            if (!physicalDevices.contains(deviceIds[i])) {
                log.info("Unknown physical device {}, rejecting TC[2,7]", deviceIds[i]);
                nack_start(tc, START_ERR_UNKNOWN_PHYSICAL_DEVICE);
                return;
            }
            log.debug("TC[2,7] instruction {}: device={}, protocol={}", i, deviceIds[i], pd);
        }

        ack_start(tc);
        for (int i = 0; i < n; i++) {
            log.info("Distributing command to physical device {}", deviceIds[i]);
        }
        ack_completion(tc);
    }

    // TC[2,8] acquire data from physical devices -> one TM[2,9] per instruction
    private void acquirePhysicalData(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;

        int[] transactionIds = new int[n];
        int[] deviceIds = new int[n];
        for (int i = 0; i < n; i++) {
            transactionIds[i] = bb.getShort() & 0xFFFF;
            deviceIds[i] = bb.get() & 0xFF;
            readProtocolData(bb);
            if (!physicalDevices.contains(deviceIds[i])) {
                log.info("Unknown physical device {}, rejecting TC[2,8]", deviceIds[i]);
                nack_start(tc, START_ERR_UNKNOWN_PHYSICAL_DEVICE);
                return;
            }
        }

        ack_start(tc);
        for (int i = 0; i < n; i++) {
            sendPhysicalDataReport(transactionIds[i], deviceIds[i]);
        }
        ack_completion(tc);
    }

    // TM[2,9] physical device data report (transaction_id + return_code + 4 x uint16 data block)
    private void sendPhysicalDataReport(int transactionId, int deviceId) {
        PusTmPacket pkt = newPacket(9, 2 + 1 + 4 * 2);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.putShort((short) transactionId);
        bb.put((byte) RETURN_CODE_SUCCESS);
        // sample acquired data words derived from the device id
        for (int w = 0; w < 4; w++) {
            bb.putShort((short) (deviceId * 1000 + w));
        }
        log.info("Sending TM[2,9] physical data report for transaction {} (device {})", transactionId, deviceId);
        pusSimulator.transmitRealtimeTM(pkt);
    }

    // TC[2,10] distribute logical device commands
    private void distributeLogicalCommands(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;

        int[] deviceIds = new int[n];
        int[] commandIds = new int[n];
        long[] commandArgs = new long[n];
        for (int i = 0; i < n; i++) {
            deviceIds[i] = bb.get() & 0xFF;
            commandIds[i] = bb.get() & 0xFF;
            commandArgs[i] = bb.getInt() & 0xFFFFFFFFL;
            Set<Integer> commands = logicalCommands.get(deviceIds[i]);
            if (commands == null) {
                log.info("Unknown logical device {}, rejecting TC[2,10]", deviceIds[i]);
                nack_start(tc, START_ERR_UNKNOWN_LOGICAL_DEVICE);
                return;
            }
            if (!commands.contains(commandIds[i])) {
                log.info("Unknown command {} for logical device {}, rejecting TC[2,10]", commandIds[i], deviceIds[i]);
                nack_start(tc, START_ERR_UNKNOWN_DEVICE_COMMAND);
                return;
            }
        }

        ack_start(tc);
        for (int i = 0; i < n; i++) {
            log.info("Logical device {} command {} args={}", deviceIds[i], commandIds[i], commandArgs[i]);
        }
        ack_completion(tc);
    }

    // TC[2,11] acquire data from logical devices -> one TM[2,12] per instruction
    private void acquireLogicalData(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;

        int[] transactionIds = new int[n];
        int[] deviceIds = new int[n];
        int[] parameterIds = new int[n];
        for (int i = 0; i < n; i++) {
            transactionIds[i] = bb.getShort() & 0xFFFF;
            deviceIds[i] = bb.get() & 0xFF;
            parameterIds[i] = bb.get() & 0xFF;
            if (!logicalCommands.containsKey(deviceIds[i])) {
                log.info("Unknown logical device {}, rejecting TC[2,11]", deviceIds[i]);
                nack_start(tc, START_ERR_UNKNOWN_LOGICAL_DEVICE);
                return;
            }
            if (!logicalParameters.containsKey(paramKey(deviceIds[i], parameterIds[i]))) {
                log.info("Unknown parameter {} for logical device {}, rejecting TC[2,11]", parameterIds[i],
                        deviceIds[i]);
                nack_start(tc, START_ERR_UNKNOWN_DEVICE_PARAMETER);
                return;
            }
        }

        ack_start(tc);
        for (int i = 0; i < n; i++) {
            sendLogicalDataReport(transactionIds[i], deviceIds[i], parameterIds[i]);
        }
        ack_completion(tc);
    }

    // TM[2,12] logical device data report (transaction_id + return_code + uint32 value)
    private void sendLogicalDataReport(int transactionId, int deviceId, int parameterId) {
        long value = logicalParameters.getOrDefault(paramKey(deviceId, parameterId), 0L);
        PusTmPacket pkt = newPacket(12, 2 + 1 + 4);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.putShort((short) transactionId);
        bb.put((byte) RETURN_CODE_SUCCESS);
        bb.putInt((int) value);
        log.info("Sending TM[2,12] logical data report for transaction {} (device {}, param {}, value {})",
                transactionId, deviceId, parameterId, value);
        pusSimulator.transmitRealtimeTM(pkt);
    }

    private static ProtocolData readProtocolData(ByteBuffer bb) {
        int direction = bb.get() & 0xFF;
        int subAddress = bb.get() & 0xFF;
        int wordCount = bb.getShort() & 0xFFFF;
        return new ProtocolData(direction, subAddress, wordCount);
    }

    private static long paramKey(int deviceId, int parameterId) {
        return ((long) deviceId << 32) | (parameterId & 0xFFFFFFFFL);
    }

    private record ProtocolData(int direction, int subAddress, int wordCount) {
    }
}
