package org.yamcs;

import org.yamcs.security.User;
import org.yamcs.utils.TimeEncoding;

/**
 * A client connected to Yamcs
 * 
 * Clients may optionally be connected with a processor.
 */
public class ConnectedClient {

    private int clientId;

    private User user;
    private String applicationName;
    private long loginTime;
    private Processor processor;

    public ConnectedClient(User user, String applicationName) {
        this(user, applicationName, null);
    }

    public ConnectedClient(User user, String applicationName, String yamcsInstance) {
        this.user = user;
        this.applicationName = applicationName;
        loginTime = TimeEncoding.getWallclockTime();
        if (yamcsInstance != null) {
            processor = Processor.getFirstProcessor(yamcsInstance);
        }
    }

    public int getId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    /**
     * The user associated with the connected client
     */
    public User getUser() {
        return user;
    }

    /**
     * Descriptive application name of the connected client
     */
    public String getApplicationName() {
        return applicationName;
    }

    /**
     * The time at which the client connected to Yamcs
     */
    public long getLoginTime() {
        return loginTime;
    }

    /**
     * Select a processor for this client.
     * 
     * @param processor
     *            the processor to select
     */
    public void selectProcessor(Processor processor) throws ProcessorException {
        this.processor = processor;
    }

    /**
     * Called when the processor is closing down
     */
    public void processorQuit() {
    }

    /**
     * @return the current processor the client is connected to (if any).
     */
    public Processor getProcessor() {
        return processor;
    }
}
