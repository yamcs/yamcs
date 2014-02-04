package org.yamcs.tctm;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.ParameterListener;
import org.yamcs.ParameterProvider;
import org.yamcs.ParameterValue;
import org.yamcs.ProcessedParameterDefinition;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.ppdb.PpDbFactory;
import org.yamcs.ppdb.PpDefDb;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

import com.google.common.util.concurrent.AbstractService;

/**
 * Provides PPs from yarch streams (pp_realtime) to ParameterRequestManager.
 * 
 * @author nm
 *
 */
public class YarchPpProvider extends AbstractService implements StreamSubscriber, ParameterProvider {
    Stream stream;
    PpListener ppListener;
    ParameterListener paraListener;
    final PpDefDb ppdb;
    
    public YarchPpProvider(String archiveInstance, String streamName) throws ConfigurationException {
        YarchDatabase ydb=YarchDatabase.getInstance(archiveInstance);
        stream=ydb.getStream(streamName);
        if(stream==null) throw new ConfigurationException("Cannot find a stream named "+streamName);
        ppdb=PpDbFactory.getInstance(archiveInstance);
    }
    


    @Override
    public String getDetailedStatus() {
        return "receiving PPs from "+stream;
    }


    @Override
    protected void doStart() {
        stream.addSubscriber(this);
        notifyStarted();
    }

    @Override
    protected void doStop() {
        stream.removeSubscriber(this);
        notifyStopped();
    }

    @Override
    public void onTuple(Stream s, Tuple tuple) {//the definition of the tuple is in PpProviderAdapter
        List<ParameterValue> params=new ArrayList<ParameterValue>();
        for(int i=4;i<tuple.size();i++) {
            org.yamcs.protobuf.Pvalue.ParameterValue gpv=(org.yamcs.protobuf.Pvalue.ParameterValue)tuple.getColumn(i);
            String name=tuple.getColumnDefinition(i).getName();
            ProcessedParameterDefinition ppdef=ppdb.getProcessedParameter(name);
            if(ppdef==null) continue;
            ParameterValue pv=ParameterValue.fromGpb(ppdef, gpv);
            params.add(pv);
        }
        paraListener.update(params);
    }

    @Override
    public void streamClosed(Stream s) {
       notifyStopped();
    }


    @Override
    public void setParameterListener(ParameterListener paraListener) {
        this.paraListener=paraListener;
    }

    @Override
    public void stopProviding(Parameter paramDef) {
     
    }
    
    @Override
    public boolean canProvide(NamedObjectId id) {
        if(ppdb.getProcessedParameter(id)!=null) return true;
        else return false;
    }
    
    @Override
    public Parameter getParameter(NamedObjectId id) throws InvalidIdentification {
        Parameter p=ppdb.getProcessedParameter(id);
        if(p==null) throw new InvalidIdentification();
        else return p;
    }


    @Override
    public void startProviding(Parameter paramDef) {
        // TODO Auto-generated method stub
        
    }


    @Override
    public void startProvidingAll() {
        // TODO Auto-generated method stub
        
    }
}
