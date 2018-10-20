package org.yamcs.parameter;


import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.Processor;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.DeprecationInfo;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SystemParameter;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;

import com.google.common.util.concurrent.AbstractService;

/**
 * Provides system variables from the sys_var stream to ParameterRequestManager
 * 
 * It also provides some info about the current processor
 * 
 * For the moment all variables starting with "/yamcs" are accepted and nothing else.
 * Aliases are not supported.
 * 
 */
@Deprecated
@DeprecationInfo(info = "this class is not required anymore, the ParameterProvider can also provide parameters from the sys_var stream")
public class SystemParametersProvider extends AbstractService implements StreamSubscriber, ParameterProvider {
    
    public SystemParametersProvider(String yamcsInstance) throws ConfigurationException {
    }
    
    @Override
    public void init(Processor proc) throws ConfigurationException {
    }

    
    @Override
    public void startProviding(Parameter paramDef) {
        //TODO
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
    }

    @Override
    public void streamClosed(Stream s) {
        notifyStopped();
    }
   
    /**
     * return true if parameter starts with "/YAMCS" or the namespace is null and the name starts with "/YAMCS" 
     * 
     */
    @Override
    public boolean canProvide(NamedObjectId paraId) {
        return false;
    }

    @Override
    public boolean canProvide(Parameter para) {
        return false;
    }

    
    @Override
    public Parameter getParameter(NamedObjectId paraId)  throws InvalidIdentification {
        return null;
    }
    
    /**
     * Get (and possibly create) system parameter. 
     * The parameter may be in the XtceDB in case it is referred to by an algorithm. Otherwise is created on the fly and not added to XtceDB.
     * 
     * @param fqname - the fully qualified name of the parameter to be returned 
     * 
     * @return the system parameter having the fqname. 
     */
    public SystemParameter getSystemParameter(String fqname) {
      return null;
    }

    @Override
    public void setParameterListener(ParameterListener parameterRequestManager) {
    }


    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }
}
