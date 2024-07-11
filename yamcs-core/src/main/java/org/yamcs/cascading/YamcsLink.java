package org.yamcs.cascading;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.client.ClientException;
import org.yamcs.client.ConnectionListener;
import org.yamcs.client.YamcsClient;
import org.yamcs.client.base.WebSocketClient;
import org.yamcs.client.mdb.MissionDatabaseClient;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.tctm.AbstractLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.tctm.Link;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class YamcsLink extends AbstractLink implements AggregatedDataLink, ConnectionListener {
    YamcsClient yclient;
    String upstreamInstance;
    List<Link> subLinks = new ArrayList<>();
    YamcsTmLink tmLink;
    YamcsTcLink tcLink;
    YamcsParameterLink ppLink;
    YamcsTmArchiveLink tmArchiveLink;
    YamcsEventLink eventLink;

    String upstreamName;
    String upstreamProcessor;
    ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder().setNameFormat("YamcsLink").build());

    long reconnectionDelay;

    private String username;
    private char[] password;

    @Override
    public void init(String instance, String name, YConfiguration config) {
        super.init(instance, name, config);
        this.reconnectionDelay = config.getLong("reconnectionDelay", 5000);

        yclient = YamcsClient.newBuilder(config.getString("yamcsUrl"))
                .withConnectionAttempts(config.getInt("connectionAttempts", 20))
                .withRetryDelay(reconnectionDelay)
                .withVerifyTls(config.getBoolean("verifyTls", true))
                .build();
        yclient.addConnectionListener(this);

        if (config.containsKey("username")) {
            if (config.containsKey("password")) {
                username = config.getString("username");
                password = config.getString("password").toCharArray();
            } else {
                throw new ConfigurationException("Username provided with no password");
            }
        } else if (config.containsKey("password")) {
            throw new ConfigurationException("Password provided with no username");
        }

        if (config.getBoolean("tm", true)) {
            tmLink = new YamcsTmLink(this);
            tmLink.init(instance, name + ".tm", config);
            subLinks.add(tmLink);
        }

        if (config.getBoolean("tc", true)) {
            tcLink = new YamcsTcLink(this);
            tcLink.init(instance, name + ".tc", config);
            subLinks.add(tcLink);
        }

        if (config.getBoolean("pp", true)) {
            ppLink = new YamcsParameterLink(this);
            ppLink.init(instance, name + ".pp", config);
            subLinks.add(ppLink);
        }

        if (config.getBoolean("tmArchive", true)) {
            tmArchiveLink = new YamcsTmArchiveLink(this);
            tmArchiveLink.init(instance, name + ".tmArchive", config);
            subLinks.add(tmArchiveLink);
        }

        if (config.getBoolean("event", true)) {
            eventLink = new YamcsEventLink(this);
            eventLink.init(instance, name + ".event", config);
            subLinks.add(eventLink);
        }

        this.upstreamName = config.getString("upstreamName");

        if (upstreamName.contains("<") || upstreamName.contains(">")) {
            throw new ConfigurationException("Invalid upstream name '" + upstreamName + "'. It cannot contain < or >");
        }
        this.upstreamProcessor = config.getString("upstreamProcessor", "realtime");
        this.upstreamInstance = config.getString("upstreamInstance");
    }

    @Override
    public Spec getSpec() {
        Spec spec = getDefaultSpec();
        spec.addOption("yamcsUrl", OptionType.STRING).withRequired(true)
                .withDescription("The URL to connect to the server.");

        spec.addOption("username", OptionType.STRING)
                .withDescription("Username to connect to the server");

        spec.addOption("password", OptionType.STRING).withSecret(true)
                .withDescription("Password to connect to the server");

        spec.addOption("upstreamInstance", OptionType.STRING).withRequired(true)
                .withDescription("The instance to connect to.");

        spec.addOption("upstreamProcessor", OptionType.STRING).withDefault("realtime")
                .withDescription("The processor to connect to. Default is realtime");

        spec.addOption("upstreamName", OptionType.STRING).withRequired(true)
                .withDescription(
                        "The name of the upstream Yamcs server. The name will be used in the command history entries");

        spec.addOption("verifyTls", OptionType.BOOLEAN).withDefault(true)
                .withDescription("If the connection is over SSL, "
                        + "this option can enable/disable the verification of the server certificate against local accepted CA list");

        spec.addOption("reconnectionDelay", OptionType.INTEGER).withDefault(5000)
                .withDescription("If the connection fails or breaks, "
                        + "the time (in milliseconds) to wait before reconnection.");

        spec.addOption("connectionAttempts", OptionType.INTEGER).withDefault(20)
                .withDescription(
                        "How many times to attempt reconnection if the connection fails. "
                                + "Reconnection will not be reatempted if the authentication fails. "
                                + "Link disable/enable is required to reattempt the connection");

        /*
         * TM
         */
        spec.addOption("tm", OptionType.BOOLEAN).withDefault(true)
                .withDescription("Subscribe telemetry containers (packets). "
                        + "The list of containers (packets) has to be specified using the containers option.");
        spec.addOption("tmRealtimeStream", OptionType.STRING);

        spec.addOption("tmArchive", OptionType.BOOLEAN)
                .withAliases("archiveTm") // Legacy typo
                .withDefault(true);
        spec.addOption("tmArchiveStream", OptionType.STRING);

        spec.addOption("containers", OptionType.LIST).withAliases("packets")
                .withElementType(OptionType.STRING)
                .withDescription("The list of packets to subscribe to.");

        spec.when("tm", true).requireAll("containers");

        spec.addOption("retrievalDays", OptionType.INTEGER);
        spec.addOption("mergeTime", OptionType.INTEGER);
        spec.addOption("gapFillingInterval", OptionType.INTEGER);

        /*
         * PP
         */
        spec.addOption("pp", OptionType.BOOLEAN).withDefault(true)
                .withDescription("Subscribe parameters. "
                        + "The list of parameters has to be specified using the parameters option.");
        spec.addOption("ppRealtimeStream", OptionType.STRING);
        spec.addOption("parameters", OptionType.LIST).withElementType(OptionType.STRING)
                .withDescription("The list of parameters to subscribe to.");

        /*
         * TC
         */
        spec.addOption("tc", OptionType.BOOLEAN).withDefault(true)
                .withDescription("Allow to send TC and subscribe to command history.");

        spec.addOption("keepUpstreamAcks", OptionType.LIST)
                .withElementType(OptionType.STRING)
                .withDescription("List of command acknowledgements names received "
                        + "from the upstream server to keep unmodified")
                .withDefault(List.of(CommandHistoryPublisher.CcsdsSeq_KEY));

        spec.addOption("commandMapping", OptionType.LIST).withElementType(OptionType.MAP)
                .withSpec(CommandMapData.getSpec())
                .withDescription("The mapping of commands and arguments between downstream and upstream.");
        spec.addOption("failCommandIfNoMappingMatches", OptionType.BOOLEAN).withDefault(false)
                .withDescription("Fail the command if no mapping matches");

        /*
         * EV
         */
        spec.addOption("event", OptionType.BOOLEAN).withDefault(true)
                .withDescription("Allow to subscribe to realtime events.");
        spec.addOption("eventRealtimeStream", OptionType.STRING);

        return spec;
    }

    @Override
    public Status connectionStatus() {
        WebSocketClient wsclient = yclient.getWebSocketClient();
        if (wsclient == null) {
            return Status.UNAVAIL;
        }
        return wsclient.isConnected() ? Status.OK : Status.UNAVAIL;
    }

    @Override
    public List<Link> getSubLinks() {
        return subLinks;
    }

    @Override
    public long getDataInCount() {
        long count = 0;
        for (Link l : subLinks) {
            count += l.getDataInCount();
        }
        return count;
    }

    @Override
    public long getDataOutCount() {
        return tcLink == null ? 0 : tcLink.getDataOutCount();
    }

    @Override
    public void resetCounters() {
        for (Link l : subLinks) {
            l.resetCounters();
        }
    }

    @Override
    public void doDisable() {
        WebSocketClient wsclient = yclient.getWebSocketClient();
        if (wsclient != null && wsclient.isConnected()) {
            wsclient.disconnect();
        }
    }

    @Override
    public void doEnable() {
        timer.execute(() -> connectToUpstream());
    }

    private void connectToUpstream() {
        WebSocketClient wsclient = yclient.getWebSocketClient();
        if (wsclient != null && wsclient.isConnected()) {
            // we have to protect against double connection because there might be a timer and a user action that causes
            // it to come in here
            return;
        }

        try {
            if (username != null) {
                yclient.login(username, password);
            }
            yclient.connectWebSocket();
        } catch (ClientException cause) {
            log.warn("Connection to upstream Yamcs server failed", cause);
            eventProducer.sendWarning("Connection to upstream Yamcs failed: " + cause);
            return;
        }

        if (tmLink != null || tmArchiveLink != null) {
            retrieveContainers();
        }

        if (tcLink != null && !tcLink.isDisabled()) {
            tcLink.doEnable();
        }

        if (ppLink != null && !ppLink.isDisabled()) {
            ppLink.doEnable();
        }

        if (eventLink != null && !eventLink.isDisabled()) {
            eventLink.doEnable();
        }
    }

    private void retrieveContainers() {
        MissionDatabaseClient mdbClient = getClient().createMissionDatabaseClient(getUpstreamInstance());
        ContainerFetcher.fetchAndMatch(mdbClient, config.getList("containers"), log)
                .whenComplete((list, t) -> {
                    if (t != null) {
                        log.warn("Failed to fetch containers from remote: {}", t);
                    }

                    if (tmLink != null) {
                        tmLink.setContainers(list);
                        if (!tmLink.isDisabled()) {
                            tmLink.subscribeContainers();
                        }
                    }
                    if (tmArchiveLink != null) {
                        tmArchiveLink.setContainers(list);
                        if (!tmArchiveLink.isDisabled()) {
                            tmArchiveLink.scheduleDataRetrieval();
                        }
                    }
                });
    }

    @Override
    protected void doStart() {
        if (!isDisabled()) {
            doEnable();
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        yclient.close();
        notifyStopped();
    }

    @Override
    public void connecting() {
        log.debug("Connecting to upstream Yamcs server");
    }

    @Override
    public void connected() {
        log.debug("Connected to upstream Yamcs server");
    }

    @Override
    public void connectionFailed(Throwable cause) {
        eventProducer.sendWarning("Connection to upstream Yamcs failed: " + cause);
    }

    @Override
    public void disconnected() {
        if (isRunningAndEnabled()) {
            log.warn("Disconnected from upstream Yamcs server");
            timer.schedule(() -> connectToUpstream(), reconnectionDelay, TimeUnit.MILLISECONDS);
        } else {
            log.debug("Disconnected from upstream Yamcs server");
        }
    }

    YamcsClient getClient() {
        return yclient;
    }

    String getUpstreamName() {
        return upstreamName;
    }

    String getUpstreamInstance() {
        return upstreamInstance;
    }

    String getUpstreamProcessor() {
        return upstreamProcessor;
    }

    public ScheduledThreadPoolExecutor getExecutor() {
        return timer;
    }
}
