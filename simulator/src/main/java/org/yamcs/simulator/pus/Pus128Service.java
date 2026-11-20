package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;

import org.yamcs.utils.BitBuffer;

/** Stores and validates the simulator's two-line element set. */
final class Pus128Service extends AbstractPusService {
    private static final int LINE_LENGTH = 69;
    private static final int TLE_LENGTH = 2 * LINE_LENGTH;
    private static final int START_ERR_INVALID_TLE_LENGTH = 8;
    private static final int COMPL_ERR_TLE_NOT_SET = 6;

    private byte[] tle;

    Pus128Service(PusSimulator simulator) {
        super(simulator, 128);
    }

    @Override
    public void executeTc(PusTcPacket tc) {
        int subtype = tc.getSubtype();
        if (subtype != 1 && subtype != 2 && subtype != 4) {
            nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
            return;
        }

        ByteBuffer data = tc.getUserDataBuffer();
        if (data.remaining() != (subtype == 1 ? TLE_LENGTH : 0)) {
            nack_start(tc, START_ERR_INVALID_TLE_LENGTH);
            return;
        }

        ack_start(tc);
        if (subtype == 1) {
            tle = new byte[TLE_LENGTH];
            data.get(tle);
        } else if (tle == null) {
            nack_completion(tc, COMPL_ERR_TLE_NOT_SET);
            return;
        } else if (subtype == 2) {
            PusTmPacket report = newPacket(3, TLE_LENGTH);
            report.getUserDataBuffer().put(tle);
            pusSimulator.transmitRealtimeTM(report);
        } else {
            PusTmPacket report = newPacket(5, 3);
            BitBuffer fields = PusPackedFields.bitBuffer(report.getUserDataBuffer());
            writeChecksum(fields, 0);
            writeChecksum(fields, LINE_LENGTH);
            pusSimulator.transmitRealtimeTM(report);
        }
        ack_completion(tc);
    }

    private void writeChecksum(BitBuffer fields, int offset) {
        int checksum = 0;
        int checksumOffset = offset + LINE_LENGTH - 1;
        for (int i = offset; i < checksumOffset; i++) {
            byte value = tle[i];
            if (value >= '0' && value <= '9') {
                checksum += value - '0';
            } else if (value == '-') {
                checksum++;
            }
        }
        checksum %= 10;
        fields.putBits(checksum, 8);
        fields.putBits(tle[checksumOffset] == '0' + checksum ? 1 : 0, 1);
    }
}
