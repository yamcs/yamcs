package org.yamcs.yarch;

/**
 * The simplest stream implementation, just passes data to the subscribers
 * 
 * @author nm
 *
 */
public class InternalStream extends Stream {

    public InternalStream(YarchDatabaseInstance dict, String name, TupleDefinition definition) {
        super(dict, name, definition);
    }

    @Override
    protected void doClose() {
        //nothing to do
    }

    @Override
    public void doStart() {
        //nothing to do
    }
}
