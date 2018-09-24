package org.yamcs.tse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;

/**
 * Connect and command a device over TCP/IP. Typical use case is an instrument with LXI support.
 * 
 * Not thread safe.
 */
public class TcpIpDriver extends InstrumentDriver {

    private static final Logger log = LoggerFactory.getLogger(TcpIpDriver.class);

    private static final int POLLING_INTERVAL = 20;

    private String host;
    private int port;

    private Socket socket;

    public TcpIpDriver(String name, Map<String, Object> args) {
        super(name, args);
        host = YConfiguration.getString(args, "host");
        port = YConfiguration.getInt(args, "port");
    }

    @Override
    public void connect() throws IOException {
        if (socket != null) {
            if (socket.isConnected() && socket.isBound() && !socket.isClosed()) {
                return;
            }
            socket.close();
        }

        socket = new Socket();

        log.info("Connecting to {}:{}", host, port);
        socket.connect(new InetSocketAddress(host, port), responseTimeout);
        log.info("Connected to {}:{}", host, port);

        socket.setSoTimeout(POLLING_INTERVAL);
        if (responseTimeout < POLLING_INTERVAL) {
            socket.setSoTimeout(responseTimeout);
        }
    }

    @Override
    public void disconnect() throws IOException {
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }

    @Override
    public void write(String cmd) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write((cmd + "\n").getBytes(encoding));
        out.flush();
    }

    @Override
    public String read() throws IOException, TimeoutException {
        long time = System.currentTimeMillis();
        long timeoutTime = time + responseTimeout;

        ResponseBuilder responseBuilder = new ResponseBuilder(encoding, getResponseTermination());
        byte[] buf = new byte[4096];
        InputStream socketIn = socket.getInputStream();
        while (System.currentTimeMillis() < timeoutTime) {
            try {
                int n = socketIn.read(buf);
                if (n > 0) {
                    responseBuilder.append(buf, 0, n);
                    String response = responseBuilder.parseCompleteResponse();
                    if (response != null) {
                        return response;
                    }
                }
            } catch (SocketTimeoutException e) {
                // Ignore. SoTimeout is used as polling interval
                // This allows to read multiple buffers while respecting the actual intended timeout.
            }
        }

        String response = responseBuilder.parsePartialResponse();
        throw new TimeoutException(response != null ? "unterminated response: " + response : null);
    }
}
