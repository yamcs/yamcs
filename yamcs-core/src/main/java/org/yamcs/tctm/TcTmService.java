package org.yamcs.tctm;

import java.util.List;

import org.yamcs.TmPacketProvider;
import org.yamcs.commanding.CommandReleaser;
import org.yamcs.parameter.ParameterProvider;

import com.google.common.util.concurrent.Service;

public interface TcTmService extends Service {
    public TmPacketProvider getTmPacketProvider();
    public CommandReleaser getCommandReleaser();
    public List<ParameterProvider> getParameterProviders();
    boolean isSynchronous();
}
