package org.yamcs.simulator.pus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.yamcs.simulator.pus.PusSimulator.MAIN_APID;
import static org.yamcs.simulator.pus.PusSimulator.ack;
import static org.yamcs.simulator.pus.PusSimulator.nack;

public abstract class AbstractPusService {
    // start errors
    static final int START_ERR_INVALID_PUS_SUBTYPE = 1;
    static final int START_ERR_NOT_IMPLEMENTED = 2;

    // completion errors
    static final int COMPL_ERR_NOT_IMPLEMENTED = 2;
    static final int COMPL_ERR_INVALID_EVENT_ID = 3;
    static final int COMPL_ERR_SCHEDULE_TIME_IN_THE_PAST = 4;

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
        return new PusTmPacket(MAIN_APID, userDataLength, pusType, subtype);
    }

    public void ack_start(PusTcPacket tc) {
        pusSimulator.transmitRealtimeTM(ack(tc, 3));
    }

    public void nack_start(PusTcPacket tc, int code) {
        pusSimulator.transmitRealtimeTM(nack(tc, 4, code));
    }

    public void ack_completion(PusTcPacket tc) {
        pusSimulator.transmitRealtimeTM(ack(tc, 7));
    }

    public void nack_completion(PusTcPacket tc, int code) {
        pusSimulator.transmitRealtimeTM(nack(tc, 8, code));
    }
}
