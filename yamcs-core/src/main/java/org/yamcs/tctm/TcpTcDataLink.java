package org.yamcs.tctm;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.commanding.PreparedCommand;
import static org.yamcs.cmdhistory.CommandHistoryPublisher.*;

/**
 * Sends raw command packets on TCP socket.
 * 
 * @author nm
 *
 */
public class TcpTcDataLink extends AbstractThreadedTcDataLink {
    protected SocketChannel socketChannel;
    protected String host;
    protected int port;
    protected Selector selector;
    SelectionKey selectionKey;

    public TcpTcDataLink(String yamcsInstance, String name, YConfiguration config) throws ConfigurationException {
        super(yamcsInstance, name, config);
        configure(yamcsInstance, config);
        timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    public TcpTcDataLink(String yamcsInstance, String name, String spec) throws ConfigurationException {
        this(yamcsInstance, name, YConfiguration.getConfiguration("tcp").getConfig(spec));
    }

    private void configure(String yamcsInstance, YConfiguration config) {
        if (config.containsKey("tcHost")) {// this is when the config is specified in tcp.yaml
            host = config.getString("tcHost");
            port = config.getInt("tcPort");
        } else {
            host = config.getString("host");
            port = config.getInt("port");
        }
    }

    protected void initPostprocessor(String instance, YConfiguration config) {
        // traditionally this has used by default the ISS post-processor
        Map<String, Object> m = null;
        if (config == null) {
            m = new HashMap<>();
            config = YConfiguration.wrap(m);
        } else if (!config.containsKey("commandPostprocessorClassName")) {
            m = config.getRoot();
        }
        if (m != null) {
            log.warn("Please set the commandPostprocessorClassName for the TcpTcDataLink; in the future versions it will default to GenericCommandPostprocessor");
            m.put("commandPostprocessorClassName", IssCommandPostprocessor.class.getName());
        }
        super.initPostprocessor(instance, config);
    }

    /**
     * attempts to open the socket if not already open and returns true if its open at the end of the call
     * 
     * @return
     */
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

    protected void disconnect() {
        if (socketChannel == null) {
            return;
        }
        try {
            socketChannel.close();
            selector.close();
            socketChannel = null;
        } catch (IOException e) {
            log.warn("Exception caught when checking if the socket to {}:{} is open", host, port, e);
        }
    }

    /**
     * we check if the socket is open by trying a select on the read part of it
     * 
     * @return
     */
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
                    log.info("Data read on the TC socket to {}:{}!! : {}", host, port, bb);
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
    public Status getLinkStatus() {
        if (disabled) {
            return Status.DISABLED;
        }
        if (isSocketOpen()) {
            return Status.OK;
        } else {
            return Status.UNAVAIL;
        }
    }

    @Override
    public String getDetailedStatus() {
        if (disabled) {
            return String.format("DISABLED (should connect to %s:%d)", host, port);
        }
        if (isSocketOpen()) {
            return String.format("OK, connected to %s:%d", host, port);
        } else {
            return String.format("Not connected to %s:%d", host, port);
        }
    }

    @Override
    public void disable() {
        super.disable();
        if (isRunning()) {
            disconnect();
        }
    }

    @Override
    protected void startUp() throws Exception {
        openSocket();
    }

    @Override
    public void shutDown() throws Exception {
        disconnect();
    }

    public void uplinkCommand(PreparedCommand pc) {
        byte[] binary = cmdPostProcessor.process(pc);

        int retries = 5;
        boolean sent = false;

        ByteBuffer bb = ByteBuffer.wrap(binary);
        bb.rewind();
        String reason = null;
        while (!sent && (retries > 0)) {
            if (openSocket()) {
                try {
                    socketChannel.write(bb);
                    dataCount++;
                    sent = true;
                } catch (IOException e) {
                    reason = String.format("Error writing to TC socket to %s:%d : %s", host, port, e.getMessage());
                    log.warn(reason);
                    try {
                        if (socketChannel.isOpen()) {
                            socketChannel.close();
                        }
                        selector.close();
                        socketChannel = null;
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            } else {
                reason = String.format("Cannot connect to %s:%d : %s", host, port);
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
        }
        if (sent) {
            commandHistoryPublisher.publishWithTime(pc.getCommandId(), ACK_SENT_CNAME_PREFIX, getCurrentTime(), "OK");
        } else {
            failedCommand(pc.getCommandId(), reason);
        }
    }

    protected void doHousekeeping() {
        if (!isRunning() || disabled) {
            return;
        }
        openSocket();
    }
}
