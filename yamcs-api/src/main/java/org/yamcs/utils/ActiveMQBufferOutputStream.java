package org.yamcs.utils;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;

/**
 * OutputStream that writes into an ActiveMQBuffer
 * @author nm
 *
 */
public class ActiveMQBufferOutputStream extends OutputStream {
    private final ActiveMQBuffer hqb;

    public ActiveMQBufferOutputStream(ActiveMQBuffer out) { 
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
