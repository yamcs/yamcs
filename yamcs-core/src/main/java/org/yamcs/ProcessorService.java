package org.yamcs;

import com.google.common.util.concurrent.Service;

/**
 * This interface has to be implemented by all services that run as part of a processor.
 * 
 * They need to have a constructor with one or two arguments:
 * MyProcessorService (String yamcsInstance)
 * or
 * MyProcessorService (String yamcsInstance, Map<String, Object> config)
 * 
 * The second one will be called if the service is declared in the processor.yaml with "args". For example:
 *  services:
 *    - class: a.b.c.MyProcessorService
 *      args: 
 *         x: 3
 *         y: "my y config"
 *                
 * Additional config may be passed by the user when the procesor is created using the spec parameter in the init method
 */
public interface ProcessorService extends Service {
    public void init(Processor proc);

    /**
     * @param proc
     * @param spec - passed by the user
     */
    default void init(Processor proc, Object spec) {
        init(proc);
    }
}