package org.yamcs.simulator.pus;

import org.yamcs.utils.BitBuffer;

public class Pus17Service extends AbstractPusService {
    Pus17Service(PusSimulator pusSimulator) {
        super(pusSimulator, 17);
    }

    @Override
    public void executeTc(PusTcPacket tc) {
        switch (tc.getSubtype()) {
        case 1 -> areYouAlive(tc);
        case 3 -> onBoardConnectionTest(tc);
        default -> {
            log.info("invalid subtype {}, sending NACK start", tc.getSubtype());
            nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
        }
        }
    }

    private void areYouAlive(PusTcPacket tc) {
        ack_start(tc);
        pusSimulator.transmitRealtimeTM(newPacket(2, 0));
        ack_completion(tc);
    }

    private void onBoardConnectionTest(PusTcPacket tc) {
        ack_start(tc);
        BitBuffer in = PusPackedFields.bitBuffer(tc.getUserDataBuffer());
        int targetApid = PusPackedFields.readUnsigned(in, PusPackedFields.APID_BITS);

        PusTmPacket report = newPacket(4, PusPackedFields.bytesForBits(PusPackedFields.APID_BITS));
        BitBuffer out = PusPackedFields.bitBuffer(report.getUserDataBuffer());
        out.putBits(targetApid, PusPackedFields.APID_BITS);
        pusSimulator.transmitRealtimeTM(report);
        ack_completion(tc);
    }
}
