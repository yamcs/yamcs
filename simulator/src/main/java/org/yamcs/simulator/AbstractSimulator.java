package org.yamcs.simulator;

import org.yamcs.cfdp.pdu.CfdpPacket;

import com.google.common.util.concurrent.AbstractService;
import org.yamcs.simulator.cfdp.CfdpSender;

public abstract class AbstractSimulator extends AbstractService {
    protected abstract void processTc(SimulatorCcsdsPacket tc);

    protected abstract void setTmLink(TcpTmTcLink tmLink);

    protected abstract void setTm2Link(TcpTmTcLink tm2Link);

    protected abstract void setLosLink(TcpTmTcLink losLink);

    /**
     * Set the TM frame link. Simulators that support sending their telemetry as CCSDS transfer frames override this and
     * feed {@link UdpTmFrameLink#queuePacket}. The default is a no-op (packet-only simulator).
     */
    public void setTmFrameLink(UdpTmFrameLink tmFrameLink) {
        // no frame support by default
    }

    public abstract void transmitCfdp(CfdpPacket packet);

    public void setCfdpSender(CfdpSender cfdpSender) {
        throw new UnsupportedOperationException("CfdpSender is not settable for " + this);
    }

    public abstract int maxTmDataSize();

}
