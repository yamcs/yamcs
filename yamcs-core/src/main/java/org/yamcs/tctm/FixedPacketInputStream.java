package org.yamcs.tctm;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.yamcs.YConfiguration;
import org.yamcs.logging.Log;

/**
 * This input stream reads packets of a configurable fixed packet size.
 *
 * @author st
 *
 */
public class FixedPacketInputStream implements PacketInputStream {
    private int packetSize;
    protected DataInputStream dataInputStream;
    static Log log = new Log(FixedPacketInputStream.class);

    @Override
    public void init(InputStream inputStream, YConfiguration args) {
        this.dataInputStream = new DataInputStream(inputStream);
        this.packetSize = args.getInt("packetSize");
    }

    @Override
    public byte[] readPacket() throws IOException, PacketTooLongException {
        log.trace("Reading packet length of fixed size {}", packetSize);
        byte[] data = new byte[packetSize];
        dataInputStream.readFully(data);
        return data;
    }

    @Override
    public void close() throws IOException {
        dataInputStream.close();
    }
}
