package org.yamcs.simulator.pus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractPusService {
    static final int START_ERR_INVALID_PUS_SUBTYPE = 1;
    static final int START_ERR_NOT_IMPLEMENTED = 2;

    static final int COMPL_ERR_NOT_IMPLEMENTED = 2;
    static final int COMPL_ERR_INVALID_EVENT_ID = 3;
    static final int COMPL_ERR_SCHEDULE_TIME_IN_THE_PAST = 4;
    static final int COMPL_ERR_INVALID_PACKET_DATA = 5;

    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    protected final PusSimulator pusSimulator;
    protected final int pusType;

    public AbstractPusService(PusSimulator pusSimulator, int pusType) {
        this.pusSimulator = pusSimulator;
        this.pusType = pusType;
    }

    public void start() {
    }

    public abstract void executeTc(PusTcPacket tc);

    public PusTmPacket newPacket(int subtype, int userDataLength) {
        return new PusTmPacket(pusSimulator.getMainApid(), userDataLength, pusType, subtype);
    }

    public void ack_start(PusTcPacket tc) {
        pusSimulator.transmitRealtimeTM(pusSimulator.ack(tc, 3));
    }

    public void nack_start(PusTcPacket tc, int code) {
        pusSimulator.transmitRealtimeTM(pusSimulator.nack(tc, 4, code));
    }

    public void ack_completion(PusTcPacket tc) {
        pusSimulator.transmitRealtimeTM(pusSimulator.ack(tc, 7));
    }

    public void nack_completion(PusTcPacket tc, int code) {
        pusSimulator.transmitRealtimeTM(pusSimulator.nack(tc, 8, code));
    }
}
