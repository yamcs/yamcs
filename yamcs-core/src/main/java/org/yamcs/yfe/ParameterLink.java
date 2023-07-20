package org.yamcs.yfe;

import org.yamcs.YConfiguration;
import org.yamcs.client.ParameterSubscription;
import org.yamcs.mdb.XtceDbFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.tctm.AbstractLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.tctm.ParameterDataLink;
import org.yamcs.tctm.ParameterSink;
import org.yamcs.xtce.XtceDb;
import org.yamcs.yfe.protobuf.Yfe.ParameterData;
import org.yamcs.yfe.protobuf.Yfe.Value;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Yamcs Parameter link - receives parameters from a Yamcs upstream server.
 * <p>
 * The parameters have to be defined in the local MDB and have the same name like in the remote MDB. This restriction
 * may be lifted in the new versions.
 */
public class ParameterLink extends AbstractLink implements ParameterDataLink {
    final YfeLink parentLink;
    final int targetId;

    List<String> parameters;
    ParameterSubscription subscription;
    ParameterSink paraSink;
    AtomicLong paraCount = new AtomicLong();
    int seqCount;
    XtceDb xtcedb;

    public ParameterLink(YfeLink yfeLink, int targetId) {
        this.parentLink = yfeLink;
        this.targetId = targetId;
    }

    public void init(String instance, String name, YConfiguration config) {
        //config = YfeTmLink.swapConfig(config, "ppRealtimeStream", "ppStream", "pp_realtime");
        super.init(instance, name, config);

        this.xtcedb = XtceDbFactory.getInstance(instance);
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {

        notifyStopped();
    }

    @Override
    protected Status connectionStatus() {
        return parentLink.connectionStatus();
    }

    @Override
    public AggregatedDataLink getParent() {
        return parentLink;
    }

    @Override
    public long getDataInCount() {
        return paraCount.get();
    }

    @Override
    public long getDataOutCount() {
        return 0;
    }

    @Override
    public void resetCounters() {
        paraCount.set(0);

    }

    public void processParameters(ParameterData pd) {
        // group by time and group (although it's very likely they are already grouped by time)
        long rectime = timeService.getMissionTime();
        Collection<ParameterValue> col = new HashSet<ParameterValue>();

        long time = pd.getGenerationTime().getMillis();
        for (int i = 0; i < pd.getParameterCount(); i++) {
            Value protoVal = pd.getParameter(i).getEngValue();

            org.yamcs.parameter.Value val = ProtoConverter.fromProto(protoVal);

            org.yamcs.parameter.ParameterValue pv = new ParameterValue(pd.getParameter(i).getFqn());
            pv.setEngValue(val);
            pv.setGenerationTime(time);
            col.add(pv);
        }
        paraSink.updateParameters(rectime, linkName, seqCount, col);
    }

    @Override
    public void setParameterSink(ParameterSink parameterSink) {
        this.paraSink = parameterSink;
    }
}
