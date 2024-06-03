package org.yamcs.cascading;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.yamcs.YConfiguration;
import org.yamcs.client.Page;
import org.yamcs.client.ParameterSubscription;
import org.yamcs.client.YamcsClient;
import org.yamcs.client.base.WebSocketClient;
import org.yamcs.client.mdb.MissionDatabaseClient.ListOptions;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersService;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.Pvalue;
import org.yamcs.protobuf.SubscribeParametersRequest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.tctm.AbstractLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.tctm.ParameterDataLink;
import org.yamcs.tctm.ParameterSink;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SpaceSystem;

/**
 * 
 * Yamcs Parameter link - receives parameters from a Yamcs upstream server.
 * <p>
 * The parameters have to be defined in the local MDB and have the same name like in the remote MDB. This restriction
 * may be lifted in the new versions.
 *
 */
public class YamcsParameterLink extends AbstractLink implements ParameterDataLink {
    YamcsLink parentLink;

    List<String> parameters;
    ParameterSubscription subscription;
    ParameterSink paraSink;
    AtomicLong paraCount = new AtomicLong();
    int seqCount;
    Mdb mdb;

    // when subscribing to remote Yamcs parameters, we rename them to include the upstream name into their name
    Map<String, String> remoteYamcsParams;

    public YamcsParameterLink(YamcsLink parentLink) {
        this.parentLink = parentLink;
    }

    public void init(String instance, String name, YConfiguration config) {
        config = YamcsTmLink.swapConfig(config, "ppRealtimeStream", "ppStream", "pp_realtime");
        super.init(instance, name, config);
        this.parameters = config.getList("parameters");
        this.mdb = MdbFactory.getInstance(instance);
    }

    @Override
    protected void doStart() {
        if (!isEffectivelyDisabled()) {
            doEnable();
        }
        notifyStarted();
    }

    @Override
    public void doDisable() {
        if (subscription != null) {
            subscription.cancel(true);
            subscription = null;
        }
    }

    @Override
    public void doEnable() {
        if (subscription != null && !subscription.isDone()) {
            return;
        }
        WebSocketClient wsclient = parentLink.getClient().getWebSocketClient();
        if (wsclient.isConnected()) {
            subscribeParameters();
        }
    }

    public void subscribeParameters() {
        YamcsClient yclient = parentLink.getClient();

        subscription = yclient.createParameterSubscription();
        subscription.addListener(new ParameterSubscription.Listener() {

            @Override
            public void onData(List<Pvalue.ParameterValue> values) {
                processParameters(values);
            }

            @Override
            public void onInvalidIdentification(NamedObjectId id) {
                log.warn("Parameter subscription raised invalid identification(could be lack of permission): {}", id);
            }
        });
        
        SubscribeParametersRequest.Builder request = SubscribeParametersRequest.newBuilder()
                .setInstance(parentLink.getUpstreamInstance())
                .setProcessor(parentLink.getUpstreamProcessor())
                .setSendFromCache(false);

        HashSet<String> toAdd = new HashSet<>();

        List<String> requestedYamcsParamsFilter = new ArrayList<>();
        boolean requestAllRemoteYamcsParams = false;

        for(String p: parameters) {
            if (p.equals("/yamcs/") || p.equals("/yamcs")) {
                requestAllRemoteYamcsParams = true;
                requestedYamcsParamsFilter.add(p);
            } else if (p.startsWith("/yamcs/")) {
                requestedYamcsParamsFilter.add(p);
            } else if (p.endsWith("/")) {
                SpaceSystem sps = mdb.getSpaceSystem(p.substring(0, p.length() - 1));

                if (sps == null) {
                    log.warn("Cannot find space system {} in local MDB; ignoring", p);
                    continue;
                }
                sps.getParameters().forEach(pdef -> toAdd.add(pdef.getQualifiedName()));
            } else {
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + p);

                boolean added = false;
                for (Parameter param : mdb.getParameters()) {
                    if (matcher.matches(Path.of(param.getQualifiedName()))) {
                        toAdd.add(param.getQualifiedName());
                        added = true;
                    }
                }

                if (!added) {
                    log.warn("Cannot find parameter {} in local MDB; ignoring", p);
                }
            }
        }


        toAdd.forEach(name -> request.addId(NamedObjectId.newBuilder().setName(name).build()));

        log.debug("Sending parameter subcription {}", request);
        subscription.sendMessage(request.build());

        if (requestAllRemoteYamcsParams || !requestedYamcsParamsFilter.isEmpty()) {
            if (requestAllRemoteYamcsParams) {
                requestedYamcsParamsFilter.clear();
                requestedYamcsParamsFilter.add("**");
             }

            remoteYamcsParams = new HashMap<>();
            var mdbClient = parentLink.getClient().createMissionDatabaseClient(parentLink.getUpstreamInstance());

            collectRemoteYamcsParameters(requestedYamcsParamsFilter,
                    mdbClient.listParameters(ListOptions.details(false),
                            ListOptions.system("/yamcs"), ListOptions.q("/")));
        }

    }


    private void collectRemoteYamcsParameters(List<String> requestedYamcsParamsFilter,
            CompletableFuture<Page<ParameterInfo>> cf) {

        cf.whenComplete((page, t) -> {
            if (t != null) {
                log.warn("Failed to retrieve yamcs parameter names");
            } else {
                for (String p : requestedYamcsParamsFilter) {
                    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + p);
                    boolean match = false;
                    for (ParameterInfo pi : page) {
                        if (!pi.getQualifiedName().startsWith("/yamcs/")) {
                            log.warn("Remote yamcs parameter that does not start with /yamcs/?? '{}'", pi.getQualifiedName());
                            continue;
                        }
                        if (matcher.matches(Path.of(pi.getQualifiedName()))) {
                            String name = pi.getQualifiedName().replaceFirst("/yamcs/", "/yamcs/"+parentLink.getUpstreamName()+"_");
                            remoteYamcsParams.put(pi.getQualifiedName(), name);
                            match = true;
                        }
                    }
                    if (!match) {
                        log.info("No Yamcs parameter matching " + p + " found on the upstream server");
                    }
                }

                if (page.hasNextPage()) {
                    collectRemoteYamcsParameters(requestedYamcsParamsFilter, page.getNextPage());
                } else {
                    subscribeYamcsParameters();
                }
            }
        });
    }

    void subscribeYamcsParameters() {
        log.debug("Subscribing to the following yamcs parameters from upstream: {}", remoteYamcsParams.keySet());

        SubscribeParametersRequest.Builder request = SubscribeParametersRequest.newBuilder()
                .setInstance(parentLink.getUpstreamInstance())
                .setProcessor(parentLink.getUpstreamProcessor())
                .setSendFromCache(false);

        remoteYamcsParams.keySet().forEach(name -> request.addId(NamedObjectId.newBuilder().setName(name).build()));
        subscription.sendMessage(request.build());
    }


    private void processParameters(List<Pvalue.ParameterValue> values) {
        // group by time and group (although it's very likely they are already grouped by time)
        Map<Long, Map<String, List<ParameterValue>>> vmap = new HashMap<>();

        for (Pvalue.ParameterValue gpv : values) {
            Parameter pdef;
            if (gpv.getId().getName().startsWith("/yamcs")) {
                String newName = remoteYamcsParams.get(gpv.getId().getName());
                if (newName == null) {
                    log.warn("Received system parameter not subscribed " + gpv);
                    continue;
                }
                pdef = mdb.getParameter(newName);
                if (pdef == null) {
                    pdef = SystemParametersService.createSystemParameter(mdb, newName,
                            ValueUtility.fromGpb(gpv.getEngValue()));
                }
            } else {
                pdef = mdb.getParameter(gpv.getId());
            }
            if (pdef == null) {
                log.warn("Ignoring unknown parameter {}", gpv.getId());
                continue;
            }

            String group = pdef.getRecordingGroup();
            ParameterValue pv = BasicParameterValue.fromGpb(pdef, gpv);
            List<ParameterValue> l = vmap.computeIfAbsent(pv.getGenerationTime(), x -> new HashMap<>())
                    .computeIfAbsent(group, x -> new ArrayList<>());
            l.add(pv);
        }

        for (Map.Entry<Long, Map<String, List<ParameterValue>>> me : vmap.entrySet()) {
            long gentime = me.getKey();
            for (Map.Entry<String, List<ParameterValue>> me1 : me.getValue().entrySet()) {
                paraSink.updateParameters(gentime, me1.getKey(), seqCount, me1.getValue());
                paraCount.addAndGet(me1.getValue().size());
            }
            seqCount++;
        }
    }

    @Override
    protected void doStop() {
        if (!isDisabled()) {
            doDisable();
        }
        notifyStopped();
    }

    @Override
    protected Status connectionStatus() {
        Status parentStatus = parentLink.connectionStatus();
        if (parentStatus == Status.OK) {
            boolean ok = subscription != null && !subscription.isDone();
            return ok ? Status.OK : Status.UNAVAIL;
        } else {
            return parentStatus;
        }
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

    @Override
    public void setParameterSink(ParameterSink parameterSink) {
        this.paraSink = parameterSink;
    }
}
