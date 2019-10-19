package org.yamcs.tctm.ccsds;

import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;

/**
 * Sends events and websocket messages for FOP1 state changes and alerts
 * 
 * @author nm
 *
 */
public class Cop1MonitorImpl implements Cop1Monitor {
    EventProducer eventProducer;
    static final String[] FOP1_STATE = new String[] { "",
            "1(Active)",
            "2(Retransmit without wait)",
            "3(Retransmit with wait)",
            "4(Initialising without BC Frame)",
            "5(Initialising with BC Frame)",
            "6(Initial)"
    };

    public Cop1MonitorImpl(String yamcsInstance, String linkName) {
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, "FOP1_" + linkName, 10000);
    }

    @Override
    public void suspended(int suspendState) {
        eventProducer.sendWarning("SUSPENDED", "FOP1 operation suspended due to timeout");
    }

    @Override
    public void alert(AlertType alert) {
        eventProducer.sendWarning("ALERT", alert.msg);
    }

    @Override
    public void stateChanged(int newState) {
        eventProducer.sendInfo("STATE_CHANGE", FOP1_STATE[newState]);
    }

}
