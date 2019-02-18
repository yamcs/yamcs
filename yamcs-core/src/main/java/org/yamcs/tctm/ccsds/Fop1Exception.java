package org.yamcs.tctm.ccsds;

import org.yamcs.tctm.ccsds.Cop1Monitor.AlertType;

public class Fop1Exception extends Exception {
    AlertType alert;
    public Fop1Exception(String msg) {
        super(msg);
    }

    public Fop1Exception(AlertType alert) {
        super(alert.toString());
        this.alert = alert;
    }
}
