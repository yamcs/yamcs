package org.yamcs;

/**
 * This interface has to be implemented by all services that run as part of a processor.
 * 
 * The implementing classes need to have a constructor with one or two arguments: MyProcessorService (String
 * yamcsInstance) or MyProcessorService (String yamcsInstance, Map&lt;String, Object&gt; config)
 * 
 * The second one will be called if the service is declared in the processor.yaml with "args". For example: services: -
 * class: a.b.c.MyProcessorService args: x: 3 y: "my y config"
 * 
 * Additional config may be passed by the user when the processor is created using the spec parameter in the init method
 */
public interface ProcessorService extends YamcsService {
    public void init(Processor proc);

    /**
     * @param proc
     * @param spec
     *            - passed by the user when creating the processor (for instance via the REST API)
     */
    default void init(Processor proc, Object spec) {
        init(proc);
    }
}
