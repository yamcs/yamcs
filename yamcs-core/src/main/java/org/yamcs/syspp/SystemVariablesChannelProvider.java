package org.yamcs.syspp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.Channel;
import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.ParameterListener;
import org.yamcs.ParameterProvider;
import org.yamcs.ParameterRequestManager;
import org.yamcs.ParameterValue;

import com.google.common.util.concurrent.AbstractService;

import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

/**
 * Provides system variables from the sys_var stream to ParameterRequestManager
 * 
 * For the moment all variables starting with "/YAMCS" are accepted and nothing else.
 * Aliases are not supported.
 * 
 */
public class SystemVariablesChannelProvider extends AbstractService implements StreamSubscriber, ParameterProvider {
    private ParameterListener parameterListener;
    
    volatile private Map<String, SystemVariable> variables = new HashMap<String, SystemVariable>(); 
    
    Stream stream;
    
    
    public SystemVariablesChannelProvider(ParameterRequestManager parameterRequestManager, Channel channel) throws ConfigurationException {
        String instance = channel.getInstance();
        YarchDatabase ydb=YarchDatabase.getInstance(instance);
        stream=ydb.getStream(SystemVariablesCollector.STREAM_NAME);
        if(stream==null) throw new ConfigurationException("Cannot find a stream named "+SystemVariablesCollector.STREAM_NAME);
    }

    @Override
    public void startProviding(Parameter paramDef) {
        //TOOD
    }

    @Override
    public void startProvidingAll() {
      //TOOD
    }

    @Override
    public void stopProviding(Parameter paramDef) {
      //TODO
    }
    
    @Override
    public void onTuple(Stream s, Tuple tuple) {//the definition of the tuple is in PpProviderAdapter
        List<ParameterValue> params=new ArrayList<ParameterValue>();
        for(int i=4;i<tuple.size();i++) {
            org.yamcs.protobuf.Pvalue.ParameterValue gpv=(org.yamcs.protobuf.Pvalue.ParameterValue)tuple.getColumn(i);
            String name=tuple.getColumnDefinition(i).getName();
            SystemVariable sv = variables.get(name);
            if(sv==null) {
                sv = createVariableForName(name);
            } 
            
            ParameterValue pv=ParameterValue.fromGpb(sv, gpv);
            params.add(pv);
        }
    //    System.out.println("-------------- updating parameters "+params);
        parameterListener.update(params);
    }

    @Override
    public void streamClosed(Stream s) {
       notifyStopped();
    }

    
    private synchronized SystemVariable createVariableForName(String fqname) {
        SystemVariable sv = variables.get(fqname);
        if(sv==null) {
            sv = new SystemVariable(fqname);
            variables.put(fqname, sv);
        }
        
        return sv;
    }
    /**
     * return true if parameter starts with "/YAMCS" or the namespace is null and the name starts with "/YAMCS" 
     * 
     */
    @Override
    public boolean canProvide(NamedObjectId paraId) {
        
        if(!paraId.hasNamespace()) {
            return paraId.getName().startsWith(SystemVariablesCollector.YAMCS_NAMESPACE);
        } else {
            return paraId.getNamespace().startsWith(SystemVariablesCollector.YAMCS_NAMESPACE);
        }
    }

    @Override
    public Parameter getParameter(NamedObjectId paraId)  throws InvalidIdentification {
        String name = paraId.getName();
        if(paraId.hasNamespace())  {
            name = paraId.getNamespace()+"/"+name;
        }
        
        SystemVariable sv = variables.get(name);
        if(sv==null) {
            sv = createVariableForName(name);
        }
        return sv;
    }


    @Override
    public void setParameterListener(ParameterListener parameterRequestManager) {
        this.parameterListener = parameterRequestManager; 

    }

    @Override
    public String getDetailedStatus() {
        return null;
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
}

/**
 * let's not make a big fuss about it: store both the definition and the value
 * in the same object such that we create the values once at the beginning and
 * not when they are subscribed and we can just refer to them by name
 * 
 * @author nm
 * 
 */
class SystemVariable extends Parameter {

    public SystemVariable(String name) {
        super(name);
    }

    @Override
    public String toString() {
        return "SysVar(name=" + getName() + ")";
    }
}