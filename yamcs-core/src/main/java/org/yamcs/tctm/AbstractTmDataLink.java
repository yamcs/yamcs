package org.yamcs.tctm;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersCollector;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.time.TimeService;
import org.yamcs.utils.DataRateMeter;
import org.yamcs.utils.YObjectLoader;


public abstract class AbstractTmDataLink extends AbstractLink implements TmPacketDataLink, SystemParametersProducer {
    protected volatile long packetCount = 0;
    DataRateMeter packetRateMeter = new DataRateMeter();
    DataRateMeter dataRateMeter = new DataRateMeter();

    String packetPreprocessorClassName;
    YConfiguration packetPreprocessorArgs;
    protected PacketPreprocessor packetPreprocessor;

    final protected Log log;
    protected SystemParametersCollector sysParamCollector;
    private String spLinkStatus, spDataCount, spDataRate, spPacketRate;
    final protected TimeService timeService;

    final static String CFG_PREPRO_CLASS = "packetPreprocessorClassName";
    protected TmSink tmSink;

    

    protected AbstractTmDataLink(String instance, String name, YConfiguration config) {
        super(instance, name, config);
        this.timeService = YamcsServer.getTimeService(instance);
        this.log = new Log(this.getClass(), instance);
        log.setContext(name);
    }

    protected void initPreprocessor(String instance, YConfiguration config) {
        if (config != null) {
            if (config.containsKey(CFG_PREPRO_CLASS)) {
                this.packetPreprocessorClassName = config.getString(CFG_PREPRO_CLASS);
            } else {
                this.packetPreprocessorClassName = IssPacketPreprocessor.class.getName();
            }
            if (config.containsKey("packetPreprocessorArgs")) {
                this.packetPreprocessorArgs = config.getConfig("packetPreprocessorArgs");
            }
        } else {
            this.packetPreprocessorClassName = IssPacketPreprocessor.class.getName();
            this.packetPreprocessorArgs = null;
        }

        try {
            if (packetPreprocessorArgs != null) {
                packetPreprocessor = YObjectLoader.loadObject(packetPreprocessorClassName, instance,
                        packetPreprocessorArgs);
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
            spLinkStatus = sysParamCollector.getNamespace() + "/" + linkName + "/linkStatus";
            spDataCount = sysParamCollector.getNamespace() + "/" + linkName + "/dataCount";
            spDataRate = sysParamCollector.getNamespace() + "/" + linkName + "/dataRate";
            spPacketRate = sysParamCollector.getNamespace() + "/" + linkName + "/packetRate";
        } else {
            log.info("System variables collector not defined for instance {} ", yamcsInstance);
        }
    }

    @Override
    public Collection<ParameterValue> getSystemParameters() {
        long time = timeService.getMissionTime();
        ParameterValue linkStatus = SystemParametersCollector.getPV(spLinkStatus, time, getLinkStatus().name());
        ParameterValue dataCount = SystemParametersCollector.getPV(spDataCount, time, packetCount);
        ParameterValue dataRate = SystemParametersCollector.getPV(spDataRate, time, dataRateMeter.getFiveSecondsRate());
        ParameterValue packetRate = SystemParametersCollector.getPV(spPacketRate, time,
                packetRateMeter.getFiveSecondsRate());
        return Arrays.asList(linkStatus, dataCount, dataRate, packetRate);
    }

    @Override
    public long getDataInCount() {
        return packetCount;
    }

    @Override
    public long getDataOutCount() {
        return 0;
    }
    
    @Override
    public void setTmSink(TmSink tmSink) {
        this.tmSink = tmSink;
    }

    /**
     * called when a new packet is received to update the statistics
     * 
     * @param packetSize
     */
    protected void updateStats(int packetSize) {
        packetCount++;
        packetRateMeter.mark(1);
        dataRateMeter.mark(packetSize);
    }

    @Override
    public void resetCounters() {
        packetCount = 0;
    }
}
