package org.yamcs;

import com.google.common.util.concurrent.Service;

/**
 * Required interface of a Yamcs Service. A Yamcs Service is a Guava service with hooks in the Yamcs configuration
 * system.
 */
public interface YamcsService extends Service {

    /**
     * Returns the valid configuration options for this service.
     * 
     * @return the argument specification, or {@code null} if the args should not be validated.
     */
    public default Spec getSpec() {
        return null;
    }

    /**
     * returns the instance name
     * 
     * @return
     */
    public String getYamcsInstance();

    /**
     * Initialize this service. This is called before the service is started. All operations should finish fast.
     * 
     * @param yamcsInstance
     *            The yamcs instance, or {@code null} if this is a global service.
     * @param serviceName
     *            The service name.
     * @param config
     *            The configured arguments for this service. If {@link #getSpec()} is implemented then this contains the
     *            arguments after being validated (including any defaults).
     * @throws InitException
     *             When something goes wrong during the execution of this method.
     */
    public default void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
    }
}
