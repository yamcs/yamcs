package org.yamcs;


/**
 * A client of a processor
 * @author nm
 *
 */
public interface ProcessorClient {
    /**
     * change the connection to another processor
     * @param p - processor 
     */
    public void switchProcessor(Processor p) throws ProcessorException;
    /**
     * called when the processor is closing down
     */
    void processorQuit();

    /**    
     * @return the current processor the client is connected to 
     */
    public Processor getProcessor();
    
    public String getUsername();
    public String getApplicationName();
}
