package org.yamcs.tctm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yamcs.YProcessor;
import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
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
public class StreamPpProvider extends AbstractService implements StreamSubscriber, ParameterProvider {
    Stream stream;
    PpListener ppListener;
    ParameterRequestManager paraListener;
    final XtceDb xtceDb;
    
    public StreamPpProvider(String archiveInstance, Map<String, String> config) throws ConfigurationException {
        YarchDatabase ydb=YarchDatabase.getInstance(archiveInstance);
        
        if(!config.containsKey("stream")) {
        	throw new ConfigurationException("the config(args) for YarchPpProvider has to contain a parameter 'stream' - stream name for retrieving parameters from");
        }
        String streamName = config.get("stream");
        
        stream=ydb.getStream(streamName);
        if(stream==null) throw new ConfigurationException("Cannot find a stream named "+streamName);
        xtceDb=XtceDbFactory.getInstance(archiveInstance);
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
            Parameter ppdef=xtceDb.getParameter(name);
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
    public void setParameterListener(ParameterRequestManager paraListener) {
        this.paraListener=paraListener;
    }

    @Override
    public void stopProviding(Parameter paramDef) {
     
    }
    
    @Override
    public boolean canProvide(NamedObjectId id) {
        if(xtceDb.getParameter(id)!=null) return true;
        else return false;
    }
    
    @Override
    public boolean canProvide(Parameter p) {
        return xtceDb.getParameter(p.getQualifiedName())!=null;
    }
    
    @Override
    public Parameter getParameter(NamedObjectId id) throws InvalidIdentification {
        Parameter p=xtceDb.getParameter(id);
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



	@Override
	public void init(YProcessor channel) {
		//nothing to be done here
	}
}
