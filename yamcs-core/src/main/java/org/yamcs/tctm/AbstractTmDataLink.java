package org.yamcs.tctm;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.utils.YObjectLoader;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

public abstract class AbstractTmDataLink extends AbstractExecutionThreadService
    implements TmPacketDataLink, SystemParametersProducer {
  
    String packetPreprocessorClassName;
    Object packetPreprocessorArgs;
    PacketPreprocessor packetPreprocessor;
    
    final Logger log = LoggerFactory.getLogger(this.getClass().getName());
    
    
    protected void initPreprocessor(String instance, Map<String, Object> args) {
        if(args!=null) {
            this.packetPreprocessorClassName = YConfiguration.getString(args, "packetPreprocessorClassName",
                    IssPacketPreprocessor.class.getName());
            this.packetPreprocessorArgs = args.get("packetPreprocessorArgs");
        } else {
            this.packetPreprocessorClassName = IssPacketPreprocessor.class.getName();
            this.packetPreprocessorArgs = null;
        }
        
        try {
            if (packetPreprocessorArgs != null) {
                packetPreprocessor = YObjectLoader.loadObject(packetPreprocessorClassName, instance, packetPreprocessorArgs);
            } else {
                packetPreprocessor = YObjectLoader.loadObject(packetPreprocessorClassName, instance);
            }
        } catch (ConfigurationException e) {
            log.error("Cannot instantiate the packetInput stream", e);
            throw e;
        } catch (IOException e) {
            log.error("Cannot instantiate the packetInput stream", e);
            throw new ConfigurationException(e);
        }
    }
   
    @Override
    public Collection<ParameterValue> getSystemParameters() {
        // TODO Auto-generated method stub
        return null;
    }

}
