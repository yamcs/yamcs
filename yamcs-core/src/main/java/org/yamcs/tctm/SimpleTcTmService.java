package org.yamcs.tctm;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.TmPacketProvider;
import org.yamcs.commanding.CommandReleaser;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;

import com.google.common.util.concurrent.AbstractService;

public class SimpleTcTmService extends AbstractService implements TcTmService {
    TmPacketProvider tm;
    CommandReleaser tc;
    List<ParameterProvider> ppList = new ArrayList<ParameterProvider>();
    
    public SimpleTcTmService(TmPacketProvider tm, List<ParameterProvider> ppList, CommandReleaser tc) {
        this.tm=tm;
        this.ppList=ppList;
        this.tc=tc;
    }
    @Override
    public TmPacketProvider getTmPacketProvider() {
       return tm;
    }
    @Override
    public CommandReleaser getCommandReleaser() {
        return tc;
    }
    
    @Override
    public List<ParameterProvider> getParameterProviders() {
    	return ppList;
    }
    @Override
    protected void doStart() {
        if(tm!=null) tm.startAsync();
        for(ParameterProvider pp:ppList) {
        	pp.startAsync();
        }
        notifyStarted();
    }
    @Override
    protected void doStop() {
        if(tm!=null) tm.stopAsync();
        for(ParameterProvider pp:ppList) {
        	pp.stopAsync();
        }
        notifyStarted();
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
