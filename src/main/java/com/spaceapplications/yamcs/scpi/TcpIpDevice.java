package com.spaceapplications.yamcs.scpi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class TcpIpDevice extends Device {

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
        socket = new Socket(host, port);
        socket.setSoTimeout(20); // Use this as 'polling period'
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
                // Ignore. SoTimeout is used polling interval
                // This allows to read multiple buffers within the actual intended timeout.
            }
        }

        return responseString;
    }
}
