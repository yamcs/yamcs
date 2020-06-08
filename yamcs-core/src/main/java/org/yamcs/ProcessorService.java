package org.yamcs;

/**
 * This interface has to be implemented by all services that run as part of a processor.
 * 
 * <p>
 * The implementing classes need to have one {@link #init(String, YConfiguration)} method called with the configuration passed as "args" in the service 
 * definition in processor.yaml and an extra {@link #init(Processor, Object)} called with the configuration passed by the user when creating the service.
 *   
 * 
 */
public interface ProcessorService extends YamcsService {

    public void init(Processor proc);

    /**
     * @param proc
     * @param spec
     *            passed by the user when creating the processor (for instance via the REST API)
     */
    default void init(Processor proc, Object spec) {
        init(proc);
    }
}
