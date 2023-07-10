package org.yamcs.yfe;

import org.yamcs.tctm.AbstractTcDataLink;

import io.netty.buffer.ByteBuf;

public class TcLink extends AbstractTcDataLink {
    YfeLink parentLink;

    @Override
    protected Status connectionStatus() {
        return parentLink.connectionStatus();
    }

    @Override
    protected void doStart() {
    }

    @Override
    protected void doStop() {
    }

}
