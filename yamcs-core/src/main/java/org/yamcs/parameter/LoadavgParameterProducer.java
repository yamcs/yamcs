package org.yamcs.parameter;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;

import org.yamcs.logging.Log;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.Parameter;

public class LoadavgParameterProducer implements SystemParametersProducer {
    final static Log log = new Log(LoadavgParameterProducer.class);

    private Parameter spLoad1;

    public LoadavgParameterProducer(SystemParametersService sysParamsService) {
        spLoad1 = sysParamsService.createSystemParameter("load1", Type.DOUBLE,
                "System load average for the last minute");
        log.debug("Publishing load1 with parameter id {}", spLoad1);
    }

    @Override
    public Collection<ParameterValue> getSystemParameters(long gentime) {
        var pvals = new ArrayList<ParameterValue>();

        var os = ManagementFactory.getOperatingSystemMXBean();

        var load1 = SystemParametersService.getPV(spLoad1, gentime, os.getSystemLoadAverage());
        pvals.add(load1);

        return pvals;
    }

    @Override
    public int getFrequency() {
        return 5;
    }
}
