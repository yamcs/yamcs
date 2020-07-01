package org.yamcs.tctm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.time.TimeService;

import com.google.common.util.concurrent.AbstractService;

/**
 * Receives PP data via UDP.
 * 
 * The UDP packets are protobuf encoded ParameterData. We don't use any checksum, assume it's done by UDP.
 * 
 * @author nm
 *
 */
public class UdpParameterDataLink extends AbstractService implements ParameterDataLink, Runnable {

    private volatile int validDatagramCount = 0;
    private volatile int invalidDatagramCount = 0;
    private volatile boolean disabled = false;

    private int sequenceCount = 0;

    private TimeService timeService;
    private DatagramSocket udpSocket;
    private int port = 31002;
    private String defaultRecordingGroup;

    ParameterSink parameterSink;

    private Log log;
    int MAX_LENGTH = 10 * 1024;

    DatagramPacket datagram = new DatagramPacket(new byte[MAX_LENGTH], MAX_LENGTH);
    YConfiguration config;
    String name;

    private ScheduledThreadPoolExecutor timer;

    @Override
    public void init(String instance, String name, YConfiguration config) {
        this.config = config;
        this.name = name;
        log = new Log(getClass(), instance);
        log.setContext(name);
        timeService = YamcsServer.getTimeService(instance);
        port = config.getInt("port");
        defaultRecordingGroup = config.getString("recordingGroup", "DEFAULT");
    }

    @Override
    protected void doStart() {
        if (!isDisabled()) {
            timer = new ScheduledThreadPoolExecutor(1);
            try {
                udpSocket = new DatagramSocket(port);
                new Thread(this).start();
            } catch (SocketException e) {
                notifyFailed(e);
            }
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        udpSocket.close();
        timer.shutdown();
        notifyStopped();
    }

    private boolean isRunningAndEnabled() {
        State state = state();
        return (state == State.RUNNING || state == State.STARTING) && !disabled;
    }

    @Override
    public void run() {
        while (isRunningAndEnabled()) {
            ParameterData pdata = getNextData();
            if (pdata == null) {
                continue;
            }

            if (pdata.hasGenerationTime()) {
                log.error("Generation time must be specified for each parameter separately");
                continue;
            }

            long now = timeService.getMissionTime();
            String recgroup = pdata.hasGroup() ? pdata.getGroup() : defaultRecordingGroup;
            int sequenceNumber = pdata.hasSeqNum() ? pdata.getSeqNum() : sequenceCount++;

            // Regroup by gentime, just in case multiple parameters are submitted with different times.
            Map<Long, List<ParameterValue>> valuesByTime = new LinkedHashMap<>();

            for (Pvalue.ParameterValue gpv : pdata.getParameterList()) {
                NamedObjectId id = gpv.getId();
                if (id == null) {
                    log.warn("parameter without id, skipping");
                    continue;
                }
                String fqn = id.getName();
                if (id.hasNamespace()) {
                    log.trace("Using namespaced name for parameter {} because fully qualified name not available.", id);
                }
                ParameterValue pv = ParameterValue.fromGpb(fqn, gpv);
                long gentime = gpv.hasGenerationTime() ? pv.getGenerationTime() : now;
                pv.setGenerationTime(gentime);

                List<ParameterValue> pvals = valuesByTime.computeIfAbsent(gentime, x -> new ArrayList<>());
                pvals.add(pv);
            }

            for (Entry<Long, List<ParameterValue>> group : valuesByTime.entrySet()) {
                parameterSink.updateParameters((long) group.getKey(), recgroup, sequenceNumber, group.getValue());
            }
        }
    }

    /**
     * 
     * Called to retrieve the next packet. It blocks in reading on the UDP socket.
     * 
     * @return anything that looks as a valid packet, just the size is taken into account to decide if it's valid or not
     */
    public ParameterData getNextData() {
        while (isRunning()) {
            try {
                udpSocket.receive(datagram);
                validDatagramCount++;
                return ParameterData.newBuilder()
                        .mergeFrom(datagram.getData(), datagram.getOffset(), datagram.getLength())
                        .build();
            } catch (IOException e) {
                // Shutdown or disable will close the socket. That generates an exception
                // which we ignore here.
                if (!isRunning() || isDisabled()) {
                    return null;
                }
                log.warn("Exception when receiving parameter data: {}'", e.getMessage());
                invalidDatagramCount++;
            }
        }
        return null;
    }

    @Override
    public Status getLinkStatus() {
        return disabled ? Status.DISABLED : Status.OK;
    }

    /**
     * Returns statistics with the number of datagrams received and the number of invalid datagrams
     */
    @Override
    public String getDetailedStatus() {
        if (disabled) {
            return "DISABLED";
        } else {
            return String.format("OK (%s)\nValid datagrams received: %d\nInvalid datagrams received: %d",
                    port, validDatagramCount, invalidDatagramCount);
        }
    }

    /**
     * Sets the disabled to true such that getNextPacket ignores the received datagrams
     */
    @Override
    public void disable() {
        disabled = true;
    }

    /**
     * Sets the disabled to false such that getNextPacket does not ignore the received datagrams
     */
    @Override
    public void enable() {
        disabled = false;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public long getDataInCount() {
        return validDatagramCount;
    }

    @Override
    public long getDataOutCount() {
        return 0;
    }

    @Override
    public void resetCounters() {
        validDatagramCount = 0;
    }

    @Override
    public void setParameterSink(ParameterSink parameterSink) {
        this.parameterSink = parameterSink;
    }

    @Override
    public YConfiguration getConfig() {
        return config;
    }

    @Override
    public String getName() {
        return name;
    }
}
