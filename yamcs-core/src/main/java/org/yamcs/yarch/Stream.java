package org.yamcs.yarch;

import java.util.Collection;

/**
 * Streams are means to transport tuples.
 *
 */
public interface Stream {

    //states
    public final static int SETUP = 0;
    public final static int RUNNING = 1;
    public final static int QUITTING = 2;

    /**
     * Start emitting tuples.
     */
    public abstract void start();

    public abstract TupleDefinition getDefinition();

    public abstract void emitTuple(Tuple t);

    public abstract String getName();

    public abstract void setName(String streamName);

    public abstract void addSubscriber(StreamSubscriber s);

    public abstract void removeSubscriber(StreamSubscriber s);

    public abstract ColumnDefinition getColumnDefinition(String colName);

    /**
     * Closes the stream by:
     *  send the streamClosed signal to all subscribed clients
     */
    public abstract void close();

    public abstract int getState();

    public abstract long getNumEmittedTuples();

    public abstract int getSubscriberCount();

    public abstract Collection<StreamSubscriber> getSubscribers();
    
    public void exceptionHandler(ExceptionHandler h);

    public static interface ExceptionHandler {
        public void handle(Tuple tuple, StreamSubscriber s, Throwable t);
    }

}