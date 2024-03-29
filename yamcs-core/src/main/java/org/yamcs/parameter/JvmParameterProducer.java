package org.yamcs.parameter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.yamcs.logging.Log;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.UnitType;

public class JvmParameterProducer implements SystemParametersProducer {
    final static Log log = new Log(JvmParameterProducer.class);

    private Parameter spJvmTotalMemory, spJvmMemoryUsed, spJvmTheadCount;

    public JvmParameterProducer(SystemParametersService sysParamsService) {
        UnitType kbunit = new UnitType("KB");
        spJvmTotalMemory = sysParamsService.createSystemParameter("jvm/totalMemory", Type.UINT64, kbunit,
                "Total amount of memory allocated by the Java Virtual Machine");
        log.debug("Publishing jvmTotalMemory with parameter id {}", spJvmTotalMemory);

        spJvmMemoryUsed = sysParamsService.createSystemParameter("jvm/memoryUsed", Type.UINT64, kbunit,
                "Amount of memory currently used in the Java Virtual Machine");
        log.debug("Publishing jvmMemoryUsed with parameter id {}", spJvmMemoryUsed);

        spJvmTheadCount = sysParamsService.createSystemParameter("jvm/threadCount", Type.UINT32,
                "Current thread count of the Java Virtual Machine");
        log.debug("Publishing jvmThreadCount with parameter id {}", spJvmTheadCount);
    }

    @Override
    public Collection<ParameterValue> getSystemParameters(long gentime) {
        List<ParameterValue> pvlist = new ArrayList<>();
        Runtime r = Runtime.getRuntime();
        ParameterValue jvmTotalMemory = SystemParametersService.getPV(spJvmTotalMemory, gentime,
                r.totalMemory() / 1024);
        ParameterValue jvmMemoryUsed = SystemParametersService.getPV(spJvmMemoryUsed, gentime,
                (r.totalMemory() - r.freeMemory()) / 1024);
        ParameterValue jvmThreadCount = SystemParametersService.getUnsignedIntPV(spJvmTheadCount, gentime,
                Thread.activeCount());

        pvlist.add(jvmTotalMemory);
        pvlist.add(jvmMemoryUsed);
        pvlist.add(jvmThreadCount);

        return pvlist;
    }

    @Override
    public int getFrequency() {
        return 60;
    }
}
