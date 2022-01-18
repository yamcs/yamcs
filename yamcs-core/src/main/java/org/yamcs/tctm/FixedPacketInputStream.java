package org.yamcs.tctm;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;

public class FixedPacketInputStream implements PacketInputStream {
    private int packetSize;
    protected DataInputStream dataInputStream;
    static Logger log = LoggerFactory.getLogger(FixedPacketInputStream.class);

    public FixedPacketInputStream(InputStream inputStream, Map<String, Object> args) {
        this.dataInputStream = new DataInputStream(inputStream);
        this.packetSize = YConfiguration.getInt(args, "packetSize");
    }

    @Override
    public byte[] readPacket() throws IOException {
        log.trace("Reading packet length of fixed size {}", packetSize);
        byte[] data = new byte[packetSize];
        dataInputStream.readFully(data);
        return data;
    }
}
