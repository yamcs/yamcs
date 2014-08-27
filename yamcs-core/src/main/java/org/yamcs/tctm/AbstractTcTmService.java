package org.yamcs.tctm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.yamcs.ParameterProvider;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeedType;

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
    public List<ParameterProvider> getParameterProviders() {
        if(pp!=null) {
            return Arrays.asList(pp);
        } else {
            return new ArrayList<ParameterProvider>();
        }
    }
    
    @Override
    protected void doStart() {
        tm.start();
        notifyStarted();
    }

    @Override
    protected void doStop() {
        tm.stop();
        notifyStopped();
    }
    
    @Override
    public boolean isSynchronous() {
        boolean s = false;
        if(tm instanceof ArchiveTmPacketProvider) {
            ReplaySpeed speed=((ArchiveTmPacketProvider)tm).getSpeed();
            if(speed.getType()==ReplaySpeedType.AFAP) s = true;
        }
        return s;
    }
}
