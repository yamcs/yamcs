package org.yamcs.tctm;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersCollector;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.time.TimeService;
import org.yamcs.utils.DataRateMeter;
import org.yamcs.utils.YObjectLoader;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

public abstract class AbstractTmDataLink extends AbstractExecutionThreadService
        implements TmPacketDataLink, SystemParametersProducer {
    protected volatile long packetcount = 0;
    DataRateMeter packetRateMeter = new DataRateMeter();
    DataRateMeter dataRateMeter = new DataRateMeter();

    String packetPreprocessorClassName;
    Object packetPreprocessorArgs;
    PacketPreprocessor packetPreprocessor;

    final Logger log = LoggerFactory.getLogger(this.getClass().getName());
    protected SystemParametersCollector sysParamCollector;
    private String spLinkStatus, spDataCount, spDataRate, spPacketRate;
    final protected TimeService timeService;

    final protected String yamcsInstance;
    final protected String name;
    final YConfiguration config;

    final static String CFG_PREPRO_CLASS = "packetPreprocessorClassName";

    protected AbstractTmDataLink(String instance, String name, YConfiguration config) {
        this.timeService = YamcsServer.getTimeService(instance);
        this.yamcsInstance = instance;
        this.name = name;
        this.config = config;
    }

    protected void initPreprocessor(String instance, YConfiguration config) {
        if(config!=null) {
            if(config.containsKey(CFG_PREPRO_CLASS)) {
                this.packetPreprocessorClassName = config.getString(CFG_PREPRO_CLASS);
            } else {
                this.packetPreprocessorClassName = IssPacketPreprocessor.class.getName();
            }
            this.packetPreprocessorArgs = config.get("packetPreprocessorArgs");
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
            log.error("Cannot instantiate the packet preprocessor", e);
            throw e;
        } catch (IOException e) {
            log.error("Cannot instantiate the packetInput stream", e);
            throw new ConfigurationException(e);
        }
    }

    protected void setupSysVariables() {
        this.sysParamCollector = SystemParametersCollector.getInstance(yamcsInstance);
        if (sysParamCollector != null) {
            sysParamCollector.registerProducer(this);
            spLinkStatus = sysParamCollector.getNamespace() + "/" + name + "/linkStatus";
            spDataCount = sysParamCollector.getNamespace() + "/" + name + "/dataCount";
            spDataRate = sysParamCollector.getNamespace() + "/" + name + "/dataRate";
            spPacketRate = sysParamCollector.getNamespace() + "/" + name + "/packetRate";
        } else {
            log.info("System variables collector not defined for instance {} ", yamcsInstance);
        }
    }

    @Override
    public Collection<ParameterValue> getSystemParameters() {
        long time = timeService.getMissionTime();
        ParameterValue linkStatus = SystemParametersCollector.getPV(spLinkStatus, time, getLinkStatus().name());
        ParameterValue dataCount = SystemParametersCollector.getPV(spDataCount, time, packetcount);
        ParameterValue dataRate = SystemParametersCollector.getPV(spDataRate, time, dataRateMeter.getFiveSecondsRate());
        ParameterValue packetRate = SystemParametersCollector.getPV(spPacketRate, time,
                packetRateMeter.getFiveSecondsRate());
        return Arrays.asList(linkStatus, dataCount, dataRate, packetRate);
    }

    @Override
    public long getDataInCount() {
        return packetcount;
    }

    @Override
    public long getDataOutCount() {
        return 0;
    }

    /**
     * called when a new packet is received to update the statistics
     * 
     * @param packetSize
     */
    protected void updateStats(int packetSize) {
        packetcount++;
        packetRateMeter.mark(1);
        dataRateMeter.mark(packetSize);
    }

    /**
     * Return the configuration used when creating the link
     * 
     * @return
     */
    @Override
    public YConfiguration getConfig() {
        return config;
    }
    
    @Override
    public String getName() {
        return name;
    }
}
