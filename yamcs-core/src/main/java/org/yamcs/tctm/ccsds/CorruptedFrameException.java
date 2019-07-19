package org.yamcs.tctm.ccsds;

import org.yamcs.tctm.TcTmException;

/**
 * Exception indicating a frame is corrupted
 * 
 * @author nm
 *
 */
@SuppressWarnings("serial")
public class CorruptedFrameException extends TcTmException {

    public CorruptedFrameException(String msg) {
        super(msg);
    }

    public CorruptedFrameException(String message, Throwable cause) {
        super(message, cause);
    }
}
