package org.yamcs.management;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeSent_KEY;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.TmPacket;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.client.utils.WellKnownTypes;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.cmdhistory.StreamCommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.management.LinkManager.InvalidPacketAction.Action;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.memento.MementoDb;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.parameter.SystemParametersService;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.links.LinkInfo;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.tctm.Link;
import org.yamcs.tctm.LinkMemento;
import org.yamcs.tctm.LinkState;
import org.yamcs.tctm.ParameterDataLink;
import org.yamcs.tctm.StreamPbParameterSender;
import org.yamcs.tctm.TcDataLink;
import org.yamcs.tctm.TmPacketDataLink;
import org.yamcs.time.Instant;
import org.yamcs.utils.ServiceUtil;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.Service;
import com.google.gson.Gson;

/**
 * Service that manages all the data links:
 *
 * <ul>
 * <li>is endpoint for the /links API calls</li>
 * <li>for the commanding links it will only send commands if the link is enabled. If no commanding link is enabled, a
 * negative Sent ACK will be produced.</li>
 * <li>TODO: can set exclusive flags - i.e. only one link from a group can be enabled at a time</li>
 * </ul>
 *
 * The configuration of this service is done in the "dataLinks" sections of the yamcs.&lt;instance-name&gt;.yaml file.
 *
 */
public class LinkManager {
    private static final String MEMENTO_KEY = "yamcs.links";
    public static final String PP_STREAM_KEY = "ppStream";

    private Map<String, Link> linksByName = new HashMap<>();

    private YarchDatabaseInstance ydb;
    Log log;
    final String yamcsInstance;
    Set<LinkListener> linkListeners = new CopyOnWriteArraySet<>();
    final CommandHistoryPublisher cmdHistPublisher;
    Map<Stream, TcStreamSubscriber> tcStreamSubscribers = new HashMap<>();

    // To be fully replaced by linksByName, currently still
    // used for deprecated register/unregister methods in LinkListener.
    @Deprecated
    List<LinkWithInfo> links = new CopyOnWriteArrayList<>();

    public LinkManager(String instanceName) throws ValidationException {
        this.yamcsInstance = instanceName;
        log = new Log(getClass(), instanceName);
        ydb = YarchDatabase.getInstance(instanceName);
        cmdHistPublisher = new StreamCommandHistoryPublisher(yamcsInstance);

        YamcsServerInstance instance = YamcsServer.getServer().getInstance(instanceName);
        YConfiguration instanceConfig = instance.getConfig();

        if (instanceConfig.containsKey("dataLinks")) {
            var mementoDb = MementoDb.getInstance(instanceName);
            var memento = mementoDb.getObject(MEMENTO_KEY, LinkMemento.class)
                    .orElse(new LinkMemento());

            List<YConfiguration> linkConfigs = instanceConfig.getConfigList("dataLinks");
            for (YConfiguration linkConfig : linkConfigs) {
                createDataLink(linkConfig, memento);
            }
        } else {
            log.info("No link created because the section dataLinks was not found");
        }
    }

    private void createDataLink(YConfiguration linkConfig, LinkMemento memento) throws ValidationException {
        String className = linkConfig.getString("class");
        String linkName = linkConfig.getString("name");
        if (linksByName.containsKey(linkName)) {
            throw new ConfigurationException(
                    "Instance " + yamcsInstance + ": there is already a link named '" + linkName + "'");
        }

        Link link = YObjectLoader.loadObject(className);

        Spec spec = link.getSpec();
        if (spec != null) {
            if (log.isDebugEnabled()) {
                Map<String, Object> unsafeArgs = ((YConfiguration) linkConfig).getRoot();
                Map<String, Object> safeArgs = spec.maskSecrets(unsafeArgs);
                log.debug("Raw args for {}: {}", linkName, safeArgs);
            }

            linkConfig = spec.validate((YConfiguration) linkConfig);

            if (log.isDebugEnabled()) {
                Map<String, Object> unsafeArgs = ((YConfiguration) linkConfig).getRoot();
                Map<String, Object> safeArgs = spec.maskSecrets(unsafeArgs);
                log.debug("Initializing {} with resolved args: {}", linkName, safeArgs);
            }
        }

        link.init(yamcsInstance, linkName, linkConfig);

        // Try to restore previous link state, but if enabledAtStartup
        // is explicitly configured, give priority to that setting.
        boolean enabledAtStartup = true;
        if (linkConfig.containsKey("enabledAtStartup")) {
            enabledAtStartup = linkConfig.getBoolean("enabledAtStartup");
        } else {
            var savedState = memento.getLinkState(linkName);
            if (savedState != null) {
                enabledAtStartup = savedState.isEnabled();
            }
        }

        if (!enabledAtStartup) {
            link.disable();
        }

        configureDataLink(link, linkConfig);
    }

    /**
     * Connects the links to streams
     * <p>
     * Updates the mappings which are provided via API
     * <p>
     * Can be called dynamically for example when an aggregate link updates its sub-links
     */
    public void configureDataLink(Link link, YConfiguration linkArgs) {
        if (linkArgs == null) {
            linkArgs = YConfiguration.emptyConfig();
        }
        // this is the old configuration where each link was configured one stream
        Stream singleStream = getStream(linkArgs, "stream");

        // this is the new configuration where one can specify one of each stream for a link
        // such that we can have links doing both TC and TM
        Stream tcStream = getStream(linkArgs, "tcStream");
        Stream tmStream = getStream(linkArgs, "tmStream");
        Stream ppStream = getStream(linkArgs, PP_STREAM_KEY);

        if (link instanceof TmPacketDataLink) {
            TmPacketDataLink tmLink = (TmPacketDataLink) link;
            if (tmLink.isTmPacketDataLinkImplemented()) {
                Stream streamf = tmStream == null ? singleStream : tmStream;
                if (streamf != null) {
                    InvalidPacketAction ipa = getInvalidPacketAction(link.getName(), linkArgs);
                    tmLink.setTmSink(tmPacket -> processTmPacket(tmLink, tmPacket, streamf, ipa));
                } else {
                    throw new ConfigurationException("No stream configured for parameter link " + link.getName()
                            + ". Please set a stream using the 'ppStream; option");
                }
            }
        }

        if (link instanceof TcDataLink) {
            TcDataLink tcLink = (TcDataLink) link;
            if (tcLink.isTcDataLinkImplemented()) {
                Stream stream = tcStream == null ? singleStream : tcStream;

                if (stream != null) {
                    TcStreamSubscriber tcs = tcStreamSubscribers.get(stream);
                    if (tcs == null) {
                        tcs = new TcStreamSubscriber(true);
                        tcStreamSubscribers.put(stream, tcs);
                        stream.addSubscriber(tcs);
                    }
                    tcs.addLink(tcLink);
                }
            }
            // the Yamcs gateway links will send ygw registered commands even if the isTcDataLinkImplemented
            // returns false (because it does not want to send normal MDB binary commands)
            // thats why we set the command history publisher so it can still update the command history
            tcLink.setCommandHistoryPublisher(cmdHistPublisher);
        }

        if (link instanceof ParameterDataLink) {
            ParameterDataLink ppLink = (ParameterDataLink) link;
            if (ppLink.isParameterDataLinkImplemented()) {
                Stream stream = ppStream == null ? singleStream : ppStream;
                if (stream != null) {
                    ppLink.setParameterSink(new StreamPbParameterSender(yamcsInstance, stream));
                }
            }
        }

        if (link instanceof AggregatedDataLink) {
            for (Link l : ((AggregatedDataLink) link).getSubLinks()) {
                configureDataLink(l, l.getConfig());
            }
        }

        linksByName.put(link.getName(), link);
        String json = null;
        if (!linkArgs.toMap().isEmpty()) {
            json = new Gson().toJson(linkArgs.toMap());
        }
        registerLink(link.getName(), json, link);
    }

    Stream getStream(YConfiguration linkArgs, String configKey) {
        Stream stream = null;
        String streamName = linkArgs.getString(configKey, null);
        if (streamName != null) {
            stream = ydb.getStream(streamName);
            if (stream == null) {
                throw new ConfigurationException("Cannot find stream '" + streamName + "'");
            }
        }
        return stream;
    }

    private void processTmPacket(TmPacketDataLink tmLink, TmPacket tmPacket, Stream stream, InvalidPacketAction ipa) {
        if (tmPacket.isInvalid()) {
            if (ipa.action == Action.DROP) {
                return;
            } else if (ipa.action == Action.DIVERT) {
                Tuple t = new Tuple(StandardTupleDefinitions.INVALID_TM,
                        new Object[] { tmPacket.getReceptionTime(), ipa.divertStream.getDataCount(),
                                tmPacket.getPacket() });
                ipa.divertStream.emitTuple(t);
                return;
            } // if action is PROCESS, continue below
        }

        Instant ertime = tmPacket.getEarthReceptionTime();
        Tuple t = null;
        if (ertime == Instant.INVALID_INSTANT) {
            ertime = null;
        }
        Long obt = tmPacket.getObt() == Long.MIN_VALUE ? null : tmPacket.getObt();
        String rootContainer = tmPacket.getRootContainer() != null
                ? tmPacket.getRootContainer().getQualifiedName()
                : null;
        t = new Tuple(StandardTupleDefinitions.TM, new Object[] {
                tmPacket.getGenerationTime(),
                tmPacket.getSeqCount(),
                tmPacket.getReceptionTime(),
                tmPacket.getStatus(),
                tmPacket.getPacket(),
                ertime,
                obt,
                tmLink.getName(),
                rootContainer,
        });
        stream.emitTuple(t);

    }

    private InvalidPacketAction getInvalidPacketAction(String linkName, YConfiguration linkArgs) {
        InvalidPacketAction ipa = new InvalidPacketAction();
        if (linkArgs.containsKey("invalidPackets")) {
            ipa.action = linkArgs.getEnum("invalidPackets", Action.class);
            if (ipa.action == Action.DIVERT) {
                String divertStream = linkArgs.getString("invalidPacketsStream", "invalid_tm");
                ipa.divertStream = ydb.getStream(divertStream);
                if (ipa.divertStream == null) {
                    throw new ConfigurationException("Cannot find stream '" + divertStream
                            + "' (required if invalidPackets: DIVERT has been specified)");
                }
            }
        } else {
            ipa.action = Action.DROP;
        }

        return ipa;
    }

    public void startLinks() {
        SystemParametersService collector = SystemParametersService.getInstance(yamcsInstance);

        if (collector != null) {
            linksByName.forEach((name, link) -> {
                if (link instanceof SystemParametersProducer) {
                    link.setupSystemParameters(collector);
                    collector.registerProducer((SystemParametersProducer) link);
                }
            });
        }

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
    }

    public void stopLinks() {
        linksByName.forEach((name, link) -> {
            unregisterLink(name, link);
            if (link instanceof Service) {
                ((Service) link).stopAsync();
            }
        });
        linksByName.forEach((name, link) -> {
            if (link instanceof Service) {
                log.info("Awaiting termination of link {}", link.getName());
                ServiceUtil.awaitServiceTerminated((Service) link, YamcsServer.SERVICE_STOP_GRACE_TIME, log);
            }
        });
    }

    private void registerLink(String linkName, String spec, Link link) {
        LinkInfo.Builder linkb = LinkInfo.newBuilder().setInstance(yamcsInstance)
                .setName(linkName)
                .setDisabled(link.isDisabled())
                .setStatus(link.getLinkStatus().name())
                .setType(link.getClass().getName())
                .setSpec(spec)
                .setDataInCount(link.getDataInCount())
                .setDataOutCount(link.getDataOutCount());
        if (link.getDetailedStatus() != null) {
            linkb.setDetailedStatus(link.getDetailedStatus());
        }
        var extra = link.getExtraInfo();
        if (extra != null) {
            linkb.setExtra(WellKnownTypes.toStruct(extra));
        }
        Link parent = link.getParent();
        if (parent != null) {
            linkb.setParentName(parent.getName());
        }
        LinkInfo linkInfo = linkb.build();
        links.add(new LinkWithInfo(link, linkInfo));
        linkListeners.forEach(l -> {
            l.linkAdded(link);
            l.linkRegistered(linkInfo);
        });
    }

    private void unregisterLink(String linkName, Link link) {
        Optional<LinkWithInfo> o = getLinkWithInfo(linkName);
        if (o.isPresent()) {
            LinkWithInfo lwi = o.get();
            links.remove(lwi);
            linkListeners.forEach(l -> {
                l.linkRemoved(link);
                l.linkUnregistered(lwi.linkInfo);
            });
        }
    }

    /**
     * Use {@link #getLink(String)} instead.
     */
    @Deprecated
    public Optional<LinkWithInfo> getLinkWithInfo(String linkName) {
        return links.stream()
                .filter(lwi -> linkName.equals(lwi.linkInfo.getName()))
                .findFirst();
    }

    /**
     * Adds a listener that is to be notified when any processor, or any client is updated. Calling this multiple times
     * has no extra effects. Either you listen, or you don't.
     */
    public boolean addLinkListener(LinkListener l) {
        return linkListeners.add(l);
    }

    public void enableLink(String linkName) {
        log.debug("received enableLink for {}", linkName);
        checkAndGetLink(linkName).enable();
        saveMemento();
    }

    public void disableLink(String linkName) {
        log.debug("received disableLink for {}", linkName);
        checkAndGetLink(linkName).disable();
        saveMemento();
    }

    public void resetCounters(String linkName) {
        log.debug("received resetCounters for {}", linkName);
        checkAndGetLink(linkName).resetCounters();
    }

    private void saveMemento() {
        var memento = new LinkMemento();

        for (var link : getLinks()) {
            var state = LinkState.forLink(link);
            memento.addLinkState(link.getName(), state);
        }

        var mementoDb = MementoDb.getInstance(yamcsInstance);
        mementoDb.putObject(MEMENTO_KEY, memento);
    }

    private Link checkAndGetLink(String linkName) {
        Link link = getLink(linkName);
        if (link == null) {
            throw new IllegalArgumentException(
                    "There is no link named '" + linkName + "' in instance " + yamcsInstance);
        }
        return link;
    }

    public boolean removeLinkListener(LinkListener l) {
        return linkListeners.remove(l);
    }

    public List<Link> getLinks() {
        return new ArrayList<>(linksByName.values());
    }

    /**
     * Return the link by the given name or null if there is no such link.
     */
    public Link getLink(String linkName) {
        return linksByName.get(linkName);
    }

    /**
     * What to do with invalid packets.
     */
    static class InvalidPacketAction {
        enum Action {
            /**
             * Do nothing
             */
            DROP,

            /**
             * Send packets on the normal TM stream
             */
            PROCESS,

            /**
             * Send packets on an alternate stream
             */
            DIVERT
        };

        Stream divertStream;
        Action action;
    }

    /**
     * @deprecated Access to linkInfo copy will be removed in a future release.
     */
    @Deprecated
    public class LinkWithInfo {
        final Link link;
        LinkInfo linkInfo;

        public LinkWithInfo(Link link, LinkInfo linkInfo) {
            this.link = link;
            this.linkInfo = linkInfo;
        }

        public Link getLink() {
            return link;
        }
    }

    class TcStreamSubscriber implements StreamSubscriber {
        final List<TcDataLink> tcLinks = new ArrayList<>();
        final boolean failIfNoLinkAvailable;

        public TcStreamSubscriber(boolean failIfNoLinkAvailable) {
            this.failIfNoLinkAvailable = failIfNoLinkAvailable;
        }

        void addLink(TcDataLink tcLink) {
            tcLinks.add(tcLink);
        }

        @Override
        public void onTuple(Stream s, Tuple tuple) {
            Mdb mdb = MdbFactory.getInstance(yamcsInstance);
            PreparedCommand pc = PreparedCommand.fromTuple(tuple, mdb);
            boolean sent = false;
            String reason = "no link available";
            for (TcDataLink tcLink : tcLinks) {
                if (tcLink.isCommandingAvailable()) {
                    try {
                        if (tcLink.sendCommand(pc)) {
                            sent = true;
                            break;
                        }
                    } catch (Exception e) {
                        log.error("Error sending command via link {}", tcLink, e);
                        reason = "Error sending command via " + tcLink.getName() + ": " + e.getMessage();
                    }
                }
            }

            if (!sent && failIfNoLinkAvailable) {
                CommandId commandId = pc.getCommandId();
                log.info("Failing command stream: {}, cmdId: {}, reason: {}", s.getName(), pc.getCommandId(), reason);
                long currentTime = YamcsServer.getTimeService(yamcsInstance).getMissionTime();
                cmdHistPublisher.publishAck(commandId, AcknowledgeSent_KEY,
                        currentTime, AckStatus.NOK, reason);
                cmdHistPublisher.commandFailed(commandId, currentTime, reason);
            }
        }

        @Override
        public void streamClosed(Stream s) {
            log.debug("Stream {} closed", s.getName());
        }
    }
}
