package org.yamcs.tctm;

import java.io.IOException;

/** Dispatches completed uplink frame bytes. */
public interface TcFrameTransport extends DataLinkComponent {

    void start() throws Exception;

    void stop();

    void send(byte[] data) throws IOException;

    default String getDetailedStatus() {
        return "";
    }
}
