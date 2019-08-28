package org.yamcs.tctm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

/**
 * Receives PP data via UDP.
 * 
 * The UDP packets are protobuf encoded ParameterData. We don't use any checksum, assume it's done by UDP.
 * 
 * @author nm
 *
 */
public class UdpParameterDataLink extends AbstractExecutionThreadService implements ParameterDataLink {

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
    final String name;

    /**
     * Creates a new UDP data link
     * 
     * @param instance
     * @param name
     * @param config
     * @throws ConfigurationException
     *             if port is not defined in the config
     */
    public UdpParameterDataLink(String instance, String name, YConfiguration config)
            throws ConfigurationException {
        this.config = config;
        this.name = name;
        log = new Log(getClass(), instance);
        log.setContext(name);
        timeService = YamcsServer.getTimeService(instance);
        port = config.getInt("port");
        defaultRecordingGroup = config.getString("recordingGroup", "DEFAULT");
    }

    @Override
    public void startUp() throws IOException {
        udpSocket = new DatagramSocket(port);
    }

    @Override
    public void run() {
        while (isRunning()) {
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

            for (ParameterValue pval : pdata.getParameterList()) {
                long gentime = now;
                if (pval.hasGenerationTimeUTC()) {
                    gentime = TimeEncoding.parse(pval.getGenerationTimeUTC());
                    pval = ParameterValue.newBuilder(pval).clearGenerationTimeUTC().setGenerationTime(gentime).build();
                } else if (!pval.hasGenerationTime()) {
                    pval = ParameterValue.newBuilder(pval).setGenerationTime(now).build();
                }

                List<ParameterValue> pvals = valuesByTime.computeIfAbsent(gentime, x -> new ArrayList<>());
                pvals.add(pval);
            }

            for (Entry<Long, List<ParameterValue>> group : valuesByTime.entrySet()) {
                parameterSink.updateParams(group.getKey(), recgroup, sequenceNumber, group.getValue());
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

        while (isRunning() && disabled) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        try {
            udpSocket.receive(datagram);
            ParameterData.Builder pdb = ParameterData.newBuilder().mergeFrom(datagram.getData(), datagram.getOffset(),
                    datagram.getLength());
            validDatagramCount++;
            return pdb.build();
        } catch (IOException e) {
            log.warn("Exception when receiving parameter data: {}'", e.getMessage());
            invalidDatagramCount++;
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
