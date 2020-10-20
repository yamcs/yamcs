package org.yamcs.tctm;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import org.yamcs.YConfiguration;

/**
 * Interface implemented by the classes that read packets from an input stream.
 * <p>
 * It is used by TM data links which work with streams, such as {@link TcpTmDataLink}
 * <p>
 * An object of this class will be instantiated each time a stream (e.g. socket or file) will be open.
 * 
 * <p>
 * Each implementing class has to have a constructor taking a {@link InputStream} and {@link YConfiguration} as
 * arguments.
 * 
 * @author nm
 *
 */
public interface PacketInputStream extends Closeable {
    /**
     * Called each time an input stream is opened to initialize the object
     * 
     * @param stream
     * @param config
     */
    void init(InputStream stream, YConfiguration config);

    /**
     * read the next packet - blocking if necessary until all the data is available.
     * 
     * @return the next packet read from the input stream.
     * 
     * @exception EOFException
     *                if this input stream reaches the end.
     * @exception IOException
     *                an I/O error has occurred
     * @exception PacketTooLongException
     *                if a packet read is longer than a defined limit
     */
    public byte[] readPacket() throws IOException, PacketTooLongException;
}
