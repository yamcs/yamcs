package org.yamcs.tctm;

import java.io.IOException;
import java.util.List;

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
    private String spDataRate, spPacketRate;
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

    @Override
    public void setupSystemParameters(SystemParametersCollector sysParamCollector) {
        super.setupSystemParameters(sysParamCollector);
        spDataRate = sysParamCollector.getNamespace() + "/" + linkName + "/dataRate";
        spPacketRate = sysParamCollector.getNamespace() + "/" + linkName + "/packetRate";
    }

    @Override
    protected void collectSystemParameters(long time, List<ParameterValue> list) {
        super.collectSystemParameters(time, list);
        list.add(SystemParametersCollector.getPV(spDataRate, time, dataRateMeter.getFiveSecondsRate()));
        list.add(SystemParametersCollector.getPV(spPacketRate, time, packetRateMeter.getFiveSecondsRate()));
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
