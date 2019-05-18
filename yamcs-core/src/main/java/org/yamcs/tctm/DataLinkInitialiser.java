package org.yamcs.tctm;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.yamcs.ConfigurationException;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsService;
import org.yamcs.cmdhistory.StreamCommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.management.ManagementService;
import org.yamcs.utils.LoggingUtils;
import org.yamcs.utils.ServiceUtil;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.Service;
import com.google.gson.Gson;

/**
 * Service that initialises all the data links
 *
 * @author nm
 *
 */
public class DataLinkInitialiser extends AbstractService implements YamcsService {

    public static final String REALTIME_TC_STREAM_NAME = "tc_realtime";

    private Map<String, Link> linksByName = new HashMap<>();

    private final Logger log;

    private String yamcsInstance;
    private YarchDatabaseInstance ydb;

    public DataLinkInitialiser(String yamcsInstance) throws IOException {
        log = LoggingUtils.getLogger(this.getClass(), yamcsInstance);

        this.yamcsInstance = yamcsInstance;
        YConfiguration c = YConfiguration.getConfiguration("yamcs." + yamcsInstance);
        ydb = YarchDatabase.getInstance(yamcsInstance);
      
        if (c.containsKey("dataLinks")) {
            List<YConfiguration> links = c.getConfigList("dataLinks");
            for (YConfiguration linkConfig : links) {
                createDataLink(linkConfig);
            }
        }

        if (c.containsKey("tmDataLinks")) {
            log.warn("DEPRECATION ALERT: Define links under 'dataLinks' instead of 'tmDataLinks' ");
            List<?> links = c.getList("tmDataLinks");
            createTmDataLinks(links);
        }
        if (c.containsKey("tcDataLinks")) {
            log.warn("DEPRECATION ALERT: Define links under 'dataLinks' instead of 'tcDataLinks' ");
            List<?> links = c.getList("tcDataLinks");
            createTcDataLinks(links);
        }
        if (c.containsKey("parameterDataLinks")) {
            log.warn("DEPRECATION ALERT: Define links under 'dataLinks' instead of 'parameterDataLinks' ");
            List<?> links = c.getList("parameterDataLinks");
            createParameterDataLinks(links);
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
        String json = linkArgs.toJson();
        ManagementService.getInstance().registerLink(yamcsInstance, link.getName(), json, link);
    }

    private void createTmDataLinks(List<?> links) throws IOException {
        int count = 1;
        ManagementService mgrsrv = ManagementService.getInstance();
        for (Object o : links) {
            if (!(o instanceof Map<?, ?>)) {
                throw new ConfigurationException("tmDataLink has to be a Map and not a " + o.getClass());
            }
            Map<String, Object> m = (Map<String, Object>) o;
            String className = YConfiguration.getString(m, "class");
            Object args = null;
            if (m.containsKey("args")) {
                args = m.get("args");
            } else if (m.containsKey("spec")) {
                log.warn("DEPRECATION ALERT: Use 'args' instead of 'spec' on TM Link configuration");
                args = m.get("spec");
            }
            String name = "tm" + count;
            if (m.containsKey("name")) {
                name = m.get("name").toString();
            }
            if (linksByName.containsKey(name)) {
                throw new ConfigurationException(
                        "Instance " + yamcsInstance + ": there is already a link named '" + name + "'");
            }
            boolean enabledAtStartup = true;
            if (m.containsKey("enabledAtStartup")) {
                enabledAtStartup = YConfiguration.getBoolean(m, "enabledAtStartup");
            }
            String streamName = YConfiguration.getString(m, "stream");
            Stream s = ydb.getStream(streamName);
            if (s == null) {
                throw new ConfigurationException("Cannot find stream '" + streamName + "'");
            }

            TmPacketDataLink link = null;
            if (args != null) {
                link = YObjectLoader.loadObject(className, yamcsInstance, name, args);
            } else {
                link = YObjectLoader.loadObject(className, yamcsInstance, name);
            }

            if (!enabledAtStartup) {
                link.disable();
            }
            boolean dropCorrupted = YConfiguration.getBoolean(m, "dropCorruptedPackets", true);
            link.setTmSink(pwrt -> {
                if (pwrt.isCorrupted() && dropCorrupted) {
                    return;
                }
                long time = pwrt.getGenerationTime();
                byte[] pkt = pwrt.getPacket();
                Tuple t = new Tuple(StandardTupleDefinitions.TM,
                        new Object[] { time, pwrt.getSeqCount(), pwrt.getReceptionTime(), pkt });
                s.emitTuple(t);
            });

            linksByName.put(name, link);
            String json = (args != null) ? new Gson().toJson(args) : "";
            mgrsrv.registerLink(yamcsInstance, name, json, link);
            count++;
        }
    }

    private void createTcDataLinks(List<?> uplinkers) throws IOException {
        int count = 1;
        for (Object o : uplinkers) {
            if (!(o instanceof Map)) {
                throw new ConfigurationException("link has to be Map and not a " + o.getClass());
            }
            Map m = (Map) o;

            String className = YConfiguration.getString(m, "class");
            Object args = null;
            if (m.containsKey("args")) {
                args = m.get("args");
            } else if (m.containsKey("spec")) {
                log.warn("DEPRECATION ALERT: Use 'args' instead of 'spec' on TM Link configuration");
                args = m.get("spec");
            }

            String streamName = YConfiguration.getString(m, "stream");
            boolean enabledAtStartup = true;
            if (m.containsKey("enabledAtStartup")) {
                enabledAtStartup = YConfiguration.getBoolean(m, "enabledAtStartup");
            }
            final Stream stream;
            String name = "tc" + count;
            if (m.containsKey("name")) {
                name = m.get("name").toString();
            }
            if (linksByName.containsKey(name)) {
                throw new ConfigurationException(
                        "Instance " + yamcsInstance + ": there is already a link named '" + name + "'");
            }

            if (streamName == null) {
                streamName = REALTIME_TC_STREAM_NAME;
            }
            stream = ydb.getStream(streamName);
            if (stream == null) {
                throw new ConfigurationException("Cannot find stream '" + streamName + "'");
            }

            TcDataLink tcuplinker = (args == null) ? YObjectLoader.loadObject(className, yamcsInstance, name)
                    : YObjectLoader.loadObject(className, yamcsInstance, name, args);
            if (!enabledAtStartup) {
                tcuplinker.disable();
            }

            stream.addSubscriber(new StreamSubscriber() {
                @Override
                public void onTuple(Stream s, Tuple tuple) {
                    XtceDb xtcedb = XtceDbFactory.getInstance(yamcsInstance);
                    tcuplinker.sendTc(PreparedCommand.fromTuple(tuple, xtcedb));
                }

                @Override
                public void streamClosed(Stream s) {
                    stopAsync();
                }
            });

            tcuplinker.setCommandHistoryPublisher(new StreamCommandHistoryPublisher(yamcsInstance));
            linksByName.put(name, tcuplinker);
            String json = (args != null) ? new Gson().toJson(args) : "";
            ManagementService.getInstance().registerLink(yamcsInstance, name, json, tcuplinker);
            count++;
        }
    }

    /**
     * Injects processed parameters from ParameterDataLinks into yamcs streams. To the base definition there is one
     * column for each parameter name with the type PROTOBUF({@link org.yamcs.protobuf.Pvalue.ParameterValue})
     */
    private void createParameterDataLinks(List<?> providers) throws IOException {
        int count = 1;
        for (Object o : providers) {
            if (!(o instanceof Map)) {
                throw new ConfigurationException("link has to be a Map and not a " + o.getClass());
            }

            Map<String, Object> m = (Map<String, Object>) o;

            Object args = null;
            if (m.containsKey("args")) {
                args = m.get("args");
            } else if (m.containsKey("config")) {
                args = m.get("config");
            } else if (m.containsKey("spec")) {
                log.warn("DEPRECATION ALERT: Use 'args' instead of 'spec' on TM Link configuration");
                args = m.get("spec");
            }
            String streamName = YConfiguration.getString(m, "stream");
            String linkName = "pp" + count;
            if (linksByName.containsKey(linkName)) {
                throw new ConfigurationException(
                        "Instance " + yamcsInstance + ": there is already a link named '" + linkName + "'");
            }
            boolean enabledAtStartup = true;
            if (m.containsKey("enabledAtStartup")) {
                enabledAtStartup = YConfiguration.getBoolean(m, "enabledAtStartup");
            }

            Stream stream = ydb.getStream(streamName);
            if (stream == null) {
                throw new ConfigurationException("Cannot find stream '" + streamName + "'");
            }

            ParameterDataLink link = YObjectLoader.loadObject(m, yamcsInstance, linkName);

            if (!enabledAtStartup) {
                link.disable();
            }

            link.setParameterSink(new StreamPbParameterSender(yamcsInstance, stream));

            String json = (args != null) ? new Gson().toJson(args) : "";
            ManagementService.getInstance().registerLink(yamcsInstance, linkName, json, link);
            linksByName.put(linkName, link);
            count++;
        }
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
