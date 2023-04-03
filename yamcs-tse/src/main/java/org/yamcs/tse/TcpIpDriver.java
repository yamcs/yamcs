package org.yamcs.tse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;

/**
 * Connect and command a device over TCP/IP. Typical use case is an instrument with LXI support.
 * 
 * Not thread safe.
 */
public class TcpIpDriver extends InstrumentDriver {

    /*
     * One idea that is not currently implemented (awaiting business need) is support for
     * an argument called "pingCommand" that could be used to proactively detect a non-clean
     * remote socket disconnect. For example:
     * 
     * class: org.yamcs.tse.TcpIpDriver
     * args:
     *   ...
     *   pingCommand: "*OPC?"
     *   pingInterval: 5000
     * 
     * The pingCommand would need to return any response. If it does not within responseTimeout,
     * then we proactively disconnect the socket.
     * 
     * Without pingCommand, abrupt closure of a remote socket is only very slowly discovered
     * and may lead to multiple commands being written to the send buffer before generating any
     * errors. Queries (that expect a response) lead to only one failed command, but even that may
     * still be too much.
     */

    private static final Logger log = LoggerFactory.getLogger(TcpIpDriver.class);

    private String host;
    private int port;

    private SocketChannel socketChannel;
    private Selector selector;
    private SelectionKey selectionKey;

    @Override
    public void init(String name, YConfiguration config) {
        super.init(name, config);
        host = config.getString("host");
        port = config.getInt("port");
    }

    @Override
    public void connect() throws IOException {
        if (socketChannel != null) {
            Socket socket = socketChannel.socket();
            if (socket.isConnected() && socket.isBound() && !socket.isClosed()) {
                return;
            }
            disconnect();
        }

        log.info("Connecting to {}:{}", host, port);

        selector = Selector.open();
        socketChannel = SocketChannel.open();
        socketChannel.socket().setKeepAlive(true);
        socketChannel.socket().connect(new InetSocketAddress(host, port), responseTimeout);

        socketChannel.configureBlocking(false);
        selectionKey = socketChannel.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);
        log.info("Connected to {}:{}", host, port);
    }

    @Override
    public void disconnect() throws IOException {
        if (socketChannel != null) {
            socketChannel.close();
            selector.close();

            socketChannel = null;
        }
    }

    @Override
    public String getDefaultRequestTermination() {
        return "\n";
    }

    @Override
    public void write(byte[] cmd) throws IOException {
        boolean sent = false;
        while (!sent) {
            selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            selector.select();
            if (selectionKey.isReadable()) {
                // Discard any pending reads before writing.
                // For example: a previous query command whose response was ignored.
                ByteBuffer buf = ByteBuffer.allocate(4096);
                try {
                    int n = socketChannel.read(buf);
                    while (n > 0) {
                        buf.clear();
                        n = socketChannel.read(buf);
                    }

                    // Apparent end of stream, attempt new socket
                    if (n < 0) {
                        throw new IOException("end-of-stream");
                    }
                } catch (IOException e) { // Either n < 0, or some deeper error like 'timeout' thrown by the read()
                    log.warn(e.getMessage());
                    disconnect();
                    connect();
                }
            }
            if (selectionKey.isWritable()) {
                try {
                    ByteBuffer bb = ByteBuffer.wrap(cmd);
                    socketChannel.write(bb); // TODO write remainder in case of partial write
                    sent = true;
                } catch (IOException e) { // Ex.: Broken pipe
                    log.warn(e.getMessage());
                    disconnect();
                    connect();
                }
            }
        }
    }

    @Override
    public void readAvailable(ResponseBuffer responseBuffer, int timeout) throws IOException {
        selectionKey.interestOps(SelectionKey.OP_READ);

        // Block this on a small timeout only. Otherwise we may block longer than the
        // global timeout managed by the caller.
        selector.select(timeout);
        if (selectionKey.isReadable()) {
            ByteBuffer buf = ByteBuffer.allocate(4096);
            int n = socketChannel.read(buf);
            if (n > 0) {
                responseBuffer.append(buf.array(), 0, n);
            } else if (n < 0) {
                // Apparent end-of-stream. Nothing we can do about it in read mode.
                disconnect();
                throw new IOException("end-of-stream");
            }
        }
    }

    @Override
    public boolean isFragmented() {
        return true;
    }
}
