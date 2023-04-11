package org.yamcs.tctm;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.yamcs.YConfiguration;

/**
 * Reads CCSDS packets from an input stream: first it reads 6 bytes primary header, it derives the length from the last
 * two bytes and reads the remaining of the data.
 * 
 * It also support a maxLength property to limit the size of the packet that is being read.
 * 
 * @author nm
 *
 */
public class CcsdsPacketInputStream implements PacketInputStream {
    protected DataInputStream dataInputStream;
    protected int maxPacketLength = 1500;

    @Override
    public void init(InputStream inputStream, YConfiguration args) {
        this.dataInputStream = new DataInputStream(inputStream);
        this.maxPacketLength = args.getInt("maxPacketLength", maxPacketLength);
    }

    @Override
    public byte[] readPacket() throws IOException {
        byte[] hdr = new byte[6];
        dataInputStream.readFully(hdr);
        int remaining = ((hdr[4] & 0xFF) << 8) + (hdr[5] & 0xFF) + 1;
        int pktLength = remaining + hdr.length;
        if (pktLength > maxPacketLength) {
            throw new IOException("Invalid packet read: "
                    + "packetLength (" + pktLength + ") > maxPacketLength(" + maxPacketLength + ")");
        }
        byte[] packet = new byte[pktLength];
        System.arraycopy(hdr, 0, packet, 0, hdr.length);
        dataInputStream.readFully(packet, hdr.length, remaining);
        return packet;
    }

    @Override
    public void close() throws IOException {
        dataInputStream.close();
    }
}
