package org.yamcs.tctm.ccsds;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.utils.StringConverter;

import com.google.common.util.concurrent.RateLimiter;

/**
 * Sends TC as TC frames (CCSDS 232.0-B-3) or TC frames embedded in CLTU (CCSDS 231.0-B-3).
 * <p>
 * This class implements rate limiting.
 * args:
 * <ul>
 * <li>frameMaxRate: maximum number of command frames to send per second.</li>
 * </ul>
 * 
 * @author a.laborie (from nm)
 *
 */
public class TcpTcFrameLink extends AbstractTcFrameLink implements Runnable {
    String host;
    int port;    
    SocketChannel socketChannel; 
    Selector selector;
    SelectionKey selectionKey;
    InetAddress address;
    Thread thread;
    RateLimiter rateLimiter;

    public void init(String yamcsInstance, String name, YConfiguration config) {
        super.init(yamcsInstance, name, config);
        host = config.getString("host");
        port = config.getInt("port");

        try {
            address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new ConfigurationException("Cannot resolve host '" + host + "'", e);
        }
        if (config.containsKey("frameMaxRate")) {
            rateLimiter = RateLimiter.create(config.getDouble("frameMaxRate"), 1, TimeUnit.SECONDS);
        }
        openSocket();
    }

    protected synchronized boolean openSocket() {
        if (isSocketOpen()) {
            return true;
        }
        try {        

            InetAddress address = InetAddress.getByName(host);
            selector = Selector.open();
            socketChannel = SocketChannel.open(new InetSocketAddress(address, port));
            socketChannel.configureBlocking(false);
            socketChannel.socket().setKeepAlive(true);
            selectionKey = socketChannel.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);
            log.info("Link established to {}:{}", host, port);
            return true;
        } catch (IOException e) {
            String exc = (e instanceof ConnectException) ? ((ConnectException) e).getMessage() : e.toString();
            log.info("Cannot connect to {}:{} '{}'. Retrying in 10s", host, port, exc.toString());
            try {
                socketChannel.close();
            } catch (Exception e1) {
            }
            try {
                selector.close();
            } catch (Exception e1) {
            }
            socketChannel = null;
        }
        return false;
    }

    private synchronized boolean isSocketOpen() {
        if (socketChannel == null) {
            return false;
        }
        final ByteBuffer bb = ByteBuffer.allocate(16);
        boolean connected = false;
        try {
            selector.select();
            if (selectionKey.isReadable()) {
                int read = socketChannel.read(bb);
                if (read > 0) {
                    log.info("Data read on the TC socket to {}:{} : {}", host, port, bb);
                    connected = true;
                } else if (read < 0) {
                    log.warn("TC socket to {}:{} has been closed", host, port);
                    socketChannel.close();
                    selector.close();
                    socketChannel = null;
                    connected = false;
                }
            } else if (selectionKey.isWritable()) {
                connected = true;
            } else {
                log.warn("The TC socket to {}:{} is neither writable nor readable", host, port);
                connected = false;
            }
        } catch (IOException e) {
            log.warn("Exception caught when checking if the socket to {}:{} is open:", host, port, e);
            connected = false;
        }
        return connected;
    }

    @Override
    public void run() {
        while (isRunningAndEnabled()) {
            if (rateLimiter != null) {
                rateLimiter.acquire();
            }

            TcTransferFrame tf = multiplexer.getFrame();
            if (tf != null) {
                byte[] data = tf.getData();

                if (log.isTraceEnabled()) {
                    log.trace("Outgoing frame data: {}", StringConverter.arrayToHexString(data, true));
                }

                if (cltuGenerator != null) {
                    data = encodeCltu(tf.getVirtualChannelId(), data);

                    if (log.isTraceEnabled()) {
                        log.trace("Outgoing CLTU: {}", StringConverter.arrayToHexString(data, true));
                    }
                }
                int retries = 5;
                boolean sent = false;

                ByteBuffer bb = ByteBuffer.wrap(data); 
                bb.rewind();
                String reason = null;
                openSocket();
                while (!sent && (retries > 0)) {
                    if (isSocketOpen()) { //isSocketOpen() opens the socket if it is not
                        try {
                            socketChannel.write(bb);
                            sent = true;
                        } catch (IOException e) {
                            reason = String.format("Error writing to TC TCP socket to %s:%d : %s", host, port, e.toString());
                            log.warn(reason);
                            try {
                                if (socketChannel.isOpen()) {
                                    socketChannel.close();
                                }
                                selector.close();
                                socketChannel = null;
                            } catch (IOException e1) {
                                log.warn("Exception caught when checking if the socket to {}:{} is open:", host, port, e);
                            }
                        }
                    } else {
                        reason = String.format("Cannot connect to %s:%d", host, port);
                    }
                    retries--;
                    if (!sent && (retries > 0)) {
                        try {
                            log.warn("Command not sent, retrying in 2 seconds");
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            log.warn("exception {} thrown when sleeping 2 sec", e.toString());
                            Thread.currentThread().interrupt();
                        }
                    }
                    if (tf.isBypass()) {
                        ackBypassFrame(tf);
                    }
                    if (sent){
                        frameCount++;
                    }
                }
            }
        }
    }

    @Override
    protected void doDisable() throws Exception {
        if (thread != null) {
            super.doDisable();
        }
        if (socketChannel != null) {
            socketChannel.close();
            socketChannel = null;
        }
    }

    @Override
    protected void doEnable() throws Exception {
        thread = new Thread(this);
        super.doEnable();
        openSocket();

    }

    @Override
    protected void doStart() {
        try {
            doEnable();
            notifyStarted();
        } catch (Exception e) {
            log.warn("Exception starting link", e);
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
            log.warn("Exception stopping link", e);
            notifyFailed(e);
        }
    }
        
    @Override
    protected Status connectionStatus() {
        return (socketChannel == null) ? Status.UNAVAIL : Status.OK;
    }

    @Override
    public String getDetailedStatus() {
        if (isDisabled()) {
            return String.format("DISABLED (should connect to %s:%d)", host, port);
        }
        if (isSocketOpen()) {
            return String.format("OK, connected to %s:%d", host, port);
        } else {
            return String.format("Not connected to %s:%d", host, port);
        }
    }

}
