package org.yamcs.tctm;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.yamcs.YConfiguration;
import org.yamcs.utils.ByteArrayUtils;

import com.google.common.primitives.Bytes;

/**
 * CFDP packet reader that splits the stream into packets based on the length of the packet
 * 
 * @author ddw
 *
 */
public class CfdpPacketInputStream implements PacketInputStream {
    DataInputStream dataInputStream;

    @Override
    public void init(InputStream inputStream, YConfiguration config) {
        this.dataInputStream = new DataInputStream(inputStream);
    }

    @Override
    public byte[] readPacket() throws IOException {
        // The size of the full packet depends on:
        // - the PDU Data field length (length specified at offset 1, 2 bytes long)
        // - the source entity ID field of the header; this field has a length of x bytes plus 1, where x is specified
        // by bits
        // 25-27 of the packet
        // - the destination entity ID field of the header; this field has a length of x bytes plus 1, where x is
        // specified by
        // bits 25-27 of the packet
        // - the transaction sequence number field of the header; this field has a length of x bytes plus 1, where x is
        // specified by bits 29-31 of the packet
        byte[] b = new byte[4];
        dataInputStream.readFully(b);
        int PDUDataFieldLength = ByteArrayUtils.decodeUnsignedShort(b, 1);
        int entityIdLength = ((b[3] >> 4) & 0x07) + 1;
        int sequenceNumberLength = (b[3] & 0x07) + 1;
        int fixedPacketHeaderLength = 4;
        int totalLength = fixedPacketHeaderLength + PDUDataFieldLength + 2 * entityIdLength + sequenceNumberLength;

        byte[] packet = new byte[totalLength - b.length];
        dataInputStream.readFully(packet);
        return Bytes.concat(b, packet);
    }

    @Override
    public void close() throws IOException {
        dataInputStream.close();
    }
}
