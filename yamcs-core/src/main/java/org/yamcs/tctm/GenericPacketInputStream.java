package org.yamcs.tctm;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.logging.Log;
import org.yamcs.utils.ByteArrayUtils;

/**
 * Generic packet reader that splits the stream into packets based on the length of the packet
 * <p>
 * Inspired from Netty's LengthFieldBasedFrameDecoder
 * 
 * <p>
 * The following configuration variables are used
 * <ul>
 * <li>maxPacketLength - the maximum packet length; if a packet with the length greater than this would be received, the
 * input stream is closed and an exception is raised. The length of the packet considered here is the number of data
 * bytes read from the
 * stream - that is including the length field itself and the bytes to strip at the beginning if set (see below)</li>
 * <li>lengthFieldOffset - the offset in the packet where the length is read from</li>
 * <li>lengthFieldLength - the size in bytes of the length field</li>
 * <li>lengthAdjustment - after reading the length from the configured offset, this variable is added to it to determine
 * the real length</li>
 * <li>initialBytesToStrip - after reading the packet, strip this number of bytes from the beginning</li>
 * </ul>
 * 
 * <p>
 * All the above configuration parameters have to be set, otherwise a ConfigurationException will be thrown.
 * 
 * @author nm
 *
 */
public class GenericPacketInputStream implements PacketInputStream {
    private int maxPacketLength;
    private int lengthFieldOffset;
    private int lengthFieldLength;
    private int lengthFieldEndOffset;
    private int lengthAdjustment;
    private int initialBytesToStrip;
    DataInputStream dataInputStream;
    static Log log = new Log(GenericPacketInputStream.class);

    long streamOffset = 0;
    ByteOrder byteOrder;

    @Override
    public void init(InputStream inputStream, YConfiguration args) { // TODO: should have defaults (spec?)
        this.dataInputStream = new DataInputStream(inputStream);
        this.maxPacketLength = args.getInt("maxPacketLength");
        this.lengthFieldOffset = args.getInt("lengthFieldOffset");
        this.lengthFieldLength = args.getInt("lengthFieldLength");
        this.lengthAdjustment = args.getInt("lengthAdjustment");
        this.initialBytesToStrip = args.getInt("initialBytesToStrip");
        this.byteOrder = AbstractPacketPreprocessor.getByteOrder(args);
        lengthFieldEndOffset = lengthFieldOffset + lengthFieldLength;

        if (lengthFieldLength != 1 && lengthFieldLength != 2 && lengthFieldLength != 3 && lengthFieldLength != 4) {
            throw new ConfigurationException("Unsupported legnthFieldLength, supported values are 1,2,3 or 4");
        }
    }

    @Override
    public byte[] readPacket() throws IOException, PacketTooLongException {
        log.trace("Reading packet length of size {} at offset {}", lengthFieldEndOffset, streamOffset);

        byte[] b = new byte[lengthFieldEndOffset];
        dataInputStream.readFully(b);
        int length;
        switch (lengthFieldLength) {
        case 1:
            length = 0xFF & b[lengthFieldOffset];
            break;
        case 2:
            length = byteOrder == ByteOrder.LITTLE_ENDIAN
                    ? ByteArrayUtils.decodeUnsignedShortLE(b, lengthFieldOffset)
                    : ByteArrayUtils.decodeUnsignedShort(b, lengthFieldOffset);
            break;
        case 3:
            length = byteOrder == ByteOrder.LITTLE_ENDIAN
                    ? ByteArrayUtils.decodeUnsigned3BytesLE(b, lengthFieldOffset)
                    : ByteArrayUtils.decodeUnsigned3Bytes(b, lengthFieldOffset);
            break;
        case 4:
            length = byteOrder == ByteOrder.LITTLE_ENDIAN
                    ? ByteArrayUtils.decodeIntLE(b, lengthFieldOffset)
                    : ByteArrayUtils.decodeInt(b, lengthFieldOffset);
            break;
        default:
            throw new IllegalStateException();
        }
        length += lengthAdjustment;
        log.trace("packet length after adjustment: {}", length);

        if (length > maxPacketLength) {
            throw new IOException(
                    "Error reading packet at offset " + streamOffset + ": length " + length
                            + " greater than maximum allowed " + maxPacketLength,
                    new PacketTooLongException(maxPacketLength, length));
        }
        streamOffset += lengthFieldEndOffset;
        byte[] packet = new byte[length - initialBytesToStrip];
        int offset;
        if (initialBytesToStrip <= lengthFieldEndOffset) {
            offset = lengthFieldEndOffset - initialBytesToStrip;
            System.arraycopy(b, initialBytesToStrip, packet, 0, offset);
        } else {
            offset = 0;
            int skip = initialBytesToStrip - lengthFieldEndOffset;
            skipFully(dataInputStream, skip);
            streamOffset += skip;
        }
        dataInputStream.readFully(packet, offset, packet.length - offset);
        streamOffset += (packet.length - offset);

        return packet;
    }

    static void skipFully(InputStream in, int n) throws IOException {
        while (n > 0) {
            long skipped = in.skip(n);
            if (skipped == 0)
                throw new EOFException("Tried to skip " + n + " but reached EOF");
            n -= skipped;

        }
    }

    @Override
    public void close() throws IOException {
        dataInputStream.close();
    }
}
