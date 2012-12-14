package org.yamcs.tctm;

import org.yamcs.ParameterProvider;

import com.google.common.util.concurrent.Service;

public interface TcTmService extends Service {
    public TmPacketProvider getTmPacketProvider();
    public TcUplinker getTcUplinker();
    public ParameterProvider getParameterProvider();
}
