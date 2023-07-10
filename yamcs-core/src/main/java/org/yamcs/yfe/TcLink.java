package org.yamcs.yfe;

import org.yamcs.tctm.AbstractTcDataLink;
import org.yamcs.tctm.AggregatedDataLink;

public class TcLink extends AbstractTcDataLink {
    YfeLink parentLink;

    public TcLink(YfeLink yfeLink) {
        this.parentLink = yfeLink;
    }
    @Override
    protected Status connectionStatus() {
        return parentLink.connectionStatus();
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }

    @Override
    public AggregatedDataLink getParent() {
        return parentLink;
    }

}
