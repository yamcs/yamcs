package org.yamcs.yarch;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class OutputStream extends Stream implements StreamSubscriber {
    ServerSocket serverSocket;
    Socket socket;
    Stream subscribedStream;
    java.io.DataOutputStream dos;

    public OutputStream(YarchDatabaseInstance dict, String name, TupleDefinition def) throws YarchException {
        super(dict, name, def);
        try {
            serverSocket = new ServerSocket(0);
            log.info("Created output stream {} listening to port {}", this, getPort());
        } catch (IOException e) {
            throw new YarchException(e);
        }
    }

    public void setSubscribedStream(Stream s) {
        this.subscribedStream = s;
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    @Override
    public void onTuple(Stream s, Tuple t) {
        try {
            if (socket == null) {
                socket = serverSocket.accept();
            }
            dos = new java.io.DataOutputStream(socket.getOutputStream()); // TODO endinaness
        } catch (IOException e) {
            return;
        }
        /*
         * log.trace("Outputing tuple: {}",t);
         * try {
         * getDefinition().write(dos,t);
         * } catch (IOException e) {
         * e.printStackTrace();
         * socket=null;
         * }
         */
    }

    /**
     * Called when the subcribed stream is closed we close this stream also.
     */
    @Override
    public void streamClosed(Stream stream) {
        close();
    }

    @Override
    public void doClose() {
        subscribedStream.removeSubscriber(this);
        try {
            serverSocket.close();
        } catch (IOException e) {
            log.error("got exception when closing the output stream socket: ", e);
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                log.warn("got exception when closing the output stream socket:", e);
            }
        }
    }

    @Override
    public void doStart() {
        // does nothing.
    }

    @Override
    public String toString() {
        return "OUTPUT STREAM " + name + "(" + outputDefinition.toString() + ")";
    }

}
