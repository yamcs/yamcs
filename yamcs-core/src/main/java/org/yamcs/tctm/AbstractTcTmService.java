package org.yamcs.tctm;

import org.yamcs.ParameterProvider;

import com.google.common.util.concurrent.AbstractService;

public abstract class AbstractTcTmService extends AbstractService implements TcTmService {
    protected TmPacketProvider tm;
    protected TcUplinker tc;
    protected ParameterProvider pp;

    @Override
    public TmPacketProvider getTmPacketProvider() {
        return tm;
    }

    @Override
    public TcUplinker getTcUplinker() {
        return tc;
    }

    @Override
    public ParameterProvider getParameterProvider() {
        return pp;
    }
}
