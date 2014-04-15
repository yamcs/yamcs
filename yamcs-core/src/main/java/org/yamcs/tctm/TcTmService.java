package org.yamcs.tctm;

import java.util.List;

import org.yamcs.ParameterProvider;

import com.google.common.util.concurrent.Service;

public interface TcTmService extends Service {
    public TmPacketProvider getTmPacketProvider();
    public TcUplinker getTcUplinker();
    public List<ParameterProvider> getParameterProviders();
}
