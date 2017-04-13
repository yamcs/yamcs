package org.yamcs.xtceproc;

import java.nio.ByteBuffer;
import java.util.Map;

import org.yamcs.parameter.Value;
import org.yamcs.xtce.Argument;

/**
 * Keeps track of where we are when filling in the bits and bytes of a command
 * @author nm
 *
 */
public class TcProcessingContext {
    final ProcessorData pdata;

    ByteBuffer bb;
    public int bitPosition;

    //keep track of the maximum size in bytes to know at the end the buffer required
    public int size;

    //arguments and their values - the lists have the same length all the time and arguments correspond one to one to values
    public Map<Argument,Value> argValues;


    public long generationTime;
    final MetaCommandContainerProcessor mccProcessor; 
    final DataEncodingEncoder deEncoder;

    public TcProcessingContext(ProcessorData pdata, ByteBuffer bb, int bitPosition) {
        this.bb = bb;
        this.bitPosition = bitPosition;
        this.pdata = pdata;
        this.mccProcessor = new MetaCommandContainerProcessor(this);
        this.deEncoder = new DataEncodingEncoder(this);
    }

    public Value getArgumentValue(Argument arg) {
        return argValues.get(arg);
    }

}
