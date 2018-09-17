package org.yamcs.tctm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.Processor;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.ParameterListener;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.ParameterTypeProcessor;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;

/**
 * Provides parameters from yarch streams (pp_realtime) to ParameterRequestManager.
 * 
 * @author nm
 *
 */
public class StreamParameterProvider extends AbstractService implements StreamSubscriber, ParameterProvider {
    Stream stream;
    ParameterListener paraListener;
    final XtceDb xtceDb;
    private static final Logger log = LoggerFactory.getLogger(StreamParameterProvider.class);

    ParameterTypeProcessor ptypeProcessor;

    public StreamParameterProvider(String archiveInstance, Map<String, Object> config) throws ConfigurationException {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(archiveInstance);
        xtceDb = XtceDbFactory.getInstance(archiveInstance);

        List<String> streamNames;
        if (config.containsKey("stream")) {
            streamNames = Arrays.asList(YConfiguration.getString(config, "stream"));
        } else if (config.containsKey("streams")) {
            streamNames = YConfiguration.getList(config, "streams");
        } else {
            throw new ConfigurationException("the config(args) for StreamParameterProvider has to contain a parameter"
                    + " 'stream' - stream name for retrieving parameters from");
        }

        for (String streamName : streamNames) {
            stream = ydb.getStream(streamName);
            if (stream == null) {
                throw new ConfigurationException("Cannot find a stream named " + streamName);
            }
        }
    }

    @Override
    public void init(Processor processor) {
        ptypeProcessor = processor.getProcessorData().getParameterTypeProcessor();
        processor.getParameterRequestManager().addParameterProvider(this);
        stream.addSubscriber(this);
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        stream.removeSubscriber(this);
        notifyStopped();
    }

    /**
     * Make sure all parameters are defined in the XtceDB, otherwise the PRM will choke
     */
    @Override
    public void onTuple(Stream s, Tuple tuple) {// the definition of the tuple is in PpProviderAdapter
        List<ParameterValue> params = new ArrayList<>();
        for (int i = 4; i < tuple.size(); i++) {
            Object o = tuple.getColumn(i);
            ParameterValue pv;
            if (o instanceof org.yamcs.protobuf.Pvalue.ParameterValue) {
                org.yamcs.protobuf.Pvalue.ParameterValue gpv = (org.yamcs.protobuf.Pvalue.ParameterValue) tuple
                        .getColumn(i);
                String name = tuple.getColumnDefinition(i).getName();
                Parameter ppdef = xtceDb.getParameter(name);
                if (ppdef == null) {
                    continue;
                }
                pv = ParameterValue.fromGpb(ppdef, gpv);
            } else if (o instanceof ParameterValue) {
                pv = (ParameterValue) o;
                if (pv.getParameter() == null) {
                    Parameter ppdef = xtceDb.getParameter(pv.getParameterQualifiedNamed());
                    if (ppdef == null) {
                        continue;
                    }
                    pv.setParameter(ppdef);
                }
            } else {
                log.warn("Recieved data that is not parameter value but {}", o.getClass());
                continue;
            }

            if (pv.getEngValue() == null && pv.getRawValue() != null) {
                ptypeProcessor.calibrate(pv);
            }
            params.add(pv);
        }
        paraListener.update(params);
    }

    @Override
    public void streamClosed(Stream s) {
        notifyStopped();
    }

    @Override
    public void setParameterListener(ParameterListener paraListener) {
        this.paraListener = paraListener;
    }

    @Override
    public void stopProviding(Parameter paramDef) {

    }

    @Override
    public boolean canProvide(NamedObjectId id) {
        if (xtceDb.getParameter(id) != null) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean canProvide(Parameter p) {
        return xtceDb.getParameter(p.getQualifiedName()) != null;
    }

    @Override
    public Parameter getParameter(NamedObjectId id) throws InvalidIdentification {
        Parameter p = xtceDb.getParameter(id);
        if (p == null) {
            throw new InvalidIdentification();
        } else {
            return p;
        }
    }

    @Override
    public void startProviding(Parameter paramDef) {
        // TODO Auto-generated method stub
    }

    @Override
    public void startProvidingAll() {
        // TODO Auto-generated method stub
    }

}
