package org.yamcs.management;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.yamcs.ConnectedClient;

public class ClientControlImpl extends StandardMBean implements ClientControl {
    ConnectedClient client;

    public ClientControlImpl(ConnectedClient client) throws NotCompliantMBeanException {
        super(ClientControl.class);
        this.client = client;
    }

    @Override
    public String getApplicationName() {
        return client.getApplicationName();
    }

    public ConnectedClient getClient() {
        return client;
    }
}
