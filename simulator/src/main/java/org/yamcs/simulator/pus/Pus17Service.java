package org.yamcs.simulator.pus;


public class Pus17Service extends AbstractPusService {
    Pus17Service(PusSimulator pusSimulator) {
        super(pusSimulator, 17);
    }

    @Override
    public void executeTc(PusTcPacket tc) {

        if (tc.getSubtype() != 1) {
            log.info("invalid subtype {}, sending NACK start", tc.getSubtype());
            nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
            return;
        }

        ack_start(tc);
        pusSimulator.transmitRealtimeTM(newPacket(2, 0));
        ack_completion(tc);
    }

}
