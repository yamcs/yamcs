package org.yamcs.tctm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.yamcs.AbstractProcessorService;
import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.Processor;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.mdb.ParameterTypeProcessor;
import org.yamcs.mdb.ProcessingData;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.parameter.ParameterProcessor;
import org.yamcs.parameter.ParameterProcessorManager;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersService;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * Provides parameters from yarch streams (pp_realtime) to {@link ParameterProcessorManager}
 * 
 * @author nm
 *
 */
public class StreamParameterProvider extends AbstractProcessorService implements StreamSubscriber, ParameterProvider {
    List<Stream> streams = new ArrayList<>();
    ParameterProcessor ppm;
    Mdb mdb;

    ParameterTypeProcessor ptypeProcessor;

    public void init(Processor processor, YConfiguration config, Object spec) {
        super.init(processor, config, spec);
        String yamcsInstance = processor.getInstance();
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        mdb = MdbFactory.getInstance(yamcsInstance);

        List<String> streamNames;
        if (config.containsKey("stream")) {
            streamNames = Arrays.asList(config.getString("stream"));
        } else if (config.containsKey("streams")) {
            streamNames = config.getList("streams");
        } else {
            streamNames = StreamConfig.getInstance(yamcsInstance).getEntries(StandardStreamType.PARAM).stream()
                    .map(sce -> sce.getName())
                    .filter(s -> !Processor.PROC_PARAMETERS_STREAM.equals(s))
                    .collect(Collectors.toList());
        }

        log.debug("Subscribing to streams {} ", streamNames);

        for (String streamName : streamNames) {
            Stream stream = ydb.getStream(streamName);
            if (stream == null) {
                throw new ConfigurationException("Cannot find a stream named " + streamName);
            }
            streams.add(stream);
        }

        ptypeProcessor = processor.getProcessorData().getParameterTypeProcessor();
        processor.getParameterProcessorManager().addParameterProvider(this);
        streams.forEach(s -> s.addSubscriber(this));
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        streams.forEach(s -> s.removeSubscriber(this));
        notifyStopped();
    }

    /**
     * Make sure all parameters are defined in the MDB, otherwise the PRM will choke
     */
    @Override
    public void onTuple(Stream s, Tuple tuple) {// the definition of the tuple is in PpProviderAdapter
        ProcessingData data = ProcessingData.createForTmProcessing(processor.getLastValueCache());

        for (int i = 4; i < tuple.size(); i++) {
            Object o = tuple.getColumn(i);
            ParameterValue pv;
            if (o instanceof org.yamcs.protobuf.Pvalue.ParameterValue) {
                org.yamcs.protobuf.Pvalue.ParameterValue gpv = (org.yamcs.protobuf.Pvalue.ParameterValue) tuple
                        .getColumn(i);
                String name = tuple.getColumnDefinition(i).getName();
                Parameter ppdef = mdb.getParameter(name);
                if (ppdef == null) {
                    continue;
                }
                pv = BasicParameterValue.fromGpb(ppdef, gpv);
            } else if (o instanceof ParameterValue) {
                pv = (ParameterValue) o;
                if (pv.getParameter() == null) {
                    String fqn = pv.getParameterQualifiedName();
                    Parameter ppdef = mdb.getParameter(fqn);
                    if (ppdef == null) {
                        if (Mdb.isSystemParameter(fqn)) {
                            Value engValue = pv.getEngValue();
                            ppdef = SystemParametersService.createSystemParameter(mdb, fqn, engValue);
                        } else {
                            log.trace("Ignoring unknown parameter {}", fqn);
                            continue;
                        }
                    }
                    pv.setParameter(ppdef);
                }
            } else {
                log.warn("Received data that is not parameter value but {}", o.getClass());
                continue;
            }

            if (pv.getEngValue() == null && pv.getRawValue() != null) {
                ptypeProcessor.calibrate(pv);
            }
            data.addTmParam(pv);
        }
        ppm.process(data);
    }

    @Override
    public void streamClosed(Stream s) {
        stopAsync();
    }

    @Override
    public void setParameterProcessor(ParameterProcessor paraListener) {
        this.ppm = paraListener;
    }

    @Override
    public void stopProviding(Parameter paramDef) {
        // not implemented, this always provides all parameters
    }

    @Override
    public boolean canProvide(NamedObjectId id) {
        if (mdb.getParameter(id) != null) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean canProvide(Parameter p) {
        return mdb.getParameter(p.getQualifiedName()) != null;
    }

    @Override
    public Parameter getParameter(NamedObjectId id) throws InvalidIdentification {
        Parameter p = mdb.getParameter(id);
        if (p == null) {
            throw new InvalidIdentification();
        } else {
            return p;
        }
    }

    @Override
    public void startProviding(Parameter paramDef) {
        // not implemented, this always provides all parameters
    }

    @Override
    public void startProvidingAll() {
        // not implemented, this always provides all parameters
    }
}
