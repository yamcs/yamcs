package org.yamcs.tctm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.yamcs.StandardTupleDefinitions;
import org.yamcs.YamcsServer;
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.time.TimeService;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

/**
 * Sends collection of parameters to stream by
 * 
 * @author nm
 *
 */
public class StreamParameterSender {
    final Stream stream;
    final DataType paraDataType = DataType.PARAMETER_VALUE;
    TimeService timeService;
    protected final Log log;
    Map<String, AtomicInteger> groupSeq = new HashMap<>();

    public StreamParameterSender(String yamcsInstance, Stream stream) {
        this.stream = stream;
        timeService = YamcsServer.getTimeService(yamcsInstance);
        log = new Log(getClass(), yamcsInstance);
    }

    /**
     * Send the parameters to the stream grouping by group and time
     * 
     * @param params
     */
    public void sendParameters(Collection<ParameterValue> params) {
        params.stream().collect(Collectors.groupingBy(ParameterValue::getGenerationTime))
                .forEach((t, l) -> sendParameters(t, l));
    }

    // Send the parameters to the stream grouping by group
    private void sendParameters(long genTime, Collection<ParameterValue> params) {
        params.stream().collect(Collectors.groupingBy(pv -> pv.getParameter().getRecordingGroup()))
                .forEach((g, l) -> sendParameters(genTime, g, l));
    }

    private void sendParameters(long genTime, String group, Collection<ParameterValue> params) {
        int seqNum = groupSeq.computeIfAbsent(group, g -> new AtomicInteger()).getAndIncrement();
        updateParameters(genTime, group, seqNum, params);
    }

    public void updateParameters(long gentime, String group, int seqNum, Collection<ParameterValue> params) {
        TupleDefinition tdef = StandardTupleDefinitions.PARAMETER.copy();
        List<Object> cols = new ArrayList<>(4 + params.size());
        cols.add(gentime);
        cols.add(group);
        cols.add(seqNum);
        cols.add(timeService.getMissionTime());
        for (ParameterValue pv : params) {
            String qualifiedName = pv.getParameterQualifiedName();
            int idx = tdef.getColumnIndex(qualifiedName);
            if (idx != -1) {
                log.warn("duplicate value for {} \nfirst: {}" + "\n second: {} ", pv.getParameter(), cols.get(idx),
                        pv);
                continue;
            }
            tdef.addColumn(qualifiedName, DataType.PARAMETER_VALUE);
            cols.add(pv);
        }
        Tuple t = new Tuple(tdef, cols);
        stream.emitTuple(t);
    }

}
