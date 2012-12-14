package org.yamcs.yarch.management;

import java.util.List;

public interface StreamControl {
    /**
     * Returns the stream name
     */
    String getName();
    
    /**
     * Returns the class name of the stream object
     */
    String getType();
    /**
     * Returns the tuple definition
     */
    String getSchema();
    /**
     * Returns the number of tuples that have transited through the stream
     */
    long getNumEmittedTuples();
    
    int getSubscriberCount();
    
    /**
     * 
     * Returns a list of subscriber.toString()
     */
    List<String> getSubscribers();
  
}
