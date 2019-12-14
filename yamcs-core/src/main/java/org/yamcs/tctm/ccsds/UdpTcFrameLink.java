package org.yamcs.tctm.ccsds;


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.ccsds.AbstractTcFrameLink;
import org.yamcs.tctm.ccsds.DownlinkManagedParameters.FrameErrorCorrection;

import org.yamcs.tctm.ccsds.TcTransferFrame;
import org.yamcs.utils.StringConverter;

/**
 * Sends TC as TC frames (CCSDS 232.0-B-3) or TC frames embedded in CLTU (CCSDS 231.0-B-3).
 * 
 * @author nm
 *
 */
public class UdpTcFrameLink extends AbstractTcFrameLink implements Runnable {
    FrameErrorCorrection errorCorrection;
    String host;
    int port;
    DatagramSocket socket;
    InetAddress address;
    Thread thread;

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
    public void run() {
        while (isRunning()&& !isDisabled()) {
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
                DatagramPacket dtg = new DatagramPacket(data, data.length, address, port);
                try {
                    socket.send(dtg);
                } catch (IOException e) {
                   log.warn("Error sending datagram", e);
                   notifyFailed(e);
                   return;
                }
                
                if (tf.isBypass()) {
                    ackBypassFrame(tf);
                }

                frameCount++;
            }
        }
    }

    @Override
    protected void doDisable() throws Exception {
        thread.interrupt();
        socket.close();
        socket = null;
    }

    @Override
    protected void doEnable() throws Exception {
        socket = new DatagramSocket();
        thread = new Thread(this);
        thread.start();
    }

    @Override
    protected void doStart() {
        try {
            doEnable();
            notifyStarted();
        } catch (Exception e) {
            notifyFailed(e);
        }
    }

    @Override
    protected void doStop() {
        try {
            doDisable();
            multiplexer.quit();
            notifyStopped();
        } catch (Exception e) {
            notifyFailed(e);
        }
    }


    @Override
    protected Status connectionStatus() {
        return Status.OK;
    }
}
