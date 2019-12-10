package org.yamcs.tctm.ccsds;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.ACK_SENT_CNAME_PREFIX;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.ccsds.AbstractTcFrameLink;
import org.yamcs.tctm.ccsds.DownlinkManagedParameters.FrameErrorCorrection;
import org.yamcs.tctm.ccsds.TcFrameFactory;

import org.yamcs.tctm.ccsds.TcTransferFrame;
import org.yamcs.utils.StringConverter;

/**
 * Sends TC as TC frames (CCSDS 232.0-B-3) or TC frames embedded in CLTU (CCSDS 231.0-B-3).
 * 
 * @author nm
 *
 */
public class UdpTcFrameLink extends AbstractTcFrameLink {
    FrameErrorCorrection errorCorrection;
    TcFrameFactory tcFrameFactory;
    String host;
    int port;
    DatagramSocket socket;
    InetAddress address;

    public UdpTcFrameLink(String yamcsInstance, String name, YConfiguration config) {
        super(yamcsInstance, name, config);
        host = config.getString("host");
        port = config.getInt("port");
       
        try {
            address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new ConfigurationException("Cannot resolve host '" + host + "'", e);
        }
    }

    @Override
    protected void startUp() throws Exception {
        super.startUp();
        socket = new DatagramSocket();
    }

    @Override
    protected void run() throws Exception {
        while (isRunning()) {
            TcTransferFrame tf = multiplexer.getFrame();
            if (tf != null) {
                byte[] data = tf.getData();
                if (log.isTraceEnabled()) {
                    log.trace("Frame data: {}", StringConverter.arrayToHexString(data, true));
                }

                if (cltuGenerator != null) {
                    data = cltuGenerator.makeCltu(data);
                    if (log.isTraceEnabled()) {
                        log.trace("CLTU: {}", StringConverter.arrayToHexString(data, true));
                    }
                }
                // Ack the BD frames
                // (note that the AD frames are acknowledged in the when the COP1 ack is received)
                if (tf.isBypass()) {
                    if (tf.getCommands() != null) {
                        for (PreparedCommand pc : tf.getCommands()) {
                            commandHistoryPublisher.publishAck(pc.getCommandId(), ACK_SENT_CNAME_PREFIX,
                                    getCurrentTime(), AckStatus.OK);
                        }
                    }
                }
                DatagramPacket dtg = new DatagramPacket(data, data.length, address, port);
                socket.send(dtg);
                frameCount++;
            }
        }
    }

    @Override
    public void triggerShutdown() {
        socket.close();
        multiplexer.quit();
    }

}
