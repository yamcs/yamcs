package org.yamcs.utils;

import java.io.IOException;
import java.io.OutputStream;

import org.hornetq.api.core.HornetQBuffer;

/**
 * OutputStream that writes into an HornetQBuffer
 * @author nm
 *
 */
public class HornetQBufferOutputStream extends OutputStream {
    private final HornetQBuffer hqb;

    public HornetQBufferOutputStream(HornetQBuffer out) { 
	this.hqb = out;
    }

    @Override
    public void write(int b) throws IOException {
	hqb.writeByte((byte)(b & 0xff));
    }

    @Override
    public void write (byte[] src, int offset, int length) throws IOException {
	hqb.writeBytes(src, offset, length);
    }
}
