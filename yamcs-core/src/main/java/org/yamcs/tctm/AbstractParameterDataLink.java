package org.yamcs.tctm;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersService;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.DataRateMeter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.UnitType;

public abstract class AbstractParameterDataLink extends AbstractLink implements ParameterDataLink {

    protected AtomicLong parameterCount = new AtomicLong(0);
    private DataRateMeter parameterRateMeter = new DataRateMeter();

    private Parameter parameterRateParameter;
    private ParameterSink parameterSink;

    protected void updateParameters(long gentime, String group, int seqNum, Collection<ParameterValue> params) {
        parameterCount.addAndGet(params.size());
        parameterRateMeter.mark(params.size());

        parameterSink.updateParameters(gentime, group, seqNum, params);
    }

    @Override
    public void setupSystemParameters(SystemParametersService sysParamService) {
        super.setupSystemParameters(sysParamService);
        parameterRateParameter = sysParamService.createSystemParameter(LINK_NAMESPACE + linkName + "/parameterRate",
                Type.DOUBLE, new UnitType("p/s"),
                "Number of parameters per second computed over a five second interval");
    }

    @Override
    protected void collectSystemParameters(long time, List<ParameterValue> list) {
        super.collectSystemParameters(time, list);
        list.add(SystemParametersService.getPV(parameterRateParameter, time, parameterRateMeter.getFiveSecondsRate()));
    }

    @Override
    public long getDataInCount() {
        return parameterCount.get();
    }

    @Override
    public long getDataOutCount() {
        return 0;
    }

    @Override
    public void resetCounters() {
        parameterCount.set(0);
    }

    @Override
    public void setParameterSink(ParameterSink parameterSink) {
        this.parameterSink = parameterSink;
    }
}
