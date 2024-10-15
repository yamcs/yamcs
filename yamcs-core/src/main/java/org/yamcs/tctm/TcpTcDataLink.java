package org.yamcs.tctm;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.commanding.PreparedCommand;

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

    @Override
    public Spec getSpec() {
        var spec = getDefaultSpec();
        spec.addOption("host", OptionType.STRING).withRequired(true);
        spec.addOption("port", OptionType.INTEGER).withRequired(true);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, String name, YConfiguration config) throws ConfigurationException {
        super.init(yamcsInstance, name, config);
        configure(yamcsInstance, config);
        timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    private void configure(String yamcsInstance, YConfiguration config) {
        host = config.getString("host");
        port = config.getInt("port");
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
        } catch (CancelledKeyException | ClosedSelectorException e) {
            // May happen during shutdown, don't be too verbose about it
            log.debug(e.getMessage());
            connected = false;
        }
        return connected;
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

    @Override
    protected void startUp() {
        if (!isDisabled()) {
            openSocket();
        }
    }

    @Override
    public void shutDown() throws Exception {
        disconnect();
    }

    @Override
    public void uplinkCommand(PreparedCommand pc) {
        byte[] binary = postprocess(pc);
        if (binary == null) {
            return;
        }

        int retries = 5;
        boolean sent = false;

        ByteBuffer bb = ByteBuffer.wrap(binary);
        bb.rewind();
        String reason = null;
        while (!sent && (retries > 0)) {
            if (openSocket()) {
                try {
                    socketChannel.write(bb);
                    dataOut(1, binary.length);
                    sent = true;
                } catch (IOException e) {
                    reason = String.format("Error writing to TC socket to %s:%d : %s", host, port, e.toString());
                    log.warn(reason);
                    try {
                        if (socketChannel.isOpen()) {
                            socketChannel.close();
                        }
                        selector.close();
                        socketChannel = null;
                    } catch (IOException e1) {
                        // ignore any close exception
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
        }
        if (sent) {
            ackCommand(pc.getCommandId());
        } else {
            failedCommand(pc.getCommandId(), reason);
        }
    }

    @Override
    protected void doHousekeeping() {
        if (!isRunningAndEnabled()) {
            return;
        }
        openSocket();
    }

    @Override
    protected Status connectionStatus() {
        if (isSocketOpen()) {
            return Status.OK;
        } else {
            return Status.UNAVAIL;
        }
    }
}
