package org.yamcs.tctm;

import java.io.EOFException;
import java.io.IOException;

/**
 * Interface implemented by the classes that read packets from an input stream. 
 * 
 * @author nm
 *
 */
public interface PacketInputStream {
   /**
    * read the next packet - blocking if necessary until all the data is available.
    * 
    * @return the next packet read from the input stream.
    * 
    * @exception EOFException if this input stream reaches the end before reading eight bytes.
    * @exception IOException an I/O error has occurred
    */
   public byte[] readPacket() throws IOException;
}
