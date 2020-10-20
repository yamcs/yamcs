package org.yamcs.tctm;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.yamcs.YConfiguration;
import org.yamcs.utils.DeprecationInfo;

/**
 * Plays files in PaCTS format, HRDP format or containing raw CCSSDS ISS packets.
 * <p>
 * Used in the context of ISS/Columbus ground control; it should eventually be removed from the Yamcs core.
 * 
 * @author nm
 *
 */
@Deprecated
@DeprecationInfo(info = "this class will be moved outside of yamcs-core because it is specific to ISS/Columbus ground environment. "
        + "Please use the GenericPacketInputStream or the CcsdsPacketInputStream instead.")
public class UsocPacketInputStream implements PacketInputStream {
    protected InputStream inputStream;
    int fileoffset = 0;
    int packetcount = 0;
    int maxPacketLength;

    
    @Override
    public void init(InputStream inputStream, YConfiguration args) {
        this.inputStream = inputStream;
        this.maxPacketLength = args.getInt("maxPacketLength", 1500);
    }

    @Override
    public byte[] readPacket() throws IOException, PacketTooLongException {
        int res;
        byte[] buffer;
        byte[] fourb = new byte[4];
        res = inputStream.read(fourb);
        if (res == -1) {
            inputStream.close();
            return null;
        } else if (res != 4) {
            throw new IOException("Could only read " + res + " out of 4 bytes. Corrupted file?");
        }

        byte[] ccsdshdr = new byte[16];
        int ccsdshdroffset = 0;
        boolean isPacts = false;

        if ((fourb[2] == 0) && (fourb[3] == 0)) {// hrdp packet: first 4 bytes are the size in little endian
            byte[] b = new byte[6];
            res = inputStream.read(b);
            if (res != 6) {
                throw new IOException("Could only read " + res + " out of 6 bytes. Corrupted file?");
            }
        } else if ((fourb[0] & 0xe8) == 0x08) {// CCSDS packet
            System.arraycopy(fourb, 0, ccsdshdr, 0, 4);
            ccsdshdroffset = 4;
        } else {// pacts packet
            isPacts = true;
            // read ASCII header up to the second blank
            int i, j;
            StringBuilder hdr = new StringBuilder();
            j = 0;
            for (i = 0; i < 4; i++) {
                hdr.append((char) fourb[i]);
                if (fourb[i] == 32) {
                    ++j;
                }
            }
            while ((j < 2) && (i < 20)) {
                int c = inputStream.read();
                if (c == -1) {
                    inputStream.close();
                    throw new IOException("short PaCTS ASCII header: '" + hdr.toString() + "'");
                }
                hdr.append((char) c);
                if (c == 32) {
                    ++j;
                }
                i++;
            }

            if (i == 20) {
                inputStream.close();
                throw new IOException(
                        "ASCII header too long, probably not a PaCTS archive file: '" + hdr.toString() + "'");
            }
        }

        res = inputStream.read(ccsdshdr, ccsdshdroffset, 16 - ccsdshdroffset);
        if (res != 16 - ccsdshdroffset) {
            inputStream.close();
            throw new IOException("CCSDS packet header short read " + res + "/16-ccsdshdroffset");
        }
        int len = ((ccsdshdr[4] & 0xff) << 8) + (ccsdshdr[5] & 0xff) + 7;
        if ((len < 16) || len > maxPacketLength) {
            inputStream.close();
            throw new IOException("invalid ccsds packet of length " + len + ". Corrupted file?");
        }
        buffer = Arrays.copyOf(ccsdshdr, len);
        res = inputStream.read(buffer, 16, len - 16);
        if (res != len - 16) {
            inputStream.close();
            throw new IOException("CCSDS packet body short read " + res + "/" + (len - 16));
        }
        if (isPacts) {
            if (inputStream.skip(1) != 1) {// terminator newline
                inputStream.close();
                throw new IOException("no new line at the end of the PaCTS packet");
            }
        }
        return buffer;
    }

    public void close() throws IOException {
        inputStream.close();
    }
}
