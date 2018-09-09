package org.yamcs.tse.commander;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Connect and command a device over TCP/IP. Typical use case is an instrument with LXI support.
 * 
 * Not thread safe.
 */
public class TcpIpDevice extends Device {

    private static final int POLLING_INTERVAL = 20;

    private String host;
    private int port;

    private Socket socket;

    public TcpIpDevice(String id, String host, int port) {
        super(id);
        this.host = host;
        this.port = port;
    }

    @Override
    public void connect() throws IOException {
        if (socket != null && !socket.isClosed()) {
            return;
        }

        socket = new Socket(host, port);

        socket.setSoTimeout(POLLING_INTERVAL);
        if (responseTimeout < POLLING_INTERVAL) {
            socket.setSoTimeout((int) responseTimeout);
        }
    }

    @Override
    public void disconnect() throws IOException {
        if (socket != null) {
            socket.close();
        }
    }

    @Override
    public void write(String cmd) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write((cmd + "\n").getBytes(encoding));
        out.flush();
    }

    @Override
    public String read() throws InterruptedException, IOException {
        long time = System.currentTimeMillis();
        long timeoutTime = time + responseTimeout;

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        String responseString = null;
        while (System.currentTimeMillis() < timeoutTime) {
            try {
                int n = socket.getInputStream().read(buf);
                if (n > 0) {
                    bout.write(buf, 0, n);
                    byte[] barr = bout.toByteArray();
                    responseString = new String(barr, encoding);

                    if (responseString.endsWith("\n")) {
                        responseString = responseString.substring(0, responseString.length() - 1);
                        break;
                    }
                }
            } catch (SocketTimeoutException e) {
                // Ignore. SoTimeout is used as polling interval
                // This allows to read multiple buffers while respecting the actual intended timeout.
            }
        }

        return responseString;
    }
}
