package org.yamcs;

import com.google.common.util.concurrent.Service;

/**
 * This interface has to be implemented by all services that run as part of a processor.
 * 
 * 
 */
public interface ProcessorService extends Service {

    /**
     * Returns the valid configuration of the input args of this service.
     * 
     * @return the argument specification, or {@code null} if the args should not be validated.
     */
    default Spec getSpec() {
        return null;
    }

    /**
     * @param proc
     * @param config
     *            service configuration as specified in processor.yaml
     * @param spec
     *            passed by the user when creating the processor (for instance via the REST API)
     * 
     */
    void init(Processor proc, YConfiguration config, Object spec) throws InitException;
}
