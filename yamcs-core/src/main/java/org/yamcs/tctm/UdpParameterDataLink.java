package org.yamcs.tctm;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;

import com.google.protobuf.util.JsonFormat;

/**
 * Receives PP data via UDP.
 * 
 * The UDP packets are protobuf encoded ParameterData. We don't use any checksum, assume it's done by UDP.
 * 
 * @author nm
 *
 */
public class UdpParameterDataLink extends AbstractParameterDataLink implements Runnable {

    private volatile int validDatagramCount = 0;
    private volatile int invalidDatagramCount = 0;

    private int sequenceCount = 0;

    private DatagramSocket udpSocket;
    private int port = 31002;
    private String defaultRecordingGroup;
    private Format format;

    int MAX_LENGTH = 10 * 1024;

    DatagramPacket datagram = new DatagramPacket(new byte[MAX_LENGTH], MAX_LENGTH);

    @Override
    public Spec getSpec() {
        var spec = getDefaultSpec();
        spec.addOption("port", OptionType.INTEGER).withRequired(true);
        spec.addOption("recordingGroup", OptionType.STRING).withDefault("DEFAULT");
        spec.addOption("json", OptionType.BOOLEAN).withDefault(false);
        return spec;
    }

    @Override
    public void init(String instance, String name, YConfiguration config) {
        super.init(instance, name, config);
        port = config.getInt("port");
        defaultRecordingGroup = config.getString("recordingGroup", "DEFAULT");
        format = config.getBoolean("json", false) ? Format.JSON : Format.PROTOBUF;
    }

    @Override
    protected void doStart() {
        if (!isDisabled()) {
            try {
                udpSocket = new DatagramSocket(port);
                Thread thread = new Thread(this);
                thread.setName(getClass().getSimpleName() + "-" + linkName);
                thread.start();
            } catch (SocketException e) {
                notifyFailed(e);
                return;
            }
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        if (udpSocket != null) {
            udpSocket.close();
        }
        notifyStopped();
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
                ParameterValue pv = BasicParameterValue.fromGpb(fqn, gpv);
                long gentime = gpv.hasGenerationTime() ? pv.getGenerationTime() : now;
                pv.setGenerationTime(gentime);

                List<ParameterValue> pvals = valuesByTime.computeIfAbsent(gentime, x -> new ArrayList<>());
                pvals.add(pv);
            }

            for (Entry<Long, List<ParameterValue>> group : valuesByTime.entrySet()) {
                updateParameters((long) group.getKey(), recgroup, sequenceNumber, group.getValue());
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
                ParameterData pd = decodeDatagram(datagram.getData(), datagram.getOffset(), datagram.getLength());
                dataIn(pd.getParameterCount(), datagram.getLength());

                return pd;
            } catch (IOException e) {
                // Shutdown or disable will close the socket. That generates an exception
                // which we ignore here.
                if (!isRunning() || isDisabled()) {
                    return null;
                }
                log.warn("Exception when receiving parameter data: {}'", e.toString());
                dataIn(0, datagram.getLength());
                invalidDatagramCount++;
            }
        }
        return null;
    }

    /**
     * Decode {@link ParameterData} from the content of a single received UDP Datagram.
     * <p>
     * {@link UdpParameterDataLink} has configurable support for either Protobuf or JSON-encoded data. Extending links
     * may provide a custom decoder by overriding this method.
     * 
     * @param data
     *            data buffer. The data received starts from {@code offset} and runs for {@code length} long.
     * @param offset
     *            offset of the data received
     * @param length
     *            length of the data received
     */
    public ParameterData decodeDatagram(byte[] data, int offset, int length) throws IOException {
        switch (format) {
        case JSON:
            try (Reader reader = new InputStreamReader(new ByteArrayInputStream(data, offset, length))) {
                ParameterData.Builder builder = ParameterData.newBuilder();
                JsonFormat.parser().merge(reader, builder);
                return builder.build();
            }
        case PROTOBUF:
            return ParameterData.newBuilder()
                    .mergeFrom(data, offset, length)
                    .build();
        default:
            throw new IllegalStateException("Unexpected format " + format);
        }
    }

    @Override
    public Status connectionStatus() {
        return Status.OK;
    }

    @Override
    public String getDetailedStatus() {
        if (isDisabled()) {
            return "DISABLED (should receive on " + port + ")";
        } else {
            return "OK, receiving on " + port;
        }
    }

    @Override
    public Map<String, Object> getExtraInfo() {
        var extra = new LinkedHashMap<String, Object>();
        extra.put("Valid datagrams", validDatagramCount);
        extra.put("Invalid datagrams", invalidDatagramCount);
        return extra;
    }

    @Override
    protected void doEnable() throws Exception {
        udpSocket = new DatagramSocket(port);
        Thread thread = new Thread(this);
        thread.setName(getClass().getSimpleName() + "-" + linkName);
        thread.start();
    }

    @Override
    protected void doDisable() throws Exception {
        if (udpSocket != null) {
            udpSocket.close();
            udpSocket = null;
        }
    }

    @Override
    public void resetCounters() {
        super.resetCounters();
        validDatagramCount = 0;
        invalidDatagramCount = 0;
    }

    /**
     * Default supported data formats
     */
    private static enum Format {
        JSON,
        PROTOBUF;
    }
}
