package org.yamcs.cascading;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.YConfiguration;
import org.yamcs.TmPacket;
import org.yamcs.client.ClientException;
import org.yamcs.client.ContainerSubscription;
import org.yamcs.client.MessageListener;
import org.yamcs.client.YamcsClient;
import org.yamcs.protobuf.ContainerData;
import org.yamcs.protobuf.SubscribeContainersRequest;
import org.yamcs.tctm.AbstractTmDataLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.utils.TimeEncoding;

/**
 * 
 * Yamcs TM link - subscribes to realtime telemetry
 *
 */
public class YamcsTmLink extends AbstractTmDataLink {
    YamcsLink parentLink;

    public void setContainers(List<String> containers) {
        this.containers = containers;
    }

    List<String> containers;
    ContainerSubscription subscription;

    public YamcsTmLink(YamcsLink parentLink) {
        this.parentLink = parentLink;
    }

    public void init(String instance, String name, YConfiguration config) {
        config = swapConfig(config, "tmRealtimeStream", "tmStream", "tm_realtime");
        super.init(instance, name, config);
    }

    // a little bit of a hack: because we use only one config for the parent,
    // we generate a new one for the sublink with the name tmRealtimeStream/tmArchiveStream changed to tmStream
    // depending on which link it is
    static YConfiguration swapConfig(YConfiguration config, String oldKey, String newKey, String defaultValue) {
        Map<String, Object> root = new HashMap<>(config.getRoot());
        String value = (String) root.remove(oldKey);
        if (value == null) {
            value = defaultValue;
        }

        root.put(newKey, value);
        return YConfiguration.wrap(root);
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

        if (containers != null) {
            subscribeContainers();
        }
    }

    void subscribeContainers() {
        YamcsClient yclient = parentLink.getClient();

        subscription = yclient.createContainerSubscription();
        subscription.addMessageListener(new MessageListener<ContainerData>() {
            @Override
            public void onMessage(ContainerData data) {
                processContainerData(data);
            }

            public void onError(Throwable t) {
                if (t instanceof ClientException) {
                    eventProducer.sendWarning("Got error when subscribing to containers: " + t.getMessage());
                } else {
                    log.warn("Got error when subscribing to containers: " + t.getMessage());
                }
            }
        });

        subscription.sendMessage(SubscribeContainersRequest.newBuilder()
                .setInstance(parentLink.getUpstreamInstance())
                .setProcessor(parentLink.getUpstreamProcessor())
                .addAllNames(containers).build());

    }

    private void processContainerData(ContainerData data) {
        long rectime = timeService.getMissionTime();
        byte[] pktData = data.getBinary().toByteArray();

        TmPacket pkt = new TmPacket(rectime, pktData);
        if (data.hasGenerationTime()) {
            pkt.setGenerationTime(TimeEncoding.fromProtobufTimestamp(data.getGenerationTime()));
        }
        pkt.setSequenceCount(data.getSeqCount());
        packetCount.incrementAndGet();
        processPacket(pkt);
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
}
