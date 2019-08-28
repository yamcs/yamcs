package org.yamcs.tctm;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.cmdhistory.StreamCommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.management.ManagementService;
import org.yamcs.utils.ServiceUtil;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.Service;
import com.google.gson.Gson;

/**
 * Service that initialises all the data links
 *
 * @author nm
 */
public class DataLinkInitialiser extends AbstractYamcsService {

    public static final String REALTIME_TC_STREAM_NAME = "tc_realtime";

    private Map<String, Link> linksByName = new HashMap<>();

    private YarchDatabaseInstance ydb;

    @Override
    public void init(String instanceName, YConfiguration config) throws InitException {
        super.init(instanceName, config);

        ydb = YarchDatabase.getInstance(instanceName);

        YamcsServerInstance instance = YamcsServer.getServer().getInstance(instanceName);
        YConfiguration instanceConfig = instance.getConfig();

        if (instanceConfig.containsKey("dataLinks")) {
            try {
                List<YConfiguration> linkConfigs = instanceConfig.getConfigList("dataLinks");
                for (YConfiguration linkConfig : linkConfigs) {
                    createDataLink(linkConfig);
                }
            } catch (IOException e) {
                throw new InitException(e);
            }
        }
    }

    private void createDataLink(YConfiguration linkConfig) throws IOException {
        String className = linkConfig.getString("class");
        YConfiguration args = null;
        args = linkConfig.getConfig("args");

        String name = linkConfig.getString("name");
        if (linksByName.containsKey(name)) {
            throw new ConfigurationException(
                    "Instance " + yamcsInstance + ": there is already a link named '" + name + "'");
        }

        // this is maintained for compatibility with DaSS which defines no stream name because the config is specified
        // in a separate file
        String linkLevelStreamName = null;
        if (linkConfig.containsKey("stream")) {
            log.warn("DEPRECATION ALERT: Define 'stream' under 'args'.");
            linkLevelStreamName = linkConfig.getString("stream");
        }
        Link link;
        if (args != null) {
            link = YObjectLoader.loadObject(className, yamcsInstance, name, args);
        } else {
            link = YObjectLoader.loadObject(className, yamcsInstance, name);
        }

        boolean enabledAtStartup = linkConfig.getBoolean("enabledAtStartup", true);

        if (!enabledAtStartup) {
            link.disable();
        }

        configureDataLink(link, args, linkLevelStreamName);
    }

    void configureDataLink(Link link, YConfiguration linkArgs, String linkLevelStreamName) {
        if (linkArgs == null) {
            linkArgs = YConfiguration.emptyConfig();
        }

        Stream s = null;
        String streamName = linkLevelStreamName;
        if (linkArgs.containsKey("stream")) {
            streamName = linkArgs.getString("stream");
        }
        if (streamName != null) {
            s = ydb.getStream(streamName);
            if (s == null) {
                throw new ConfigurationException("Cannot find stream '" + streamName + "'");
            }
        }

        if (link instanceof TmPacketDataLink) {
            if (s != null) {
                Stream stream = s;
                TmPacketDataLink tmLink = (TmPacketDataLink) link;
                boolean dropCorrupted = linkArgs.getBoolean("dropCorruptedPackets", true);
                tmLink.setTmSink(pwrt -> {
                    if (pwrt.isCorrupted() && dropCorrupted) {
                        return;
                    }
                    long time = pwrt.getGenerationTime();
                    byte[] pkt = pwrt.getPacket();
                    Tuple t = new Tuple(StandardTupleDefinitions.TM,
                            new Object[] { time, pwrt.getSeqCount(), pwrt.getReceptionTime(), pkt });
                    stream.emitTuple(t);
                });
            }
        }

        if (link instanceof TcDataLink) {
            TcDataLink tcLink = (TcDataLink) link;
            if (s != null) {
                s.addSubscriber(new StreamSubscriber() {
                    @Override
                    public void onTuple(Stream s, Tuple tuple) {
                        XtceDb xtcedb = XtceDbFactory.getInstance(yamcsInstance);
                        tcLink.sendTc(PreparedCommand.fromTuple(tuple, xtcedb));
                    }

                    @Override
                    public void streamClosed(Stream s) {
                        stopAsync();
                    }
                });
            }
            tcLink.setCommandHistoryPublisher(new StreamCommandHistoryPublisher(yamcsInstance));
        }

        if (link instanceof ParameterDataLink) {
            if (s != null) {
                ((ParameterDataLink) link).setParameterSink(new StreamPbParameterSender(yamcsInstance, s));
            }
        }

        if (link instanceof AggregatedDataLink) {
            for (Link l : ((AggregatedDataLink) link).getSubLinks()) {
                configureDataLink(l, l.getConfig(), null);
            }
        }

        linksByName.put(link.getName(), link);
        String json = new Gson().toJson(linkArgs.toMap());
        ManagementService.getInstance().registerLink(yamcsInstance, link.getName(), json, link);
    }

    @Override
    protected void doStart() {
        linksByName.forEach((name, link) -> {
            if (link instanceof Service) {
                log.debug("Starting service link {}", name);
                ((Service) link).startAsync();
            }
        });
        linksByName.forEach((name, link) -> {
            if (link instanceof Service) {
                ServiceUtil.awaitServiceRunning((Service) link);
            }
        });
        notifyStarted();
    }

    @Override
    protected void doStop() {
        ManagementService mgrsrv = ManagementService.getInstance();
        linksByName.forEach((name, link) -> {
            mgrsrv.unregisterLink(yamcsInstance, name);
            if (link instanceof Service) {
                ((Service) link).stopAsync();
            }
        });
        linksByName.forEach((name, link) -> {
            if (link instanceof Service) {
                ServiceUtil.awaitServiceTerminated((Service) link, YamcsServer.SERVICE_STOP_GRACE_TIME, log);
            }
        });
        notifyStopped();
    }
}
