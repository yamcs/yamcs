package org.yamcs.simulator;

import org.yamcs.cfdp.pdu.CfdpPacket;

import com.google.common.util.concurrent.AbstractService;

public abstract class AbstractSimulator extends AbstractService {
    static public final int CFDP_APID = 2045;
    
    protected abstract void processTc(SimulatorCcsdsPacket tc);

    protected abstract void setTmLink(TcpTmTcLink tmLink);

    protected abstract void setTm2Link(TcpTmTcLink tm2Link);

    protected abstract void setLosLink(TcpTmTcLink losLink);

    public abstract void transmitCfdp(CfdpPacket packet);

}
