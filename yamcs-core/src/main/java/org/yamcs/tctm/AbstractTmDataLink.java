package org.yamcs.tctm;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.parameter.SystemParametersService;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.time.SimulationTimeService;
import org.yamcs.utils.DataRateMeter;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.UnitType;

public abstract class AbstractTmDataLink extends AbstractLink implements TmPacketDataLink, SystemParametersProducer {
    protected AtomicLong packetCount = new AtomicLong(0);
    DataRateMeter packetRateMeter = new DataRateMeter();

    String packetPreprocessorClassName;
    YConfiguration packetPreprocessorArgs;
    protected PacketPreprocessor packetPreprocessor;

    private Parameter spPacketRate;

    final static String CFG_PREPRO_CLASS = "packetPreprocessorClassName";
    private TmSink tmSink;
    protected boolean updateSimulationTime;

    @Override
    public Spec getDefaultSpec() {
        var spec = super.getDefaultSpec();
        spec.addOption("packetPreprocessorClassName", OptionType.STRING);
        spec.addOption("packetPreprocessorArgs", OptionType.MAP).withSpec(Spec.ANY);
        spec.addOption("updateSimulationTime", OptionType.BOOLEAN).withDefault(false);
        return spec;
    }

    @Override
    public void init(String instance, String name, YConfiguration config) {
        super.init(instance, name, config);
        if (config.containsKey(CFG_PREPRO_CLASS)) {
            this.packetPreprocessorClassName = config.getString(CFG_PREPRO_CLASS);
        } else {
            this.packetPreprocessorClassName = IssPacketPreprocessor.class.getName();
        }
        if (config.containsKey("packetPreprocessorArgs")) {
            this.packetPreprocessorArgs = config.getConfig("packetPreprocessorArgs");
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
        }

        updateSimulationTime = config.getBoolean("updateSimulationTime", false);
        if (updateSimulationTime) {
            if (timeService instanceof SimulationTimeService) {
                SimulationTimeService sts = (SimulationTimeService) timeService;
                sts.setTime0(0);
            } else {
                throw new ConfigurationException(
                        "updateSimulationTime can only be used together with SimulationTimeService "
                                + "(add 'timeService: org.yamcs.time.SimulationTimeService' in yamcs.<instance>.yaml)");
            }
        }

    }

    @Override
    public void setupSystemParameters(SystemParametersService sysParamService) {
        super.setupSystemParameters(sysParamService);
        spPacketRate = sysParamService.createSystemParameter(LINK_NAMESPACE + linkName + "/packetRate", Type.DOUBLE,
                new UnitType("p/s"), "Number of packets per second computed over a five second interval");
    }

    @Override
    protected void collectSystemParameters(long time, List<ParameterValue> list) {
        super.collectSystemParameters(time, list);
        list.add(SystemParametersService.getPV(spPacketRate, time, packetRateMeter.getFiveSecondsRate()));
    }

    @Override
    public long getDataInCount() {
        return packetCount.get();
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
     * Sends the packet downstream for processing.
     * <p>
     * Starting in Yamcs 5.2, if the updateSimulationTime option is set on the link configuration,
     * <ul>
     * <li>the timeService is expected to be SimulationTimeService</li>
     * <li>at initialization, the time0 is set to 0</li>
     * <li>upon each packet received, the generationTime (as set by the pre-processor) is used to update the simulation
     * elapsed time</li>
     * </ul>
     * <p>
     * Should be called by all sub-classes (instead of directly calling {@link TmSink#processPacket(TmPacket)}
     * 
     * @param tmpkt
     */
    protected void processPacket(TmPacket tmpkt) {
        tmSink.processPacket(tmpkt);
        if (updateSimulationTime) {
            SimulationTimeService sts = (SimulationTimeService) timeService;
            if (!tmpkt.isInvalid()) {
                sts.setSimElapsedTime(tmpkt.getGenerationTime());
            }
        }
    }

    /**
     * called when a new packet is received to update the statistics
     * 
     * @param packetSize
     */
    protected void updateStats(int packetSize) {
        super.dataIn(1, packetSize);
        packetCount.getAndIncrement();
        packetRateMeter.mark(1);
    }

    @Override
    public void resetCounters() {
        packetCount.set(0);
    }
}
